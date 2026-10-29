[CmdletBinding()]
param(
    [string]$WorldArchivePath = (Join-Path ([IO.Path]::GetTempPath()) 'CopiMine-world-remote-20261029T123355Z.tar.gz'),
    [string]$PluginStagePath
)

$ErrorActionPreference = 'Stop'

$identity = [Security.Principal.WindowsIdentity]::GetCurrent()
$principal = [Security.Principal.WindowsPrincipal]::new($identity)
if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    throw 'Run this script in PowerShell opened with Run as administrator. It does not change ACLs or elevate itself.'
}

$root = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../..')).Path
$expectedRuntime = Join-Path $root 'local-runtime/end-rift-server-26.3'
$runtime = (Resolve-Path -LiteralPath $expectedRuntime).Path
if (-not [string]::Equals($runtime, $expectedRuntime, [StringComparison]::OrdinalIgnoreCase)) {
    throw "Local runtime resolved outside its canonical directory: $runtime"
}

if ([string]::IsNullOrWhiteSpace($PluginStagePath)) {
    $PluginStagePath = Join-Path $root 'build/minecraft-26.3/server-plugins-codex-aa25309e553e4f3598c36526666da0c0'
}
$PluginStagePath = (Resolve-Path -LiteralPath $PluginStagePath).Path
$installerPath = Join-Path $root 'scripts/minecraft/install_migration_server_plugins.py'
$worldExtractor = Join-Path $root 'scripts/minecraft/copimine_world_snapshot.py'
$archiveSha256 = '1c3af8ec92fad95d8a5632ce5357c852c577b020965fa79f6eb5dbd97ba04af2'
$resourcePackUrl = 'https://copimine.ru/assets/CopiMineResourcePack-26.3-7229299416f31e9c.zip'
$resourcePackSha1 = 'ae863669efb0bb08210ef268f2a74f4ea247552a'
$resourcePackSha256 = '7229299416f31e9c22086b20b2adf397b8b5d3d4cf42ef969517b7f484173ca1'
$offlineKuusoUuid = '5fa2e00c-ad45-3663-837f-d7ce56e73d3d'

if (-not (Test-Path -LiteralPath $WorldArchivePath -PathType Leaf)) {
    throw "Remote CopiMine world archive is missing: $WorldArchivePath"
}
$actualArchiveSha256 = (Get-FileHash -LiteralPath $WorldArchivePath -Algorithm SHA256).Hash.ToLowerInvariant()
if ($actualArchiveSha256 -ne $archiveSha256) {
    throw "World archive SHA-256 mismatch: expected $archiveSha256, found $actualArchiveSha256"
}

$packPath = Join-Path $root 'build/minecraft-26.3/CopiMineResourcePack-26.3.zip'
if (-not (Test-Path -LiteralPath $packPath -PathType Leaf)) {
    throw "Pinned Minecraft 26.3 resource pack is missing: $packPath"
}
$actualPackSha256 = (Get-FileHash -LiteralPath $packPath -Algorithm SHA256).Hash.ToLowerInvariant()
if ($actualPackSha256 -ne $resourcePackSha256) {
    throw "Resource pack SHA-256 mismatch: expected $resourcePackSha256, found $actualPackSha256"
}

$python = Get-Command 'python.exe' -ErrorAction SilentlyContinue
if ($null -eq $python) { throw 'Python 3 is required for the pinned plugin and safe world archive checks.' }

$verifyStageCode = @'
import importlib.util
import pathlib
import sys
root, stage = pathlib.Path(sys.argv[1]), pathlib.Path(sys.argv[2])
source = root / 'scripts/minecraft/install_migration_server_plugins.py'
spec = importlib.util.spec_from_file_location('copimine_migration_plugins', source)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)
records = module.select_runtime_records(module.load_install_records(root))
expected = {record['filename'] for record in records}
actual = {path.name for path in stage.glob('*.jar')}
if actual != expected:
    raise SystemExit(f'Plugin stage inventory mismatch: expected {len(expected)} pinned JARs, found {len(actual)}')
for record in records:
    module.verify_staged(record, stage / record['filename'])
