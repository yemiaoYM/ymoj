[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$projectRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$fixtureRoot = Join-Path $projectRoot ('target/test-output/storage-initialization-' + [Guid]::NewGuid().ToString('N'))
$deployment = Join-Path $fixtureRoot 'seaweedfs'
$null = [System.IO.Directory]::CreateDirectory($deployment)
$utf8 = [System.Text.UTF8Encoding]::new($false)
$checks = [System.Collections.Generic.List[string]]::new()
foreach ($file in @('initialize.ps1', 'bootstrap.ps1', 'compose.yml', 'activate.ps1')) {
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot $file) -Destination (Join-Path $deployment $file)
}
$initialize = Join-Path $deployment 'initialize.ps1'
$activate = Join-Path $deployment 'activate.ps1'

function Assert-Check {
    param([bool]$Passed, [string]$Name)
    if (-not $Passed) { throw "Initialization regression failed: $Name" }
    $checks.Add($Name)
}

function Assert-Rejected {
    param([scriptblock]$Action, [string]$Name)
    $rejected = $false
    try { & $Action | Out-Null } catch { $rejected = $true }
    Assert-Check -Passed $rejected -Name $Name
}

function Assert-PrivateFile {
    param([string]$Path)
    if ($env:OS -eq 'Windows_NT') {
        $current = [Security.Principal.WindowsIdentity]::GetCurrent().User.Value
        $acl = Get-Acl -LiteralPath $Path
        Assert-Check -Passed $acl.AreAccessRulesProtected -Name ('private-acl:' + (Split-Path -Leaf $Path))
        foreach ($rule in $acl.Access) {
            $identity = $rule.IdentityReference.Translate([Security.Principal.SecurityIdentifier]).Value
            if ($rule.AccessControlType -eq 'Allow' -and $identity -notin @($current, 'S-1-5-18')) {
                throw 'A generated private file grants access to an unexpected identity.'
            }
        }
    } else {
        $mode = & stat -c '%a' $Path
        Assert-Check -Passed ($LASTEXITCODE -eq 0 -and $mode -eq '600') -Name ('private-mode:' + (Split-Path -Leaf $Path))
    }
}

& $initialize -S3Port 18444 *> (Join-Path $fixtureRoot 'first-install.log')
$environmentFile = Join-Path $deployment '.env'
$identitiesFile = Join-Path $deployment 'secrets/s3.json'
$originalEnvironment = (Get-FileHash -LiteralPath $environmentFile -Algorithm SHA256).Hash
$originalIdentities = (Get-FileHash -LiteralPath $identitiesFile -Algorithm SHA256).Hash
$identities = [System.IO.File]::ReadAllText($identitiesFile, $utf8) | ConvertFrom-Json
Assert-Check -Passed ($identities.identities.Count -eq 2) -Name 'separate-application-and-bootstrap-identities'
Assert-Check -Passed (@($identities.identities[1].actions | Where-Object { $_ -eq 'Admin' }).Count -eq 0) -Name 'application-account-without-admin'
Assert-PrivateFile -Path $environmentFile
Assert-PrivateFile -Path $identitiesFile
& $initialize -S3Port 18555 *> (Join-Path $fixtureRoot 'repeated-install.log')
Assert-Check -Passed ((Get-FileHash $environmentFile -Algorithm SHA256).Hash -eq $originalEnvironment) -Name 'repeat-preserves-endpoint-and-credentials'
Assert-Check -Passed ((Get-FileHash $identitiesFile -Algorithm SHA256).Hash -eq $originalIdentities) -Name 'repeat-preserves-identities'

& $activate *> (Join-Path $fixtureRoot 'first-application.log')
$keyFile = Join-Path $deployment 'secrets/storage-config.key'
$configFile = Join-Path $deployment 'application-storage.local.yml'
$key = [System.IO.File]::ReadAllText($keyFile, $utf8)
Assert-Check -Passed ([Convert]::FromBase64String($key).Length -eq 32) -Name 'aes-master-key-size'
$initialKeyHash = (Get-FileHash $keyFile -Algorithm SHA256).Hash
$initialConfigHash = (Get-FileHash $configFile -Algorithm SHA256).Hash
$content = [System.IO.File]::ReadAllText($configFile, $utf8)
Assert-Check -Passed ($content.Contains('active: local') -and -not $content.Contains('endpoint:')) -Name 'application-local-only-without-remote-credentials'
Assert-PrivateFile -Path $keyFile
Assert-PrivateFile -Path $configFile
& $activate *> (Join-Path $fixtureRoot 'repeated-application.log')
Assert-Check -Passed ((Get-FileHash $keyFile -Algorithm SHA256).Hash -eq $initialKeyHash) -Name 'repeat-preserves-master-key'
Assert-Check -Passed ((Get-FileHash $configFile -Algorithm SHA256).Hash -eq $initialConfigHash) -Name 'repeat-preserves-application-config'

