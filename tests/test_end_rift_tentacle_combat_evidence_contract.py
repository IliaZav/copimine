import re
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
HARNESS = ROOT / "tests" / "RunEndRiftTentacleLive.ps1"
BOT = ROOT / "tests" / "LocalEndRiftMobCombatBot.js"
END_EVENT = ROOT / "copimine-end-event" / "src" / "me" / "copimine" / "endevent" / "CopiMineEndEvent.java"
ANIMATION_POLICY = ROOT / "copimine-end-event" / "src" / "me" / "copimine" / "endevent" / "domain" / "TentacleAnimationPolicy.java"


def test_tentacle_live_harness_measures_unmasked_player_damage_and_impulse():
    script = HARNESS.read_text(encoding="utf-8")
    bot = BOT.read_text(encoding="utf-8")

    assert "effect clear $BotName" in script
    assert "effect give $BotName minecraft:resistance" not in script
    assert "effect give $BotName minecraft:regeneration" not in script
    assert "END_RIFT_TENTACLE_TRACE" in script
    assert "PLAYER_HURT ' + [Regex]::Escape($BotName)" in script
    assert "PLAYER_VELOCITY ' + [Regex]::Escape($BotName)" in script
    assert "RIFT_TENTACLE_THROW_DAMAGE" in script
    assert "entity_velocity" in bot
    assert "PLAYER_HURT ${username}" in bot
    assert "PLAYER_VELOCITY ${username}" in bot
    assert "horizontal=" in bot
    assert "launch=(-?[0-9.]+),(-?[0-9.]+),(-?[0-9.]+) horizontal=([0-9.]+)" in script
    assert "target=' +" in script
    assert "[Math]::Abs([double]$candidateVelocity.Groups[1].Value - [double]$candidateThrow.Groups[1].Value)" in script
    assert "[Math]::Abs($candidateHurtAtMs - $candidateVelocityAtMs) -gt 500" in script


def test_tentacle_live_evidence_pins_the_loaded_plugin_and_source_hashes():
    script = HARNESS.read_text(encoding="utf-8")

    assert "CopiMineEndEvent.jar" in script
    assert "Get-FileHash" in script
    assert "CopiMineEndEvent.java" in script
    assert "TentacleThrowPolicy.java" in script
    assert "PLUGIN_SHA256" in script
    assert "SOURCE_SHA256" in script
    assert "$serverStarted = [datetime]$serverProcess.CreationDate" in script


def test_tentacle_log_cursor_is_taken_before_commands_that_can_spawn_it():
    script = HARNESS.read_text(encoding="utf-8")

    cleanup = script.index("Invoke-LocalRcon 'cmend boss kill cleanup'")
    cursor = script.index("$spawnOffset = Log-Length")
    spawn = script.index("Invoke-LocalRcon 'cmend boss spawn official confirm'")
    set_phase = script.index("Invoke-LocalRcon 'cmend boss phase last_seal'")

    assert cleanup < cursor < spawn < set_phase


def test_live_setup_checks_rcon_replies_without_non_ascii_literals():
    script = HARNESS.read_text(encoding="utf-8")

    assert "$spawnReply -notmatch '(?i)Rift Guardian'" in script
    assert "$phaseReply -notmatch '(?i)LAST_SEAL'" in script


def test_tentacle_probe_attacks_only_the_living_giant_hitbox():
    script = HARNESS.read_text(encoding="utf-8")
    bot = BOT.read_text(encoding="utf-8")

    assert "END_RIFT_TENTACLE_PROBE_NAMES" in script
    assert "END_RIFT_TENTACLE_PROBE_NAMES" in bot
    assert "function isTentacleHealthCarrier(entity)" in bot
    assert "name === 'giant' || displayName === 'giant'" in bot
    assert re.search(
        r"if \(tentacleProbeEnabled\)\s*\{\s*//[\s\S]*?return entities\s*\.filter\(isTentacleHealthCarrier\)",
        bot,
    )


def test_tentacle_probe_targets_stable_permanent_hitboxes_from_server_diagnostics():
    script = HARNESS.read_text(encoding="utf-8")
    bot = BOT.read_text(encoding="utf-8")

    assert "END_RIFT_TENTACLE_PROBE_TARGETS_FILE" in script
    assert "END_RIFT_TENTACLE_PROBE_TARGETS_FILE" in bot
    assert "targetIds.has(String(entity.uuid || '').toLowerCase())" in bot
    assert "$probeHitboxes = @($snapshot.tentacles" in script
    assert "Where-Object { -not $_.temporary -and" in script
    assert "temporary_excluded_for_stability=true" in script
    assert "hitboxUuid" in script
    assert "Value -eq [Guid]::Empty" in script or "-not [string]::IsNullOrWhiteSpace" in script


