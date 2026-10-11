[CmdletBinding()]
param(
    [ValidateSet('init', 'start', 'bootstrap', 'admin', 'status', 'stop', 'reset')]
    [string]$Action = 'start',
    [ValidateRange(1024, 65535)]
    [int]$S3Port = 18333,
    [switch]$DeleteData
)

$ErrorActionPreference = 'Stop'
if ($DeleteData -and $Action -ne 'reset') {
    throw '-DeleteData is only valid with -Action reset.'
}
if ($Action -eq 'reset' -and -not $DeleteData) {
    throw 'Reset permanently deletes SeaweedFS objects and credentials. Specify -DeleteData explicitly.'
}
if ($env:OS -ne 'Windows_NT') {
    $arguments = @((Join-Path $PSScriptRoot 'manage.sh'), $Action, '--s3-port', $S3Port)
    if ($DeleteData) {
        $arguments += '--delete-data'
    }
    & bash @arguments
    if ($LASTEXITCODE -ne 0) {
        throw 'SeaweedFS operation failed. See the Bash diagnostic above.'
    }
    return
}

$utf8 = [System.Text.UTF8Encoding]::new($false)
$composeArgs = @('compose', '--project-directory', $PSScriptRoot, '-f', (Join-Path $PSScriptRoot 'compose.yml'))

function Set-PrivatePath {
    param([string]$Path, [switch]$Directory)
    $owner = [System.Security.Principal.WindowsIdentity]::GetCurrent()
    $permissions = if ($Directory) { '(OI)(CI)F' } else { 'F' }
    $command = Join-Path $env:SystemRoot 'System32/icacls.exe'
    & $command $Path /inheritance:r /grant:r ($owner.Name + ':' + $permissions) ('*S-1-5-18:' + $permissions) | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw 'Unable to restrict credential permissions.'
    }
    $acl = Get-Acl -LiteralPath $Path
    foreach ($rule in $acl.Access) {
        $identity = $rule.IdentityReference.Translate([System.Security.Principal.SecurityIdentifier]).Value
        if ($rule.AccessControlType -eq 'Allow' -and $identity -notin @($owner.User.Value, 'S-1-5-18')) {
            & $command $Path /remove:g ('*' + $identity) | Out-Null
            if ($LASTEXITCODE -ne 0) {
                throw 'Unable to remove an unexpected credential access grant.'
            }
        }
    }
}
function Write-PrivateText {
    param([string]$Path, [string]$Text)
    $stream = [System.IO.File]::Open($Path, [System.IO.FileMode]::CreateNew)
    $stream.Dispose()
    Set-PrivatePath -Path $Path
    [System.IO.File]::WriteAllText($Path, $Text, $utf8)
}

function Require-Docker {
    & docker compose version | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw 'Docker Compose v2 is required.'
    }
    $type = & docker info --format '{{.OSType}}'
    if ($LASTEXITCODE -ne 0 -or $type -ne 'linux') {
        throw 'Start Docker with Linux containers before running this operation.'
    }
}

function Test-CredentialMount {
    param([string]$Service, [string]$File)
    & docker @composeArgs --profile admin-ui run --rm --no-deps --entrypoint /bin/sh $Service -c "test -r /etc/seaweedfs/$File"
    if ($LASTEXITCODE -ne 0) {
        throw 'Container cannot read its credential mount. Check file permissions and Docker access.'
    }
}

function Restart-FailedService {
    param([string]$Service)
    $id = & docker @composeArgs --profile admin-ui ps --all --quiet $Service
    if ($LASTEXITCODE -ne 0) { throw 'Unable to read container state.' }
    if ($id) {
        $state = & docker inspect --format '{{.State.Status}} {{if .State.Health}}{{.State.Health.Status}}{{end}}' $id
        if ($LASTEXITCODE -ne 0) { throw 'Unable to inspect container state.' }
        if ($state -ne 'running healthy') {
            & docker @composeArgs --profile admin-ui restart $Service
            if ($LASTEXITCODE -ne 0) { throw 'Unable to restart the failed service.' }
        }
    }
}

