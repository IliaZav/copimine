[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string] $ArtifactRoot,
    [string] $MetadataPath = "",
    [string] $Version = "1.0.0"
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path -LiteralPath $ArtifactRoot -ErrorAction Stop).Path
$metadata = if ([string]::IsNullOrWhiteSpace($MetadataPath)) {
    Join-Path $root 'latest.json'
} else {
    (Resolve-Path -LiteralPath $MetadataPath -ErrorAction Stop).Path
}

$payload = Get-Content -Raw -LiteralPath $metadata | ConvertFrom-Json
if ($payload.schemaVersion -ne 1 -or $payload.channel -ne 'stable') { throw 'launcher metadata schema/channel is invalid' }
if ($payload.version -ne $Version) { throw "metadata version mismatch: $($payload.version) != $Version" }
if ($payload.architecture -ne 'x64') { throw 'metadata architecture is not x64' }
if ($payload.sha256 -notmatch '^[0-9a-f]{64}$') { throw 'metadata sha256 is not lowercase 64-hex' }
if ($payload.downloadUrl -notmatch '^/downloads/launcher/[A-Za-z0-9._-]+\.exe$') { throw 'metadata downloadUrl is unsafe' }
if ($payload.releaseNotesUrl -notmatch '^/news/[A-Za-z0-9._-]+\.html$') { throw 'metadata releaseNotesUrl is unsafe' }

$installerName = [System.IO.Path]::GetFileName([string]$payload.filename)
$installer = Join-Path $root $installerName
if (-not (Test-Path -LiteralPath $installer -PathType Leaf)) {
    $installer = Join-Path $root (Join-Path 'packages' $installerName)
}
if (-not (Test-Path -LiteralPath $installer -PathType Leaf)) { throw "installer is missing in artifact root or packages: $installerName" }
$bytes = [System.IO.File]::ReadAllBytes($installer)
if ($bytes.Length -lt 64 -or $bytes[0] -ne 0x4D -or $bytes[1] -ne 0x5A) { throw 'installer is not a PE file' }
$peOffset = [BitConverter]::ToInt32($bytes, 0x3C)
if ($peOffset -lt 64 -or $peOffset + 4 -gt $bytes.Length -or $bytes[$peOffset] -ne 0x50 -or $bytes[$peOffset + 1] -ne 0x45 -or $bytes[$peOffset + 2] -ne 0x00 -or $bytes[$peOffset + 3] -ne 0x00) { throw 'installer has no valid PE signature' }
$sha256 = [System.Security.Cryptography.SHA256]::Create()
try {
    $digest = $sha256.ComputeHash($bytes)
} finally {
    $sha256.Dispose()
}
$hash = [BitConverter]::ToString($digest).Replace('-', '').ToLowerInvariant()
if ($bytes.LongLength -ne [long]$payload.sizeBytes) { throw "installer size mismatch: $($bytes.LongLength) != $($payload.sizeBytes)" }
if ($hash -ne $payload.sha256) { throw "installer SHA-256 mismatch: $hash != $($payload.sha256)" }

Write-Output "RELEASE_VERIFY=PASS"
Write-Output "INSTALLER=$installer"
Write-Output "SHA256=$hash"
Write-Output "SIZE=$($bytes.LongLength)"
