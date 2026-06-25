. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$state = Read-Utf8 (Join-Path $root 'copimine-narcotics\src\me\copimine\clientbridge\ClientCapabilityState.java')
$service = Read-Utf8 (Join-Path $root 'copimine-narcotics\src\me\copimine\clientbridge\ClientCapabilityService.java')
$bridge = Read-Utf8 (Join-Path $root 'copimine-narcotics\src\me\copimine\clientbridge\CopiMineClientBridge.java')

Require-Contains $state 'boolean expired(long ttlMillis)' 'Client bridge capability state must expose expiry logic.'
Require-Contains $service 'states.remove(player.getUniqueId());' 'Client bridge capability cache must evict stale states.'
Require-Contains $service 'setTtlMillis' 'Client bridge capability cache must support a TTL.'
Require-Contains $bridge 'applyCapabilityTtl()' 'Client bridge must configure capability TTL from runtime settings.'

Throw-IfErrors 'ValidateCopiMineClientBridgeCapabilityExpiry'
