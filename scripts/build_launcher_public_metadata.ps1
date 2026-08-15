[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string] $InstallerPath,
    [Parameter(Mandatory = $true)]
    [string] $Version,
    [Parameter(Mandatory = $true)]
    [string] $OutputPath,
    [string] $DownloadUrl = "",
    [string] $ReleaseNotesUrl = "",
    [string] $PublishedAtUtc = "",
    [string] $MinimumWindowsBuild = "19045"
)

$ErrorActionPreference = 'Stop'

if ($Version -notmatch '^[0-9]+\.[0-9]+\.[0-9]+(?:[-+][0-9A-Za-z.-]+)?$') {
    throw "Version is not a safe semantic version: $Version"
}

$installer = (Resolve-Path -LiteralPath $InstallerPath -ErrorAction Stop).Path
$info = Get-Item -LiteralPath $installer -ErrorAction Stop
if (-not $info.PSIsContainer -and $info.Extension -ieq '.exe') {
    # expected executable extension
} else {
    throw "InstallerPath must point to an .exe file: $installer"
}
if ($info.Length -le 0) {
    throw "Installer is empty: $installer"
}

$bytes = [System.IO.File]::ReadAllBytes($installer)
if ($bytes.Length -lt 64 -or $bytes[0] -ne 0x4D -or $bytes[1] -ne 0x5A) {
    throw "Installer is not a Windows PE file (missing MZ header): $installer"
}

$sha256 = [System.Security.Cryptography.SHA256]::Create()
try {
    $digest = $sha256.ComputeHash($bytes)
} finally {
    $sha256.Dispose()
}
$hash = [BitConverter]::ToString($digest).Replace('-', '').ToLowerInvariant()
$download = if ([string]::IsNullOrWhiteSpace($DownloadUrl)) {
    "/downloads/launcher/CopiMineLauncherSetup-$Version.exe"
} else {
    $DownloadUrl
}
$notes = if ([string]::IsNullOrWhiteSpace($ReleaseNotesUrl)) {
    "/news/copimine-launcher-1-0-0.html"
} else {
    $ReleaseNotesUrl
}
$published = if ([string]::IsNullOrWhiteSpace($PublishedAtUtc)) {
    [DateTimeOffset]::UtcNow.ToString('o')
} else {
    ([DateTimeOffset]::Parse($PublishedAtUtc, [Globalization.CultureInfo]::InvariantCulture, [Globalization.DateTimeStyles]::AssumeUniversal)).ToUniversalTime().ToString('o')
}

if ($download -notmatch '^/downloads/launcher/[A-Za-z0-9._-]+\.exe$') {
    throw "downloadUrl must be a safe launcher-relative .exe URL: $download"
}
if ($notes -notmatch '^/news/[A-Za-z0-9._-]+\.html$') {
    throw "releaseNotesUrl must be a safe news-relative .html URL: $notes"
}
if ($MinimumWindowsBuild -notmatch '^[0-9]+$') {
    throw "minimumWindowsBuild must be numeric: $MinimumWindowsBuild"
}

$payload = [ordered]@{
    schemaVersion = 1
    channel = 'stable'
    version = $Version
    minimumWindowsBuild = $MinimumWindowsBuild
    architecture = 'x64'
    filename = $info.Name
    downloadUrl = $download
    sizeBytes = [long]$info.Length
    sha256 = $hash
    releaseNotesUrl = $notes
    publishedAt = $published
}

$destination = [System.IO.Path]::GetFullPath($OutputPath)
$directory = [System.IO.Path]::GetDirectoryName($destination)
if ([string]::IsNullOrWhiteSpace($directory)) {
    throw "OutputPath has no parent directory: $OutputPath"
}
New-Item -ItemType Directory -Path $directory -Force | Out-Null
$temporary = "$destination.tmp"
$json = $payload | ConvertTo-Json -Depth 4
[System.IO.File]::WriteAllText($temporary, $json + [Environment]::NewLine, [System.Text.UTF8Encoding]::new($false))
Move-Item -LiteralPath $temporary -Destination $destination -Force

Write-Output "METADATA_OUTPUT=$destination"
Write-Output "INSTALLER_SHA256=$hash"
Write-Output "INSTALLER_SIZE=$($info.Length)"
