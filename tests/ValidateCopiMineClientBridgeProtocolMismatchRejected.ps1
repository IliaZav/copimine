. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$bridge = Read-Utf8 (Join-Path $root 'copimine-narcotics\src\me\copimine\clientbridge\ClientBridgePayloads.java')

Require-Contains $bridge 'if (protocol != PROTOCOL_VERSION)' 'Client bridge must reject mismatched protocol versions inside decodeHello.'
Require-Contains $bridge 'Unsupported client bridge protocol' 'Client bridge must emit a clear protocol mismatch error.'
Require-Contains $bridge 'MAX_SUPPORTED_EFFECTS' 'Client bridge must bound the supported effect list size.'

Throw-IfErrors 'ValidateCopiMineClientBridgeProtocolMismatchRejected'
