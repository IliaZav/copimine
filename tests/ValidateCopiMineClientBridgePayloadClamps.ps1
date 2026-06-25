. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$bridge = Read-Utf8 (Join-Path $root 'copimine-narcotics\src\me\copimine\clientbridge\ClientBridgePayloads.java')

Require-Contains $bridge 'Math.max(1, Math.min(600, seconds))' 'Client bridge visual_start must clamp seconds on the server side.'
Require-Contains $bridge 'Math.max(0.0F, Math.min(1.0F, intensity))' 'Client bridge visual_start must clamp intensity on the server side.'
Require-Contains $bridge 'effectId == null ? "CHAOS"' 'Client bridge must null-guard effect ids.'

Throw-IfErrors 'ValidateCopiMineClientBridgePayloadClamps'
