. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$index = Read-Utf8 $Paths.FrontendIndex
$bootstrap = Read-Utf8 (Join-Path $Paths.FrontendAssetsJs 'theme\theme-bootstrap.js')

Require-Regex $index '<script src="/assets/js/theme/theme-bootstrap\.js"></script>\s*<link rel="stylesheet" href="/assets/style\.css"' 'Theme bootstrap must run before CSS to avoid wrong-theme flash.'
Require-NotContains $index 'defer src="/assets/js/theme/theme-bootstrap.js"' 'Theme bootstrap must not be deferred.'
Require-Contains $bootstrap 'applyTheme(api.current, false);' 'Theme bootstrap must apply the theme immediately on load.'
Require-Contains $bootstrap 'root.style.colorScheme = next === "dark" ? "dark" : "light";' 'Theme bootstrap must align browser color-scheme before app render.'

Throw-IfErrors 'ValidateCopiMineWebsiteNoThemeFlash'
