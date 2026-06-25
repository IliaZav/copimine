. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$text = Read-Utf8 $Paths.Election

Require-Contains $text 'textLinkedIdKey' 'TextDisplay ownership must store a linked-id key.'
Require-Contains $text 'entity.getPersistentDataContainer().set(textLinkedIdKey, PersistentDataType.STRING, linkedId);' 'Spawned TextDisplay must carry the linked-id marker.'
Require-Contains $text 'SELECT entity_uuid FROM text_display_links WHERE linked_id=?' 'Cleanup must use DB-owned TextDisplay links.'
Require-Contains $text 'isManagedTextDisplay(display, expectedKind, linkedId, base)' 'Cleanup must verify CopiMine ownership before deleting nearby displays.'
Require-NotContains $text 'plain.contains(label)' 'Cleanup must not delete TextDisplay by visible text only.'

Throw-IfErrors 'ValidateCopiMineTextDisplayOwnership'