print(f'PLUGIN_STAGE_VERIFIED count={len(records)}')
'@
& $python.Source -c $verifyStageCode $root $PluginStagePath
if ($LASTEXITCODE -ne 0) { throw "Pinned plugin stage verification failed with exit code $LASTEXITCODE." }

$runtimeStoppedCode = @'
import importlib.util
import pathlib
import sys
root, server = map(pathlib.Path, sys.argv[1:3])
source = root / 'scripts/minecraft/install_migration_server_plugins.py'
spec = importlib.util.spec_from_file_location('copimine_migration_plugins', source)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)
properties = module._java_property_entries((server / 'server.properties').read_text(encoding='utf-8'))
module._validate_local_runtime_properties(properties)
for key in ('server-port', 'rcon.port'):
    values = properties.get(key, [])
    if len(values) != 1 or not values[0].strip().isdigit():
        raise SystemExit('Missing or duplicate local runtime port: ' + key)
    port = int(values[0].strip())
    if not 1 <= port <= 65535:
        raise SystemExit('Invalid local runtime port: ' + key)
    bind = properties['server-ip'][0].strip() if key == 'server-port' else ''
    if module.port_open(port, bind):
        raise SystemExit('Local runtime is still listening on port ' + str(port))
print('LOCAL_RUNTIME_STOPPED ports=server,rcon')
'@
function Assert-LocalRuntimeStopped {
    & $python.Source -c $runtimeStoppedCode $root $runtime
    if ($LASTEXITCODE -ne 0) {
        throw "Local Paper runtime is running or its stopped state could not be verified. Stop it cleanly before continuing."
    }
}

$serverPropertiesPath = Join-Path $runtime 'server.properties'
$opsPath = Join-Path $runtime 'ops.json'
if (-not (Test-Path -LiteralPath $serverPropertiesPath -PathType Leaf)) {
    throw "Local server.properties is missing: $serverPropertiesPath"
}
$propertiesText = [IO.File]::ReadAllText($serverPropertiesPath)
$securityProperties = [ordered]@{
    'online-mode' = 'online-mode=false'
    'server-ip' = 'server-ip=127.0.0.1'
    'enable-rcon' = 'enable-rcon=false'
    'server-port' = 'server-port=25567'
}
$securityPropertyLines = @{}
foreach ($key in $securityProperties.Keys) { $securityPropertyLines[$key] = @() }
$propertyLines = [IO.File]::ReadAllLines($serverPropertiesPath)
foreach ($line in $propertyLines) {
    if ($line -match '^\s*[#!]' -or [string]::IsNullOrWhiteSpace($line)) { continue }
    foreach ($key in $securityProperties.Keys) {
        if ($line -match ('^\s*' + [regex]::Escape($key) + '\b')) {
            $securityPropertyLines[$key] += $line.Trim()
            break
        }
    }
}
foreach ($key in $securityProperties.Keys) {
    $lines = @($securityPropertyLines[$key])
    if ($lines.Count -ne 1 -or $lines[0] -cne $securityProperties[$key]) {
        throw "Local server.properties must contain exactly one canonical $($securityProperties[$key]) entry."
    }
}
$rconPortLines = @($propertyLines | Where-Object { $_ -match '^\s*rcon\.port\b' })
if ($rconPortLines.Count -ne 1) {
    throw 'Local server.properties must contain exactly one canonical rcon.port entry.'
}
$rconPortMatch = [regex]::Match($rconPortLines[0].Trim(), '^rcon\.port=(\d+)$')
if (-not $rconPortMatch.Success) { throw 'Local server.properties must contain exactly one canonical rcon.port entry.' }
$rconPort = [int]$rconPortMatch.Groups[1].Value
if ($rconPort -lt 1 -or $rconPort -gt 65535) { throw 'Local server.properties has an invalid rcon.port.' }
$desiredProperties = [ordered]@{
    'level-name' = 'CopiMine'
    'resource-pack' = $resourcePackUrl
    'resource-pack-sha1' = $resourcePackSha1
    'require-resource-pack' = 'true'
}
foreach ($key in $desiredProperties.Keys) {
    $pattern = '(?m)^[ \t]*' + [regex]::Escape($key) + '(?=[ \t:=]|$)[^\r\n]*'
    if ([regex]::Matches($propertiesText, $pattern).Count -gt 1) {
        throw "server.properties has duplicate $key entries; refusing to update it."
    }
}

