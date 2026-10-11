[CmdletBinding()]
param(
    [switch]$RunSdk,
    [ValidateRange(1, 1000)]
    [int]$Iterations = 20
)

# Real Docker lifecycle checks use only this run's named project and private temporary files.
$ErrorActionPreference = 'Stop'
$projectRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../../..'))
$runName = 'ymcloud-storage-test-' + [Guid]::NewGuid().ToString('N')
$temporaryRoot = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath())
$fixtureRoot = [System.IO.Path]::GetFullPath((Join-Path $temporaryRoot $runName))
$deployment = Join-Path $fixtureRoot 'seaweedfs'
$reportRoot = Join-Path $projectRoot ('target/test-output/' + $runName)
$utf8 = [System.Text.UTF8Encoding]::new($false)
$checks = [System.Collections.Generic.List[string]]::new()
$previousEnvironment = @{}
foreach ($name in @('COMPOSE_PROJECT_NAME', 'YMCLOUD_ADMIN_UI_PORT', 'YMCLOUD_RUN_S3_TESTS',
    'YMCLOUD_S3_ENDPOINT', 'YMCLOUD_S3_REGION', 'YMCLOUD_S3_ACCESS_KEY', 'YMCLOUD_S3_SECRET_KEY',
    'YMCLOUD_S3_PUBLIC_BUCKET', 'YMCLOUD_S3_PRIVATE_BUCKET')) {
    $previousEnvironment[$name] = [System.Environment]::GetEnvironmentVariable($name, 'Process')
}
$succeeded = $false
$created = $false

function Assert-Check {
    param([bool]$Passed, [string]$Name)
    if (-not $Passed) { throw "Docker regression failed: $Name" }
    $checks.Add($Name)
    Write-Host "PASS $Name"
}

function Free-Port {
    $listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, 0)
    try {
        $listener.Start()
        return $listener.LocalEndpoint.Port
    } finally {
        $listener.Stop()
    }
}

function Install-Action {
    param([string]$Action, [switch]$DeleteData)
    $parameters = @{ Action = $Action; S3Port = $script:s3Port }
    if ($DeleteData) { $parameters.DeleteData = $true }
    & (Join-Path $deployment 'install.ps1') @parameters *> (Join-Path $reportRoot ($Action + '.log'))
}

function Compose {
    & docker compose --project-directory $deployment -f (Join-Path $deployment 'compose.yml') --profile admin-ui @args
    if ($LASTEXITCODE -ne 0) { throw 'Isolated Docker Compose command failed.' }
}

function S3-Check {
    & python (Join-Path $PSScriptRoot 'verify_storage.py') --deployment-dir $deployment --report-dir $reportRoot @args
    if ($LASTEXITCODE -ne 0) { throw 'Isolated S3 check failed.' }
}

function Read-Environment {
    param([string]$Path)
    $values = @{}
    foreach ($line in [System.IO.File]::ReadAllLines($Path, $utf8)) {
        if ($line -and -not $line.StartsWith('#')) {
            $parts = $line.Split('=', 2)
            if ($parts.Length -ne 2) { throw 'Invalid fixture environment.' }
            $values[$parts[0]] = $parts[1]
        }
    }
    return $values
}

