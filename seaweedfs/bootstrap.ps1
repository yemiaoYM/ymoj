[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$envFile = Join-Path $PSScriptRoot '.env'
if (-not (Test-Path -LiteralPath $envFile -PathType Leaf)) {
    throw 'Run initialize.ps1 before initializing buckets.'
}
$values = @{}
foreach ($line in (Get-Content -LiteralPath $envFile -Encoding UTF8)) {
    if ([string]::IsNullOrWhiteSpace($line) -or $line.StartsWith('#')) {
        continue
    }
    $parts = $line.Split('=', 2)
    if ($parts.Length -ne 2 -or $parts[0] -notmatch '^[A-Z][A-Z0-9_]*$') {
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
