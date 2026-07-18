param(
    [string]$SourceJar = "",
    [string]$ProjectRoot = ""
)

$ErrorActionPreference = "Stop"

if (-not $ProjectRoot) {
    $ProjectRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")
}

$targetDir = Join-Path $ProjectRoot "thirdparty\server-plugins"
New-Item -ItemType Directory -Force -Path $targetDir | Out-Null

if (-not $SourceJar) {
    $local = Join-Path $ProjectRoot "minecraft\server\plugins\CoreProtect-CE-23.0.jar"
    if (Test-Path -LiteralPath $local) {
        $SourceJar = $local
    } else {
        throw "Provide -SourceJar for the pinned CoreProtect jar or place it in minecraft/server/plugins/CoreProtect-CE-23.0.jar"
    }
}

if (-not (Test-Path -LiteralPath $SourceJar)) {
    throw "File not found: $SourceJar"
}
if ([IO.Path]::GetExtension($SourceJar) -ne ".jar") {
    throw "A CoreProtect .jar file is required"
}

$target = Join-Path $targetDir (Split-Path $SourceJar -Leaf)
Copy-Item -LiteralPath $SourceJar -Destination $target -Force
$expectedSha256 = '402075d0eca6c3748d67d5b580bc5faf78b1b5ba91446ac15ccc7c7225457a81'
$sha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $target).Hash.ToLowerInvariant()
if ($sha256 -ne $expectedSha256) {
    Remove-Item -LiteralPath $target -Force -ErrorAction SilentlyContinue
    throw 'CoreProtect SHA-256 does not match the pinned CopiMine release artifact.'
}

Write-Host "CoreProtect staged:"
Write-Host "  file: $target"
Write-Host "  sha256: $sha256"
Write-Host "Official source:"
Write-Host "  https://hangar.papermc.io/CORE/CoreProtect"
Write-Host "  https://github.com/PlayPro/CoreProtect"
