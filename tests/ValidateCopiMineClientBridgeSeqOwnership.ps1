$root = Split-Path -Parent $PSScriptRoot
$servicePath = Join-Path $root 'copimine-narcotics\src\me\copimine\clientbridge\ClientVisualEffectService.java'
if (-not (Test-Path $servicePath)) {
  throw "Missing ClientVisualEffectService.java at $servicePath"
}

$service = Get-Content -Raw -Encoding UTF8 $servicePath
$errors = New-Object System.Collections.Generic.List[string]
$handler = [regex]::Match($service, '(?s)public synchronized void handleMessage\(Player player, ClientBridgePayloads\.Message message\)\s*\{(?<body>.*?)(?=\r?\n\s*\}\s*\r?\n\s*public synchronized void onBridgeStateChanged)')
if (-not $handler.Success) {
  throw 'Could not locate the authenticated visual-report handler.'
}
$handlerBody = $handler.Groups['body'].Value

foreach ($marker in @(
  'correlations.accept(',
  'player.getUniqueId(), sessionUuid, streamId, message, clock.getAsLong()',
  'CommandKey key = new CommandKey(player.getUniqueId(), sessionUuid, report.body().commandSeq())',
  'ClientVisualCommand command = pendingAcks.get(key)',
  'pendingAcks.remove(key, command)'
)) {
  if ($handlerBody -notmatch [regex]::Escape($marker)) {
    $errors.Add("Missing client-bridge ownership marker: $marker")
  }
}

if ($handlerBody -match '(?:pendingAcks|runningCommands)\.remove\(message\.seq\(\)') {
  $errors.Add('The visual report handler must not remove a command using an unscoped sequence number.')
}
if ($handlerBody.IndexOf('correlations.accept(', [StringComparison]::Ordinal) -lt 0 -or
    $handlerBody.IndexOf('correlations.accept(', [StringComparison]::Ordinal) -gt $handlerBody.IndexOf('pendingAcks.get(key)', [StringComparison]::Ordinal)) {
  $errors.Add('The stream/player/session correlation must be accepted before looking up a command.')
}

if ($errors.Count -gt 0) {
  throw ("ValidateCopiMineClientBridgeSeqOwnership failed:`n - " + ($errors -join "`n - "))
}

Write-Host 'ValidateCopiMineClientBridgeSeqOwnership passed.'
