#!/usr/bin/env bash
# Isolated Linux regression: never uses the real deployment's volumes or credentials.
set -euo pipefail
umask 077
if (($# > 1)) || [[ ${1:-} != '' && ${1:-} != --docker ]]; then
    printf 'Usage: bash ymcloud-web/src/test/scripts/storage/verify_linux.sh [--docker]\n' >&2
    exit 1
fi
command -v python3 >/dev/null || { printf 'Regression checks require Python 3.\n' >&2; exit 1; }
if [[ ${1:-} == --docker ]]; then
    [[ $(id -u) == 0 ]] || { printf 'Use sudo for the root-owned bind-mount regression.\n' >&2; exit 1; }
    docker info --format '{{.OSType}}' >/dev/null || { printf 'Docker is unavailable; check daemon access or WSL integration.\n' >&2; exit 1; }
    docker image inspect --format '{{.Id}}' oj-python-env:latest >/dev/null || {
        printf 'Build the OJ Python sandbox image before Docker regression checks.\n' >&2
        exit 1
    }
fi
test_root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)
source_root=${YMCLOUD_STORAGE_SOURCE_DIR:-$(cd -- "$test_root/../../../../../seaweedfs" && pwd -P)}
fixture=$(mktemp -d "${YMCLOUD_STORAGE_TEST_TMPDIR:-/tmp}/ymcloud-storage-test.XXXXXXXX")
deployment=$fixture/seaweedfs
mkdir "$deployment"
cp "$source_root/manage.sh" "$source_root/compose.yml" "$test_root/verify_storage.py" "$deployment/"
export COMPOSE_PROJECT_NAME=ymcloud-linux-test-${fixture##*.}
export COMPOSE_PROJECT_NAME=${COMPOSE_PROJECT_NAME,,}
checks=0
docker_started=false
compose() { docker compose --project-directory "$deployment" -f "$deployment/compose.yml" --profile admin-ui "$@"; }
cleanup() {
    if [[ $docker_started == true ]]; then
        [[ $COMPOSE_PROJECT_NAME == ymcloud-linux-test-* ]] || return 1
        compose down -v --remove-orphans >/dev/null
    fi
    # Keep private fixtures/logs for diagnosis; cleanup applies only to these test volumes.
}
trap cleanup EXIT
pass() { checks=$((checks + 1)); printf 'PASS %s\n' "$1"; }
reject() {
    if "$@" > "$fixture/rejection.log" 2>&1; then printf 'Expected rejection did not occur.\n' >&2; exit 1; fi
}
run() { bash "$deployment/manage.sh" "$@"; }
run init --s3-port 18444 > "$fixture/init.log"
[[ ! -e $deployment/application-storage.local.yml && ! -e $deployment/secrets/storage-config.key ]]
pass 'SeaweedFS installation does not generate OJ configuration or master key'
[[ $(stat -c '%a:%g' "$deployment/secrets/s3.json") == 640:1000 ]]
[[ $(stat -c '%a' "$deployment/.env") == 600 ]]
[[ $(stat -c '%a' "$deployment/secrets") == 700 ]]
pass 'Linux credential modes and container group'
sha256sum "$deployment/.env" "$deployment/secrets/s3.json" > "$fixture/hashes"
reject run reset
sha256sum --status -c "$fixture/hashes"
pass 'Reset requires explicit data deletion and preserves rejected credentials'
chmod 600 "$deployment/secrets/s3.json"
run init --s3-port 18555 >> "$fixture/init.log"
sha256sum --status -c "$fixture/hashes"
[[ $(stat -c '%a:%g' "$deployment/secrets/s3.json") == 640:1000 ]]
pass 'Repeated init repairs old permissions without changing credentials or port'
mv "$deployment/secrets/s3.json" "$fixture/s3-backup"
reject run init
[[ ! -e $deployment/secrets/s3.json ]]
mv "$fixture/s3-backup" "$deployment/secrets/s3.json"
pass 'Incomplete S3 credentials refuse regeneration'
mv "$deployment/secrets/s3.json" "$fixture/s3-backup"
ln -s "$fixture/s3-backup" "$deployment/secrets/s3.json"
reject run init
rm -- "$deployment/secrets/s3.json"
mv "$fixture/s3-backup" "$deployment/secrets/s3.json"
pass 'Symlink credential file refused'
cp "$deployment/.env" "$fixture/environment-backup"
printf 'YMCLOUD_S3_PUBLIC_BUCKET=same-bucket\nYMCLOUD_S3_PRIVATE_BUCKET=same-bucket\n' > "$deployment/.env"
reject run init
cp "$fixture/environment-backup" "$deployment/.env"
pass 'Public and private buckets cannot coincide'
printf 'INERT_VALUE=$(touch %s)\n' "$fixture/should-not-exist" >> "$deployment/.env"
run init >> "$fixture/init.log"
[[ ! -e $fixture/should-not-exist ]]
cp "$fixture/environment-backup" "$deployment/.env"
pass 'Environment file is parsed without executing shell expressions'
mkdir "$fixture/mock"
export YMCLOUD_TEST_BUCKET_STATE=$fixture/buckets
: > "$YMCLOUD_TEST_BUCKET_STATE"
cat > "$fixture/mock/docker" <<'MOCK'
#!/usr/bin/env bash
set -eu
if [[ $* == *' exec '* ]]; then
    read -r command
    if [[ ${YMCLOUD_TEST_BUCKET_ERROR:-false} == true ]]; then
        printf 'error: mock shell failed\n'
    elif [[ $command == s3.bucket.list ]]; then
        while IFS= read -r bucket; do printf '%s size:0\n' "$bucket"; done < "$YMCLOUD_TEST_BUCKET_STATE"
    elif [[ $command =~ ^s3.bucket.create\ -name\ ([a-z0-9.-]+)\ -owner\ ymcloud-bootstrap$ ]]; then
        if grep -qxF "${BASH_REMATCH[1]}" "$YMCLOUD_TEST_BUCKET_STATE"; then exit 1; fi
        printf '%s\n' "${BASH_REMATCH[1]}" >> "$YMCLOUD_TEST_BUCKET_STATE"
    else
        exit 1
    fi
fi
MOCK
chmod 700 "$fixture/mock/docker"
PATH="$fixture/mock:$PATH" run bootstrap > "$fixture/bootstrap.log"
[[ $(wc -l < "$YMCLOUD_TEST_BUCKET_STATE") == 2 ]]
PATH="$fixture/mock:$PATH" run bootstrap >> "$fixture/bootstrap.log"
[[ $(wc -l < "$YMCLOUD_TEST_BUCKET_STATE") == 2 ]]
pass 'Bootstrap creates missing buckets and preserves existing buckets'
PATH="$fixture/mock:$PATH" YMCLOUD_TEST_BUCKET_ERROR=true reject run bootstrap
pass 'Shell error with zero exit status does not report bootstrap success'
python3 - "$deployment" "$fixture" <<'PY'
import json, pathlib, sys
root, fixture = map(pathlib.Path, sys.argv[1:])
identities = json.loads((root / 'secrets/s3.json').read_text())['identities']
assert 'Admin' not in identities[1]['actions']
logs = ''.join(p.read_text() for p in fixture.glob('*.log'))
for identity in identities:
    for credential in identity['credentials']:
        assert credential['accessKey'] not in logs and credential['secretKey'] not in logs
PY
pass 'Application has no Admin privilege; no credentials in setup logs'

if [[ ${1:-} == --docker ]]; then
    docker_started=true
    chmod 600 "$deployment/secrets/s3.json"
    if compose run --rm --no-deps --entrypoint /bin/sh s3 -c 'test -r /etc/seaweedfs/s3.json' > "$fixture/denied.log" 2>&1; then
        printf 'Root-owned 600 file unexpectedly readable by container UID 1000.\n' >&2; exit 1
    fi
    pass 'Reproduced original root-owned 600 bind-mount failure'
    run start > "$fixture/start.log" 2>&1
    if compose run --rm --no-deps --user 1001:1001 --entrypoint /bin/sh s3 -c 'test -r /etc/seaweedfs/s3.json' > "$fixture/unrelated-user.log" 2>&1; then
        printf 'Credential file unexpectedly readable by unrelated container user.\n' >&2; exit 1
    fi
    pass 'Credential bind mount denies an unrelated user and group'
    [[ $(compose ps --status running --quiet | wc -l) == 4 ]]
    pass 'Native Linux start brings all four services healthy'
    compose ps --quiet | sort > "$fixture/container-ids"
    run start > "$fixture/start-repeat.log" 2>&1
    compose ps --quiet | sort > "$fixture/repeated-container-ids"
    cmp "$fixture/container-ids" "$fixture/repeated-container-ids"
    sha256sum --status -c "$fixture/hashes"
    pass 'Repeated start preserves container identities, keys and objects'
    # Use the fixture network because WSL may not receive Desktop loopback forwarding.
    cp "$deployment/.env" "$fixture/client.env"
    sed -i 's|^YMCLOUD_S3_ENDPOINT=.*|YMCLOUD_S3_ENDPOINT=http://s3:8333|' "$fixture/client.env"
    storage_check() {
        docker run --rm --user 0:0 --network "${COMPOSE_PROJECT_NAME}_access" \
            --mount "type=bind,source=$deployment,target=/checks,readonly" \
            --mount "type=bind,source=$fixture/client.env,target=/checks/.env,readonly" \
            --mount "type=bind,source=$fixture,target=/target" \
            --entrypoint python3 oj-python-env:latest /checks/verify_storage.py \
            --deployment-dir /checks --report-dir /target/storage "$@"
    }
    storage_check --persistence-write --iterations 0 > "$fixture/persistence-write.log" 2>&1
    # Exercise recovery of an existing unhealthy S3 process, preserving container identity.
    s3_id=$(compose ps --quiet s3)
    chmod 600 "$deployment/secrets/s3.json"
    compose restart s3 > "$fixture/restart.log" 2>&1
    run start > "$fixture/recovery.log" 2>&1
    [[ $(compose ps --quiet s3) == "$s3_id" ]]
    sha256sum --status -c "$fixture/hashes"
    pass 'Existing failed S3 recovers without replacing container or keys'
    run admin > "$fixture/admin.log" 2>&1
    sha256sum "$deployment/secrets/admin.options" > "$fixture/admin-hashes"
    chmod 600 "$deployment/secrets/admin.options"
    run admin > "$fixture/admin-repeat.log" 2>&1
    sha256sum --status -c "$fixture/admin-hashes"
    [[ $(stat -c '%a:%g' "$deployment/secrets/admin.options") == 640:1000 ]]
    pass 'Admin UI starts and repeat preserves password while repairing permissions'
    docker run --rm -i --user 0:0 --network "${COMPOSE_PROJECT_NAME}_access" \
        --mount "type=bind,source=$deployment,target=/checks,readonly" \
        --entrypoint python3 oj-python-env:latest - > "$fixture/admin-login.log" 2>&1 <<'PY'
import html.parser, http.cookiejar, pathlib, urllib.parse, urllib.request
class LoginForm(html.parser.HTMLParser):
    token = None
    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        if tag == 'input' and attrs.get('name') == 'csrf_token':
            self.token = attrs.get('value')
values = dict(line.split('=', 1) for line in pathlib.Path('/checks/secrets/admin.options').read_text().splitlines())
cookies = http.cookiejar.CookieJar()
opener = urllib.request.build_opener(urllib.request.ProxyHandler({}), urllib.request.HTTPCookieProcessor(cookies))
form = LoginForm()
form.feed(opener.open('http://admin:23646/login', timeout=30).read().decode())
assert form.token, 'Login CSRF token missing'
body = urllib.parse.urlencode({'username': values['adminUser'], 'password': values['adminPassword'], 'csrf_token': form.token}).encode()
response = opener.open('http://admin:23646/login', body, timeout=30)
assert urllib.parse.urlparse(response.url).path != '/login', 'Admin login did not succeed'
assert list(cookies), 'Authenticated session cookie missing'
PY
    pass 'Preserved administrator credentials authenticate successfully'
    compose restart > "$fixture/full-restart.log" 2>&1
    compose up -d --no-recreate --wait --wait-timeout 180 > "$fixture/healthy-again.log" 2>&1
    storage_check --persistence-check --iterations 0 > "$fixture/persistence-check.log" 2>&1
    pass 'Objects survive full service restart with identical SHA-256'
    storage_check --persistence-write --iterations 0 > "$fixture/recreate-write.log" 2>&1
    compose down > "$fixture/container-removal.log" 2>&1
    run start > "$fixture/recreated-start.log" 2>&1
    run admin > "$fixture/recreated-admin.log" 2>&1
    sha256sum --status -c "$fixture/admin-hashes"
    storage_check --persistence-check --iterations 0 > "$fixture/recreate-check.log" 2>&1
    pass 'Removing and recreating containers preserves data and administrator password'
    storage_check --security --iterations 20 --concurrency 4 > "$fixture/s3-checks.log" 2>&1
    pass 'Authenticated S3 round trips, anonymous denial and security regression'
    printf 'legacy-oj-key-must-be-preserved' > "$deployment/secrets/storage-config.key"
    run reset --delete-data > "$fixture/reset.log" 2>&1
    [[ ! -e $deployment/.env && ! -e $deployment/secrets/s3.json && ! -e $deployment/secrets/admin.options ]]
    [[ $(cat "$deployment/secrets/storage-config.key") == legacy-oj-key-must-be-preserved ]]
    [[ -z $(docker ps --all --quiet --filter "label=com.docker.compose.project=$COMPOSE_PROJECT_NAME") ]]
    [[ -z $(docker volume ls --quiet --filter "label=com.docker.compose.project=$COMPOSE_PROJECT_NAME") ]]
    pass 'Reset deletes only this project and SeaweedFS credentials, preserving the OJ key'
    run start > "$fixture/from-zero.log" 2>&1
    if sha256sum --status -c "$fixture/hashes"; then
        printf 'Reset failed to generate fresh credentials.\n' >&2; exit 1
    fi
    cp "$deployment/.env" "$fixture/client.env"
    sed -i 's|^YMCLOUD_S3_ENDPOINT=.*|YMCLOUD_S3_ENDPOINT=http://s3:8333|' "$fixture/client.env"
    storage_check --security --iterations 0 > "$fixture/from-zero-check.log" 2>&1
    pass 'Fresh install after reset generates working new credentials'
fi
printf 'PASS %s Linux deployment checks. Private diagnostics: %s\n' "$checks" "$fixture"