def test_tentacle_damage_probe_retries_a_bounded_number_of_attack_packets():
    bot = BOT.read_text(encoding="utf-8")
    script = HARNESS.read_text(encoding="utf-8")

    assert "END_RIFT_TENTACLE_PROBE_MAX_ATTACKS" in bot
    assert "tentacleProbeMaxAttacks" in bot
    assert "tentacleProbeAttackAttempts >= tentacleProbeMaxAttacks" in bot
    assert "tentacleProbeAttackAttempts += 1" in bot
    assert "END_RIFT_TENTACLE_PROBE_MAX_ATTACKS" in script
    assert "END_RIFT_TENTACLE_PROBE_MAX_ATTACKS = [Guid]::NewGuid()" not in script


def test_local_tentacle_hit_diagnostics_explain_rejected_player_packets():
    source = END_EVENT.read_text(encoding="utf-8")
    handler = source.split("public void onTentacleGuardianDamage(", 1)[1]
    handler = handler.split("/** Route bow hits", 1)[0]

    assert "RIFT_TENTACLE_DAMAGE_ATTEMPT" in handler
    assert "RIFT_TENTACLE_DAMAGE_IGNORED" in handler
    assert "config.environment()" in handler
    assert '"local".equalsIgnoreCase(config.environment())' in handler
    assert "attacker_active=" in handler
    assert "window_open=" in handler
    assert "tentacle_state=" in handler


def test_official_boss_probe_protects_and_restores_named_human_viewers():
    script = HARNESS.read_text(encoding="utf-8")

    assert "[string[]]$ProtectedViewerNames = @()" in script
    assert "$RestoreViewerGameMode = 'survival'" in script
    protect = script.index("gamemode $ProtectedViewerGameMode $viewer")
    spawn = script.index("Invoke-LocalRcon 'cmend boss spawn official confirm'")
    cleanup = script.rindex("Invoke-LocalRcon 'cmend boss kill cleanup'")
    restore = script.index("gamemode $RestoreViewerGameMode $viewer", cleanup)

    assert protect < spawn < cleanup < restore


def test_tentacle_probe_is_a_survival_participant_before_the_official_boss_spawns():
    script = HARNESS.read_text(encoding="utf-8")

    bot_start = script.index("$botProcess = Start-Bot")
    online = script.index("Wait-BotOnline", bot_start)
    survival = script.index('Invoke-LocalRcon ("gamemode survival $BotName")', online)
    health = script.index('Invoke-LocalRcon ("attribute $BotName minecraft:generic.max_health base set 1000")', survival)
    cursor = script.index("$spawnOffset = Log-Length", health)
    spawn = script.index("Invoke-LocalRcon 'cmend boss spawn official confirm'", cursor)

    assert bot_start < online < survival < health < cursor < spawn
    assert 'Invoke-LocalRcon ("gamemode creative $BotName")' not in script


def test_official_last_seal_keeps_scripted_boss_ai_disabled():
    source = END_EVENT.read_text(encoding="utf-8")
    ai_invariant = source.split("private void ensureEventCombatAi(Entity entity)", 1)[1]
    ai_invariant = ai_invariant.split("private boolean hasWaveCommander", 1)[0]

    last_seal = ai_invariant.index("BossFinalSealAnchorPolicy.shouldPin(bossPhase)")
    restore_ai = ai_invariant.index("mob.setAI(true)")

    assert last_seal < restore_ai
    assert "mob.setAI(false)" in ai_invariant[:restore_ai]


def test_tentacle_live_probe_skips_authme_commands_on_the_no_auth_local_server():
    script = HARNESS.read_text(encoding="utf-8")

    assert "END_RIFT_BOT_SKIP_REGISTER" in script
    assert "END_RIFT_BOT_SKIP_AUTH" in script
    assert "$env:END_RIFT_BOT_SKIP_REGISTER = '1'" in script
    assert "$env:END_RIFT_BOT_SKIP_AUTH = '1'" in script
    assert "END_RIFT_BOT_PASSWORD = [Guid]::NewGuid()" not in script


