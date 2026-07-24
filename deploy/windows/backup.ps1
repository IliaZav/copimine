param([string]$ProjectRoot = "", [string]$ReleaseDir = "")
$ErrorActionPreference = "Stop"
if (-not $ProjectRoot) { $ProjectRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..") }
if (-not $ReleaseDir) { $ReleaseDir = Join-Path (Split-Path -Parent (Split-Path -Parent $ProjectRoot)) "release\backups" }
New-Item -ItemType Directory -Force -Path $ReleaseDir | Out-Null
$stamp = Get-Date -Format "yyyy-MM-dd-HHmmss"
$zip = Join-Path $ReleaseDir "copimine-backup-$stamp.zip"
if (Test-Path $zip) { Remove-Item $zip -Force }
$staging = Join-Path ([IO.Path]::GetTempPath()) ("copimine-backup-" + [guid]::NewGuid().ToString())
try {
    $payload = Join-Path $staging "copimine"
    New-Item -ItemType Directory -Force -Path $payload | Out-Null
    Get-ChildItem -LiteralPath $ProjectRoot -Force | Copy-Item -Destination $payload -Recurse -Force
    Compress-Archive -Path $payload -DestinationPath $zip -CompressionLevel Optimal
    (Get-FileHash -Algorithm SHA256 -LiteralPath $zip).Hash.ToLowerInvariant() | Set-Content -LiteralPath "$zip.sha256" -Encoding ascii
}
finally {
    if (Test-Path -LiteralPath $staging) { Remove-Item -LiteralPath $staging -Recurse -Force -ErrorAction SilentlyContinue }
}
Write-Host "Backup: $zip"
Write-Host "SHA256: $zip.sha256"
