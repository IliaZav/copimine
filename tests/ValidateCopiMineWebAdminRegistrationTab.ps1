. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$legacy = Read-Utf8 $Paths.FrontendLegacy
$routes = Read-Utf8 (Join-Path $root 'admin-web\frontend\assets\js\shared\app-routes.js')
$adminsHtml = Read-Utf8 (Join-Path $root 'admin-web\frontend\cabinet\admins.html')

$routeNeedle = @'
admins: "/cabinet/admins.html"
'@
$htmlNeedle = @'
data-app-route="admins"
'@
$loaderNeedle = @'
async function loadAdmins() {
'@
$redirectNeedle = @'
window.openAdminsTab = () => setTab("admins");
'@
$formNeedle = @'
id="newAdminUsername"
'@
$createActionNeedle = @'
data-click="createAdminUser()"
'@

Require-Contains $routes $routeNeedle.Trim() "Admin route table must include a dedicated admins page."
Require-Contains $adminsHtml $htmlNeedle.Trim() "admins.html must boot the cabinet in admins route mode."
Require-Contains $legacy $loaderNeedle.Trim() "Frontend must render a dedicated admins tab."
Require-Contains $legacy $redirectNeedle.Trim() "Security screen must be able to redirect into the admins tab."
Require-Contains $legacy $formNeedle.Trim() "Admins tab must include the admin-creation username field."
Require-Contains $legacy $createActionNeedle.Trim() "Admins tab must provide a dedicated creation action."

Throw-IfErrors "ValidateCopiMineWebAdminRegistrationTab"
