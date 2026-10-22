$ErrorActionPreference = 'Stop'

$root = Join-Path $PSScriptRoot '..'
$bridge = Get-Content -LiteralPath (Join-Path $root 'copimine-narcotics\src\me\copimine\clientbridge\CopiMineClientBridge.java') -Raw -Encoding UTF8
$payloads = Get-Content -LiteralPath (Join-Path $root 'copimine-narcotics\src\me\copimine\clientbridge\ClientBridgePayloads.java') -Raw -Encoding UTF8
$visuals = Get-Content -LiteralPath (Join-Path $root 'copimine-narcotics\src\me\copimine\clientbridge\ClientVisualEffectService.java') -Raw -Encoding UTF8

if ($bridge -notmatch 'MAX_INBOUND_MESSAGE_BYTES' -or $bridge -notmatch 'allowInboundMessage\(' -or $bridge -notmatch 'message\.length > MAX_INBOUND_MESSAGE_BYTES') {
    throw 'Client bridge input must have both a byte-size ceiling and a per-player rate gate before decoding.'
}

if ($bridge -notmatch 'copimine\.narcotics\.admin' -or $bridge -notmatch '"require"\.equals\(sub\)') {
    throw 'Viewing the immutable server-wide client requirement must require the full narcotics administrator permission.'
}

foreach ($eventType in @(
    'PlayerItemConsumeEvent',
    'ProjectileLaunchEvent',
    'PlayerFishEvent',
    'PlayerShearEntityEvent',
    'PlayerArmorStandManipulateEvent',
    'PlayerTakeLecternBookEvent',
    'PlayerBucketEmptyEvent',
    'PlayerBucketFillEvent'
)) {
    if ($bridge -notmatch [regex]::Escape($eventType)) {
        throw "Pending clients must not be able to mutate gameplay through $eventType."
    }
}
if ($bridge -notmatch 'EntityDamageEvent' -or $bridge -notmatch 'isPendingClient\(victim\)') {
    throw 'Pending clients must be protected from damage while the handshake is in progress.'
}

$pendingMove = [regex]::Match($bridge, '(?s)public void onPendingMove\(PlayerMoveEvent event\) \{.*?(?=\r?\n\s*@EventHandler|\r?\n\s*private )')
if (-not $pendingMove.Success -or $pendingMove.Value -notmatch 'PlayerTeleportEvent' -or $pendingMove.Value -notmatch 'instanceof PlayerTeleportEvent') {
    throw 'Pending movement restrictions must allow server-issued teleports used by offline-mode authentication.'
}

$join = [regex]::Match($bridge, '(?s)public void onJoin\(PlayerJoinEvent event\) \{.*?(?=\r?\n\s*@EventHandler\r?\n\s*public void onQuit)')
if (-not $join.Success -or $join.Value -notmatch 'PendingClientHandshake' -or $join.Value -notmatch 'encodeClientModChallenge') {
    throw 'The join path must create a per-connection CopiMineClient challenge and timeout.'
}

if ($payloads -notmatch 'Float\.isFinite\(value\)') {
    throw 'Client visual intensity must reject NaN and infinite values.'
}

$forget = [regex]::Match($visuals, '(?s)private void forgetPlayer\(UUID playerUuid, boolean clearSessionSequence\)\s*\{(?<body>.*?)(?=\r?\n\s*\}\s*\r?\n\s*synchronized void tick)')
if (-not $forget.Success -or $forget.Groups['body'].Value -notmatch 'lastAckByPlayer\.remove' -or $forget.Groups['body'].Value -notmatch 'lastFinishedByPlayer\.remove' -or $forget.Groups['body'].Value -notmatch 'lastErrorByPlayer\.remove') {
    throw 'Client visual per-player diagnostics must be released when a player leaves.'
}

Write-Host 'Client bridge abuse-guard contract OK'