try {
    $null = [System.IO.Directory]::CreateDirectory($deployment)
    $null = [System.IO.Directory]::CreateDirectory($reportRoot)
    $owner = [System.Security.Principal.WindowsIdentity]::GetCurrent().Name
    & (Join-Path $env:SystemRoot 'System32/icacls.exe') $fixtureRoot /inheritance:r /grant:r "${owner}:(OI)(CI)F" 'SYSTEM:(OI)(CI)F' | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Unable to protect private test fixtures.' }
    foreach ($file in @('install.ps1', 'compose.yml')) {
        Copy-Item -LiteralPath (Join-Path $projectRoot ('seaweedfs/' + $file)) -Destination (Join-Path $deployment $file)
    }
    $env:COMPOSE_PROJECT_NAME = $runName
    $env:YMCLOUD_ADMIN_UI_PORT = [string](Free-Port)
    $script:s3Port = Free-Port
    $created = $true
    Install-Action -Action start
    $ids = @(Compose ps --quiet | Sort-Object)
    Assert-Check -Passed ($ids.Count -eq 4) -Name 'fresh-install-four-healthy-services'
    $environmentFile = Join-Path $deployment '.env'
    $originalHash = (Get-FileHash -LiteralPath $environmentFile -Algorithm SHA256).Hash
    $oldDeployment = Join-Path $fixtureRoot 'old-deployment'
    $null = [System.IO.Directory]::CreateDirectory($oldDeployment)
    Copy-Item -LiteralPath $environmentFile -Destination (Join-Path $oldDeployment '.env')
    Install-Action -Action start
    Assert-Check -Passed ((Compare-Object $ids @(Compose ps --quiet | Sort-Object)).Count -eq 0) -Name 'repeated-start-preserves-containers'
    Assert-Check -Passed ((Get-FileHash -LiteralPath $environmentFile -Algorithm SHA256).Hash -eq $originalHash) -Name 'repeated-start-preserves-credentials'
    S3-Check --persistence-write --iterations 0 *> (Join-Path $reportRoot 'persistence-write.log')
    Install-Action -Action admin
    $adminFile = Join-Path $deployment 'secrets/admin.options'
    $adminHash = (Get-FileHash -LiteralPath $adminFile -Algorithm SHA256).Hash
    Install-Action -Action admin
    Assert-Check -Passed ((Get-FileHash -LiteralPath $adminFile -Algorithm SHA256).Hash -eq $adminHash) -Name 'administrator-password-preserved'
    $loginCheck = @'
import html.parser, http.cookiejar, pathlib, sys, urllib.parse, urllib.request
class Form(html.parser.HTMLParser):
    token = None
    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        if tag == 'input' and attrs.get('name') == 'csrf_token':
            self.token = attrs.get('value')
values = dict(line.split('=', 1) for line in pathlib.Path(sys.argv[1]).read_text().splitlines())
cookies = http.cookiejar.CookieJar()
opener = urllib.request.build_opener(urllib.request.ProxyHandler({}), urllib.request.HTTPCookieProcessor(cookies))
url = 'http://127.0.0.1:' + sys.argv[2] + '/login'
form = Form()
form.feed(opener.open(url, timeout=30).read().decode())
assert form.token, 'CSRF token missing'
body = urllib.parse.urlencode({'username': values['adminUser'], 'password': values['adminPassword'], 'csrf_token': form.token}).encode()
response = opener.open(url, body, timeout=30)
assert urllib.parse.urlparse(response.url).path != '/login' and list(cookies), 'Admin login failed'
'@
    $loginCheck | & python - $adminFile $env:YMCLOUD_ADMIN_UI_PORT
    Assert-Check -Passed ($LASTEXITCODE -eq 0) -Name 'administrator-login-with-csrf'
    foreach ($id in @(Compose ps --quiet)) {
        $fields = & docker inspect --format '{{.Config.User}}|{{.HostConfig.ReadonlyRootfs}}|{{json .HostConfig.CapDrop}}|{{json .HostConfig.SecurityOpt}}|{{json .HostConfig.PortBindings}}' $id
        if ($LASTEXITCODE -ne 0) { throw 'Unable to inspect isolated service.' }
        $parts = $fields -split '\|', 5
        Assert-Check -Passed ($parts[0] -eq '1000:1000' -and $parts[1] -eq 'true' -and
            $parts[2].Contains('ALL') -and $parts[3].Contains('no-new-privileges')) -Name 'container-privilege-restrictions'
        $ports = $parts[4] | ConvertFrom-Json
        foreach ($port in $ports.PSObject.Properties) {
            foreach ($binding in $port.Value) {
                Assert-Check -Passed ($binding.HostIp -eq '127.0.0.1') -Name 'loopback-only-exposure'
            }
        }
    }
    Compose restart *> (Join-Path $reportRoot 'restart.log')
    Compose up -d --no-recreate --wait --wait-timeout 180 *> (Join-Path $reportRoot 'healthy.log')
    S3-Check --persistence-check --iterations 0 *> (Join-Path $reportRoot 'persistence-check.log')
    Assert-Check -Passed $true -Name 'objects-survive-complete-service-restart'
    S3-Check --persistence-write --iterations 0 *> (Join-Path $reportRoot 'recreate-write.log')
    Compose down *> (Join-Path $reportRoot 'remove-containers.log')
    Install-Action -Action start
    Install-Action -Action admin
    S3-Check --persistence-check --iterations 0 *> (Join-Path $reportRoot 'recreate-check.log')
    Assert-Check -Passed ((Get-FileHash -LiteralPath $adminFile -Algorithm SHA256).Hash -eq $adminHash) -Name 'container-recreation-preserves-objects-and-password'
    S3-Check --security --iterations $Iterations --concurrency 4 *> (Join-Path $reportRoot 'security-performance.log')
    Assert-Check -Passed $true -Name 's3-security-and-performance'
    if ($RunSdk) {
        $values = Read-Environment -Path $environmentFile
        foreach ($name in $previousEnvironment.Keys) {
            if ($values.ContainsKey($name)) {
                [System.Environment]::SetEnvironmentVariable($name, $values[$name], 'Process')
            }
        }
        $env:YMCLOUD_RUN_S3_TESTS = 'true'
        Push-Location $projectRoot
        try {
            & mvn -pl ymcloud-web -am test '-Dtest=StorageS3IntegrationTest' '-Dsurefire.failIfNoSpecifiedTests=false' *> (Join-Path $reportRoot 'sdk.log')
            if ($LASTEXITCODE -ne 0) { throw 'Actual AWS SDK integration failed.' }
        } finally {
            Pop-Location
        }
        Assert-Check -Passed $true -Name 'actual-aws-sdk-50MiB-and-concurrent-roundtrips'
    }
    S3-Check --persistence-write --iterations 0 *> (Join-Path $reportRoot 'before-reset.log')
    $legacyKey = Join-Path $deployment 'secrets/storage-config.key'
    [System.IO.File]::WriteAllText($legacyKey, 'legacy-oj-key-must-be-preserved', $utf8)
    Install-Action -Action reset -DeleteData
    Assert-Check -Passed (-not (Test-Path -LiteralPath $environmentFile) -and
        -not (Test-Path -LiteralPath $adminFile)) -Name 'reset-clears-installation-credentials'
    Assert-Check -Passed ([System.IO.File]::ReadAllText($legacyKey, $utf8) -eq 'legacy-oj-key-must-be-preserved') -Name 'reset-preserves-oj-master-key'
    $volumes = & docker volume ls --quiet --filter "label=com.docker.compose.project=$runName"
    Assert-Check -Passed ($LASTEXITCODE -eq 0 -and -not $volumes) -Name 'reset-removes-all-project-volumes'
    Install-Action -Action start
    Assert-Check -Passed ((Get-FileHash -LiteralPath $environmentFile -Algorithm SHA256).Hash -ne $originalHash) -Name 'zero-state-reinstall-generates-new-credentials'
    S3-Check --security --iterations 0 *> (Join-Path $reportRoot 'fresh-security.log')
    Assert-Check -Passed $true -Name 'zero-state-reinstall-s3-security'
    $succeeded = $true
} finally {
    if ($created) {
        if ($env:COMPOSE_PROJECT_NAME -ne $runName -or $runName -notmatch '^ymcloud-storage-test-[a-f0-9]{32}$') {
            throw 'Refusing to clean an unrelated Compose project.'
        }
        Compose down --volumes --remove-orphans *> (Join-Path $reportRoot 'cleanup.log')
    }
    foreach ($name in $previousEnvironment.Keys) {
        [System.Environment]::SetEnvironmentVariable($name, $previousEnvironment[$name], 'Process')
    }
    $prefix = $temporaryRoot.TrimEnd([System.IO.Path]::DirectorySeparatorChar) + [System.IO.Path]::DirectorySeparatorChar
    if (-not $fixtureRoot.StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase) -or
        (Split-Path -Leaf $fixtureRoot) -ne $runName) {
        throw 'Refusing to delete a fixture outside the temporary directory.'
    }
    if (Test-Path -LiteralPath $fixtureRoot) { Remove-Item -LiteralPath $fixtureRoot -Recurse -Force }
    $report = @{ Succeeded = $succeeded; Checks = $checks; FixturesRemoved = -not (Test-Path -LiteralPath $fixtureRoot) }
    $null = [System.IO.Directory]::CreateDirectory($reportRoot)
    [System.IO.File]::WriteAllText((Join-Path $reportRoot 'report.json'), ($report | ConvertTo-Json -Depth 4), $utf8)
}
Write-Host "PASS $($checks.Count) real Docker checks. Report: $reportRoot"
