$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot

$generatorPath = Join-Path $root 'deploy\shared\generate_sbom.py'
$scannerPath = Join-Path $root 'deploy\shared\scan_release_strings.py'
$packagePath = Join-Path $root 'scripts\package_full_release.ps1'
$validatorPath = Join-Path $root 'scripts\validate_release_bundle.ps1'

foreach ($path in @($generatorPath, $scannerPath, $packagePath, $validatorPath)) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "Release security tooling is missing: $path"
    }
}

$generator = Get-Content -LiteralPath $generatorPath -Raw -Encoding UTF8
foreach ($marker in @('SPDX-2.3', 'checksums.txt', 'requirements.txt', 'SHA256')) {
    if ($generator -notmatch [regex]::Escape($marker)) {
        throw "SBOM generator marker is missing: $marker"
    }
}

$scanner = Get-Content -LiteralPath $scannerPath -Raw -Encoding UTF8
foreach ($marker in @('PRIVATE KEY', 'visiblePin', 'GitHub token marker')) {
    if ($scanner -notmatch [regex]::Escape($marker)) {
        throw "Release binary scanner marker is missing: $marker"
    }
}

$package = Get-Content -LiteralPath $packagePath -Raw -Encoding UTF8
$validator = Get-Content -LiteralPath $validatorPath -Raw -Encoding UTF8
if ($package -notmatch [regex]::Escape('deploy\sbom.spdx.json') -or $validator -notmatch [regex]::Escape('sbom.spdx.json')) {
    throw 'Release pipeline is not wired to the SPDX SBOM.'
}
if ($package -notmatch [regex]::Escape('generate_sbom.py')) {
    throw 'Package script does not generate the SPDX SBOM.'
}
if ($package -notmatch [regex]::Escape('scan_release_strings.py') -or $validator -notmatch [regex]::Escape('scan_release_strings.py')) {
    throw 'Release pipeline is not wired to the binary scanner.'
}

$securityRequirements = Get-Content -LiteralPath (Join-Path $root 'requirements-security.txt') -Raw -Encoding UTF8
if ($securityRequirements -notmatch '(?m)^pip-audit==2\.10\.1\s*$') {
    throw 'pip-audit must stay pinned in requirements-security.txt.'
}

Write-Host 'SBOM, binary scan, and dependency-gate validation passed.'