function Initialize-Storage {
    $storageRoot = $PSScriptRoot
    $envFile = Join-Path $storageRoot '.env'
    $secretsRoot = Join-Path $storageRoot 'secrets'
    $s3ConfigFile = Join-Path $secretsRoot 's3.json'
    $utf8 = [System.Text.UTF8Encoding]::new($false)

    function New-Secret {
        param([int]$Bytes = 32)
        $buffer = [byte[]]::new($Bytes)
        $random = [System.Security.Cryptography.RandomNumberGenerator]::Create()
        try {
            $random.GetBytes($buffer)
        }
        finally {
            $random.Dispose()
        }
        return ([System.BitConverter]::ToString($buffer)).Replace('-', '').ToLowerInvariant()
    }

    if ((Test-Path -LiteralPath $envFile) -ne (Test-Path -LiteralPath $s3ConfigFile)) {
        throw 'The credential set is incomplete. Recover .env and secrets together from backup; credentials are not overwritten automatically.'
    }

    if (-not (Test-Path -LiteralPath $envFile)) {
        [System.IO.Directory]::CreateDirectory($secretsRoot) | Out-Null
        $appAccessKey = New-Secret 16
        $appSecretKey = New-Secret
        $adminAccessKey = New-Secret 16
        $adminSecretKey = New-Secret
        $identities = @{
            identities = @(
                @{
                    name = 'ymcloud-bootstrap'
                    credentials = @(@{ accessKey = $adminAccessKey; secretKey = $adminSecretKey })
                    actions = @('Admin', 'Read', 'Write', 'List', 'Tagging')
                },
                @{
                    name = 'ymcloud-application'
                    credentials = @(@{ accessKey = $appAccessKey; secretKey = $appSecretKey })
                    actions = @(
                        'Read:ymcloud-public', 'Write:ymcloud-public', 'List:ymcloud-public',
                        'Read:ymcloud-private', 'Write:ymcloud-private', 'List:ymcloud-private'
                    )
                }
            )
        }
        Write-PrivateText -Path $s3ConfigFile -Text ($identities | ConvertTo-Json -Depth 8)
        $envContent = @(
            'SEAWEEDFS_IMAGE=chrislusf/seaweedfs:4.48'
            "YMCLOUD_S3_PORT=$S3Port"
            "YMCLOUD_S3_ENDPOINT=http://127.0.0.1:$S3Port"
            'YMCLOUD_S3_REGION=us-east-1'
            'YMCLOUD_S3_PUBLIC_BUCKET=ymcloud-public'
            'YMCLOUD_S3_PRIVATE_BUCKET=ymcloud-private'
            "YMCLOUD_S3_ACCESS_KEY=$appAccessKey"
            "YMCLOUD_S3_SECRET_KEY=$appSecretKey"
            "YMCLOUD_S3_ADMIN_ACCESS_KEY=$adminAccessKey"
            "YMCLOUD_S3_ADMIN_SECRET_KEY=$adminSecretKey"
        ) -join "`n"
        Write-PrivateText -Path $envFile -Text "$envContent`n"
        Write-Host 'Generated local credentials in .env and secrets/. Values are intentionally not displayed.'
    }
    else {
        Write-Host 'Reusing existing credentials; initialization does not rotate credentials or erase volumes.'
    }

    Set-PrivatePath -Path $envFile
    Set-PrivatePath -Path $secretsRoot -Directory
    foreach ($secretFile in @(Get-ChildItem -LiteralPath $secretsRoot -File | Where-Object { $_.Name -ne '.setup.lock' })) {
        Set-PrivatePath -Path $secretFile.FullName
    }
}

