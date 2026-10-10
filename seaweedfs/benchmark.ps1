[CmdletBinding()]
param(
    [ValidateRange(1, 1000)]
    [int]$Iterations = 20,
    [ValidateRange(1, 64)]
    [int]$Concurrency = 4,
    [switch]$PersistenceCheck
)

$ErrorActionPreference = 'Stop'
$projectRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$reportRoot = Join-Path $projectRoot 'target/test-output/seaweedfs'
[System.IO.Directory]::CreateDirectory($reportRoot) | Out-Null
$runId = Get-Date -Format 'yyyyMMdd-HHmmss'
$outputFile = Join-Path $reportRoot "benchmark-$runId.log"
$errorFile = Join-Path $reportRoot "benchmark-$runId-error.log"
$resourceFile = Join-Path $reportRoot "resources-$runId.json"
$env:PYTHONIOENCODING = 'utf-8'
$pythonCommand = (Get-Command python).Source
$verificationScript = Join-Path $PSScriptRoot 'verify_storage.py'
$arguments = @(('"' + $verificationScript + '"'), '--security', '--iterations', $Iterations, '--concurrency', $Concurrency)
if ($PersistenceCheck) {
    $arguments += '--persistence-check'
}
$processOptions = @{
    FilePath = $pythonCommand
    ArgumentList = $arguments
    WorkingDirectory = $PSScriptRoot
    RedirectStandardOutput = $outputFile
    RedirectStandardError = $errorFile
    PassThru = $true
}
if ($env:OS -eq 'Windows_NT') {
    $processOptions.WindowStyle = 'Hidden'
}
$benchmarkProcess = Start-Process @processOptions
$snapshots = [System.Collections.Generic.List[object]]::new()
do {
    $rawStats = & docker stats --no-stream --format '{{json .}}' ymcloud-storage-master-1 ymcloud-storage-volume-1 ymcloud-storage-filer-1 ymcloud-storage-s3-1
    if ($LASTEXITCODE -ne 0) {
        throw 'Unable to collect resource metrics for the dedicated storage containers.'
    }
    $stats = @($rawStats | ForEach-Object { $_ | ConvertFrom-Json })
    $snapshots.Add(@{ timestampUtc = [DateTime]::UtcNow.ToString('o'); containers = $stats })
    $benchmarkProcess.Refresh()
} while (-not $benchmarkProcess.HasExited)

$benchmarkProcess.WaitForExit()
$utf8 = [System.Text.UTF8Encoding]::new($false)
[System.IO.File]::WriteAllText($resourceFile, ($snapshots | ConvertTo-Json -Depth 8), $utf8)
if ($benchmarkProcess.ExitCode -ne 0) {
    Get-Content -LiteralPath $errorFile -Encoding utf8
    throw 'Storage security, performance or persistence validation failed.'
}
Get-Content -LiteralPath $outputFile -Encoding utf8
Write-Host "Resource snapshots: $resourceFile"
