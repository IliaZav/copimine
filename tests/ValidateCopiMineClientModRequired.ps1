$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
$configPath = Join-Path $root 'copimine-narcotics\config.yml'
$config = Get-Content -LiteralPath $configPath -Raw -Encoding UTF8
$profile = Get-Content -LiteralPath (Join-Path $root 'tools\minecraft-26.3\profile.lock.json') -Raw -Encoding UTF8 | ConvertFrom-Json
$metadata = Get-Content -LiteralPath (Join-Path $root 'CopiMineClient\src\main\resources\fabric.mod.json') -Raw -Encoding UTF8 | ConvertFrom-Json
$legacyGradleProperties = Get-Content -LiteralPath (Join-Path $root 'CopiMineClient\gradle.properties') -Raw -Encoding UTF8
$serverBridge = Get-Content -LiteralPath (Join-Path $root 'copimine-narcotics\src\me\copimine\clientbridge\CopiMineClientBridge.java') -Raw -Encoding UTF8
$serverPayloads = Get-Content -LiteralPath (Join-Path $root 'copimine-narcotics\src\me\copimine\clientbridge\ClientBridgePayloads.java') -Raw -Encoding UTF8
$clientProtocol = Get-Content -LiteralPath (Join-Path $root 'tools\minecraft-26.3\client\src\main\java\me\copimine\client\ClientBridgeProtocol.java') -Raw -Encoding UTF8
$clientEntrypoint = Get-Content -LiteralPath (Join-Path $root 'tools\minecraft-26.3\client\src\main\java\me\copimine\client\CopiMineClient.java') -Raw -Encoding UTF8
$clientBuild = Get-Content -LiteralPath (Join-Path $root 'tools\minecraft-26.3\client\build.gradle') -Raw -Encoding UTF8
$handshakePolicy = Get-Content -LiteralPath (Join-Path $root 'tools\minecraft-26.3\protocol\src\main\java\me\copimine\protocol\ClientModHandshakePolicy.java') -Raw -Encoding UTF8
$narcoticsCommands = Get-Content -LiteralPath (Join-Path $root 'copimine-narcotics\src\me\copimine\narcotics\CopiMineNarcotics.java') -Raw -Encoding UTF8

