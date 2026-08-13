$ErrorActionPreference = 'Stop'

$root = Join-Path $PSScriptRoot '..'
$bridge = Get-Content -LiteralPath (Join-Path $root 'copimine-narcotics\src\me\copimine\clientbridge\CopiMineClientBridge.java') -Raw -Encoding UTF8
$payloads = Get-Content -LiteralPath (Join-Path $root 'copimine-narcotics\src\me\copimine\clientbridge\ClientBridgePayloads.java') -Raw -Encoding UTF8
$visuals = Get-Content -LiteralPath (Join-Path $root 'copimine-narcotics\src\me\copimine\clientbridge\ClientVisualEffectService.java') -Raw -Encoding UTF8

if ($bridge -notmatch 'MAX_INBOUND_MESSAGE_BYTES' -or $bridge -notmatch 'allowInboundMessage\(' -or $bridge -notmatch 'message\.length > MAX_INBOUND_MESSAGE_BYTES') {
    throw 'Client bridge input must have both a byte-size ceiling and a per-player rate gate before decoding.'
}

$join = [regex]::Match($bridge, '(?s)public void onJoin\(PlayerJoinEvent event\) \{.*?(?=\r?\n\s*@EventHandler\r?\n\s*public void onQuit)')
if (-not $join.Success -or $join.Value -match 'kickPlayer\(' -or $join.Value -match 'enforceClientModpack\(') {
    throw 'Client bridge must not enforce a client modpack during join.'
}
if ($bridge -match 'missing-required-mods:' -or $bridge -match 'player\.kickPlayer\(' -or $bridge -match 'onAuthCommandBeforeModpack') {
    throw 'Client bridge must not contain a client-mod kick or authentication-command gate.'
}

if ($payloads -notmatch 'Float\.isFinite\(value\)') {
    throw 'Client visual intensity must reject NaN and infinite values.'
}

$forget = [regex]::Match($visuals, '(?s)public void forgetPlayer\(UUID playerUuid, String reason\) \{.*?(?=\r?\n\s*private void handleAck)')
if (-not $forget.Success -or $forget.Value -notmatch 'lastAckByPlayer\.remove' -or $forget.Value -notmatch 'lastFinishedByPlayer\.remove' -or $forget.Value -notmatch 'lastErrorByPlayer\.remove') {
    throw 'Client visual per-player diagnostics must be released when a player leaves.'
}

Write-Host 'Client bridge abuse-guard contract OK'
