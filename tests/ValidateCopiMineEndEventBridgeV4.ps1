$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$event = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-end-event\src\me\copimine\endevent\CopiMineEndEvent.java')
$sender = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-end-event\src\me\copimine\endevent\bridge\EndEventBridgeV4Sender.java')
$plugin = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-end-event\plugin.yml')
$buildPlugin = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'copimine-end-event\build-plugin.ps1')
$gradle = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'tools\minecraft-26.3\build.gradle')
$senderTests = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'tools\minecraft-26.3\server-bridge-tests\src\test\java\me\copimine\endevent\EndEventBridgeV4SenderTest.java')

function Require([bool]$condition, [string]$message) {
  if (-not $condition) { throw $message }
}

Require ($event -match 'endEventBridgeV4\.send\(') 'End Event application messages must use the v4 sender.'
Require ($event -notmatch 'sendPluginMessage\(this,\s*config\.bridgeChannel\(') 'End Event must not emit unsigned v3 mutations.'
Require ($event -match 'Objects\.equals\(clientBridgeSessions\.get\(player\.getUniqueId\(\)\),\s*session\)') 'Legacy v3 session reports must match the current hello session before changing visual capability telemetry.'
Require (($event -match '"hello"\.equals\(type\)') -and ($event -match '"capabilities_update"\.equals\(type\)') -and ($event -match 'V3 is telemetry only; mutating requests require the signed V4 receiver\.')) 'The v3 listener must accept only capability reports.'
Require ($event -match 'endEventBridgeV4\.isReady\(player\.getUniqueId\(\)\)') 'Prisoner mutation handling must require the ready v4 sender.'
Require ($sender -match 'current\.connectionEpoch\(\)\.equals\(state\.connectionEpoch\)') 'Signed sends and requests must recheck the connection epoch.'
Require ($sender -match 'registry\.current\(state\.playerUuid\)') 'Signed sends and requests must bind to the current registry session.'
Require ($sender -match 'MessageDigest\.isEqual\(state\.lastRequestBytes,\s*exactRequest\)') 'Counted request replay detection must compare the exact request bytes.'
Require ($sender -match 'readyConfirmed') 'Protected sends must be gated on sender-ready confirmation.'
Require ($plugin -match '(?m)^softdepend:\s*$[\s\S]*?(?m)^\s*-\s*CopiMineNarcotics\s*$') 'End Event must soft-depend on CopiMineNarcotics.'
Require ($buildPlugin -match 'build\\minecraft-26\.3\\plugins\\jars\\CopiMineNarcotics-26\.3\.jar') 'Local plugin build must compile against the source-built 26.3 Narcotics shared APIs.'
$compileOnlyStart = $buildPlugin.IndexOf('if ($CompileOnly) {', [StringComparison]::Ordinal)
$serverCopyStart = $buildPlugin.IndexOf('Copy-Item -LiteralPath $jar -Destination $serverJar', [StringComparison]::Ordinal)
Require ($compileOnlyStart -ge 0 -and $serverCopyStart -gt $compileOnlyStart) 'CompileOnly must return before any server JAR copy.'
Require ($gradle -match 'endEventCompileOnly sourceSets\.narcotics\.output') 'Gradle End Event compile-only wiring is missing.'
Require ($senderTests -match 'wrongBindingGapAndMalformedPacketsDoNotConsumeRequestCounter') 'Focused malformed/gap/wrong-binding request tests are missing.'
Require ($senderTests -match 'pendingConfirmConsumesAbilityCounterButDoesNotApplyIt') 'Ready-confirm fail-closed coverage is missing.'

Write-Host 'END_EVENT_BRIDGE_V4_SOURCE_GUARD_PASS'
