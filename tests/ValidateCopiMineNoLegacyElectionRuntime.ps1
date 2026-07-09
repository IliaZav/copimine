. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$admin = Read-Utf8 $Paths.Admin
$pluginYml = Read-Utf8 $Paths.AdminPluginYml

Require-Contains $admin 'openElectionCoreHub' 'AdminPlus must delegate elections to CopiMineElectionCore.'
Require-Contains $admin 'private boolean isLegacyElectionAction(String action)' 'AdminPlus must guard legacy election actions.'
Require-Contains $admin 'redirectLegacyElectionAction(p); return;' 'Legacy election actions must redirect into ElectionCore.'
Require-NotContains $admin 'runTaskTimer(this, this::tickSidebar' 'Legacy sidebar runtime task must not be started on enable.'
Require-Contains $admin 'return handleOldVoteOff(sender,args);' 'oldvoteoff must stay as an explicit maintenance command, not a legacy election runtime.'
Require-Contains $admin 'return handleSealDropCommand(sender,args);' 'cmsealdrop must stay as an explicit seal cleanup command, not a legacy election runtime.'
Require-Contains $pluginYml 'oldvoteoff:' 'oldvoteoff maintenance command must be declared.'
Require-Contains $pluginYml 'cmsealdrop:' 'cmsealdrop cleanup command must be declared.'

Throw-IfErrors 'ValidateCopiMineNoLegacyElectionRuntime'