$artifactVersion = [IO.Path]::GetFileNameWithoutExtension($profile.clientArtifact.filename) -replace '^CopiMineClient-', ''
$clientBridgeSection = [regex]::Match(
  $config,
  '(?ms)^client_bridge:\r?\n(?<section>(?:[ \t]+[^\r\n]*\r?\n|[ \t]*\r?\n)*)'
)
if (-not $clientBridgeSection.Success -or
    $clientBridgeSection.Groups['section'].Value -notmatch '(?m)^  enabled: true[ \t]*\r?$') {
  throw 'The required client bridge must be enabled by default.'
}
if ($config -notmatch '(?m)^  required_client_mod_id:\s*copimineclient\s*$') { throw 'The server must require only CopiMineClient by mod ID.' }
if ($config -notmatch "(?m)^  required_client_mod_version: $([regex]::Escape($artifactVersion))\r?$") {
  throw "The configured required client version must exactly match profile.lock.json ($artifactVersion)."
}
if ($handshakePolicy -notmatch "DEFAULT_REQUIRED_VERSION = `"$([regex]::Escape($artifactVersion))`"") {
  throw "The server fallback client version must match profile.lock.json ($artifactVersion)."
}
if ([regex]::Matches($narcoticsCommands, '/cmclient require client(?! <true\|false>)').Count -lt 2 -or
    $narcoticsCommands -match '/cmclient require client <true\|false>' -or
    $narcoticsCommands -match '(?s)args\.length == 3 && "require"\.equalsIgnoreCase\(args\[0\]\).*?List\.of\("true", "false"\)') {
  throw 'The client requirement command help and tab completion must match `/cmclient require client` without a boolean argument.'
}
if ($metadata.id -ne 'copimineclient') { throw 'The custom mod ID must remain copimineclient.' }
$legacyMinecraftMatch = [regex]::Match($legacyGradleProperties, '(?m)^minecraft_version=(?<version>\S+)\r?$')
$legacyLoaderMatch = [regex]::Match($legacyGradleProperties, '(?m)^loader_version=(?<version>\S+)\r?$')
$legacyFabricApiMatch = [regex]::Match($legacyGradleProperties, '(?m)^fabric_api_version=(?<version>\S+)\r?$')
if (-not $legacyMinecraftMatch.Success -or -not $legacyLoaderMatch.Success -or -not $legacyFabricApiMatch.Success -or
    $metadata.depends.minecraft -ne $legacyMinecraftMatch.Groups['version'].Value -or
    $metadata.depends.fabricloader -ne ">=$($legacyLoaderMatch.Groups['version'].Value)" -or
    $metadata.depends.'fabric-api' -ne '*') {
  throw 'The root client metadata must stay aligned with its legacy 1.21.1 Gradle build.'
}
$fabricApiLock = Get-Content -LiteralPath (Join-Path $root 'tools\minecraft-26.3\client-mods.lock.json') -Raw -Encoding UTF8 | ConvertFrom-Json
$fabricApiModule = @($fabricApiLock.modules | Where-Object { $_.project -eq 'fabric-api' })
if ($fabricApiModule.Count -ne 1 -or $fabricApiModule[0].bundled -ne $true) {
  throw 'Fabric API must be pinned as a bundled CopiMineClient dependency, not a separate required mod.'
}
if ($clientEntrypoint -notmatch 'getModContainer\(MOD_ID\)' -or $clientEntrypoint -notmatch 'getMetadata\(\)\.getVersion\(\)\.getFriendlyString\(\)') {
  throw 'The handshake must report the loaded jar metadata version, not a separately hard-coded value.'
}
if ($clientBuild -notmatch 'parsed\.depends\.minecraft = profile\.minecraftVersion' -or
    $clientBuild -notmatch 'parsed\.description = .Required CopiMine client features and visuals; additional client mods are optional\.' -or
    $clientBuild -notmatch "parsed\.depends\['fabric-api'\] = `">=\$\{profile\.fabric\.api\}`"" -or
    $clientBuild -notmatch 'implementation\s+"net\.fabricmc\.fabric-api:fabric-api:\$\{profile\.fabric\.api\}"' -or
    $clientBuild -notmatch 'include\s+"net\.fabricmc\.fabric-api:fabric-api:\$\{profile\.fabric\.api\}"') {
  throw 'The packaged client must declare and nest the profile-locked Fabric API inside CopiMineClient.'
}
if ($serverPayloads -notmatch 'CLIENT_MOD_CHALLENGE' -or $serverPayloads -notmatch 'encodeClientModChallenge') {
  throw 'The server bridge must issue a fresh client-mod challenge.'
}
$helloHandlerMatch = [regex]::Match(
  $serverBridge,
  '(?s)private void handleClientModHello\(Player player, ClientBridgePayloads\.Message payload\) \{.*?(?=\r?\n\s*private boolean isVerifiedClientMessage)'
)
if (-not $helloHandlerMatch.Success) { throw 'The server client-mod handshake handler must remain explicit and testable.' }
function Assert-HandshakeHandlerEnforcesExactClientVersion([string]$handler) {
  if ($handler -notmatch 'ClientModHandshakePolicy\.acceptsResponse\(' -or
      $handler -notmatch 'payload\.sessionId\(\)' -or
      $handler -notmatch 'pending\.expiresAtMillis\(\)' -or
      $handler -notmatch 'payload\.mode\(\)' -or
      $handler -notmatch 'ClientModHandshakePolicy\.MOD_ID' -or
      $handler -notmatch 'payload\.clientVersion\(\)' -or
      $handler -notmatch 'configService\.requiredClientModVersion\(\)' -or
      $handler -notmatch 'payload\.protocol\(\)' -or
      $handler -notmatch 'ClientBridgePayloads\.PROTOCOL_VERSION' -or
      $handler -notmatch '(?s)if \(!accepted \|\| !pendingHandshakes\.remove\(playerId, pending\)\)\s*\{\s*rejectClient\(player, "missing-or-incompatible-client-mod"\);') {
    throw 'The server must validate the challenge, mod ID, exact version, and protocol in the handshake handler, then reject failed claims.'
  }
}
Assert-HandshakeHandlerEnforcesExactClientVersion $helloHandlerMatch.Value

