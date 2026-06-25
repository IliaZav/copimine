. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$artifacts = Read-Utf8 $Paths.Artifacts

Require-Contains $artifacts 'private void debugGui(String message)' 'Artifacts may keep debug logging behind a dedicated helper.'
Require-NotContains $artifacts 'sendMessage(color("&7debug' 'Artifacts must not show debug text to players.'
Require-NotContains $artifacts 'sendMessage(color("&7session' 'Artifacts must not leak GUI session debug text to players.'

Throw-IfErrors 'ValidateCopiMineArtifactsNoPlayerDebugText'
