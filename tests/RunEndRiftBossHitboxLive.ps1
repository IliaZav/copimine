[CmdletBinding()]
param(
  [ValidatePattern('^[A-Za-z0-9_]{1,16}$')]
  [string]$BotName = 'EndRiftHitboxA',
  [ValidateRange(12, 180)]
  [int]$BotDurationSeconds = 18,
  [ValidateRange(15, 180)]
  [int]$TimeoutSeconds = 60,
  [string]$EvidencePath = ''
)

# Local/staging-only proof for the model-aligned boss rig.  The melee probe
# uses a real Mineflayer use_entity packet, the projectile probe uses a real
# server arrow, and the miss probe deliberately aims the same carrier outside
# the composite boxes.  This runner never targets production and never grants
# rewards.
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$runtimeRoot = (Resolve-Path (Join-Path $root 'local-runtime')).Path
$serverDir = (Resolve-Path (Join-Path $runtimeRoot 'end-rift-server')).Path
$rconScript = Join-Path $root 'tests\InvokeEndRiftLocalRcon.ps1'
$botScript = Join-Path $root 'tests\LocalEndRiftBossCombatBot.js'
$sourceConfig = Join-Path $root 'copimine-end-event\config.yml'
$installedConfig = Join-Path $serverDir 'plugins\CopiMineEndEvent\config.yml'
$propertiesPath = Join-Path $serverDir 'server.properties'
$paperLog = Join-Path $serverDir 'logs\latest.log'
if ([string]::IsNullOrWhiteSpace($EvidencePath)) {
  $EvidencePath = Join-Path $runtimeRoot 'boss-hitbox-live.log'
}
$processes = [System.Collections.Generic.List[object]]::new()

