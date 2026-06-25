. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$admin = Read-Utf8 $Paths.Admin
$body = Method-Body $admin 'private void handle(Player p, ClickType click, String a, String menuId) throws Exception {'

Require-NotContains $body 'cmv7_' 'Admin runtime action handler must not execute legacy cmv7 election SQL directly.'
Require-NotContains $body 'cmv731_' 'Admin runtime action handler must not execute legacy cmv731 election SQL directly.'
Require-Contains $body 'if(isLegacyElectionAction(a)){redirectLegacyElectionAction(p); return;}' 'Admin runtime must redirect old election actions before SQL handlers.'

Throw-IfErrors 'ValidateCopiMineNoCmv7ElectionSqlInAdminRuntime'