function Initialize-Buckets {
    $envFile = Join-Path $PSScriptRoot '.env'
    if (-not (Test-Path -LiteralPath $envFile -PathType Leaf)) {
        throw 'Run install.ps1 -Action start before initializing buckets.'
    }
    $values = @{}
    foreach ($line in (Get-Content -LiteralPath $envFile -Encoding UTF8)) {
        if ([string]::IsNullOrWhiteSpace($line) -or $line.StartsWith('#')) {
            continue
        }
        $parts = $line.Split('=', 2)
        if ($parts.Length -ne 2 -or $parts[0] -notmatch '^[A-Z][A-Z0-9_]*$' -or $values.ContainsKey($parts[0])) {
            throw 'Invalid deployment environment file.'
        }
        $values[$parts[0]] = $parts[1]
    }
    $buckets = @($values['YMCLOUD_S3_PUBLIC_BUCKET'], $values['YMCLOUD_S3_PRIVATE_BUCKET'])
    if ($buckets[0] -eq $buckets[1]) {
        throw 'Public and private storage must use separate buckets.'
    }
    foreach ($bucket in $buckets) {
        if ($bucket -notmatch '^[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]$' -or $bucket.Contains('..')) {
            throw 'Invalid storage bucket name.'
        }
    }
    $composeArgs = @('compose', '--project-directory', $PSScriptRoot, '-f', (Join-Path $PSScriptRoot 'compose.yml'))

    function Invoke-BucketShell {
        param([string]$Command)
        # The internal shell needs no S3 credentials; never interpolate credentials into commands.
        $output = @($Command | & docker @composeArgs exec -T filer weed shell -master=master:9333 -filer=filer:8888 2>&1)
        if ($LASTEXITCODE -ne 0 -or ($output -join "`n") -match '(?im)^\s*error:') {
            throw 'SeaweedFS bucket initialization failed. Check the health of master and filer.'
        }
        return $output
    }

    function Get-BucketNames {
        $output = Invoke-BucketShell -Command 's3.bucket.list'
        foreach ($line in $output) {
            if ([string]$line -match '^\s*(\S+)\s+size:') {
                $Matches[1]
            }
        }
    }

    $existing = @(Get-BucketNames)
    foreach ($bucket in $buckets) {
        if ($bucket -notin $existing) {
            $null = Invoke-BucketShell -Command "s3.bucket.create -name $bucket -owner ymcloud-bootstrap"
        }
    }
    $verified = @(Get-BucketNames)
    foreach ($bucket in $buckets) {
        if ($bucket -notin $verified) {
            throw 'A required bucket is still missing after initialization.'
        }
    }
    Write-Host 'Public and private buckets verified. Existing buckets and objects are preserved.'
}

function Enable-Admin {
    $secretsRoot = Join-Path $PSScriptRoot 'secrets'
    $optionsFile = Join-Path $secretsRoot 'admin.options'
    $utf8 = [System.Text.UTF8Encoding]::new($false)
    if (-not (Test-Path -LiteralPath (Join-Path $PSScriptRoot '.env')) -or
        -not (Test-Path -LiteralPath $secretsRoot)) {
        throw 'Run install.ps1 -Action start before enabling the Admin UI.'
    }

    if (-not (Test-Path -LiteralPath $optionsFile)) {
        $temporaryFile = Join-Path $secretsRoot ('admin-' + [Guid]::NewGuid().ToString('N') + '.tmp')
        $stream = [System.IO.File]::Open($temporaryFile, [System.IO.FileMode]::CreateNew)
        $stream.Dispose()
        try {
            Set-PrivatePath -Path $temporaryFile
            $buffer = [byte[]]::new(16)
            $random = [System.Security.Cryptography.RandomNumberGenerator]::Create()
            try {
                $random.GetBytes($buffer)
            }
            finally {
                $random.Dispose()
            }
            $password = ([System.BitConverter]::ToString($buffer)).Replace('-', '').ToLowerInvariant()
            [System.IO.File]::WriteAllText($temporaryFile, "adminUser=admin`nadminPassword=$password`n", $utf8)
            Move-Item -LiteralPath $temporaryFile -Destination $optionsFile
        }
        finally {
            if (Test-Path -LiteralPath $temporaryFile) {
                Remove-Item -LiteralPath $temporaryFile
            }
        }
    }
    else {
        $values = @{}
        foreach ($line in (Get-Content -LiteralPath $optionsFile -Encoding UTF8)) {
            $parts = $line.Split('=', 2)
            if ($parts.Length -eq 2) {
                $values[$parts[0]] = $parts[1]
            }
        }
        if ($values['adminUser'] -ne 'admin' -or [string]::IsNullOrWhiteSpace($values['adminPassword'])) {
            throw 'Existing Admin UI credentials are incomplete; restore the file before startup.'
        }
    }

    $composeArgs = @('compose', '--project-directory', $PSScriptRoot, '-f', (Join-Path $PSScriptRoot 'compose.yml'), '--profile', 'admin-ui')
    Test-CredentialMount -Service admin -File admin.options
    Restart-FailedService -Service admin
    & docker @composeArgs up -d --no-deps --no-recreate --wait --wait-timeout 180 admin
    if ($LASTEXITCODE -ne 0) {
        throw 'Admin UI startup failed. Inspect its service logs without printing the credential file.'
    }
    Write-Host 'Admin UI is running with login required. Username: admin. Password: secrets/admin.options (adminPassword).'
}

