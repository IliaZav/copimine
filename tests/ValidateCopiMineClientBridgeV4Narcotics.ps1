$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')

$bridgePath = Join-Path $root 'copimine-narcotics\src\me\copimine\clientbridge\CopiMineClientBridge.java'
$senderPath = Join-Path $root 'copimine-narcotics\src\me\copimine\clientbridge\NarcoticsBridgeV4Sender.java'
$visualsPath = Join-Path $root 'copimine-narcotics\src\me\copimine\clientbridge\ClientVisualEffectService.java'
$payloadsPath = Join-Path $root 'copimine-narcotics\src\me\copimine\clientbridge\ClientBridgePayloads.java'
$correlationPath = Join-Path $root 'copimine-narcotics\src\me\copimine\clientbridge\ClientVisualCommandCorrelation.java'

foreach ($path in @($bridgePath, $senderPath, $visualsPath, $payloadsPath, $correlationPath)) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "Missing v4 Narcotics bridge source: $path"
    }
}

$bridge = Get-Content -LiteralPath $bridgePath -Raw
$sender = Get-Content -LiteralPath $senderPath -Raw
$visuals = Get-Content -LiteralPath $visualsPath -Raw
$payloads = Get-Content -LiteralPath $payloadsPath -Raw
$correlation = Get-Content -LiteralPath $correlationPath -Raw

$v4Route = $bridge.IndexOf('ClientBridgePayloads.V4_CHANNEL.equalsIgnoreCase(channel)', [StringComparison]::Ordinal)
$v3Route = $bridge.IndexOf('ClientBridgePayloads.CHANNEL.equalsIgnoreCase(channel)', [StringComparison]::Ordinal)
if ($v4Route -lt 0 -or $v3Route -lt 0 -or $v4Route -ge $v3Route) {
    throw 'The bounded v4 request route must run before the legacy v3 channel gate.'
}
if ($bridge -notmatch 'MAX_V4_INBOUND_MESSAGE_BYTES\s*=\s*1_024') {
    throw 'The v4 inbound request path must retain its independent 1,024-byte cap.'
}
if ($bridge -notmatch 'boolean admitted\s*=\s*v4InboundRateLimiter\.allow\(player\.getUniqueId\(\)\)' -or
    $bridge -notmatch 'bridgeV4Sender\.onRequest\(player,\s*message,\s*admitted\)') {
    throw 'The v4 route must pass its per-player admission result to the Narcotics sender.'
}
if ($bridge -notmatch 'decodeStrictVisualReport\(message\)') {
    throw 'Legacy visual responses must use the strict, non-normalizing report parser.'
}
if ($sender -notmatch 'signer\.sign\(' -or $sender -notmatch 'registry\.current\(') {
    throw 'Narcotics sends and accepted requests must use the signer and current verified-session registry.'
}
if ($visuals -notmatch 'bridgeV4Sender\.isReady\(player\)' -or
    $visuals -match 'ClientBridgePayloads\.encodeVisual(Start|Stop)|ClientBridgePayloads\.encodeClearAll') {
    throw 'Visual sends must be readiness-gated and must not dispatch legacy v3 mutations.'
}
if ($bridge -notmatch '(?s)public void onDeath\(PlayerDeathEvent event\)\s*\{\s*visuals\.forgetPlayerPreservingSessionSequence\(event\.getEntity\(\)\);\s*visuals\.clearVisuals\(event\.getEntity\(\),\s*"death"\);') {
    throw 'Death cleanup must clear stale tracking before issuing the correlated, retryable visual clear.'
}
if ($payloads -notmatch 'decodeStrictVisualReport\(byte\[\] payload\)' -or
    $correlation -notmatch 'MAX_RECORDS_PER_RECIPIENT_SESSION\s*=\s*512') {
    throw 'Strict report parsing and the bounded per-session correlation store are required.'
}

Write-Host 'V4 Narcotics bridge source validation passed.'
