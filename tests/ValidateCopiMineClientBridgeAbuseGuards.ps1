$ErrorActionPreference = 'Stop'

$root = Join-Path $PSScriptRoot '..'
$bridge = Get-Content -LiteralPath (Join-Path $root 'copimine-narcotics\src\me\copimine\clientbridge\CopiMineClientBridge.java') -Raw -Encoding UTF8
$payloads = Get-Content -LiteralPath (Join-Path $root 'copimine-narcotics\src\me\copimine\clientbridge\ClientBridgePayloads.java') -Raw -Encoding UTF8
$visuals = Get-Content -LiteralPath (Join-Path $root 'copimine-narcotics\src\me\copimine\clientbridge\ClientVisualEffectService.java') -Raw -Encoding UTF8

if ($bridge -notmatch 'MAX_INBOUND_MESSAGE_BYTES' -or $bridge -notmatch 'allowInboundMessage\(' -or $bridge -notmatch 'message\.length > MAX_INBOUND_MESSAGE_BYTES') {
    throw 'Client bridge input must have both a byte-size ceiling and a per-player rate gate before decoding.'
}

if ($bridge -notmatch 'copimine\.narcotics\.admin' -or $bridge -notmatch '"require"\.equals\(sub\)') {
    throw 'Changing the server-wide client requirement must require the full narcotics administrator permission.'
}

$join = [regex]::Match($bridge, '(?s)public void onJoin\(PlayerJoinEvent event\) \{.*?(?=\r?\n\s*@EventHandler\r?\n\s*public void onQuit)')
if (-not $join.Success -or $join.Value -match 'kickPlayer\(' -or $join.Value -notmatch 'cannot prove that a client mod is installed') {
    throw 'A plugin-channel HELLO must not be treated as proof that a client mod is installed for automatic kicking.'
}

if ($payloads -notmatch 'Float\.isFinite\(value\)') {
    throw 'Client visual intensity must reject NaN and infinite values.'
}

$forget = [regex]::Match($visuals, '(?s)public void forgetPlayer\(UUID playerUuid, String reason\) \{.*?(?=\r?\n\s*private void handleAck)')
if (-not $forget.Success -or $forget.Value -notmatch 'lastAckByPlayer\.remove' -or $forget.Value -notmatch 'lastFinishedByPlayer\.remove' -or $forget.Value -notmatch 'lastErrorByPlayer\.remove') {
    throw 'Client visual per-player diagnostics must be released when a player leaves.'
}

Write-Host 'Client bridge abuse-guard contract OK'
