. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$index = Read-Utf8 $Paths.FrontendIndex
$bootstrap = Read-Utf8 (Join-Path $Paths.FrontendAssetsJs 'theme\theme-bootstrap.js')

Require-Contains $index '<meta name="color-scheme" content="light dark"' 'Frontend index must advertise both color schemes with light default.'
Require-Regex $index '<script src="/assets/js/theme/theme-bootstrap\.js"></script>\s*<link rel="stylesheet" href="/assets/style\.css"' 'Theme bootstrap must run before the stylesheet to avoid theme flash.'
Require-Contains $bootstrap 'const KEY = "copimine.theme";' 'Theme bootstrap must use the copimine.theme storage key.'
Require-Contains $bootstrap 'return "light";' 'Theme bootstrap must default to the light theme.'
Require-Contains $bootstrap 'root.dataset.theme = next;' 'Theme bootstrap must set the root theme dataset before render.'

Throw-IfErrors 'ValidateCopiMineWebsiteLightThemeDefault'
