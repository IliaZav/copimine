[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string] $MinecraftRoot,
    [Parameter(Mandatory = $true)]
    [string] $DestinationRoot,
    [string] $MinecraftVersion = '1.21.1',
    [string] $FabricLoaderVersion = '0.19.3'
)

$ErrorActionPreference = 'Stop'
$source = (Resolve-Path -LiteralPath $MinecraftRoot -ErrorAction Stop).Path
$destination = [System.IO.Path]::GetFullPath($DestinationRoot)
New-Item -ItemType Directory -Path $destination -Force | Out-Null

$required = @(
    (Join-Path $source 'assets'),
    (Join-Path $source 'libraries'),
    (Join-Path $source "versions/$MinecraftVersion/$MinecraftVersion.json"),
    (Join-Path $source "versions/fabric-loader-$FabricLoaderVersion-$MinecraftVersion/fabric-loader-$FabricLoaderVersion-$MinecraftVersion.json")
)
foreach ($path in $required) {
    if (-not (Test-Path -LiteralPath $path)) {
        throw "Minecraft baseline is incomplete; missing $path"
    }
}

$content = Join-Path $destination '_offline-seed-content'
$archive = Join-Path $destination 'offline-minecraft-baseline.zip'
$metadata = Join-Path $destination 'offline-minecraft-baseline.json'
if (Test-Path -LiteralPath $content) { Remove-Item -LiteralPath $content -Recurse -Force }
if (Test-Path -LiteralPath $archive) { Remove-Item -LiteralPath $archive -Force }
if (Test-Path -LiteralPath $metadata) { Remove-Item -LiteralPath $metadata -Force }
New-Item -ItemType Directory -Path $content -Force | Out-Null

foreach ($directoryName in @('.fabric', 'assets', 'libraries', 'versions', 'config', 'CustomSkinLoader', 'resourcepacks', 'shaderpacks')) {
    $sourceDirectory = Join-Path $source $directoryName
    if (Test-Path -LiteralPath $sourceDirectory -PathType Container) {
        Copy-Item -LiteralPath $sourceDirectory -Destination (Join-Path $content $directoryName) -Recurse -Force
    }
}

foreach ($fileName in @('options.txt', 'optionsof.txt')) {
    $sourceFile = Join-Path $source $fileName
    if (Test-Path -LiteralPath $sourceFile -PathType Leaf) {
        Copy-Item -LiteralPath $sourceFile -Destination (Join-Path $content $fileName) -Force
    }
}

Add-Type -AssemblyName System.IO.Compression.FileSystem
[System.IO.Compression.ZipFile]::CreateFromDirectory(
    $content,
    $archive,
    [System.IO.Compression.CompressionLevel]::Fastest,
    $false)

$info = Get-Item -LiteralPath $archive
$hash = (Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash.ToLowerInvariant()
$document = [ordered]@{
    schemaVersion = 1
    minecraftVersion = $MinecraftVersion
    fabricLoaderVersion = $FabricLoaderVersion
    archiveFileName = 'offline-minecraft-baseline.zip'
    sizeBytes = [int64]$info.Length
    sha256 = $hash
}
[System.IO.File]::WriteAllText(
    $metadata,
    (($document | ConvertTo-Json -Depth 4) + [Environment]::NewLine),
    [System.Text.UTF8Encoding]::new($false))
Remove-Item -LiteralPath $content -Recurse -Force

Write-Output "OFFLINE_BASELINE=$archive"
Write-Output "OFFLINE_BASELINE_METADATA=$metadata"
Write-Output "OFFLINE_BASELINE_SHA256=$hash"
Write-Output "OFFLINE_BASELINE_SIZE=$($info.Length)"
