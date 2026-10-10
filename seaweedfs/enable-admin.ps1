[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$secretsRoot = Join-Path $PSScriptRoot 'secrets'
$optionsFile = Join-Path $secretsRoot 'admin.options'
$utf8 = [System.Text.UTF8Encoding]::new($false)
if (-not (Test-Path -LiteralPath (Join-Path $PSScriptRoot '.env')) -or
    -not (Test-Path -LiteralPath $secretsRoot)) {
    throw 'Run initialize.ps1 before enabling the Admin UI.'
}

if (-not (Test-Path -LiteralPath $optionsFile)) {
    $temporaryFile = Join-Path $secretsRoot ('admin-' + [Guid]::NewGuid().ToString('N') + '.tmp')
    $stream = [System.IO.File]::Open($temporaryFile, [System.IO.FileMode]::CreateNew)
    $stream.Dispose()
    try {
        if ($env:OS -eq 'Windows_NT') {
            $owner = [System.Security.Principal.WindowsIdentity]::GetCurrent().Name
            & (Join-Path $env:SystemRoot 'System32/icacls.exe') $temporaryFile /inheritance:r /grant:r "${owner}:F" 'SYSTEM:F' | Out-Null
            if ($LASTEXITCODE -ne 0) {
                throw 'Unable to restrict Admin UI credential permissions.'
            }
        }
        else {
            & chmod 600 $temporaryFile
            if ($LASTEXITCODE -ne 0) {
                throw 'Unable to restrict Admin UI credential permissions.'
            }
        }
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
& docker @composeArgs up -d --no-deps --wait --wait-timeout 180 admin
if ($LASTEXITCODE -ne 0) {
    throw 'Admin UI startup failed. Inspect its service logs without printing the credential file.'
}
Write-Host 'Admin UI is running with login required. Username: admin. Password: secrets/admin.options (adminPassword).'
