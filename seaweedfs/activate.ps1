[CmdletBinding()]
param(
    [string]$ConfigurationDirectory = $PSScriptRoot
)

$ErrorActionPreference = 'Stop'
$storageRoot = [System.IO.Path]::GetFullPath($ConfigurationDirectory)
$secretsRoot = Join-Path $storageRoot 'secrets'
$configFile = Join-Path $storageRoot 'application-storage.local.yml'
$encryptionKeyFile = Join-Path $secretsRoot 'storage-config.key'
$utf8 = [System.Text.UTF8Encoding]::new($false)

for ($cursor = $storageRoot; $cursor; $cursor = [System.IO.Path]::GetDirectoryName($cursor)) {
    if ((Test-Path -LiteralPath $cursor) -and
        ((Get-Item -LiteralPath $cursor -Force).Attributes -band [System.IO.FileAttributes]::ReparsePoint)) {
        throw 'Storage configuration directories must not be symbolic links or junctions.'
    }
}

function ConvertTo-YamlString {
    param([string]$Value)
    return "'" + $Value.Replace("'", "''") + "'"
}

function Protect-StoragePath {
    param([string]$Path, [switch]$Directory)

    $item = Get-Item -LiteralPath $Path -Force
    if (($item.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0) {
        throw 'Storage configuration paths must not be symbolic links.'
    }
    if ($env:OS -eq 'Windows_NT') {
        $pathOwner = [System.Security.Principal.WindowsIdentity]::GetCurrent().Name
        $ownerRule = if ($Directory) { "${pathOwner}:(OI)(CI)F" } else { "${pathOwner}:F" }
        $systemRule = if ($Directory) { 'SYSTEM:(OI)(CI)F' } else { 'SYSTEM:F' }
        & (Join-Path $env:SystemRoot 'System32/icacls.exe') $Path /inheritance:r /grant:r $ownerRule $systemRule | Out-Null
    }
    else {
        $mode = if ($Directory) { '700' } else { '600' }
        & chmod $mode $Path
    }
    if ($LASTEXITCODE -ne 0) {
        throw 'Unable to restrict storage configuration permissions.'
    }
}

if ((Test-Path -LiteralPath $secretsRoot) -and -not (Test-Path -LiteralPath $secretsRoot -PathType Container)) {
    throw 'The storage secrets path must be a directory.'
}
[System.IO.Directory]::CreateDirectory($secretsRoot) | Out-Null
Protect-StoragePath -Path $secretsRoot -Directory

# Keep the encryption key outside the database and reuse it across restarts.
if (-not (Test-Path -LiteralPath $encryptionKeyFile)) {
    if ((Test-Path -LiteralPath $configFile) -and
        (Select-String -LiteralPath $configFile -Pattern '^\s*config-encryption-key:' -Quiet)) {
        throw 'The existing encryption key backup is missing. Restore storage-config.key; do not generate a new key for encrypted database records.'
    }
    $keyStream = [System.IO.File]::Open($encryptionKeyFile, [System.IO.FileMode]::CreateNew)
    $keyStream.Dispose()
    Protect-StoragePath -Path $encryptionKeyFile
    $keyBytes = New-Object byte[] 32
    $keyRandom = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $keyRandom.GetBytes($keyBytes)
        [System.IO.File]::WriteAllText($encryptionKeyFile, [Convert]::ToBase64String($keyBytes), $utf8)
    }
    finally {
        $keyRandom.Dispose()
        [Array]::Clear($keyBytes, 0, $keyBytes.Length)
    }
}
Protect-StoragePath -Path $encryptionKeyFile
$encryptionKey = [System.IO.File]::ReadAllText($encryptionKeyFile, $utf8).Trim()
try {
    if ([Convert]::FromBase64String($encryptionKey).Length -ne 32) {
        throw 'Invalid storage encryption key length.'
    }
}
catch {
    throw 'Storage encryption key must be a base64 encoded 32-byte key. Restore it from your protected backup; do not replace it.'
}

$configLines = @(
    '# Local generated credentials; ignored by Git. Do not print or commit this file.'
    'ymcloud:'
    '    storage:'
    '        active: local'
    ('        config-encryption-key: ' + (ConvertTo-YamlString $encryptionKey))
    '        profiles:'
    '            local:'
    '                type: local'
    '    judge:'
    '        exchange: ${YMCLOUD_JUDGE_EXCHANGE:judge.storage-v1.topic}'
    '        task-queue: ${YMCLOUD_JUDGE_TASK_QUEUE:judge.storage-v1.queue}'
    '        result-queue: ${YMCLOUD_JUDGE_RESULT_QUEUE:judge.storage-v1.result.queue}'
    'spring:'
    '    datasource:'
    '        druid:'
    '            connection-properties: openTelemetry=${YMCLOUD_JDBC_TELEMETRY:DISABLED}'
) -join "`n"

# Stage in the ignored secrets directory and restrict ACL before writing credentials.
$temporaryFile = Join-Path $secretsRoot ('application-storage-' + [Guid]::NewGuid().ToString('N') + '.tmp')
$temporaryStream = [System.IO.File]::Open($temporaryFile, [System.IO.FileMode]::CreateNew)
$temporaryStream.Dispose()
try {
    Protect-StoragePath -Path $temporaryFile
    [System.IO.File]::WriteAllText($temporaryFile, "$configLines`n", $utf8)
    Move-Item -LiteralPath $temporaryFile -Destination $configFile -Force
}
finally {
    if (Test-Path -LiteralPath $temporaryFile) {
        Remove-Item -LiteralPath $temporaryFile
    }
}

Write-Host 'Generated a local-only Spring configuration and initialized or reused the protected AES master key. Credentials are not displayed; restart the backend to load it.'
Write-Host 'Other services must be configured, tested and enabled manually in the backend storage configuration page. An existing database selection still takes precedence.'
