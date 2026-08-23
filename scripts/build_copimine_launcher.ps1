[CmdletBinding()]
param(
    [ValidateSet('Debug', 'Release')]
    [string] $Configuration = 'Release',
    [string] $Version = '1.0.3',
    [string] $InstanceReleaseRoot = '',
    [string] $OfflineMinecraftRoot = '',
    [string] $WebView2StandalonePath = '',
    [switch] $RequireOfflineBundle,
    [switch] $ServerHostedRuntimeOnly,
    [switch] $SkipPackaging,
    [string] $SignToolPath = '',
    [string] $SigningCertificateThumbprint = '',
    [string] $TimestampUrl = 'https://timestamp.digicert.com',
    [switch] $RequireAuthenticodeSignature
)

$ErrorActionPreference = 'Stop'
$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Split-Path -Parent $scriptRoot
$project = Join-Path $repoRoot 'CopiMineLauncher/src/CopiMineLauncher.App/CopiMineLauncher.App.csproj'
$folderInstallerProject = Join-Path $repoRoot 'CopiMineLauncher/installer/CopiMineLauncher.Installer/CopiMineLauncher.Installer.csproj'
$installContract = Join-Path $repoRoot 'CopiMineLauncher/packaging/launcher-install-contract.json'
$installerAssetsScript = Join-Path $scriptRoot 'prepare_copimine_installer_assets.ps1'
$installerLogoSource = Join-Path $repoRoot 'CopiMineLauncher/src/CopiMineLauncher.App/Assets/LauncherVisuals/copimine-icon.png'
$installerBannerSource = Join-Path $repoRoot 'CopiMineLauncher/src/CopiMineLauncher.App/Assets/LauncherVisuals/installer-banner.png'
$installerWelcome = Join-Path $repoRoot 'CopiMineLauncher/packaging/installer-welcome.txt'
$installerReadme = Join-Path $repoRoot 'CopiMineLauncher/packaging/installer-readme.txt'
$installerConclusion = Join-Path $repoRoot 'CopiMineLauncher/packaging/installer-conclusion.txt'
$publishRoot = Join-Path $repoRoot "artifacts/launcher/$Configuration/publish"
$packageRoot = Join-Path $repoRoot "artifacts/launcher/$Configuration/packages"
$installerAssetsRoot = Join-Path $repoRoot "artifacts/launcher/$Configuration/installer-assets"
$signatureRequested = $RequireAuthenticodeSignature -or -not [string]::IsNullOrWhiteSpace($SigningCertificateThumbprint)

if (-not (Test-Path -LiteralPath $project -PathType Leaf)) {
    throw "Launcher project was not found: $project"
}
if (-not (Test-Path -LiteralPath $installContract -PathType Leaf)) {
    throw "Launcher install contract was not found: $installContract"
}
foreach ($requiredInstallerInput in @($installerAssetsScript, $installerLogoSource, $installerBannerSource, $installerWelcome, $installerReadme, $installerConclusion, $folderInstallerProject)) {
    if (-not (Test-Path -LiteralPath $requiredInstallerInput -PathType Leaf)) {
        throw "Launcher installer input was not found: $requiredInstallerInput"
    }
}

$installContractDocument = Get-Content -LiteralPath $installContract -Raw | ConvertFrom-Json
if ($installContractDocument.bindingRequired -ne $true -or
    $installContractDocument.flow -ne 'browser' -or
    $installContractDocument.manualCodeEntry -ne $false -or
    $installContractDocument.allowSkip -ne $false -or
    $installContractDocument.playBlockedUntilLinked -ne $true) {
    throw 'Launcher install contract must require browser binding before play, without manual code entry.'
}

if ($ServerHostedRuntimeOnly -and $RequireOfflineBundle) {
    throw 'ServerHostedRuntimeOnly and RequireOfflineBundle are mutually exclusive.'
}

if ($ServerHostedRuntimeOnly -and -not [string]::IsNullOrWhiteSpace($OfflineMinecraftRoot)) {
    throw 'ServerHostedRuntimeOnly does not accept OfflineMinecraftRoot.'
}

if ($RequireAuthenticodeSignature -and [string]::IsNullOrWhiteSpace($SigningCertificateThumbprint)) {
    throw 'RequireAuthenticodeSignature requires a certificate thumbprint from a trusted Windows certificate store.'
}

