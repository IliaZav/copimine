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
    assert "[Math]::Abs([double]$candidateVelocity.Groups[1].Value - [double]$throwMatch.Groups[2].Value)" in script
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


def test_tentacle_live_probe_uses_fresh_credentials_and_restores_environment():
    script = HARNESS.read_text(encoding="utf-8")

    assert "endrift-local" not in script.lower()
    assert "[Guid]::NewGuid().ToString('N')" in script
    assert "RiftProbe" in script
    assert "$previousBotEnvironment" in script
    assert "function Restore-BotEnvironment" in script
    finally_block = script.rsplit("finally {", 1)[1]
    assert "Restore-BotEnvironment" in finally_block
    for name in (
        "END_RIFT_BOT_PASSWORD",
        "END_RIFT_BOT_SKIP_REGISTER",
        "END_RIFT_GUARDIAN_PROBE_NAMES",
        "END_RIFT_ATTACK_INTERVAL_MS",
        "END_RIFT_TENTACLE_TRACE",
    ):
        assert name in script


def test_nonlethal_hits_preserve_attacks_and_only_flinch_idle_tentacles():
    source = END_EVENT.read_text(encoding="utf-8")
    policy = ANIMATION_POLICY.read_text(encoding="utf-8")
    damage = source.split("private void applyTentacleGuardianDamage(", 1)[1]
    damage = damage.split("private void updateTentacleGuardianHealthVisual(", 1)[0]

    assert "TentacleAnimationPolicy.shouldEnterHitRecovery(state.state())" in damage
    assert "tentacleNextAttackTick.put" not in damage
    assert "TentacleGuardianPolicy.nextAttackTickAfterRecovery(" in source
    assert "canonical == State.READY || canonical == State.SHIELD_CHANNEL" in policy
