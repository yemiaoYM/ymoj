[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$deploymentRoot = $PSScriptRoot
$composeArgs = @('compose', '--project-directory', $deploymentRoot, '-f', (Join-Path $deploymentRoot 'compose.yml'))

function Normalize-HostPath {
    param([string]$Path)
    $normalized = $Path.Replace('\', '/').TrimEnd('/')
    if ($env:OS -eq 'Windows_NT') {
        # Docker Desktop may report Windows bind sources using its Linux host mount prefix.
        if ($normalized.StartsWith('/run/desktop/mnt/host/')) {
            $normalized = $normalized.Substring('/run/desktop/mnt/host/'.Length)
            if ($normalized -match '^([a-zA-Z])/(.*)$') {
                $normalized = $Matches[1] + ':/' + $Matches[2]
            }
        }
        return $normalized.ToLowerInvariant()
    }
    return $normalized
}

$containers = & docker @composeArgs ps -q
if ($LASTEXITCODE -ne 0 -or -not $containers) {
    throw 'No running ymcloud-storage deployment found.'
}
$requiredServices = @('master', 'volume', 'filer', 's3')
$foundServices = [System.Collections.Generic.List[string]]::new()
$internalNetwork = & docker network inspect ymcloud-storage_storage --format '{{.Internal}}'
if ($LASTEXITCODE -ne 0 -or $internalNetwork -ne 'true') {
    throw 'Storage administrative network must be internal.'
}

foreach ($containerId in $containers) {
    # Inspect only security-related fields; never dump Config.Env or the credential file.
    $details = & docker inspect --format '{{json .HostConfig.PortBindings}}|{{json .HostConfig.SecurityOpt}}|{{json .HostConfig.CapDrop}}|{{json .HostConfig.ReadonlyRootfs}}|{{json .Mounts}}|{{index .Config.Labels "com.docker.compose.service"}}|{{.Config.User}}|{{.HostConfig.Memory}}|{{.HostConfig.NanoCpus}}' $containerId
    if ($LASTEXITCODE -ne 0) {
        throw 'Unable to inspect storage container.'
    }
    $parts = $details -split '\|', 9
    $ports = $parts[0] | ConvertFrom-Json
    $security = $parts[1] | ConvertFrom-Json
    $capabilities = $parts[2] | ConvertFrom-Json
    $readOnly = $parts[3] | ConvertFrom-Json
    $mounts = $parts[4] | ConvertFrom-Json
    $service = $parts[5]
    if ($service -notin ($requiredServices + @('admin'))) {
        throw "Unexpected service $service in the dedicated storage deployment."
    }
    $foundServices.Add($service)
    $configuration = & docker inspect --format '{{index .Config.Labels "com.docker.compose.project.config_files"}}' $containerId
    if ($LASTEXITCODE -ne 0 -or
        (Normalize-HostPath $configuration) -ne (Normalize-HostPath (Join-Path $deploymentRoot 'compose.yml'))) {
        throw "Service $service must use the root seaweedfs Compose configuration."
    }
    $runtimeUser = $parts[6]
    $memoryLimit = [long]$parts[7]
    $cpuLimit = [long]$parts[8]
    if ($ports) {
        foreach ($port in $ports.PSObject.Properties) {
            foreach ($binding in $port.Value) {
                if ($binding.HostIp -ne '127.0.0.1') {
                    throw "Service $service exposes a non-loopback port."
                }
            }
        }
    }
    if ($service -in @('master', 'volume', 'filer') -and $ports.PSObject.Properties.Count -gt 0) {
        throw "Service $service publishes an administrative port."
    }
    if ($runtimeUser -ne '1000:1000') {
        throw "Service $service must run as the SeaweedFS non-root UID."
    }
    if (-not $readOnly -or $capabilities -notcontains 'ALL') {
        throw "Service $service lacks read-only root filesystem or capability restrictions."
    }
    if ($security -notcontains 'no-new-privileges:true') {
        throw "Service $service lacks no-new-privileges."
    }
    if ($memoryLimit -ne 805306368 -or $cpuLimit -ne 1000000000) {
        throw "Service $service lacks the configured memory and CPU limits."
    }
    if ($service -eq 's3') {
        $credentialMount = $mounts | Where-Object { $_.Destination -eq '/etc/seaweedfs/s3.json' }
        if (@($credentialMount).Count -ne 1 -or $credentialMount.RW -or
            (Normalize-HostPath $credentialMount.Source) -ne (Normalize-HostPath (Join-Path $deploymentRoot 'secrets/s3.json'))) {
            throw 'S3 identity configuration must be mounted read-only from the root seaweedfs directory.'
        }
    }
    if ($service -eq 'admin') {
        $credentialMount = $mounts | Where-Object { $_.Destination -eq '/etc/seaweedfs/admin.options' }
        if (@($credentialMount).Count -ne 1 -or $credentialMount.RW -or
            (Normalize-HostPath $credentialMount.Source) -ne (Normalize-HostPath (Join-Path $deploymentRoot 'secrets/admin.options'))) {
            throw 'Admin UI credentials must be mounted read-only from the root seaweedfs directory.'
        }
        if ($ports.PSObject.Properties.Name -contains '33646/tcp') {
            throw 'Admin worker gRPC must remain internal.'
        }
    }
    Write-Host "PASS $service deployment exposure and privilege checks"
}

foreach ($requiredService in $requiredServices) {
    if ($requiredService -notin $foundServices) {
        throw "Required storage service $requiredService is missing."
    }
}