$currentWorldPath = Join-Path $runtime 'CopiMine'
if (Test-Path -LiteralPath $currentWorldPath) {
    $existingWorld = Get-Item -LiteralPath $currentWorldPath -Force
    if (-not $existingWorld.PSIsContainer -or ($existingWorld.Attributes -band [IO.FileAttributes]::ReparsePoint)) {
        throw "Existing CopiMine world is not a plain directory; refusing to replace it: $currentWorldPath"
    }
}

if (Test-Path -LiteralPath $opsPath -PathType Leaf) {
    $opsText = [IO.File]::ReadAllText($opsPath).Trim()
    if (-not $opsText.StartsWith('[') -or -not $opsText.EndsWith(']')) {
        throw 'Local ops.json must contain a JSON array.'
    }
    $opsData = @(ConvertFrom-Json -InputObject $opsText -ErrorAction Stop)
    foreach ($entry in $opsData) {
        if ($null -eq $entry) { throw 'Local ops.json contains a null operator entry.' }
        if (-not $entry.PSObject.Properties['uuid'] -or -not $entry.PSObject.Properties['name'] -or
            -not $entry.PSObject.Properties['level']) {
            throw 'Local ops.json contains an operator entry without uuid, name, or level.'
        }
    }
} else {
    $opsData = @()
}
$namedKuuso = @($opsData | Where-Object { $_.name -and $_.name.Equals('kuuso', [StringComparison]::OrdinalIgnoreCase) })
$uuidKuuso = @($opsData | Where-Object { $_.uuid -and $_.uuid.Equals($offlineKuusoUuid, [StringComparison]::OrdinalIgnoreCase) })
if ($namedKuuso.Count -gt 0 -and @($namedKuuso | Where-Object { -not $_.uuid -or -not $_.uuid.Equals($offlineKuusoUuid, [StringComparison]::OrdinalIgnoreCase) }).Count -gt 0) {
    throw 'Local ops.json already contains kuuso with a different UUID; refusing to alter that identity.'
}
if ($uuidKuuso.Count -gt 1 -or $namedKuuso.Count -gt 1) { throw 'Local ops.json contains duplicate kuuso operator entries.' }

Assert-LocalRuntimeStopped

$timestamp = [DateTime]::UtcNow.ToString('yyyyMMddTHHmmssZ') + '-' + [guid]::NewGuid().ToString('N')
$backupRoot = Join-Path $runtime ("migration-backups/copimine-remote-sync-{0}" -f $timestamp)
if (Test-Path -LiteralPath $backupRoot) { throw "Backup destination already exists: $backupRoot" }
New-Item -ItemType Directory -Path $backupRoot | Out-Null
Copy-Item -LiteralPath $serverPropertiesPath -Destination (Join-Path $backupRoot 'server.properties.before')
if (Test-Path -LiteralPath $opsPath -PathType Leaf) {
    Copy-Item -LiteralPath $opsPath -Destination (Join-Path $backupRoot 'ops.json.before')
} else {
    [IO.File]::WriteAllText((Join-Path $backupRoot 'ops.json.was-absent'), 'Local ops.json did not exist before this install.' + "`r`n")
}

$extractRoot = Join-Path $backupRoot 'world-unpacked'
& $python.Source $worldExtractor $WorldArchivePath $extractRoot
if ($LASTEXITCODE -ne 0) { throw "Safe world snapshot extraction failed with exit code $LASTEXITCODE. Review $backupRoot" }
$newWorldPath = Join-Path $extractRoot 'CopiMine'
if (-not (Test-Path -LiteralPath (Join-Path $newWorldPath 'level.dat') -PathType Leaf)) {
    throw "Validated archive extraction did not produce CopiMine/level.dat: $newWorldPath"
}