# Mutation check: removing the policy call must make this contract fail, so a
# nearby identifier or a generic kick call cannot mask a weakened handshake.
$mutatedHelloHandler = [regex]::new('(?s)boolean accepted = ClientModHandshakePolicy\.acceptsResponse\(.*?\);').Replace(
  $helloHandlerMatch.Value,
  'boolean accepted = true;',
  1
)
if ($mutatedHelloHandler -eq $helloHandlerMatch.Value) { throw 'The handshake contract mutation check could not remove the policy call.' }
$mutationRejected = $false
try { Assert-HandshakeHandlerEnforcesExactClientVersion $mutatedHelloHandler } catch { $mutationRejected = $true }
if (-not $mutationRejected) { throw 'The handshake contract must fail if the server stops invoking the exact-version policy.' }
if ($serverBridge -notmatch 'Bukkit\.getOnlinePlayers\(\)' -or $serverBridge -notmatch 'beginClientModHandshake\(player\)') {
  throw 'The mandatory client check must also start for players already online when the plugin is enabled again.'
}
$hangingBreakHandler = [regex]::Match(
  $serverBridge,
  '(?s)@EventHandler\(priority = EventPriority\.HIGHEST, ignoreCancelled = true\)\s*public void onPendingHangingBreak\(HangingBreakByEntityEvent event\) \{.*?(?=\r?\n\s*@EventHandler|\r?\n\s*private )'
)
if ($serverBridge -notmatch 'import org\.bukkit\.event\.hanging\.HangingBreakByEntityEvent;' -or
    -not $hangingBreakHandler.Success -or
    $hangingBreakHandler.Value -notmatch 'event\.getRemover\(\)' -or
    $hangingBreakHandler.Value -notmatch 'isPendingClient\(player\)' -or
    $hangingBreakHandler.Value -notmatch 'projectile\.getShooter\(\) instanceof Player shooter' -or
    $hangingBreakHandler.Value -notmatch 'isPendingClient\(shooter\)' -or
    $hangingBreakHandler.Value -notmatch 'event\.setCancelled\(true\)') {
  throw 'Pending CopiMineClient handshakes must prevent direct and player-fired projectile breaks of paintings and item frames.'
}
$handlerRegistrationRemoved = [regex]::new(
  '(?s)@EventHandler\(priority = EventPriority\.HIGHEST, ignoreCancelled = true\)\s*(?=public void onPendingHangingBreak\()'
).Replace($serverBridge, '', 1)
if ([regex]::IsMatch(
    $handlerRegistrationRemoved,
    '(?s)@EventHandler\(priority = EventPriority\.HIGHEST, ignoreCancelled = true\)\s*public void onPendingHangingBreak\('
  )) {
  throw 'The hanging-break regression contract must fail if its @EventHandler registration is removed.'
}
if ($clientProtocol -notmatch 'TYPE_CLIENT_MOD_CHALLENGE' -or $clientProtocol -notmatch 'CopiMineClient\.CLIENT_VERSION') {
  throw 'The client must answer the server challenge using its packaged CopiMineClient version.'
}
if ($clientProtocol -notmatch 'resetHandshakeForChallenge\(') {
  throw 'The client must start a fresh handshake when a re-enabled server plugin issues a new challenge.'
}
$reload = [regex]::Match($serverBridge, '(?s)public void reload\(NarcoticsConfigService configService\) \{.*?(?=\r?\n\s*public ClientCapabilityService capabilities\()')
if (-not $reload.Success -or $reload.Value -notmatch 'lastRequiredClientModVersion' -or $reload.Value -notmatch 'beginClientModHandshake\(player\)') {
  throw 'Reloading the required CopiMineClient version must re-check already connected players.'
}

Write-Host "CopiMineClient mandatory exact-version contract passed ($artifactVersion)."