if ($signatureRequested) {
    $normalizedThumbprint = ($SigningCertificateThumbprint -replace '\s', '').Trim()
    if ($normalizedThumbprint -notmatch '^[0-9A-Fa-f]{40}$') {
        throw 'SigningCertificateThumbprint must be a 40-character SHA-1 certificate thumbprint.'
    }

    $timestampUri = $null
    $timestampIsValid = [Uri]::TryCreate($TimestampUrl, [UriKind]::Absolute, [ref]$timestampUri)
    if (-not $timestampIsValid -or $timestampUri.Scheme -notin @([Uri]::UriSchemeHttp, [Uri]::UriSchemeHttps) -or -not [string]::IsNullOrWhiteSpace($timestampUri.UserInfo)) {
        throw 'TimestampUrl must be an absolute HTTP(S) URL without embedded credentials.'
    }
}

function Resolve-AuthenticodeSignTool {
    if (-not [string]::IsNullOrWhiteSpace($SignToolPath)) {
        $resolved = (Resolve-Path -LiteralPath $SignToolPath -ErrorAction Stop).Path
        if (-not (Test-Path -LiteralPath $resolved -PathType Leaf)) {
            throw "SignToolPath is not a file: $resolved"
        }

        return $resolved
    }

    $fromPath = Get-Command signtool.exe -ErrorAction SilentlyContinue
    if ($null -ne $fromPath) {
        return $fromPath.Source
    }

    $windowsKitsRoots = @(
        (Join-Path ${env:ProgramFiles(x86)} 'Windows Kits\10\bin'),
        (Join-Path $env:ProgramFiles 'Windows Kits\10\bin')
    ) | Where-Object { -not [string]::IsNullOrWhiteSpace($_) -and (Test-Path -LiteralPath $_ -PathType Container) }
    $candidate = $windowsKitsRoots |
        ForEach-Object { Get-ChildItem -LiteralPath $_ -Filter 'signtool.exe' -File -Recurse -ErrorAction SilentlyContinue } |
        Where-Object { $_.Directory.Name -eq 'x64' } |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
    if ($null -eq $candidate) {
        throw 'signtool.exe was not found. Install the Windows SDK or pass -SignToolPath.'
    }

    return $candidate.FullName
}

function Sign-AuthenticodeArtifact([string] $Path) {
    $resolvedPath = (Resolve-Path -LiteralPath $Path -ErrorAction Stop).Path
    & $resolvedSignTool sign /sha1 $normalizedThumbprint /fd SHA256 /tr $TimestampUrl /td SHA256 $resolvedPath
    if ($LASTEXITCODE -ne 0) {
        throw "signtool sign failed for $resolvedPath with exit code $LASTEXITCODE"
    }

    $signature = Get-AuthenticodeSignature -LiteralPath $resolvedPath
    if ($signature.Status -ne 'Valid' -or $null -eq $signature.SignerCertificate) {
        throw "Authenticode verification failed for ${resolvedPath}: $($signature.Status)"
    }
}

$resolvedSignTool = if ($signatureRequested) { Resolve-AuthenticodeSignTool } else { $null }

if (-not $SkipPackaging -and [string]::IsNullOrWhiteSpace($WebView2StandalonePath)) {
    $defaultWebView2Candidates = @(
        (Join-Path $repoRoot "artifacts/launcher/$Configuration/webview2/MicrosoftEdgeWebView2RuntimeInstallerX64.exe"),
        (Join-Path $repoRoot 'artifacts/launcher/Release/webview2/MicrosoftEdgeWebView2RuntimeInstallerX64.exe')
    )
    $defaultWebView2 = $defaultWebView2Candidates |
        Where-Object { Test-Path -LiteralPath $_ -PathType Leaf } |
        Select-Object -First 1
    if ($null -eq $defaultWebView2) {
        throw 'WebView2StandalonePath is required for a self-contained installer. Provide the standalone MicrosoftEdgeWebView2RuntimeInstallerX64.exe path.'
    }
    $WebView2StandalonePath = $defaultWebView2
}

