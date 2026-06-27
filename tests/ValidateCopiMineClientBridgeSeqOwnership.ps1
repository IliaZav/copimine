$root = Split-Path -Parent $PSScriptRoot
$servicePath = Join-Path $root 'copimine-narcotics\src\me\copimine\clientbridge\ClientVisualEffectService.java'
if (-not (Test-Path $servicePath)) {
  throw "Missing ClientVisualEffectService.java at $servicePath"
}

$service = Get-Content -Raw -Encoding UTF8 $servicePath
$errors = New-Object System.Collections.Generic.List[string]

foreach ($marker in @(
  'commandMatchesPlayerSession(Player player, ClientBridgePayloads.Message message, ClientVisualCommand command)',
  'command.sessionId().equals(message.sessionId())',
  'pendingAcks.get(message.seq())',
  'pendingAcks.remove(message.seq(), pending)',
  'popOwnedCommand(player, message)'
)) {
  if ($service -notmatch [regex]::Escape($marker)) {
    $errors.Add("Missing client-bridge ownership marker: $marker")
  }
}

if ($service -match 'handleAck\(Player player, ClientBridgePayloads\.Message message\)\s*\{\s*ClientVisualCommand pending = pendingAcks\.remove\(message\.seq\(\)\)') {
  $errors.Add('handleAck still removes seq before verifying player/session ownership.')
}
if ($service -match 'handleFinished\(Player player, ClientBridgePayloads\.Message message\)\s*\{\s*ClientVisualCommand command = runningCommands\.remove\(message\.seq\(\)\)') {
  $errors.Add('handleFinished still removes running command before verifying player/session ownership.')
}
if ($service -match 'handleError\(Player player, ClientBridgePayloads\.Message message\)\s*\{\s*ClientVisualCommand command = runningCommands\.remove\(message\.seq\(\)\)') {
  $errors.Add('handleError still removes running command before verifying player/session ownership.')
}

if ($errors.Count -gt 0) {
  throw ("ValidateCopiMineClientBridgeSeqOwnership failed:`n - " + ($errors -join "`n - "))
}

Write-Host 'ValidateCopiMineClientBridgeSeqOwnership passed.'
