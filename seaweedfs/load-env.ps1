[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$envFile = Join-Path $PSScriptRoot '.env'
if (-not (Test-Path -LiteralPath $envFile)) {
    throw 'Run initialize.ps1 before loading local storage configuration.'
}

foreach ($line in (Get-Content -LiteralPath $envFile -Encoding utf8)) {
    if ([string]::IsNullOrWhiteSpace($line) -or $line.StartsWith('#')) {
        continue
    }
    $parts = $line.Split('=', 2)
    if ($parts.Length -ne 2 -or $parts[0] -notmatch '^[A-Z][A-Z0-9_]*$') {
        throw 'Invalid local environment file.'
    }
    [System.Environment]::SetEnvironmentVariable($parts[0], $parts[1], 'Process')
}

Write-Host 'Loaded local storage settings into this process. Credentials are not displayed.'