def test_live_tentacle_probe_requires_the_damaged_entity_to_continue_its_attack():
    script = HARNESS.read_text(encoding="utf-8")

    assert "LIVE_TENTACLE_HIT_ATTACK_CONTINUED_PASS" in script
    assert "$damagedTentacleUuid" in script
    assert "RIFT_TENTACLE_DAMAGE .*entity=" in script
    assert "$continuedAttackPattern = 'RIFT_TENTACLE_STATE .*entity=' +" in script
    assert "[Regex]::Escape($damagedTentacleUuid)" in script
    assert "state=(GRAB_SUCCESS|HOLD|THROW)" in script


def test_hit_to_attack_order_uses_entity_health_evidence_not_exact_line_text():
    script = HARNESS.read_text(encoding="utf-8")

    assert "$damageEventPattern = 'RIFT_TENTACLE_DAMAGE .*entity=' +" in script
    assert "[Regex]::Escape($damagedTentacleUuid)" in script
    assert "$damageEvent = [Regex]::Match($continuedAttackTail, $damageEventPattern)" in script
    assert "$candidate.Index -gt $damageEvent.Index" in script


def test_damaged_tentacle_followup_throw_uses_the_same_entity_and_player():
    script = HARNESS.read_text(encoding="utf-8")
    continuation = script.index('Record "LIVE_TENTACLE_HIT_ATTACK_CONTINUED_PASS')
    throw_pattern = script.index("$throwPattern = 'RIFT_TENTACLE_THROW")
    throw_damage_pattern = script.index("$throwDamagePattern = 'RIFT_TENTACLE_THROW_DAMAGE")
    hit_followup_record = script.index('Record "LIVE_TENTACLE_HIT_FOLLOWUP_THROW_PASS')

    assert continuation < throw_pattern < throw_damage_pattern < hit_followup_record
    assert "[Regex]::Escape($damagedTentacleUuid) + ' target=' + [Regex]::Escape($playerUuid)" in script[throw_pattern:throw_damage_pattern]
    assert "[Regex]::Escape($damagedTentacleUuid) + ' target=' + [Regex]::Escape($playerUuid)" in script[throw_damage_pattern:]
    assert "same_entity=true" in script[hit_followup_record:]
    assert "RIFT_TENTACLE_SPAWN .*temporary=true" not in script


def test_client_throw_correlation_checks_all_simultaneous_server_vectors():
    script = HARNESS.read_text(encoding="utf-8")

    assert "$serverThrowTail = Log-Tail $temporaryOffset" in script
    assert "$throwCandidates = [Regex]::Matches($serverThrowTail, $throwPattern)" in script
    assert "foreach ($candidateThrow in $throwCandidates)" in script
    assert "$candidateThrow.Index -le $damageEvent.Index" in script
    assert "$throwMatch = $candidateThrow" in script
    assert "$candidateVelocity.Groups[1].Value - [double]$candidateThrow.Groups[1].Value" in script
    assert "$candidateVelocity.Groups[3].Value - [double]$candidateThrow.Groups[3].Value" in script


def test_hit_tentacle_throw_correlation_is_bound_to_the_damaged_entity():
    script = HARNESS.read_text(encoding="utf-8")

    hit_uuid_binding = script.index("$damagedTentacleUuid = $damageMatch.Groups[1].Value")
    throw_pattern = script.index("$throwPattern = 'RIFT_TENTACLE_THROW", hit_uuid_binding)
    throw_damage_pattern = script.index("$throwDamagePattern = 'RIFT_TENTACLE_THROW_DAMAGE", throw_pattern)
    candidates = script.index("$throwCandidates = [Regex]::Matches", throw_pattern)

    assert hit_uuid_binding < throw_pattern < throw_damage_pattern < candidates
    assert "[Regex]::Escape($damagedTentacleUuid) + ' target=' + [Regex]::Escape($playerUuid)" in script[throw_pattern:candidates]
    assert "[Regex]::Escape($damagedTentacleUuid) + ' target=' + [Regex]::Escape($playerUuid)" in script[throw_damage_pattern:]
    assert "$temporarySpawnIndices" not in script


def test_live_throw_damage_is_bound_to_the_accepted_same_entity_throw():
    script = HARNESS.read_text(encoding="utf-8")

    assert "$throwDamageCandidates = [Regex]::Matches($serverThrowTail, $throwDamagePattern)" in script
    assert "$candidateThrowDamage.Index -le $damageEvent.Index" in script
    assert "$candidateThrowDamage.Index -ge $candidateThrow.Index" in script
    assert "[long]$candidateThrowDamageAtMs - [long]$candidateThrowAtMs" in script
    assert "$throwDamageMatch = $candidateThrowDamageMatch" in script


