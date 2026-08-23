[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$SourceRoot,

    [Parameter(Mandatory = $true)]
    [string]$TargetRoot
)

$ErrorActionPreference = 'Stop'

$sourceRootPath = (Resolve-Path -LiteralPath $SourceRoot).Path
$targetRootPath = (Resolve-Path -LiteralPath $TargetRoot).Path
$sourceBuild = Join-Path $sourceRootPath 'resourcepacks\build'
$targetBuild = Join-Path $targetRootPath 'resourcepacks\build'
$packName = 'CopiMineResourcePack.zip'
$packPath = Join-Path $sourceBuild $packName
$sha1Path = Join-Path $sourceBuild 'CopiMineResourcePack.sha1'
$sha256Path = Join-Path $sourceBuild 'CopiMineResourcePack.sha256'

foreach ($path in @($packPath, $sha1Path, $sha256Path)) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "End Rift local artifact is missing: $path"
    }
}

function Get-DeclaredHash {
    param([string]$Path, [int]$Length)
    $value = (Get-Content -LiteralPath $Path -Raw).Trim().Split([char[]]@(' ', "`t"))[0].ToLowerInvariant()
    if ($value -notmatch "^[0-9a-f]{$Length}$") {
        throw "Invalid hash in $Path"
    }
    return $value
}

function Assert-EndRiftPack {
    param([string]$Path)

    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive = [System.IO.Compression.ZipFile]::OpenRead($Path)
    try {
        $entries = @($archive.Entries | ForEach-Object FullName)
        $requiredEntries = @(
            'assets/minecraft/models/item/paper.json',
            'assets/copimine/models/item/end_event_core.json',
            'assets/copimine/models/item/end_event_core_charged.json',
            'assets/copimine/models/item/end_event_pad.json',
            'assets/copimine/models/item/end_event_pad_occupied.json',
            'assets/copimine/textures/item/end_event_core.png',
            'assets/copimine/textures/item/end_event_core_charged.png',
            'assets/copimine/textures/item/end_event_pad.png',
            'assets/copimine/textures/item/end_event_pad_occupied.png'
        )
        foreach ($entry in $requiredEntries) {
            if ($entries -notcontains $entry) {
                throw "End Rift resource pack is missing $entry"
            }
        }

        $paperEntry = $archive.GetEntry('assets/minecraft/models/item/paper.json')
        $reader = New-Object System.IO.StreamReader($paperEntry.Open())
        try { $paperJson = $reader.ReadToEnd() } finally { $reader.Dispose() }
        foreach ($marker in @('830001', '830002', '830003', '830005', 'copimine:item/end_event_core', 'copimine:item/end_event_core_charged', 'copimine:item/end_event_pad', 'copimine:item/end_event_pad_occupied')) {
            if ($paperJson -notmatch [regex]::Escape($marker)) {
                throw "End Rift paper override is missing $marker"
            }
        }

        $vanillaBlockOverrides = @($entries | Where-Object { $_ -match '^assets/minecraft/(textures|models)/block/' })
        if ($vanillaBlockOverrides.Count -ne 0) {
            throw "Resource pack unexpectedly overrides vanilla block assets: $($vanillaBlockOverrides -join ', ')"
        }
    } finally {
        $archive.Dispose()
    }
}

$declaredSha1 = Get-DeclaredHash -Path $sha1Path -Length 40
$declaredSha256 = Get-DeclaredHash -Path $sha256Path -Length 64
$actualSha1 = (Get-FileHash -LiteralPath $packPath -Algorithm SHA1).Hash.ToLowerInvariant()
$actualSha256 = (Get-FileHash -LiteralPath $packPath -Algorithm SHA256).Hash.ToLowerInvariant()
if ($actualSha1 -ne $declaredSha1) { throw "Resource pack SHA1 mismatch. declared=$declaredSha1 actual=$actualSha1" }
if ($actualSha256 -ne $declaredSha256) { throw "Resource pack SHA256 mismatch. declared=$declaredSha256 actual=$actualSha256" }
Assert-EndRiftPack -Path $packPath

New-Item -ItemType Directory -Path $targetBuild -Force | Out-Null
if ([System.IO.Path]::GetFullPath($sourceBuild) -ne [System.IO.Path]::GetFullPath($targetBuild)) {
    foreach ($name in @($packName, 'CopiMineResourcePack.sha1', 'CopiMineResourcePack.sha256')) {
        Copy-Item -LiteralPath (Join-Path $sourceBuild $name) -Destination (Join-Path $targetBuild $name) -Force
    }
}

$targetPack = Join-Path $targetBuild $packName
$targetSha1 = (Get-FileHash -LiteralPath $targetPack -Algorithm SHA1).Hash.ToLowerInvariant()
$targetSha256 = (Get-FileHash -LiteralPath $targetPack -Algorithm SHA256).Hash.ToLowerInvariant()
if ($targetSha1 -ne $actualSha1 -or $targetSha256 -ne $actualSha256) {
    throw 'Copied End Rift resource pack failed hash verification.'
}

Write-Host "Installed End Rift resource pack to $targetBuild"
Write-Host "SHA1=$targetSha1"
Write-Host "SHA256=$targetSha256"