$lostKey = Join-Path $fixtureRoot 'missing-key'
$null = [System.IO.Directory]::CreateDirectory($lostKey)
Copy-Item -LiteralPath $configFile -Destination (Join-Path $lostKey 'application-storage.local.yml')
Assert-Rejected -Action { & $activate -ConfigurationDirectory $lostKey } -Name 'missing-master-key-refuses-regeneration'
Assert-Check -Passed (-not (Test-Path (Join-Path $lostKey 'secrets/storage-config.key'))) -Name 'missing-master-key-leaves-no-replacement'

$incomplete = Join-Path $fixtureRoot 'incomplete-deployment'
$null = [System.IO.Directory]::CreateDirectory($incomplete)
Copy-Item -LiteralPath $initialize -Destination (Join-Path $incomplete 'initialize.ps1')
Copy-Item -LiteralPath $environmentFile -Destination (Join-Path $incomplete '.env')
Assert-Rejected -Action { & (Join-Path $incomplete 'initialize.ps1') } -Name 'incomplete-s3-credentials-refuse-regeneration'
Assert-Check -Passed (-not (Test-Path (Join-Path $incomplete 'secrets/s3.json'))) -Name 'incomplete-s3-credentials-leave-no-replacement'

# Exercise the real bootstrap entry against an isolated shell fixture, including shell errors with exit code zero.
$fixtureState = [pscustomobject]@{
    Buckets = [System.Collections.Generic.List[string]]::new()
    Commands = [System.Collections.Generic.List[string]]::new()
    Fail = $false
}
$dockerFixture = {
    [CmdletBinding(PositionalBinding = $false)]
    param(
        [Parameter(ValueFromPipeline)]
        [string]$CommandText,
        [Parameter(ValueFromRemainingArguments)]
        [string[]]$DockerArguments
    )
    process {
        $global:LASTEXITCODE = 0
        $fixtureState.Commands.Add($CommandText)
        if ($fixtureState.Fail) {
            'error: fixture shell unavailable'
        } elseif ($CommandText -eq 's3.bucket.list') {
            foreach ($bucket in $fixtureState.Buckets) { "$bucket`tsize:0" }
        } elseif ($CommandText -match '^s3.bucket.create -name ([a-z0-9.-]+) -owner ymcloud-bootstrap$') {
            if ($Matches[1] -in $fixtureState.Buckets) { throw 'Existing buckets must not be recreated.' }
            $fixtureState.Buckets.Add($Matches[1])
        } else {
            throw 'An unexpected shell command was invoked.'
        }
    }
}.GetNewClosure()
Set-Item -LiteralPath Function:docker -Value $dockerFixture
$bootstrap = Join-Path $deployment 'bootstrap.ps1'
& $bootstrap *> (Join-Path $fixtureRoot 'first-bootstrap.log')
Assert-Check -Passed ($fixtureState.Buckets.Count -eq 2) -Name 'bootstrap-creates-two-missing-buckets'
$creates = @($fixtureState.Commands | Where-Object { $_.StartsWith('s3.bucket.create ') }).Count
& $bootstrap *> (Join-Path $fixtureRoot 'repeated-bootstrap.log')
Assert-Check -Passed (@($fixtureState.Commands | Where-Object { $_.StartsWith('s3.bucket.create ') }).Count -eq $creates) -Name 'bootstrap-does-not-recreate-existing-buckets'
$fixtureState.Fail = $true
Assert-Rejected -Action { & $bootstrap } -Name 'shell-error-with-zero-exit-status-refuses-success'
$fixtureState.Fail = $false
$invalid = Join-Path $fixtureRoot 'invalid-buckets'
$null = [System.IO.Directory]::CreateDirectory($invalid)
Copy-Item -LiteralPath $bootstrap -Destination (Join-Path $invalid 'bootstrap.ps1')
[System.IO.File]::WriteAllText((Join-Path $invalid '.env'), "YMCLOUD_S3_PUBLIC_BUCKET=same-bucket`nYMCLOUD_S3_PRIVATE_BUCKET=same-bucket`n", $utf8)
Assert-Rejected -Action { & (Join-Path $invalid 'bootstrap.ps1') } -Name 'bootstrap-refuses-shared-public-private-bucket'

$logText = (Get-ChildItem -LiteralPath $fixtureRoot -Filter '*.log' | ForEach-Object {
    [System.IO.File]::ReadAllText($_.FullName, $utf8)
}) -join "`n"
Assert-Check -Passed (-not $logText.Contains($key)) -Name 'application-key-not-logged'
foreach ($identity in $identities.identities) {
    foreach ($credential in $identity.credentials) {
        if ($logText.Contains($credential.accessKey) -or $logText.Contains($credential.secretKey)) {
            throw 'A generated S3 credential was logged.'
        }
    }
}
$checks.Add('s3-credentials-not-logged')
$report = [ordered]@{Checks=$checks;NoExternalServices=$true;FixtureCredentialsIgnored=$true;ReportDirectory=$fixtureRoot}
[System.IO.File]::WriteAllText((Join-Path $fixtureRoot 'report.json'), ($report | ConvertTo-Json -Depth 3), $utf8)
Write-Host "PASS $($checks.Count) initialization checks. Private fixtures retained only in ignored target/test-output."
