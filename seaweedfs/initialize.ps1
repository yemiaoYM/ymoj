[CmdletBinding()]
param(
    [ValidateRange(1024, 65535)]
    [int]$S3Port = 18333,
    [switch]$Start
)

$ErrorActionPreference = 'Stop'
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
    [System.IO.File]::WriteAllText($s3ConfigFile, ($identities | ConvertTo-Json -Depth 8), $utf8)
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
    [System.IO.File]::WriteAllText($envFile, "$envContent`n", $utf8)
    Write-Host 'Generated local credentials in .env and secrets/. Values are intentionally not displayed.'
}
else {
    Write-Host 'Reusing existing credentials; initialization does not rotate credentials or erase volumes.'
}

if ($env:OS -eq 'Windows_NT') {
    $owner = [System.Security.Principal.WindowsIdentity]::GetCurrent().Name
    $aclCommand = Join-Path $env:SystemRoot 'System32/icacls.exe'
    & $aclCommand $envFile /inheritance:r /grant:r "${owner}:F" 'SYSTEM:F' | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw 'Unable to restrict credential file permissions.'
    }
    & $aclCommand $secretsRoot /inheritance:r /grant:r "${owner}:(OI)(CI)F" 'SYSTEM:(OI)(CI)F' | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw 'Unable to restrict credential directory permissions.'
    }
    foreach ($secretFile in @(Get-ChildItem -LiteralPath $secretsRoot -File)) {
        & $aclCommand $secretFile.FullName /inheritance:r /grant:r "${owner}:F" 'SYSTEM:F' | Out-Null
        if ($LASTEXITCODE -ne 0) {
            throw 'Unable to restrict credential file permissions.'
        }
    }
}
else {
    & chmod 700 $secretsRoot
    & chmod 600 $envFile $s3ConfigFile
    if ($LASTEXITCODE -ne 0) {
        throw 'Unable to restrict credential file permissions.'
    }
}

if ($Start) {
    $composeArgs = @('compose', '--project-directory', $storageRoot, '-f', (Join-Path $storageRoot 'compose.yml'))
    & docker @composeArgs up -d --no-recreate --wait --wait-timeout 180
    if ($LASTEXITCODE -ne 0) {
        throw 'Storage startup failed. Inspect docker compose ps and logs; avoid printing .env or compose config.'
    }
    & (Join-Path $storageRoot 'bootstrap.ps1')
}
