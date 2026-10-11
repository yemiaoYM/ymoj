#!/usr/bin/env bash
# Native Linux entry point. Never source .env or print credential values.
set -euo pipefail
umask 077

fail() { printf 'Error: %s\n' "$*" >&2; exit 1; }
root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)
action=${1:-help}
if (($#)); then shift; fi
port=18333
delete_data=false
while (($#)); do
    case "$1" in
        --s3-port) (($# >= 2)) || fail 'Missing S3 port.'; port=$2; shift 2 ;;
        --delete-data) delete_data=true; shift ;;
        *) fail "Unknown option: $1" ;;
    esac
done
case "$action" in
    help) printf 'Usage: bash seaweedfs/manage.sh {init|start|bootstrap|admin|status|stop|reset} [--s3-port 18333] [--delete-data]\n'; exit 0 ;;
    init|start|bootstrap|admin|status|stop|reset) ;;
    *) fail 'Unknown action. Use help for usage.' ;;
esac
[[ $delete_data == false || $action == reset ]] || fail '--delete-data is only valid with reset.'
[[ $action != reset || $delete_data == true ]] || fail 'Reset permanently deletes SeaweedFS objects and credentials. Specify --delete-data explicitly.'
[[ $port =~ ^[0-9]{4,5}$ ]] && ((10#$port >= 1024 && 10#$port <= 65535)) || fail 'S3 port must be between 1024 and 65535.'
for dependency in openssl flock stat; do
    command -v "$dependency" >/dev/null || fail "Install $dependency before running this script."
done
# Reject symlink parents as well as individual credential files.
cursor=$root
while [[ $cursor != / ]]; do
    [[ ! -L $cursor ]] || fail 'Configuration directories must not be symbolic links.'
    cursor=$(dirname -- "$cursor")
done
mkdir -p -- "$root"
secrets=$root/secrets
[[ ! -L $secrets && (! -e $secrets || -d $secrets) ]] || fail 'Invalid secrets directory.'
mkdir -p -- "$secrets"
chmod 700 "$secrets"
for file in .env secrets/s3.json secrets/admin.options secrets/.setup.lock; do
    [[ ! -L $root/$file && (! -e $root/$file || -f $root/$file) ]] || fail "Invalid configuration path: $file"
done
exec 9>"$secrets/.setup.lock"
flock -x 9
chmod 600 "$secrets/.setup.lock"
temporary_files=()
cleanup() {
    if ((${#temporary_files[@]})); then rm -f -- "${temporary_files[@]}"; fi
}
trap cleanup EXIT
new_temporary() {
    temporary=$(mktemp "$secrets/setup.XXXXXXXX")
    temporary_files+=("$temporary")
}
protect_mounted() {
    # The container is 1000:1000. Only its group may read this root-owned bind file.
    chgrp 1000 "$1" || fail 'Cannot assign container group 1000. Run this command with sudo.'
    chmod 640 "$1"
}
declare -A values=()
read_environment() {
    [[ -f $root/.env ]] || fail 'Run init or start first.'
    local line key value
    while IFS= read -r line || [[ -n $line ]]; do
        line=${line%$'\r'}
        [[ -z $line || $line == \#* ]] && continue
        [[ $line == *=* ]] || fail 'Invalid deployment environment file.'
        key=${line%%=*}; value=${line#*=}
        [[ $key =~ ^[A-Z][A-Z0-9_]*$ && ! ${values[$key]+present} ]] || fail 'Invalid or duplicate environment key.'
        values[$key]=$value
    done < "$root/.env"
    local public=${values[YMCLOUD_S3_PUBLIC_BUCKET]:-} private=${values[YMCLOUD_S3_PRIVATE_BUCKET]:-} bucket
    [[ $public != "$private" ]] || fail 'Public and private buckets must be different.'
    for bucket in "$public" "$private"; do
        [[ $bucket =~ ^[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]$ && $bucket != *..* ]] || fail 'Invalid storage bucket name.'
    done
}
initialize() {
    [[ -f $root/.env && -f $secrets/s3.json || ! -e $root/.env && ! -e $secrets/s3.json ]] || fail 'Incomplete credential set. Restore .env and secrets together; keys will not be overwritten.'
    if [[ ! -f $root/.env ]]; then
        local app_access app_secret admin_access admin_secret env_temporary
        app_access=$(openssl rand -hex 16); app_secret=$(openssl rand -hex 32)
        admin_access=$(openssl rand -hex 16); admin_secret=$(openssl rand -hex 32)
        new_temporary
        cat > "$temporary" <<EOF
SEAWEEDFS_IMAGE=chrislusf/seaweedfs:4.48
YMCLOUD_S3_PORT=$port
YMCLOUD_S3_ENDPOINT=http://127.0.0.1:$port
YMCLOUD_S3_REGION=us-east-1
YMCLOUD_S3_PUBLIC_BUCKET=ymcloud-public
YMCLOUD_S3_PRIVATE_BUCKET=ymcloud-private
YMCLOUD_S3_ACCESS_KEY=$app_access
YMCLOUD_S3_SECRET_KEY=$app_secret
YMCLOUD_S3_ADMIN_ACCESS_KEY=$admin_access
YMCLOUD_S3_ADMIN_SECRET_KEY=$admin_secret
EOF
        env_temporary=$temporary
        new_temporary
        cat > "$temporary" <<EOF
{"identities":[{"name":"ymcloud-bootstrap","credentials":[{"accessKey":"$admin_access","secretKey":"$admin_secret"}],"actions":["Admin","Read","Write","List","Tagging"]},{"name":"ymcloud-application","credentials":[{"accessKey":"$app_access","secretKey":"$app_secret"}],"actions":["Read:ymcloud-public","Write:ymcloud-public","List:ymcloud-public","Read:ymcloud-private","Write:ymcloud-private","List:ymcloud-private"]}]}
EOF
        protect_mounted "$temporary"
        mv -- "$temporary" "$secrets/s3.json"
        mv -- "$env_temporary" "$root/.env"
        printf 'Generated credentials. Values are not displayed.\n'
    else
        printf 'Reusing existing credentials and data.\n'
    fi
    chmod 600 "$root/.env"
    protect_mounted "$secrets/s3.json"
    if [[ -f $secrets/admin.options ]]; then protect_mounted "$secrets/admin.options"; fi
    read_environment
}
compose() { docker compose --project-directory "$root" -f "$root/compose.yml" "$@"; }
require_docker() {
    command -v docker >/dev/null || fail 'Install Docker and Docker Compose v2 first.'
    docker compose version >/dev/null || fail 'Docker Compose v2 is required.'
    docker info --format '{{.OSType}}' >/dev/null || fail 'Docker is not running or access is denied.'
}
check_mount() {
    compose --profile admin-ui run --rm --no-deps --entrypoint /bin/sh "$1" -c "test -r /etc/seaweedfs/$2" || fail 'Container cannot read its credential bind mount. Check group mapping and host file permissions.'
}
restart_failed() {
    local id state
    id=$(compose --profile admin-ui ps --all --quiet "$1")
    if [[ -n $id ]]; then
        state=$(docker inspect --format '{{.State.Status}} {{if .State.Health}}{{.State.Health.Status}}{{end}}' "$id")
        if [[ $state != 'running healthy' ]]; then compose --profile admin-ui restart "$1"; fi
    fi
}
bucket_shell() {
    local output
    if ! output=$(printf '%s\n' "$1" | compose exec -T filer weed shell -master=master:9333 -filer=filer:8888 2>&1); then
        fail 'Bucket initialization failed. Check master and filer health.'
    fi
    if printf '%s\n' "$output" | grep -qiE '^[[:space:]]*error:'; then fail 'SeaweedFS reported a bucket initialization error.'; fi
    printf '%s\n' "$output"
}
bootstrap() {
    local existing verified bucket
    existing=$(bucket_shell 's3.bucket.list' | awk '$2 ~ /^size:/ {print $1}')
    for bucket in "${values[YMCLOUD_S3_PUBLIC_BUCKET]}" "${values[YMCLOUD_S3_PRIVATE_BUCKET]}"; do
        if ! grep -qxF "$bucket" <<< "$existing"; then
            bucket_shell "s3.bucket.create -name $bucket -owner ymcloud-bootstrap" >/dev/null
        fi
    done
    verified=$(bucket_shell 's3.bucket.list' | awk '$2 ~ /^size:/ {print $1}')
    for bucket in "${values[YMCLOUD_S3_PUBLIC_BUCKET]}" "${values[YMCLOUD_S3_PRIVATE_BUCKET]}"; do
        grep -qxF "$bucket" <<< "$verified" || fail 'A required bucket is still missing.'
    done
    printf 'Public and private buckets verified; existing objects preserved.\n'
}
enable_admin() {
    local options=$secrets/admin.options
    if [[ ! -f $options ]]; then
        new_temporary
        printf 'adminUser=admin\nadminPassword=%s\n' "$(openssl rand -hex 16)" > "$temporary"
        protect_mounted "$temporary"
        mv -- "$temporary" "$options"
    fi
    tr -d '\r' < "$options" | grep -qx 'adminUser=admin' || fail 'Invalid Admin UI username. Restore the original file.'
    grep -qE '^adminPassword=[^[:space:]]+' "$options" || fail 'Incomplete Admin UI credentials. Restore the original file.'
    protect_mounted "$options"
    check_mount admin admin.options
    restart_failed admin
    compose --profile admin-ui up -d --no-deps --no-recreate --wait --wait-timeout 180 admin
    printf 'Admin UI ready. Username: admin. Password remains in secrets/admin.options.\n'
}
case "$action" in
    init) initialize ;;
    start)
        initialize
        require_docker
        check_mount s3 s3.json
        restart_failed s3
        if ! compose up -d --no-recreate --wait --wait-timeout 180; then
            compose ps --all
            fail 'Startup failed. Inspect service logs; do not print .env or expanded compose configuration.'
        fi
        bootstrap
        ;;
    bootstrap) read_environment; require_docker; bootstrap ;;
    status) require_docker; compose --profile admin-ui ps --all ;;
    stop) require_docker; compose --profile admin-ui stop ;;
    reset)
        require_docker
        compose --profile admin-ui down --volumes --remove-orphans || fail 'Reset failed; credential files have been preserved.'
        rm -f -- "$root/.env" "$secrets/s3.json" "$secrets/admin.options"
        printf 'SeaweedFS data and credentials cleared. Install again, then update OJ through its admin page.\n'
        ;;
    admin)
        [[ -f $root/.env && -f $secrets/s3.json ]] || fail 'Run start before enabling Admin UI.'
        initialize
        require_docker
        enable_admin
        ;;
esac