if (Test-Path -LiteralPath $publishRoot) {
    Remove-Item -LiteralPath $publishRoot -Recurse -Force
}
New-Item -ItemType Directory -Path $publishRoot -Force | Out-Null

dotnet publish $project `
    --configuration $Configuration `
    --runtime win-x64 `
    --self-contained true `
    --property:Version=$Version `
    --output $publishRoot
if ($LASTEXITCODE -ne 0) {
    throw "dotnet publish failed with exit code $LASTEXITCODE"
}

$publishedExe = Join-Path $publishRoot 'CopiMineLauncher.App.exe'
if (-not (Test-Path -LiteralPath $publishedExe -PathType Leaf)) {
    throw "Published Launcher executable was not produced: $publishedExe"
}

if ($signatureRequested) {
    Sign-AuthenticodeArtifact $publishedExe
}

$bootstrapSource = if ([string]::IsNullOrWhiteSpace($InstanceReleaseRoot)) {
    # instance-current is the promoted local release pointer. The older
    # instance directory is retained only as historical evidence and must
    # never silently become the bootstrap source for a new installer.
    Join-Path $repoRoot 'artifacts/launcher/Release/instance-current'
} else {
    (Resolve-Path -LiteralPath $InstanceReleaseRoot -ErrorAction Stop).Path
}
$bootstrapFiles = Join-Path $bootstrapSource 'files'
foreach ($required in @(
    (Join-Path $bootstrapSource 'instance-manifest.json'),
    (Join-Path $bootstrapSource 'instance-manifest.sig'),
    $bootstrapFiles
)) {
    if (-not (Test-Path -LiteralPath $required)) {
        throw "Launcher bootstrap artifact is missing: $required. Build/stage the signed instance release before packaging the installer."
    }
}

$bootstrapDestination = Join-Path $publishRoot 'launcher-bootstrap'
New-Item -ItemType Directory -Path (Join-Path $bootstrapDestination 'files') -Force | Out-Null
Copy-Item -LiteralPath (Join-Path $bootstrapSource 'instance-manifest.json') -Destination $bootstrapDestination -Force
Copy-Item -LiteralPath (Join-Path $bootstrapSource 'instance-manifest.sig') -Destination $bootstrapDestination -Force
Get-ChildItem -LiteralPath $bootstrapFiles -File | Copy-Item -Destination (Join-Path $bootstrapDestination 'files') -Force
Copy-Item -LiteralPath $installContract -Destination (Join-Path $publishRoot 'launcher-install-contract.json') -Force

if (-not $ServerHostedRuntimeOnly -and $RequireOfflineBundle -and [string]::IsNullOrWhiteSpace($OfflineMinecraftRoot)) {
    throw 'RequireOfflineBundle was specified but OfflineMinecraftRoot is empty.'
}
if (-not $ServerHostedRuntimeOnly -and -not [string]::IsNullOrWhiteSpace($OfflineMinecraftRoot)) {
    $offlineBaselineScript = Join-Path $scriptRoot 'prepare_launcher_offline_baseline.ps1'
    & $offlineBaselineScript -MinecraftRoot $OfflineMinecraftRoot -DestinationRoot $bootstrapDestination -MinecraftVersion '1.21.1' -FabricLoaderVersion '0.19.3'
    if ($LASTEXITCODE -ne 0) {
        throw "Offline Minecraft baseline preparation failed with exit code $LASTEXITCODE"
    }
}

if (-not $ServerHostedRuntimeOnly -and $RequireOfflineBundle -and [string]::IsNullOrWhiteSpace($WebView2StandalonePath)) {
    throw 'RequireOfflineBundle was specified but WebView2StandalonePath is empty.'
}
if (-not [string]::IsNullOrWhiteSpace($WebView2StandalonePath)) {
    $webView2Source = (Resolve-Path -LiteralPath $WebView2StandalonePath -ErrorAction Stop).Path
    if ((Get-Item -LiteralPath $webView2Source).Length -lt 100MB) {
        throw "WebView2 standalone runtime is unexpectedly small: $webView2Source"
    }
    $webView2Destination = Join-Path $publishRoot 'Assets/WebView2/MicrosoftEdgeWebView2RuntimeInstallerX64.exe'
    New-Item -ItemType Directory -Path (Split-Path -Parent $webView2Destination) -Force | Out-Null
    Copy-Item -LiteralPath $webView2Source -Destination $webView2Destination -Force
}

if (-not $ServerHostedRuntimeOnly -and $RequireOfflineBundle) {
    foreach ($requiredOfflineFile in @(
        (Join-Path $bootstrapDestination 'offline-minecraft-baseline.json'),
        (Join-Path $bootstrapDestination 'offline-minecraft-baseline.zip'),
        (Join-Path $publishRoot 'Assets/WebView2/MicrosoftEdgeWebView2RuntimeInstallerX64.exe')
    )) {
        if (-not (Test-Path -LiteralPath $requiredOfflineFile -PathType Leaf)) {
            throw "Offline bundle file is missing from publish output: $requiredOfflineFile"
        }
    }
}

if ($SkipPackaging) {
    Write-Output "PUBLISH_OUTPUT=$publishRoot"
    if (-not $ServerHostedRuntimeOnly -and -not [string]::IsNullOrWhiteSpace($OfflineMinecraftRoot)) {
        Write-Output "OFFLINE_BASELINE_OUTPUT=$(Join-Path $bootstrapDestination 'offline-minecraft-baseline.zip')"
    }
    if (-not [string]::IsNullOrWhiteSpace($WebView2StandalonePath)) {
        Write-Output "WEBVIEW2_STANDALONE_OUTPUT=$(Join-Path $publishRoot 'Assets/WebView2/MicrosoftEdgeWebView2RuntimeInstallerX64.exe')"
    }
    exit 0
}

if (Test-Path -LiteralPath $installerAssetsRoot) {
    Remove-Item -LiteralPath $installerAssetsRoot -Recurse -Force
}
New-Item -ItemType Directory -Path $installerAssetsRoot -Force | Out-Null
& $installerAssetsScript -SourceLogo $installerLogoSource -SourceBanner $installerBannerSource -DestinationRoot $installerAssetsRoot
if ($LASTEXITCODE -ne 0) {
    throw "Installer asset preparation failed with exit code $LASTEXITCODE"
}
$installerBanner = Join-Path $installerAssetsRoot 'installer-banner.bmp'
$installerLogo = Join-Path $installerAssetsRoot 'installer-logo.bmp'
foreach ($requiredInstallerAsset in @($installerBanner, $installerLogo)) {
    if (-not (Test-Path -LiteralPath $requiredInstallerAsset -PathType Leaf)) {
        throw "Installer asset is missing before packaging: $requiredInstallerAsset"
    }
}

$vpk = Get-Command vpk -ErrorAction SilentlyContinue
$vpkPath = if ($null -ne $vpk) {
    $vpk.Source
} else {
    $localVpk = Join-Path $repoRoot 'artifacts/tools/vpk/vpk.exe'
    if (Test-Path -LiteralPath $localVpk -PathType Leaf) { $localVpk } else { $null }
}
if ([string]::IsNullOrWhiteSpace($vpkPath)) {
    throw 'vpk 1.2.0 is required for packaging. Install the pinned Velopack CLI or place it at artifacts/tools/vpk/vpk.exe before running without -SkipPackaging.'
}

if (Test-Path -LiteralPath $packageRoot) {
    Remove-Item -LiteralPath $packageRoot -Recurse -Force
}
New-Item -ItemType Directory -Path $packageRoot -Force | Out-Null

# Cache, staging and extracted Java are transient local state.  The signed
# bootstrap archives under launcher-bootstrap/files are the distributable
# source of truth and remain in the package.
$packageExclude = if ($ServerHostedRuntimeOnly) {
    '(?i)(?:\.copimine[\\/](?:cache|staging|java)[\\/].*|(?:^|[\\/])Minecraft[\\/].*|(?:^|[\\/])launcher-bootstrap[\\/]files[\\/].*|(?:^|[\\/])CopiMineLauncher\.App\.exe\.WebView2[\\/].*)'
} else {
    '(?i)\.copimine[\\/]((cache|staging|java)[\\/]).*'
}
& $vpkPath pack `
    --packId CopiMineLauncher `
    --packTitle 'CopiMine Launcher' `
    --packAuthors 'CopiMine' `
    --packVersion $Version `
    --packDir $publishRoot `
    --mainExe 'CopiMineLauncher.App.exe' `
    --icon (Join-Path $repoRoot 'CopiMineLauncher/src/CopiMineLauncher.App/Assets/copimine.ico') `
    --exclude $packageExclude `
    --splashImage $installerLogo `
    --splashProgressColor '#63dfa0' `
    --msi `
    --instLocation Either `
    --msiBanner $installerBanner `
    --msiLogo $installerLogo `
    --instWelcome $installerWelcome `
    --instReadme $installerReadme `
    --instConclusion $installerConclusion `
    --shortcuts 'Desktop,StartMenuRoot' `
    --outputDir $packageRoot
