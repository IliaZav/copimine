[CmdletBinding()]
param(
    [string]$OriginalJar = "",
    [string]$OutputJar = ""
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$patchRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
if ([string]::IsNullOrWhiteSpace($OriginalJar)) {
    $OriginalJar = Join-Path $patchRoot "..\..\minecraft\server\plugins\GrimAC.jar"
}
if ([string]::IsNullOrWhiteSpace($OutputJar)) {
    $OutputJar = Join-Path $patchRoot "build\GrimAC-2.3.74-40684fb-creative-fix.jar"
}

$expectedOriginalSha256 = "7F1DF9C02B52C1D2F69949A0C465781ED8C53DD0C52234FEEC01785E675FF5D1"
$sourceFile = Join-Path $patchRoot "src\ac\grim\grimac\utils\lists\CorrectingPlayerInventoryStorage.java"
$relativeClass = "ac/grim/grimac/utils/lists/CorrectingPlayerInventoryStorage.class"
$buildRoot = Join-Path $patchRoot "build\classes"
$compiledClass = Join-Path $buildRoot ($relativeClass -replace "/", "\\")

if (-not (Test-Path -LiteralPath $OriginalJar -PathType Leaf)) {
    throw "The live GrimAC JAR was not found: $OriginalJar"
}

$actualSha256 = (Get-FileHash -LiteralPath $OriginalJar -Algorithm SHA256).Hash.ToUpperInvariant()
if ($actualSha256 -ne $expectedOriginalSha256) {
    throw "Refusing to patch an unexpected GrimAC JAR. Expected $expectedOriginalSha256, got $actualSha256."
}

New-Item -ItemType Directory -Force -Path $buildRoot | Out-Null
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $OutputJar) | Out-Null

& javac --release 17 -encoding UTF-8 -cp $OriginalJar -d $buildRoot $sourceFile
if ($LASTEXITCODE -ne 0) {
    throw "javac failed with exit code $LASTEXITCODE"
}
if (-not (Test-Path -LiteralPath $compiledClass -PathType Leaf)) {
    throw "The expected compiled class was not produced: $compiledClass"
}

Copy-Item -LiteralPath $OriginalJar -Destination $OutputJar -Force
Push-Location $buildRoot
try {
    & jar uf $OutputJar $relativeClass
    if ($LASTEXITCODE -ne 0) {
        throw "jar update failed with exit code $LASTEXITCODE"
    }
} finally {
    Pop-Location
}

$patchedClass = & javap -classpath $OutputJar -c -p ac.grim.grimac.utils.lists.CorrectingPlayerInventoryStorage 2>&1
if ($LASTEXITCODE -ne 0 -or ($patchedClass -join "`n") -notmatch "GameMode.CREATIVE") {
    throw "The patched JAR does not contain the required Creative early return."
}

Write-Output "Patched GrimAC JAR: $OutputJar"
Write-Output "Original SHA-256: $actualSha256"
Write-Output "Patched SHA-256: $((Get-FileHash -LiteralPath $OutputJar -Algorithm SHA256).Hash.ToUpperInvariant())"