Assert-LocalRuntimeStopped
$pluginInstallCode = @'
import importlib.util
import pathlib
import sys
root, server, stage = map(pathlib.Path, sys.argv[1:4])
source = root / 'scripts/minecraft/install_migration_server_plugins.py'
spec = importlib.util.spec_from_file_location('copimine_migration_plugins', source)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)
records = module.load_install_records(root)
receipt = module.install_records(root, server, records, stage, receipt_writer=module.write_receipt)
print('LOCAL_PLUGIN_INSTALL_OK count=' + str(len(receipt.get('installed', []))) + ' inventory=' + str(len(receipt.get('pluginInventory', []))))
'@
& $python.Source -c $pluginInstallCode $root $runtime $PluginStagePath
if ($LASTEXITCODE -ne 0) { throw "Local pinned plugin installation failed with exit code $LASTEXITCODE. Existing plugin backups are retained under $runtime/migration-backups/plugins." }

$currentWorldPath = Join-Path $runtime 'CopiMine'
$previousWorldPath = Join-Path $backupRoot 'CopiMine.before'
$previousWorldMoved = $false
Assert-LocalRuntimeStopped
if (Test-Path -LiteralPath $currentWorldPath) {
    Move-Item -LiteralPath $currentWorldPath -Destination $previousWorldPath
    $previousWorldMoved = $true
}
try {
    Move-Item -LiteralPath $newWorldPath -Destination $currentWorldPath
} catch {
    $moveFailure = $_
    if ($previousWorldMoved -and -not (Test-Path -LiteralPath $currentWorldPath) -and
        (Test-Path -LiteralPath $previousWorldPath -PathType Container)) {
        try {
            Move-Item -LiteralPath $previousWorldPath -Destination $currentWorldPath
            Write-Warning 'New world move failed; restored the previous CopiMine world.'
        } catch {
            throw "Moving the remote world failed and restoring the previous world also failed. Previous world remains at $previousWorldPath. Move error: $moveFailure. Restore error: $_"
        }
    }
    throw $moveFailure
}

foreach ($key in $desiredProperties.Keys) {
    $pattern = '(?m)^[ \t]*' + [regex]::Escape($key) + '(?=[ \t:=]|$)[^\r\n]*'
    $matches = [regex]::Matches($propertiesText, $pattern)
    if ($matches.Count -gt 1) { throw "server.properties has duplicate $key entries; review the backup at $backupRoot" }
    $replacement = $key + '=' + $desiredProperties[$key]
    if ($matches.Count -eq 1) {
        $propertiesText = [regex]::Replace($propertiesText, $pattern, [System.Text.RegularExpressions.MatchEvaluator]{ param($match) $replacement })
    } else {
        if ($propertiesText.Length -gt 0 -and -not $propertiesText.EndsWith("`n")) { $propertiesText += "`r`n" }
        $propertiesText += $replacement + "`r`n"
    }
}
[IO.File]::WriteAllText($serverPropertiesPath, $propertiesText, [Text.UTF8Encoding]::new($false))

if ($uuidKuuso.Count -eq 1) {
    $operator = $uuidKuuso[0]
    $operator.name = 'kuuso'
    $operator.level = 4
    $operator.bypassesPlayerLimit = $true
} else {
    $opsData = @($opsData) + @([pscustomobject]@{
        uuid = $offlineKuusoUuid
        name = 'kuuso'
        level = 4
        bypassesPlayerLimit = $true
    })
}
$opsJson = ConvertTo-Json -InputObject @($opsData) -Depth 8
[IO.File]::WriteAllText($opsPath, $opsJson + "`r`n", [Text.UTF8Encoding]::new($false))

Write-Output "Remote CopiMine world installed at $currentWorldPath"
Write-Output "Pinned plugin candidates installed from $PluginStagePath"
Write-Output "Texture pack required: $resourcePackUrl (SHA1 $resourcePackSha1)"
Write-Output "kuuso operator entry: level 4; previous world, server.properties, and ops.json are backed up at $backupRoot"
Write-Output 'Starting the loopback-only Paper server on 127.0.0.1:25567 in this PowerShell window.'

$localLauncher = Join-Path $root 'scripts/minecraft/StartCopiMineWavesLocal.ps1'
& $localLauncher -RepositoryRoot $root