def test_live_harness_records_verified_pass_after_damage_continuation_and_throw():
    script = HARNESS.read_text(encoding="utf-8")

    velocity_pass = script.index('Record "LIVE_TENTACLE_PLAYER_VELOCITY_PASS')
    verified = script.index("Record 'LIVE_TENTACLE_PASS=VERIFIED'")
    success = script.index("$success = $true", verified)

    assert velocity_pass < verified < success


def test_live_harness_can_hold_verified_local_scene_for_f2_capture():
    script = HARNESS.read_text(encoding="utf-8")

    assert "[ValidateRange(0, 60)]" in script
    assert "[int]$VisualHoldSeconds = 0" in script
    hold_start = script.index('Record "LIVE_VISUAL_HOLD_START seconds=$VisualHoldSeconds"')
    hold_sleep = script.index("Start-Sleep -Seconds $VisualHoldSeconds", hold_start)
    hold_end = script.index('Record "LIVE_VISUAL_HOLD_END seconds=$VisualHoldSeconds"', hold_sleep)
    verified = script.index("Record 'LIVE_TENTACLE_PASS=VERIFIED'", hold_end)

    assert hold_start < hold_sleep < hold_end < verified


def test_live_player_health_delta_matches_the_selected_throw_transaction():
    script = HARNESS.read_text(encoding="utf-8")

    assert "[double]$candidateHurt.Groups[1].Value - [double]$candidateHurt.Groups[2].Value" in script
    assert "[double]$candidateThrowDamageMatch.Groups[2].Value" in script
    assert "candidateThrowDamageMatch.Groups[2].Value) -gt 0.05D" in script
    assert "Client health transition" not in script


def test_unsafe_tentacle_throw_rejection_precedes_release_damage():
    source = END_EVENT.read_text(encoding="utf-8")
    release = source.split("private void releaseTentaclePlayer(", 1)[1]
    release = release.split("private TentacleThrowPolicy.Launch safeTentacleThrowLaunch(", 1)[0]

    safe_launch = release.index("launch = safeTentacleThrowLaunch")
    rejected = release.index("RIFT_TENTACLE_THROW_REJECTED")
    damage = release.index("double healthBefore = target.getHealth()")

    assert safe_launch < rejected < damage


def test_tentacle_live_probe_restores_its_bot_environment():
    script = HARNESS.read_text(encoding="utf-8")

    assert "RiftProbe" in script
    assert "$previousBotEnvironment" in script
    assert "function Restore-BotEnvironment" in script
    finally_block = script.rsplit("finally {", 1)[1]
    assert "Restore-BotEnvironment" in finally_block
    for name in (
        "END_RIFT_BOT_SKIP_REGISTER",
        "END_RIFT_BOT_SKIP_AUTH",
        "END_RIFT_TENTACLE_PROBE_NAMES",
        "END_RIFT_TENTACLE_PROBE_MAX_ATTACKS",
        "END_RIFT_GUARDIAN_PROBE_NAMES",
        "END_RIFT_ATTACK_INTERVAL_MS",
        "END_RIFT_TENTACLE_TRACE",
    ):
        assert name in script


def test_live_probe_verifies_safe_viewer_mode_after_official_phase_start():
    script = HARNESS.read_text(encoding="utf-8")

    assert "[string]$ProtectedViewerGameMode = 'spectator'" in script
    assert "function Assert-ProtectedViewers" in script
    assert "data get entity $viewer playerGameType" in script
    assert "LIVE_VIEWER_PROTECTION_VERIFIED" in script
    phase_start = script.index("Invoke-LocalRcon 'cmend boss phase last_seal'")
    protection_check = script.index("Assert-ProtectedViewers 'last-seal'", phase_start)
    bot_attack_start = script.index("Set-Content -LiteralPath $botControlFile -Value 'ACTIVE'", phase_start)

    assert phase_start < protection_check < bot_attack_start


def test_nonlethal_hits_preserve_attacks_and_only_flinch_idle_tentacles():
    source = END_EVENT.read_text(encoding="utf-8")
    policy = ANIMATION_POLICY.read_text(encoding="utf-8")
    damage = source.split("private void applyTentacleGuardianDamage(", 1)[1]
    damage = damage.split("private void updateTentacleGuardianHealthVisual(", 1)[0]

    assert "TentacleAnimationPolicy.shouldEnterHitRecovery(state.state())" in damage
    assert "tentacleNextAttackTick.put" not in damage
    assert "TentacleGuardianPolicy.nextAttackTickAfterRecovery(" in source
    assert "canonical == State.READY || canonical == State.SHIELD_CHANNEL" in policy
