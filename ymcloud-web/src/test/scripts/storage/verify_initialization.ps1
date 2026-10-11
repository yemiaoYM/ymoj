[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
if ($env:OS -ne 'Windows_NT') {
    & bash (Join-Path $PSScriptRoot 'verify_linux.sh')
    if ($LASTEXITCODE -ne 0) {
        throw 'Linux initialization regression failed.'
    }
    return
}
$projectRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../../..'))
$runName = 'storage-initialization-' + [Guid]::NewGuid().ToString('N')
$temporaryRoot = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath())
$fixtureRoot = [System.IO.Path]::GetFullPath((Join-Path $temporaryRoot $runName))
$reportRoot = Join-Path $projectRoot 'target/test-output'
$reportFile = Join-Path $reportRoot ($runName + '.json')
$deployment = Join-Path $fixtureRoot 'seaweedfs'
$utf8 = [System.Text.UTF8Encoding]::new($false)
$checks = [System.Collections.Generic.List[string]]::new()
$initialize = Join-Path $deployment 'install.ps1'

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

function Invoke-InitializationChecks {
    $null = [System.IO.Directory]::CreateDirectory($deployment)
    foreach ($file in @('install.ps1', 'compose.yml')) {
        Copy-Item -LiteralPath (Join-Path (Join-Path $projectRoot 'seaweedfs') $file) -Destination (Join-Path $deployment $file)
    }

    & $initialize -Action init -S3Port 18444 *> (Join-Path $fixtureRoot 'first-install.log')
    Assert-Check -Passed (-not (Test-Path (Join-Path $deployment 'application-storage.local.yml'))) -Name 'installer-does-not-create-oj-configuration'
    Assert-Check -Passed (-not (Test-Path (Join-Path $deployment 'secrets/storage-config.key'))) -Name 'installer-does-not-create-oj-master-key'
    $environmentFile = Join-Path $deployment '.env'
    $identitiesFile = Join-Path $deployment 'secrets/s3.json'
    $originalEnvironment = (Get-FileHash -LiteralPath $environmentFile -Algorithm SHA256).Hash
    $originalIdentities = (Get-FileHash -LiteralPath $identitiesFile -Algorithm SHA256).Hash
    $identities = [System.IO.File]::ReadAllText($identitiesFile, $utf8) | ConvertFrom-Json
    Assert-Check -Passed ($identities.identities.Count -eq 2) -Name 'separate-application-and-bootstrap-identities'
    Assert-Check -Passed (@($identities.identities[1].actions | Where-Object { $_ -eq 'Admin' }).Count -eq 0) -Name 'application-account-without-admin'
    Assert-PrivateFile -Path $environmentFile
    Assert-PrivateFile -Path $identitiesFile
    & $initialize -Action init -S3Port 18555 *> (Join-Path $fixtureRoot 'repeated-install.log')
    Assert-Check -Passed ((Get-FileHash $environmentFile -Algorithm SHA256).Hash -eq $originalEnvironment) -Name 'repeat-preserves-endpoint-and-credentials'
    Assert-Check -Passed ((Get-FileHash $identitiesFile -Algorithm SHA256).Hash -eq $originalIdentities) -Name 'repeat-preserves-identities'

    $incomplete = Join-Path $fixtureRoot 'incomplete-deployment'
    $null = [System.IO.Directory]::CreateDirectory($incomplete)
    Copy-Item -LiteralPath $initialize -Destination (Join-Path $incomplete 'install.ps1')
    Copy-Item -LiteralPath $environmentFile -Destination (Join-Path $incomplete '.env')
    Assert-Rejected -Action { & (Join-Path $incomplete 'install.ps1') -Action init } -Name 'incomplete-s3-credentials-refuse-regeneration'
    Assert-Check -Passed (-not (Test-Path (Join-Path $incomplete 'secrets/s3.json'))) -Name 'incomplete-s3-credentials-leave-no-replacement'

    # Exercise the real bootstrap entry against an isolated shell fixture, including shell errors with exit code zero.
    $fixtureState = [pscustomobject]@{
        Buckets = [System.Collections.Generic.List[string]]::new()
        Commands = [System.Collections.Generic.List[string]]::new()
        Fail = $false
    }
    $dockerFixture = {
        process {
            $CommandText = [string]$_
            $DockerArguments = $args
            $global:LASTEXITCODE = 0
            if ($DockerArguments -contains 'info') {
                'linux'
                return
            }
            if ($DockerArguments -contains 'run') {
                if ($DockerArguments[-1] -notlike 'test -r /etc/seaweedfs/*') {
                    throw 'Unexpected credential mount probe.'
                }
                return
            }
            if ([string]::IsNullOrWhiteSpace($CommandText)) {
                return
            }
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
    $bootstrap = Join-Path $deployment 'install.ps1'
    & $bootstrap -Action bootstrap *> (Join-Path $fixtureRoot 'first-bootstrap.log')
    Assert-Check -Passed ($fixtureState.Buckets.Count -eq 2) -Name 'bootstrap-creates-two-missing-buckets'
    $creates = @($fixtureState.Commands | Where-Object { $_.StartsWith('s3.bucket.create ') }).Count
    & $bootstrap -Action bootstrap *> (Join-Path $fixtureRoot 'repeated-bootstrap.log')
    Assert-Check -Passed (@($fixtureState.Commands | Where-Object { $_.StartsWith('s3.bucket.create ') }).Count -eq $creates) -Name 'bootstrap-does-not-recreate-existing-buckets'
    $fixtureState.Fail = $true
    Assert-Rejected -Action { & $bootstrap -Action bootstrap } -Name 'shell-error-with-zero-exit-status-refuses-success'
    $fixtureState.Fail = $false
    $invalid = Join-Path $fixtureRoot 'invalid-buckets'
    $null = [System.IO.Directory]::CreateDirectory($invalid)
    Copy-Item -LiteralPath $bootstrap -Destination (Join-Path $invalid 'install.ps1')
    [System.IO.File]::WriteAllText((Join-Path $invalid '.env'), "YMCLOUD_S3_PUBLIC_BUCKET=same-bucket`nYMCLOUD_S3_PRIVATE_BUCKET=same-bucket`n", $utf8)
    Assert-Rejected -Action { & (Join-Path $invalid 'install.ps1') -Action bootstrap } -Name 'bootstrap-refuses-shared-public-private-bucket'

    & $initialize -Action admin *> (Join-Path $fixtureRoot 'admin.log')
    $adminFile = Join-Path $deployment 'secrets/admin.options'
    Assert-PrivateFile -Path $adminFile
    $adminHash = (Get-FileHash -LiteralPath $adminFile -Algorithm SHA256).Hash
    & $initialize -Action admin *> (Join-Path $fixtureRoot 'admin-repeat.log')
    Assert-Check -Passed ((Get-FileHash -LiteralPath $adminFile -Algorithm SHA256).Hash -eq $adminHash) -Name 'repeat-preserves-admin-password'
    Assert-Rejected -Action { & $initialize -Action reset } -Name 'reset-requires-explicit-data-deletion'
    Assert-Check -Passed ((Get-FileHash -LiteralPath $environmentFile -Algorithm SHA256).Hash -eq $originalEnvironment) -Name 'rejected-reset-preserves-credentials'
    $ojKey = Join-Path $deployment 'secrets/storage-config.key'
    [System.IO.File]::WriteAllText($ojKey, 'legacy-oj-key-must-be-preserved', $utf8)
    & $initialize -Action reset -DeleteData *> (Join-Path $fixtureRoot 'reset.log')
    Assert-Check -Passed (-not (Test-Path -LiteralPath $environmentFile) -and
        -not (Test-Path -LiteralPath $identitiesFile) -and -not (Test-Path -LiteralPath $adminFile)) -Name 'reset-clears-only-seaweedfs-credentials'
    Assert-Check -Passed ([System.IO.File]::ReadAllText($ojKey, $utf8) -eq 'legacy-oj-key-must-be-preserved') -Name 'reset-preserves-legacy-oj-master-key'
    & $initialize -Action init *> (Join-Path $fixtureRoot 'fresh-install.log')
    Assert-Check -Passed ((Get-FileHash -LiteralPath $environmentFile -Algorithm SHA256).Hash -ne $originalEnvironment) -Name 'fresh-install-generates-new-s3-credentials'

    $logText = (Get-ChildItem -LiteralPath $fixtureRoot -Filter '*.log' | ForEach-Object {
        [System.IO.File]::ReadAllText($_.FullName, $utf8)
    }) -join "`n"
    foreach ($identity in $identities.identities) {
        foreach ($credential in $identity.credentials) {
            if ($logText.Contains($credential.accessKey) -or $logText.Contains($credential.secretKey)) {
                throw 'A generated S3 credential was logged.'
            }
        }
    }
    $checks.Add('s3-credentials-not-logged')
}

$succeeded = $false
try {
    Invoke-InitializationChecks
    $succeeded = $true
}
finally {
    # Never retain restricted credential fixtures in Maven's target directory.
    $temporaryPrefix = $temporaryRoot.TrimEnd([System.IO.Path]::DirectorySeparatorChar) + [System.IO.Path]::DirectorySeparatorChar
    if (-not $fixtureRoot.StartsWith($temporaryPrefix, [StringComparison]::OrdinalIgnoreCase) -or
        (Split-Path -Leaf $fixtureRoot) -ne $runName) {
        throw 'Refusing to clean a fixture outside the temporary directory.'
    }
    if (Test-Path -LiteralPath $fixtureRoot) {
        $fixtureItem = Get-Item -LiteralPath $fixtureRoot -Force
        if (($fixtureItem.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0) {
            throw 'Refusing to recursively clean a linked fixture directory.'
        }
        Remove-Item -LiteralPath $fixtureRoot -Recurse -Force
    }
    if (Test-Path -LiteralPath $fixtureRoot) {
        throw 'Initialization fixture cleanup failed.'
    }
    $null = [System.IO.Directory]::CreateDirectory($reportRoot)
    $report = [ordered]@{
        Checks = $checks
        Succeeded = $succeeded
        NoExternalServices = $true
        FixturesRemoved = $true
    }
    [System.IO.File]::WriteAllText($reportFile, ($report | ConvertTo-Json -Depth 3), $utf8)
}
Write-Host "PASS $($checks.Count) initialization checks. Private fixtures removed; sanitized report: $reportFile"
