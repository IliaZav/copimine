. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$admin = Read-Utf8 $Paths.Admin
$pluginYml = Read-Utf8 $Paths.AdminPluginYml

Require-Contains $admin 'openElectionCoreHub' 'AdminPlus must delegate elections to CopiMineElectionCore.'
Require-Contains $admin 'private boolean isLegacyElectionAction(String action)' 'AdminPlus must guard legacy election actions.'
Require-Contains $admin 'redirectLegacyElectionAction(p); return;' 'Legacy election actions must redirect into ElectionCore.'
Require-NotContains $admin 'runTaskTimer(this, this::tickSidebar' 'Legacy sidebar runtime task must not be started on enable.'
Require-NotContains $admin 'for(String commandName:List.of("cmultra","rpguard","cmsealdrop","cadm","ar","cmbank","appeal","report","oldvoteoff"))' 'Legacy election commands must not be registered.'
Require-NotContains $pluginYml 'oldvoteoff:' 'oldvoteoff command must be removed from plugin.yml.'
Require-NotContains $pluginYml 'cmsealdrop:' 'cmsealdrop command must be removed from plugin.yml.'

Throw-IfErrors 'ValidateCopiMineNoLegacyElectionRuntime'