# Guard every generated path before acquiring the setup lock or deleting credentials.
foreach ($relative in @('', 'secrets', '.env', 'secrets/s3.json', 'secrets/admin.options', 'secrets/.setup.lock')) {
    $path = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot $relative))
    if (Test-Path -LiteralPath $path) {
        $item = Get-Item -LiteralPath $path -Force
        if (($item.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0 -or
            ($item.PSIsContainer -ne ($relative -in @('', 'secrets')))) {
            throw 'Deployment paths must be ordinary files and directories, without symbolic links.'
        }
    }
}
$secretsRoot = Join-Path $PSScriptRoot 'secrets'
$null = [System.IO.Directory]::CreateDirectory($secretsRoot)
Set-PrivatePath -Path $secretsRoot -Directory
$setupLock = Join-Path $secretsRoot '.setup.lock'
$lock = $null
for ($attempt = 0; $attempt -lt 150 -and $null -eq $lock; $attempt++) {
    try {
        $lock = [System.IO.File]::Open($setupLock, [System.IO.FileMode]::OpenOrCreate,
            [System.IO.FileAccess]::ReadWrite, [System.IO.FileShare]::None)
    } catch [System.IO.IOException] {
        Start-Sleep -Milliseconds 200
    }
}
if ($null -eq $lock) {
    throw 'Another SeaweedFS operation is running; retry after it completes.'
}
try {
    switch ($Action) {
        'init' { Initialize-Storage }
        'start' {
            Require-Docker
            Initialize-Storage
            Test-CredentialMount -Service s3 -File s3.json
            Restart-FailedService -Service s3
            & docker @composeArgs up -d --no-recreate --wait --wait-timeout 180
            if ($LASTEXITCODE -ne 0) {
                throw 'Startup failed. Inspect service logs without printing .env or credentials.'
            }
            Initialize-Buckets
        }
        'bootstrap' { Require-Docker; Initialize-Buckets }
        'admin' {
            if (-not (Test-Path -LiteralPath (Join-Path $PSScriptRoot '.env'))) {
                throw 'Run install.ps1 -Action start before enabling the Admin UI.'
            }
            Require-Docker
            Initialize-Storage
            Enable-Admin
        }
        'status' {
            Require-Docker
            & docker @composeArgs --profile admin-ui ps --all
            if ($LASTEXITCODE -ne 0) { throw 'Unable to read SeaweedFS status.' }
        }
        'stop' {
            Require-Docker
            & docker @composeArgs --profile admin-ui stop
            if ($LASTEXITCODE -ne 0) { throw 'Unable to stop SeaweedFS.' }
        }
        'reset' {
            Require-Docker
            & docker @composeArgs --profile admin-ui down --volumes --remove-orphans
            if ($LASTEXITCODE -ne 0) { throw 'Reset failed; credential files have been preserved.' }
            foreach ($relative in @('.env', 'secrets/s3.json', 'secrets/admin.options')) {
                $path = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot $relative))
                $prefix = [System.IO.Path]::GetFullPath($PSScriptRoot) + [System.IO.Path]::DirectorySeparatorChar
                if (-not $path.StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase)) {
                    throw 'Refusing to delete a path outside the deployment directory.'
                }
                if (Test-Path -LiteralPath $path) { Remove-Item -LiteralPath $path -Force }
            }
            Write-Host 'SeaweedFS data and credentials cleared. Install again, then update OJ through its admin page.'
        }
    }
} finally {
    $lock.Dispose()
}