function Assert-UnderRuntime {
  param([Parameter(Mandatory = $true)][string]$Path)
  $full = [IO.Path]::GetFullPath($Path).TrimEnd('\') + '\'
  $allowed = [IO.Path]::GetFullPath($runtimeRoot).TrimEnd('\') + '\'
  if (-not $full.StartsWith($allowed, [StringComparison]::OrdinalIgnoreCase)) {
    throw "Boss hitbox evidence path is outside local-runtime: $Path"
  }
}

Assert-UnderRuntime $EvidencePath
foreach ($path in @($serverDir, $rconScript, $botScript, $sourceConfig,
    $installedConfig, $propertiesPath, $paperLog)) {
  if (-not (Test-Path -LiteralPath $path -PathType Leaf) -and $path -ne $serverDir) {
    throw "Boss hitbox live input is missing: $path"
  }
}
$sourceText = Get-Content -LiteralPath $sourceConfig -Raw
$installedText = Get-Content -LiteralPath $installedConfig -Raw
if ($sourceText -notmatch '(?m)^environment:\s*(local|staging)\s*$' -or
    $installedText -notmatch '(?m)^environment:\s*(local|staging)\s*$') {
  throw 'Boss hitbox live probe requires environment: local or staging.'
}
$properties = @{}
foreach ($line in Get-Content -LiteralPath $propertiesPath -Encoding UTF8) {
  if ($line -match '^([^=]+)=(.*)$') { $properties[$matches[1]] = $matches[2] }
}
if ($properties['server-port'] -ne '25566' -or $properties['rcon.port'] -ne '25576') {
  throw 'Boss hitbox live probe requires isolated local ports 25566/25576.'
}
$branch = (& git -C $root branch --show-current 2>$null).Trim()
if ($LASTEXITCODE -ne 0 -or $branch -ne 'codex/end-rift-event') {
  throw "Refused Git branch '$branch'."
}
$gitHead = (& git -C $root rev-parse HEAD 2>$null).Trim()

New-Item -ItemType Directory -Path (Split-Path -Parent $EvidencePath) -Force | Out-Null
Set-Content -LiteralPath $EvidencePath -Value @(
  "END_RIFT_BOSS_HITBOX_LIVE_START time=$((Get-Date).ToString('o')) branch=$branch gitHead=$gitHead"
  "environment=$($properties['server-port'])/$($properties['rcon.port'])"
) -Encoding UTF8

function Record {
  param([Parameter(Mandatory = $true)][string]$Text)
  Add-Content -LiteralPath $EvidencePath -Value $Text -Encoding UTF8
  Write-Output $Text
}

function Invoke-LocalRcon {
  param([Parameter(Mandatory = $true)][string]$CommandText)
  $result = & powershell -NoLogo -NoProfile -ExecutionPolicy Bypass -File $rconScript `
    -ServerDir $serverDir -RconPort 25576 -CommandText $CommandText | Out-String
  if ($LASTEXITCODE -ne 0) {
    throw "Local RCON failed: $CommandText`n$result"
  }
  return $result.Trim()
}

function Plain([string]$Text) {
  return ($Text -replace '\u00A7.', '')
}

function Read-Log {
  return [string](Get-Content -LiteralPath $paperLog -Raw)
}

function Log-Length {
  return [int64](Read-Log).Length
}

function Log-Tail([int64]$Offset) {
  $text = Read-Log
  if ($Offset -ge $text.Length) { return '' }
  return $text.Substring([int]$Offset)
}

function Wait-Log {
  param(
    [Parameter(Mandatory = $true)][string]$Pattern,
    [Parameter(Mandatory = $true)][int64]$AfterOffset,
    [int]$Seconds = $TimeoutSeconds
  )
  $deadline = (Get-Date).AddSeconds($Seconds)
  while ((Get-Date) -lt $deadline) {
    $tail = Log-Tail $AfterOffset
    if ($tail -match $Pattern) { return $tail }
    Start-Sleep -Milliseconds 250
  }
  throw "Timed out waiting for '$Pattern'."
}

function Get-BossUuid([string]$Status) {
  $plain = Plain $Status
  $match = [Regex]::Match($plain, 'boss=([0-9a-fA-F-]{36})\s+hp=')
  if (-not $match.Success) { throw "Live boss UUID is missing: $plain" }
  return $match.Groups[1].Value
}

function Get-BossHealth([string]$Status) {
  $plain = Plain $Status
  $match = [Regex]::Match($plain, 'hp=([0-9]+(?:\.[0-9]+)?)/([0-9]+(?:\.[0-9]+)?)')
  if (-not $match.Success) { throw "Live boss health is missing: $plain" }
  return [double]::Parse($match.Groups[1].Value,
      [Globalization.CultureInfo]::InvariantCulture)
}

function Get-BossPosition([string]$Uuid) {
  $raw = Plain (Invoke-LocalRcon "data get entity $Uuid Pos")
  $match = [Regex]::Match($raw,
      '\[\s*([-0-9.Ee+]+)d?,\s*([-0-9.Ee+]+)d?,\s*([-0-9.Ee+]+)d?\s*\]')
  if (-not $match.Success) { throw "Boss position is missing: $raw" }
  return @(
    [double]::Parse($match.Groups[1].Value, [Globalization.CultureInfo]::InvariantCulture),
    [double]::Parse($match.Groups[2].Value, [Globalization.CultureInfo]::InvariantCulture),
    [double]::Parse($match.Groups[3].Value, [Globalization.CultureInfo]::InvariantCulture)
  )
}

function Format-Coordinate([double]$Value) {
  return $Value.ToString('0.###', [Globalization.CultureInfo]::InvariantCulture)
}

function Wait-BotOnline([string]$PlayerName = $BotName) {
  for ($attempt = 0; $attempt -lt 120; $attempt++) {
    $list = Plain (Invoke-LocalRcon 'list')
    if ($list -match [Regex]::Escape($PlayerName)) { return }
    Start-Sleep -Milliseconds 500
  }
  throw "Combat bot did not join in time: $PlayerName"
}

function Start-Bot {
  param(
    [Parameter(Mandatory = $true)][string]$Username,
    [Parameter(Mandatory = $true)][double]$AimOffsetY,
    [Parameter(Mandatory = $true)][int]$AttackDelayMs,
    [Parameter(Mandatory = $true)][int]$AttackEveryMs
  )
  $node = (Get-Command node.exe -ErrorAction Stop).Source
  $stdoutPath = Join-Path $runtimeRoot ($Username + '-hitbox.stdout.log')
  $stderrPath = Join-Path $runtimeRoot ($Username + '-hitbox.stderr.log')
  $startInfo = [Diagnostics.ProcessStartInfo]::new()
  $startInfo.FileName = $node
  $startInfo.WorkingDirectory = $root
  $startInfo.UseShellExecute = $false
  $startInfo.CreateNoWindow = $true
  $startInfo.RedirectStandardOutput = $true
  $startInfo.RedirectStandardError = $true
  if ($null -ne $startInfo.ArgumentList) {
    $startInfo.ArgumentList.Add($botScript)
    $startInfo.ArgumentList.Add($Username)
    $startInfo.ArgumentList.Add(([string]($BotDurationSeconds * 1000)))
  } else {
    $startInfo.Arguments = '"' + $botScript + '" ' + $Username + ' ' +
      ([string]($BotDurationSeconds * 1000))
  }
  $startInfo.EnvironmentVariables['END_RIFT_BOSS_UUID'] = $script:bossUuid
  $startInfo.EnvironmentVariables['END_RIFT_BOSS_ATTACK_DELAY_MS'] = [string]$AttackDelayMs
  $startInfo.EnvironmentVariables['END_RIFT_BOSS_ATTACK_EVERY_MS'] = [string]$AttackEveryMs
  $startInfo.EnvironmentVariables['END_RIFT_BOSS_AIM_OFFSET_Y'] =
    $AimOffsetY.ToString('0.###', [Globalization.CultureInfo]::InvariantCulture)
  $process = [Diagnostics.Process]::new()
  $process.StartInfo = $startInfo
  $process.Start() | Out-Null
  $entry = [pscustomobject]@{
    Process = $process
    OutputTask = $process.StandardOutput.ReadToEndAsync()
    ErrorTask = $process.StandardError.ReadToEndAsync()
  }
  $processes.Add($entry)
  return $entry
}

function Finish-Bot {
  param([Parameter(Mandatory = $true)]$Entry)
  $milliseconds = [Math]::Max(5000, ($BotDurationSeconds + 8) * 1000)
  if (-not $Entry.Process.WaitForExit($milliseconds)) {
    try { $Entry.Process.Kill() } catch { }
    $Entry.Process.WaitForExit(5000)
    throw 'Boss hitbox bot timed out.'
  }
  $stdout = $Entry.OutputTask.GetAwaiter().GetResult()
  $stderr = $Entry.ErrorTask.GetAwaiter().GetResult()
  if ($stdout) { Add-Content -LiteralPath $EvidencePath -Value $stdout -Encoding UTF8 }
  if ($stderr) { Add-Content -LiteralPath $EvidencePath -Value $stderr -Encoding UTF8 }
  if ($Entry.Process.ExitCode -ne 0) {
    throw "Boss hitbox bot failed with exit code $($Entry.Process.ExitCode).`n$stdout`n$stderr"
  }
  return [pscustomobject]@{ Stdout = $stdout; Stderr = $stderr }
}

function Assert-CurrentHitboxStatus([string]$Status, [string]$BossUuid) {
  $plain = Plain $Status
  $match = [Regex]::Match($plain,
      'enabled=true\s+boss=' + [Regex]::Escape($BossUuid) +
      '\s+proxies=(\d+)\s+bounded=true\s+generation=(-?\d+)\s+parts=([^\s]+)\s+proxy_ids=([^\s]+)')
  if (-not $match.Success) { throw "Unexpected boss hitbox debug status: $plain" }
  $count = [int]$match.Groups[1].Value
  if ($count -lt 1 -or $count -gt 32) { throw "Unbounded/empty boss proxy count: $count" }
  $ids = @($match.Groups[4].Value -split ',' | Where-Object { $_ -match '^[0-9a-fA-F-]{36}$' })
  if ($ids.Count -ne $count) {
    throw "Debug status proxy id count does not match proxy count: ids=$($ids.Count) count=$count"
  }
  return [pscustomobject]@{
    Count = $count
    Generation = [long]$match.Groups[2].Value
    Parts = $match.Groups[3].Value
    Ids = $ids
  }
}

function Assert-ProxiesTagged($DebugStatus, [string]$BossUuid) {
  function Read-ProxyPdc([string]$ProxyId, [string]$Key) {
    # A broad BukkitValues query is abbreviated by Minecraft with "..." once
    # enough keys are present.  Read each namespaced value directly so the
    # live assertion cannot mistake output truncation for a missing tag.
    return Plain (Invoke-LocalRcon ("data get entity $ProxyId BukkitValues.copimineendevent:$Key"))
  }

  foreach ($proxyId in $DebugStatus.Ids) {
    $kind = Read-ProxyPdc $proxyId 'boss_hitbox_kind'
    $event = Read-ProxyPdc $proxyId 'boss_hitbox_event'
    $generation = Read-ProxyPdc $proxyId 'boss_hitbox_generation'
    $parent = Read-ProxyPdc $proxyId 'boss_hitbox_parent'
    $part = Read-ProxyPdc $proxyId 'boss_hitbox_part'
    if ($kind -notmatch '"BOSS_HITBOX"' -or
        $event -notmatch '"[0-9a-fA-F-]{36}"' -or
        $generation -notmatch ([Regex]::Escape([string]$DebugStatus.Generation) + 'L') -or
        $parent -notmatch [Regex]::Escape($BossUuid) -or
        $part -notmatch '"[A-Z_]+"') {
      throw "Proxy $proxyId is missing a direct model hitbox PDC value: kind=$kind event=$event generation=$generation parent=$parent part=$part"
    }
  }
}

function Accepted-Lines([int64]$Offset, [string]$BossUuid) {
  return @((Log-Tail $Offset) -split '\r?\n' |
    Where-Object { $_ -match ('BOSS_DAMAGE_ACCEPTED .*boss=' + [Regex]::Escape($BossUuid)) })
}

$script:bossUuid = $null
$script:missBotName = if ($BotName.Length -lt 16) {
  $BotName + 'M'
} else {
  $BotName.Substring(0, 15) + 'M'
}
$script:anchorBotName = if ($BotName.Length -lt 16) {
  $BotName + 'X'
} else {
  $BotName.Substring(0, 15) + 'X'
}
$anchorBot = $null
$success = $false
try {
  $null = Invoke-LocalRcon 'cmend wave clear'
  $null = Invoke-LocalRcon 'cmend boss kill cleanup'
  # Remove only local probe arrows left by an interrupted run.  The live
  # harness is isolated to local-runtime and never touches a production world.
  $null = Invoke-LocalRcon 'minecraft:kill @e[type=minecraft:arrow]'
  $null = Invoke-LocalRcon 'cmend boss spawn official confirm'
  $null = Invoke-LocalRcon 'cmend boss freeze'
  Start-Sleep -Seconds 2

  $status = Plain (Invoke-LocalRcon 'cmend status')
  $script:bossUuid = Get-BossUuid $status
  $before = Get-BossHealth $status
  $null = Invoke-LocalRcon 'cmend debug bosshitbox on'
  $debugStatus = Assert-CurrentHitboxStatus (Invoke-LocalRcon 'cmend debug bosshitbox status') $bossUuid
  Assert-ProxiesTagged $debugStatus $bossUuid
  Record ("LIVE_BOSS_HITBOX_PROFILE_PASS parts=$($debugStatus.Parts) proxies=$($debugStatus.Count) generation=$($debugStatus.Generation) tagged=true parent=$bossUuid bounded=true")

  # Mutation proof at the live boundary: remove one known Interaction proxy
  # from the running server, then use a real arrow to force the same runtime
  # reconciliation path that combat uses.  The old UUID must disappear, the
  # canonical count must return, and every recreated proxy must retain PDC.
  $removedProxyId = $debugStatus.Ids[0]
  $repairOffset = Log-Length
  # Namespace the vanilla command so Essentials does not reinterpret the
  # proxy UUID as a player name and report a false removal.
  $null = Invoke-LocalRcon ("minecraft:kill $removedProxyId")
  Start-Sleep -Milliseconds 250
  $null = Invoke-LocalRcon ("execute as $bossUuid at @s run summon arrow ~3 ~2.2 ~ {Motion:[-1.0d,0.0d,0.0d],NoGravity:1b,pickup:0b,damage:1.0d}")
  $repairLog = Wait-Log -AfterOffset $repairOffset `
    -Pattern ('BOSS_HITBOX_PROXY_RECREATED .*boss=' + [Regex]::Escape($bossUuid) + '.*generation=' + [Regex]::Escape([string]$debugStatus.Generation)) -Seconds 15
  $repairedStatus = Assert-CurrentHitboxStatus (Invoke-LocalRcon 'cmend debug bosshitbox status') $bossUuid
  Assert-ProxiesTagged $repairedStatus $bossUuid
  if ($repairedStatus.Ids -contains $removedProxyId) {
    throw "Removed boss proxy UUID was reused instead of recreated: $removedProxyId"
  }
  if (@($repairedStatus.Ids | Sort-Object -Unique).Count -ne $repairedStatus.Count) {
    throw "Boss hitbox self-heal produced duplicate proxy UUIDs: $($repairedStatus.Ids -join ',')"
  }
  Record "LIVE_BOSS_HITBOX_PROXY_REMOVAL_CONFIRMED removed=$removedProxyId replacement_count=$($repairedStatus.Count) old_uuid_absent=true"
  Record "LIVE_BOSS_HITBOX_SELF_HEAL_PASS recreated=true proxies=$($repairedStatus.Count) generation=$($repairedStatus.Generation) pdc=true"
  Record "LIVE_BOSS_HITBOX_NO_DUPLICATE_PASS unique_proxy_ids=$(@($repairedStatus.Ids | Sort-Object -Unique).Count) proxy_count=$($repairedStatus.Count)"

  $position = Get-BossPosition $bossUuid
  $botX = $position[0] + 1.8D
  $botY = $position[1]
  $botZ = $position[2]
  # Join first so AuthMe has a real player to authenticate before the RCON
  # setup commands change its mode, attributes, inventory and position.
  $meleeBot = Start-Bot -Username $BotName -AimOffsetY 1.2D -AttackDelayMs 3000 -AttackEveryMs 10000
  Wait-BotOnline $BotName
  Start-Sleep -Milliseconds 750
  $null = Invoke-LocalRcon ("gamemode survival $BotName")
  $null = Invoke-LocalRcon ("attribute $BotName minecraft:generic.max_health base set 1000")
  $null = Invoke-LocalRcon ("effect give $BotName minecraft:resistance 1000 4 true")
  $null = Invoke-LocalRcon ("effect give $BotName minecraft:regeneration 1000 4 true")
  $null = Invoke-LocalRcon ("give $BotName minecraft:diamond_sword")
  $null = Invoke-LocalRcon ("tp $BotName $(Format-Coordinate $botX) $(Format-Coordinate $botY) $(Format-Coordinate $botZ) 90 0")

  $meleeOffset = Log-Length
  $meleeBefore = Get-BossHealth (Plain (Invoke-LocalRcon 'cmend status'))
  $meleeOutput = Finish-Bot $meleeBot
  $meleeAttacks = @([Regex]::Matches($meleeOutput.Stdout, 'PLAYER_ATTACK\s+' + [Regex]::Escape($BotName)))
  $meleeAccepted = Accepted-Lines $meleeOffset $bossUuid
  $meleeAfter = Get-BossHealth (Plain (Invoke-LocalRcon 'cmend status'))
  if ($meleeAttacks.Count -lt 1 -or $meleeAccepted.Count -lt 1 -or
      $meleeAccepted.Count -gt $meleeAttacks.Count -or $meleeAfter -ge $meleeBefore) {
    throw "Melee hitbox probe failed: attacks=$($meleeAttacks.Count) accepted=$($meleeAccepted.Count) before=$meleeBefore after=$meleeAfter`n$($meleeOutput.Stdout)"
  }
  Record "LIVE_BOSS_HITBOX_MELEE_PASS attacks=$($meleeAttacks.Count) accepted=$($meleeAccepted.Count) before=$meleeBefore after=$meleeAfter single_authority=true"

  $missOffset = Log-Length
  $missBefore = Get-BossHealth (Plain (Invoke-LocalRcon 'cmend status'))
  $missBot = Start-Bot -Username $missBotName -AimOffsetY 20.0D -AttackDelayMs 3000 -AttackEveryMs 10000
  Wait-BotOnline $missBotName
  Start-Sleep -Milliseconds 750
  $null = Invoke-LocalRcon ("gamemode survival $missBotName")
  $null = Invoke-LocalRcon ("attribute $missBotName minecraft:generic.max_health base set 1000")
  $null = Invoke-LocalRcon ("effect give $missBotName minecraft:resistance 1000 4 true")
  $null = Invoke-LocalRcon ("effect give $missBotName minecraft:regeneration 1000 4 true")
  $null = Invoke-LocalRcon ("give $missBotName minecraft:diamond_sword")
  $null = Invoke-LocalRcon ("tp $missBotName $(Format-Coordinate $botX) $(Format-Coordinate $botY) $(Format-Coordinate $botZ) 90 0")
  $missOutput = Finish-Bot $missBot
  $missAttacks = @([Regex]::Matches($missOutput.Stdout, 'PLAYER_ATTACK\s+' + [Regex]::Escape($missBotName)))
  $missAccepted = Accepted-Lines $missOffset $bossUuid
  $missTail = Log-Tail $missOffset
  $missAfter = Get-BossHealth (Plain (Invoke-LocalRcon 'cmend status'))
  if ($missAttacks.Count -lt 1 -or $missAccepted.Count -ne 0 -or
      [Math]::Abs($missAfter - $missBefore) -gt 0.05D -or
      $missTail -notmatch 'BOSS_CARRIER_DAMAGE_BLOCKED.*outside-composite-hitbox') {
    throw "Empty-space miss probe failed: attacks=$($missAttacks.Count) accepted=$($missAccepted.Count) before=$missBefore after=$missAfter`n$($missTail)"
  }
  Record "LIVE_BOSS_HITBOX_MISS_PASS attacks=$($missAttacks.Count) accepted=0 before=$missBefore after=$missAfter carrier_ray_validated=true"

  # Use a real server Arrow at the model-derived pelvis envelope.  The test
  # boss is frozen and the command-owned arrow has no shooter, so the local
  # administrative probe is accepted by the scheduled sweep and exercises
  # the same server-side real-health transaction as a player projectile.
  # Arm the bot's bounded duration timer promptly.  It is switched to
  # spectator before the projectile is launched and aims twenty blocks above
  # the boss, so its single harmless packet cannot damage the probe target.
  $anchorBot = Start-Bot -Username $anchorBotName -AimOffsetY 20.0D -AttackDelayMs 1000 -AttackEveryMs 10000
  Wait-BotOnline $anchorBotName
  Start-Sleep -Milliseconds 750
  $null = Invoke-LocalRcon ("gamemode spectator $anchorBotName")
  $null = Invoke-LocalRcon ("tp $anchorBotName $(Format-Coordinate $botX) $(Format-Coordinate $botY) $(Format-Coordinate $botZ) 90 0")
  Start-Sleep -Milliseconds 750

  $projectileOffset = Log-Length
  $projectileBefore = Get-BossHealth (Plain (Invoke-LocalRcon 'cmend status'))
  $null = Invoke-LocalRcon ("execute as $bossUuid at @s run summon arrow ~0.2 ~2.3 ~-0.1 {NoGravity:1b,pickup:0b,damage:1.0d}")
  Wait-Log -AfterOffset $projectileOffset -Pattern ('BOSS_DAMAGE_ACCEPTED .*boss=' + [Regex]::Escape($bossUuid) + '.*source=.*:') -Seconds 15 | Out-Null
  $projectileAccepted = Accepted-Lines $projectileOffset $bossUuid
  $projectileAfter = Get-BossHealth (Plain (Invoke-LocalRcon 'cmend status'))
  if ($projectileAccepted.Count -ne 1 -or $projectileAfter -ge $projectileBefore) {
    throw "Projectile hitbox probe failed: accepted=$($projectileAccepted.Count) before=$projectileBefore after=$projectileAfter"
  }
  Record "LIVE_BOSS_HITBOX_PROJECTILE_PASS projectile_events=$($projectileAccepted.Count) before=$projectileBefore after=$projectileAfter uuid_deduped=true"

  $null = Invoke-LocalRcon 'cmend boss phase last_seal'
  Start-Sleep -Milliseconds 500
  $invulnerabilityOffset = Log-Length
  $invulnerabilityBefore = Get-BossHealth (Plain (Invoke-LocalRcon 'cmend status'))
  $null = Invoke-LocalRcon ("execute as $bossUuid at @s run summon arrow ~0.2 ~2.3 ~-0.1 {NoGravity:1b,pickup:0b,damage:1.0d}")
  Wait-Log -AfterOffset $invulnerabilityOffset -Pattern ('BOSS_DAMAGE_BLOCKED .*boss=' + [Regex]::Escape($bossUuid)) -Seconds 15 | Out-Null
  $invulnerabilityAfter = Get-BossHealth (Plain (Invoke-LocalRcon 'cmend status'))
  $invulnerabilityTail = Log-Tail $invulnerabilityOffset
  if ([Math]::Abs($invulnerabilityAfter - $invulnerabilityBefore) -gt 0.05D -or
      $invulnerabilityTail -notmatch 'BOSS_DAMAGE_BLOCKED') {
    throw "Invulnerability probe failed: before=$invulnerabilityBefore after=$invulnerabilityAfter`n$invulnerabilityTail"
  }
  Record "LIVE_BOSS_HITBOX_INVULNERABILITY_PASS before=$invulnerabilityBefore after=$invulnerabilityAfter phase=last_seal accepted=0"

  if ($anchorBot) {
    Finish-Bot $anchorBot | Out-Null
    $anchorBot = $null
  }

  $null = Invoke-LocalRcon 'cmend debug bosshitbox off'
  $null = Invoke-LocalRcon 'cmend boss kill cleanup'
  Start-Sleep -Milliseconds 500
  $cleanupStatus = Plain (Invoke-LocalRcon 'cmend debug bosshitbox status')
  if ($cleanupStatus -notmatch 'enabled=false' -or $cleanupStatus -notmatch 'proxies=0') {
    throw "Boss hitbox cleanup left debug proxies: $cleanupStatus"
  }
  Record 'LIVE_BOSS_HITBOX_CLEANUP_PASS proxies=0 boss=none first_cleanup=true'

  $null = Invoke-LocalRcon 'cmend boss kill cleanup'
  $idempotentStatus = Plain (Invoke-LocalRcon 'cmend debug bosshitbox status')
  if ($idempotentStatus -notmatch 'enabled=false' -or $idempotentStatus -notmatch 'proxies=0') {
    throw "Repeated boss hitbox cleanup changed the empty state: $idempotentStatus"
  }
  Record 'LIVE_BOSS_HITBOX_CLEANUP_IDEMPOTENT_PASS proxies=0 second_cleanup=true'
  $success = $true
}
finally {
  foreach ($entry in $processes) {
    if ($entry -and $entry.Process -and -not $entry.Process.HasExited) {
      try { $entry.Process.Kill() } catch { }
    }
  }
  foreach ($entry in $processes) {
    if ($entry -and $entry.Process) {
      try { $entry.Process.WaitForExit(5000) | Out-Null } catch { }
    }
  }
  try { $null = Invoke-LocalRcon 'cmend debug bosshitbox off' } catch { }
  try { $null = Invoke-LocalRcon 'kill @e[type=arrow]' } catch { }
  try { $null = Invoke-LocalRcon 'cmend boss unfreeze' } catch { }
  try { $null = Invoke-LocalRcon 'cmend boss kill cleanup' } catch { }
  if (-not $success) {
    try { Record 'LIVE_BOSS_HITBOX_PASS=NOT_VERIFIED' } catch { }
  }
}
