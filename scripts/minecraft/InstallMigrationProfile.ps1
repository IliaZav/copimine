[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$ProfileDirectory,
    [switch]$DependenciesOnly,
    [switch]$ValidateOnly
)
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../..')).Path
$profile = Get-Content -LiteralPath (Join-Path $root 'tools/minecraft-26.3/profile.lock.json') -Raw | ConvertFrom-Json
$lock = Get-Content -LiteralPath (Join-Path $root 'tools/minecraft-26.3/client-mods.lock.json') -Raw | ConvertFrom-Json
$target = (Resolve-Path -LiteralPath $ProfileDirectory).Path
$id = Split-Path -Leaf $target
$definition = Get-Content -LiteralPath (Join-Path $target ($id + '.json')) -Raw | ConvertFrom-Json
if ($definition.downloads.client.sha1 -ne $profile.minecraftClient.sha1) { throw 'Profile is not the pinned Minecraft 26.3 client' }
if ($definition.mainClass -ne 'net.fabricmc.loader.impl.launch.knot.KnotClient' -or
    @($definition.libraries.name) -notcontains ('net.fabricmc:fabric-loader:' + $profile.fabric.loader)) {
    throw 'Profile must use the pinned Fabric Loader'
}
if ($definition.javaVersion.majorVersion -lt $profile.minimumJava) { throw 'Profile requires Java 25 or later' }
if (-not $DependenciesOnly -and -not $profile.fabric.clientPortComplete) {
    throw 'CopiMineClient port is incomplete; use DependenciesOnly to prepare external mods and the resource pack'
}
foreach ($module in $lock.modules) {
    if ([IO.Path]::GetFileName($module.filename) -ne $module.filename -or $module.filename -match '[\\/:]') { throw 'Unsafe mod filename' }
    if ($module.url -notmatch '^https://cdn\.modrinth\.com/data/' -or $module.sha512 -notmatch '^[0-9a-f]{128}$') { throw 'Mod source or digest is invalid' }
}
if ($ValidateOnly) { Write-Output 'Pinned 26.3 Fabric profile validated without mutation'; return }
$stage = Join-Path $root 'build/minecraft-26.3/client-mods'
New-Item -ItemType Directory -Path $stage -Force | Out-Null
foreach ($module in $lock.modules) {
    $path = Join-Path $stage $module.filename
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { Invoke-WebRequest -Uri $module.url -OutFile $path -TimeoutSec 120 }
    if ((Get-FileHash -LiteralPath $path -Algorithm SHA512).Hash.ToLowerInvariant() -ne $module.sha512) { throw ('Mod digest mismatch: ' + $module.filename) }
}
$pack = Join-Path $root 'build/minecraft-26.3/CopiMineResourcePack-26.3.zip'
if (-not (Test-Path -LiteralPath $pack -PathType Leaf)) { throw 'Build the 26.3 resource pack before installing' }
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [IO.Compression.ZipFile]::OpenRead($pack)
try {
    $entry = $archive.GetEntry('pack.mcmeta')
    $reader = [IO.StreamReader]::new($entry.Open())
    try { $metadata = $reader.ReadToEnd() | ConvertFrom-Json } finally { $reader.Dispose() }
    if (($metadata.pack.min_format -join '.') -ne '97.1' -or ($metadata.pack.max_format -join '.') -ne '97.1') { throw 'Resource pack targets a different Minecraft format' }
} finally { $archive.Dispose() }
$mods = Join-Path $target 'mods'
$packs = Join-Path $target 'resourcepacks'
New-Item -ItemType Directory -Path $mods,$packs -Force | Out-Null
$installed = @()
foreach ($module in $lock.modules) {
    $destination = Join-Path $mods $module.filename
    if ((Test-Path -LiteralPath $destination -PathType Leaf) -and
        (Get-FileHash -LiteralPath $destination -Algorithm SHA512).Hash.ToLowerInvariant() -ne $module.sha512) {
        throw ('Refusing to replace a different existing mod: ' + $module.filename)
    }
    Copy-Item -LiteralPath (Join-Path $stage $module.filename) -Destination $destination -Force
    if ((Get-FileHash -LiteralPath $destination -Algorithm SHA512).Hash.ToLowerInvariant() -ne $module.sha512) { throw 'Installed mod verification failed' }
    $installed += @{filename=$module.filename;sha512=$module.sha512;version=$module.version}
}
$packTarget = Join-Path $packs 'CopiMineResourcePack-26.3.zip'
if (Test-Path -LiteralPath $packTarget -PathType Leaf) {
    Copy-Item -LiteralPath $packTarget -Destination ($packTarget + '.backup-' + (Get-Date -Format 'yyyyMMddHHmmss'))
}
Copy-Item -LiteralPath $pack -Destination $packTarget -Force
$packHash = (Get-FileHash -LiteralPath $pack -Algorithm SHA256).Hash.ToLowerInvariant()
if ((Get-FileHash -LiteralPath $packTarget -Algorithm SHA256).Hash.ToLowerInvariant() -ne $packHash) { throw 'Installed resource pack verification failed' }
@{profile=$id;minecraftVersion='26.3';mods=$installed;resourcePackSha256=$packHash;copimineClientReady=[bool]$profile.fabric.clientPortComplete;nativeVerified=$false} |
    ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $target 'copimine-migration-receipt.json') -Encoding UTF8
Write-Output ('Installed and verified ' + $installed.Count + ' external 26.3 mods and the resource pack in ' + $id)
if ($DependenciesOnly) { Write-Output 'CopiMineClient source port is still pending; the profile is not yet accepted for wave testing' }