if ($LASTEXITCODE -ne 0) {
    throw "vpk packaging failed with exit code $LASTEXITCODE"
}

$setupSource = Join-Path $packageRoot 'CopiMineLauncher-win-Setup.exe'
$installerPath = Join-Path $packageRoot "CopiMineLauncherSetup-$Version.exe"
$msiSource = Join-Path $packageRoot 'CopiMineLauncher-win.msi'
$msiPath = Join-Path $packageRoot "CopiMineLauncherSetup-$Version.msi"
if (-not (Test-Path -LiteralPath $setupSource -PathType Leaf)) {
    throw "Velopack did not produce the expected setup bundle: $setupSource"
}
if (-not (Test-Path -LiteralPath $msiSource -PathType Leaf)) {
    throw "Velopack did not produce the expected folder-selecting MSI: $msiSource"
}
Copy-Item -LiteralPath $setupSource -Destination $installerPath -Force
Copy-Item -LiteralPath $msiSource -Destination $msiPath -Force

$folderInstallerPublishRoot = Join-Path $publishRoot 'folder-installer'
if (Test-Path -LiteralPath $folderInstallerPublishRoot) {
    Remove-Item -LiteralPath $folderInstallerPublishRoot -Recurse -Force
}
New-Item -ItemType Directory -Path $folderInstallerPublishRoot -Force | Out-Null
$folderInstallerUrl = "https://copimine.ru/downloads/launcher/CopiMineLauncherSetup-$Version.msi"
& dotnet publish $folderInstallerProject `
    --configuration $Configuration `
    --runtime win-x64 `
    --self-contained true `
    --property:Version=$Version `
    --property:InstallerMsiUrl=$folderInstallerUrl `
    --output $folderInstallerPublishRoot
