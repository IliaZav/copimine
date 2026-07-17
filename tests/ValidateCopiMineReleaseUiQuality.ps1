$ErrorActionPreference = 'Stop'

$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$releaseCssPath = Join-Path $root 'admin-web/frontend/assets/css/release-ui.css'
$themeJsPath = Join-Path $root 'admin-web/frontend/assets/js/theme/theme-toggle.js'
$cabinetPolishPath = Join-Path $root 'admin-web/frontend/assets/js/cabinet-polish.js'
$previewCssPath = Join-Path $root 'admin-web/frontend/assets/css/preview.css'
$previewNavPath = Join-Path $root 'admin-web/frontend/assets/js/preview-nav.js'
$previewAdminPath = Join-Path $root 'admin-web/frontend/preview-admin.html'
$previewPlayerPath = Join-Path $root 'admin-web/frontend/preview-player.html'

$releaseCss = Get-Content -Raw -Encoding UTF8 $releaseCssPath
$themeJs = Get-Content -Raw -Encoding UTF8 $themeJsPath
$cabinetPolish = Get-Content -Raw -Encoding UTF8 $cabinetPolishPath
$previewCss = Get-Content -Raw -Encoding UTF8 $previewCssPath
$previewNav = if (Test-Path $previewNavPath) { Get-Content -Raw -Encoding UTF8 $previewNavPath } else { '' }
$previewAdmin = Get-Content -Raw -Encoding UTF8 $previewAdminPath
$previewPlayer = Get-Content -Raw -Encoding UTF8 $previewPlayerPath

function Require-Contains([string]$text, [string]$needle, [string]$message) {
  if (-not $text.Contains($needle)) {
    throw $message
  }
}

function Reject-Regex([string]$text, [string]$pattern, [string]$message) {
  if ([regex]::IsMatch($text, $pattern, [System.Text.RegularExpressions.RegexOptions]::Singleline)) {
    throw $message
  }
}

Require-Contains $releaseCss ':root[data-theme="light"]' 'Release UI must define an explicit light theme.'
Require-Contains $releaseCss ':root[data-theme="dark"]' 'Release UI must define an explicit dark theme.'
Require-Contains $releaseCss ':focus-visible' 'Release UI must expose a keyboard-visible focus treatment.'
Require-Contains $releaseCss '@media (prefers-reduced-motion: reduce)' 'Release UI must disable nonessential motion when requested.'
Require-Contains $releaseCss '.ui-switch' 'Release UI must provide a semantic binary switch primitive.'
Require-Contains $releaseCss '.segmented' 'Release UI must provide a segmented mode control.'
Require-Contains $releaseCss 'input[type="range"]' 'Release UI must style bounded range controls.'
Require-Contains $releaseCss '.cabinet-nav-toggle' 'Release UI must expose a mobile cabinet navigation trigger.'
Require-Contains $releaseCss '.cabinet-nav-backdrop' 'Release UI must expose a mobile cabinet navigation backdrop.'
Require-Contains $releaseCss 'min-height: 44px' 'Interactive release controls must provide a 44px touch target.'

Reject-Regex $releaseCss 'body\s+\*,\s*body\s+\*::before,\s*body\s+\*::after\s*\{[^}]*background-image:\s*none\s*!important' 'Release UI must not globally suppress every background image.'
Reject-Regex $releaseCss 'body\s+\*,\s*body\s+\*::before,\s*body\s+\*::after\s*\{[^}]*box-shadow:\s*none\s*!important' 'Release UI must not globally suppress every box shadow.'
Reject-Regex $releaseCss '\*,\s*\*::before,\s*\*::after\s*\{[^}]*border-radius:\s*2px\s*!important' 'Release UI must not force one radius on every element.'

Require-Contains $themeJs 'role' 'Theme toggle runtime must expose switch semantics.'
Require-Contains $themeJs 'aria-checked' 'Theme toggle runtime must synchronize its checked state.'
Require-Contains $cabinetPolish 'cabinet-nav-toggle' 'Cabinet runtime must create or bind the mobile navigation trigger.'
Require-Contains $cabinetPolish 'cabinet-nav-backdrop' 'Cabinet runtime must create or bind the mobile navigation backdrop.'
Require-Contains $cabinetPolish 'aria-expanded' 'Cabinet runtime must synchronize mobile navigation state.'
Reject-Regex $previewCss '\.preview-shell,[\s\S]*background-image:\s*none\s*!important' 'Preview UI must not flatten every shipped preview surface.'
Require-Contains $previewCss '.preview-nav-toggle' 'Preview UI must provide a mobile navigation trigger.'
Require-Contains $previewCss '.preview-nav-backdrop' 'Preview UI must provide a mobile navigation backdrop.'
Require-Contains $previewCss '.preview-main { grid-template-columns: minmax(0, 1fr); }' 'Preview main grid must allow its only track to shrink to the mobile viewport.'
Require-Contains $previewNav 'aria-expanded' 'Preview navigation runtime must synchronize its expanded state.'
Require-Contains $previewNav 'Escape' 'Preview navigation runtime must close from the Escape key.'
Require-Contains $previewNav 'initThemeToggle' 'Preview runtime must initialize the shared theme switch.'
Require-Contains $previewAdmin 'data-theme-toggle="true"' 'Admin preview must expose the shared theme switch.'
Require-Contains $previewPlayer 'data-theme-toggle="true"' 'Player preview must expose the shared theme switch.'
Require-Contains $previewAdmin 'theme-bootstrap.js' 'Admin preview must apply the saved theme before rendering.'
Require-Contains $previewPlayer 'theme-bootstrap.js' 'Player preview must apply the saved theme before rendering.'

Write-Host 'ValidateCopiMineReleaseUiQuality passed.'
