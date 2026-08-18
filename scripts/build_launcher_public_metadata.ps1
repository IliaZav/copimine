[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string] $InstallerPath,
    [string] $MsiPath = "",
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

$header = New-Object byte[] 64
$stream = [System.IO.File]::OpenRead($installer)
try {
    $read = $stream.Read($header, 0, $header.Length)
} finally {
    $stream.Dispose()
}
if ($info.Length -lt 64 -or $read -lt 64 -or $header[0] -ne 0x4D -or $header[1] -ne 0x5A) {
    throw "Installer is not a Windows PE file (missing MZ header): $installer"
}

$sha256 = [System.Security.Cryptography.SHA256]::Create()
$stream = $null
try {
    $stream = [System.IO.File]::OpenRead($installer)
    $digest = $sha256.ComputeHash($stream)
} finally {
    if ($null -ne $stream) { $stream.Dispose() }
    $sha256.Dispose()
}
$hash = [BitConverter]::ToString($digest).Replace('-', '').ToLowerInvariant()
$msiInfo = $null
$msiHash = $null
$msiFilename = $null
$msiDownload = $null
if (-not [string]::IsNullOrWhiteSpace($MsiPath)) {
    $msi = (Resolve-Path -LiteralPath $MsiPath -ErrorAction Stop).Path
    $msiInfo = Get-Item -LiteralPath $msi -ErrorAction Stop
    if ($msiInfo.PSIsContainer -or $msiInfo.Extension -ine '.msi') {
        throw "MsiPath must point to an .msi file: $msi"
    }
    if ($msiInfo.Length -le 0) {
        throw "MSI is empty: $msi"
    }
    $msiFilename = $msiInfo.Name
    $msiDownload = "/downloads/launcher/$msiFilename"
    $msiDigest = [System.Security.Cryptography.SHA256]::Create()
    $msiStream = $null
    try {
        $msiStream = [System.IO.File]::OpenRead($msi)
        $msiHash = [BitConverter]::ToString($msiDigest.ComputeHash($msiStream)).Replace('-', '').ToLowerInvariant()
    } finally {
        if ($null -ne $msiStream) { $msiStream.Dispose() }
        $msiDigest.Dispose()
    }
}
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
if ($null -ne $msiDownload -and $msiDownload -notmatch '^/downloads/launcher/[A-Za-z0-9._-]+\.msi$') {
    throw "msiDownloadUrl must be a safe launcher-relative .msi URL: $msiDownload"
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
if ($null -ne $msiInfo) {
    $payload.msiFilename = $msiFilename
    $payload.msiDownloadUrl = $msiDownload
    $payload.msiSizeBytes = [long]$msiInfo.Length
    $payload.msiSha256 = $msiHash
    $payload.msiInstallLocation = 'choose'
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
if ($null -ne $msiInfo) {
    Write-Output "MSI_OUTPUT=$msi"
    Write-Output "MSI_SHA256=$msiHash"
    Write-Output "MSI_SIZE=$($msiInfo.Length)"
}
