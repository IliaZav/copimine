$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot

$buildScripts = @(
    'copimine-economy-core\build-plugin.ps1',
    'copimine-election-core\build-plugin.ps1',
    'copimine-artifacts\build-plugin.ps1',
    'copimine-narcotics\build-plugin.ps1',
    'copimine-world-core\build-plugin.ps1',
    'copimine-admin-plugin\build-plugin.ps1',
    'minecraft\server\plugins\AuthEffects\build-plugin.ps1'
)

foreach ($relative in $buildScripts) {
    $path = Join-Path $root $relative
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "Missing first-party build script: $relative"
    }
    $text = Get-Content -LiteralPath $path -Raw -Encoding UTF8
    if ($text -notmatch 'jar\s+--create\s+--file\s+\S+\s+--date=2020-01-01T00:00:00Z') {
        throw "Build script does not pin JAR entry timestamps: $relative"
    }
}

$proof = Get-Content -LiteralPath (Join-Path $root 'scripts\verify_independent_build.ps1') -Raw -Encoding UTF8
foreach ($marker in @('Independent build pass 1/2', 'Independent build pass 2/2', 'Get-FileHash -Algorithm SHA256', 'Independent first-party build is not reproducible')) {
    if ($proof -notmatch [regex]::Escape($marker)) {
        throw "Independent-build proof is missing marker: $marker"
    }
}

Write-Host 'Reproducible first-party build guard validation passed.'
