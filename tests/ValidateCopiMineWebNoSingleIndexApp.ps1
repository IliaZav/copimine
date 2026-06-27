. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$index = Read-Utf8 $Paths.FrontendIndex
$signin = Read-Utf8 $Paths.FrontendSignin
$register = Read-Utf8 $Paths.FrontendRegister
$dashboard = Read-Utf8 $Paths.FrontendCabinetDashboard

Require-Contains $index '<main class="public-site' 'index.html must keep the public shell mount.'
Require-Contains $index '<script src="/assets/js/theme/theme-bootstrap.js"></script>' 'index.html must load the theme bootstrap script.'
Require-Contains $index '<script type="module" src="/assets/js/public/public-page.js"></script>' 'index.html must load the lightweight public entrypoint.'
Require-NotContains $index '<div id="app" class="app hidden">' 'index.html must not embed the authenticated app shell after the public split.'

Require-NotContains $signin '<div id="app" class="app hidden">' 'signin.html must stay a dedicated auth landing page without the app shell.'
Require-Contains $signin '<form id="loginForm"' 'signin.html must keep the sign-in form.'
Require-Contains $signin '<script type="module" src="/assets/app.js"></script>' 'signin.html must load the modular authenticated frontend entrypoint.'
Require-Contains $register '<form id="loginForm"' 'register.html must keep the registration form.'
Require-Contains $dashboard '<div id="app" class="app">' 'cabinet/dashboard.html must host the authenticated app shell.'
Require-NotContains $index 'fetch("/api/' 'index.html must not embed direct business logic fetches.'
Require-NotContains $index 'onclick=' 'index.html must not contain inline JS handlers.'
Require-NotContains $signin 'onclick=' 'signin.html must not contain inline JS handlers.'
Require-NotContains $register 'onclick=' 'register.html must not contain inline JS handlers.'

Throw-IfErrors 'ValidateCopiMineWebNoSingleIndexApp'
