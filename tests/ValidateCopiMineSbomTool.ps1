$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot

$generatorPath = Join-Path $root 'deploy\shared\generate_sbom.py'
$scannerPath = Join-Path $root 'deploy\shared\scan_release_strings.py'
$signerPath = Join-Path $root 'scripts\sign_release_manifest.ps1'
$allowedSignersPath = Join-Path $root 'deploy\release-signing.allowed'
$packagePath = Join-Path $root 'scripts\package_full_release.ps1'
$validatorPath = Join-Path $root 'scripts\validate_release_bundle.ps1'

foreach ($path in @($generatorPath, $scannerPath, $signerPath, $allowedSignersPath, $packagePath, $validatorPath)) {
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
if ($package -notmatch [regex]::Escape('release_manifest.sig') -or $validator -notmatch [regex]::Escape('ssh-keygen')) {
    throw 'Release pipeline is not wired to manifest signature verification.'
}
$signer = Get-Content -LiteralPath $signerPath -Raw -Encoding UTF8
foreach ($marker in @('ssh-keygen -Y sign', 'copimine-release', '$signaturePath')) {
    if ($signer -notmatch [regex]::Escape($marker)) {
        throw "Manifest signer marker is missing: $marker"
    }
}
$allowed = Get-Content -LiteralPath $allowedSignersPath -Raw -Encoding UTF8
if ($allowed -notmatch '(?m)^release\s+ssh-ed25519\s+[A-Za-z0-9+/]+={0,2}\s*$') {
    throw 'Release signing allowlist must contain one release Ed25519 key.'
}

$securityRequirements = Get-Content -LiteralPath (Join-Path $root 'requirements-security.txt') -Raw -Encoding UTF8
if ($securityRequirements -notmatch '(?m)^pip-audit==2\.10\.1\s*$') {
    throw 'pip-audit must stay pinned in requirements-security.txt.'
}

Write-Host 'SBOM, binary scan, and dependency-gate validation passed.'