if ($LASTEXITCODE -ne 0) {
    throw "Folder-selecting Launcher installer publish failed with exit code $LASTEXITCODE"
}
$folderInstallerSource = Join-Path $folderInstallerPublishRoot 'CopiMineLauncherFolderSetup.exe'
$folderInstallerPath = Join-Path $packageRoot "CopiMineLauncherFolderSetup-$Version.exe"
if (-not (Test-Path -LiteralPath $folderInstallerSource -PathType Leaf)) {
    throw "Folder-selecting Launcher installer was not published: $folderInstallerSource"
}
Copy-Item -LiteralPath $folderInstallerSource -Destination $folderInstallerPath -Force

if ($signatureRequested) {
    foreach ($artifact in @($setupSource, $installerPath, $msiSource, $msiPath, $folderInstallerPath)) {
        Sign-AuthenticodeArtifact $artifact
    }
}

Write-Output "PUBLISH_OUTPUT=$publishRoot"
Write-Output "PACKAGE_OUTPUT=$packageRoot"
Write-Output "INSTALLER_OUTPUT=$installerPath"
Write-Output "MSI_OUTPUT=$msiPath"
Write-Output "CUSTOM_INSTALLER_OUTPUT=$folderInstallerPath"
if (-not [string]::IsNullOrWhiteSpace($OfflineMinecraftRoot)) {
    Write-Output "OFFLINE_BASELINE_OUTPUT=$(Join-Path $bootstrapDestination 'offline-minecraft-baseline.zip')"
}
if (-not [string]::IsNullOrWhiteSpace($WebView2StandalonePath)) {
    Write-Output "WEBVIEW2_STANDALONE_OUTPUT=$(Join-Path $publishRoot 'Assets/WebView2/MicrosoftEdgeWebView2RuntimeInstallerX64.exe')"
}
