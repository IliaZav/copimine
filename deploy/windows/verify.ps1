param([string]$ProjectRoot = "")
$ErrorActionPreference = "Stop"
if (-not $ProjectRoot) { $ProjectRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..") }
$checks = @(
    "admin-web\.env.example",
    "deploy\ubuntu\install.sh",
    "deploy\ubuntu\verify.sh",
    "resourcepacks\build-resourcepack.py",
    "thirdparty\modpack_manifest.json"
)
foreach ($relative in $checks) {
    $full = Join-Path $ProjectRoot $relative
    if (-not (Test-Path -LiteralPath $full)) { throw "Missing required file: $full" }
}
$validator = Join-Path $ProjectRoot "scripts\validate_release_bundle.ps1"
if (Test-Path -LiteralPath $validator) {
    & powershell -ExecutionPolicy Bypass -File $validator -ProjectRoot $ProjectRoot
    if ($LASTEXITCODE -ne 0) {
        throw "Release bundle validator failed."
    }
}
Write-Host "Windows verify passed."
