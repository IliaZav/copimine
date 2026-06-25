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
        throw "Укажи -SourceJar с официально скачанным CoreProtect jar или положи minecraft/server/plugins/CoreProtect-CE-23.0.jar"
    }
}

if (-not (Test-Path -LiteralPath $SourceJar)) {
    throw "Файл не найден: $SourceJar"
}
if ([IO.Path]::GetExtension($SourceJar) -ne ".jar") {
    throw "Нужен .jar файл CoreProtect"
}

$target = Join-Path $targetDir (Split-Path $SourceJar -Leaf)
Copy-Item -LiteralPath $SourceJar -Destination $target -Force
$sha1 = (Get-FileHash -Algorithm SHA1 -LiteralPath $target).Hash.ToLowerInvariant()

Write-Host "CoreProtect staged:"
Write-Host "  file: $target"
Write-Host "  sha1: $sha1"
Write-Host "Official source:"
Write-Host "  https://hangar.papermc.io/CORE/CoreProtect"
Write-Host "  https://github.com/PlayPro/CoreProtect"
