$ErrorActionPreference = 'Stop'

$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Split-Path -Parent (Split-Path -Parent $scriptRoot)
$assetScript = Get-Content -LiteralPath (Join-Path $repoRoot 'scripts/prepare_copimine_installer_assets.ps1') -Raw
$buildScript = Get-Content -LiteralPath (Join-Path $repoRoot 'scripts/build_copimine_launcher.ps1') -Raw

function Assert-Contains([string] $text, [string] $expected, [string] $description) {
    if (-not $text.Contains($expected, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Packaging contract failed: $description. Missing: $expected"
    }
}

Assert-Contains $assetScript '[string] $SourceBanner' 'installer asset preparation accepts the supplied banner'
Assert-Contains $assetScript 'SourceBanner' 'installer asset preparation reads the supplied banner'
Assert-Contains $buildScript 'Assets/LauncherVisuals/installer-banner.png' 'release build passes the supplied banner'
Assert-Contains $buildScript '-SourceBanner' 'release build forwards the banner parameter'
Assert-Contains $buildScript '--msi' 'MSI remains enabled'
Assert-Contains $buildScript '--instLocation Either' 'installer keeps folder/disk selection'
Assert-Contains $buildScript '--msiBanner' 'MSI uses the branded banner'
Assert-Contains $buildScript '--msiLogo' 'MSI uses the branded logo'
Assert-Contains $buildScript "--shortcuts 'Desktop,StartMenuRoot'" 'desktop shortcut remains enabled'

Write-Output 'LAUNCHER_VISUAL_PACKAGING_CONTRACT=PASS'
