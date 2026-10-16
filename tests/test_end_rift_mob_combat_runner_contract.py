from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
RUNNER = ROOT / "tests" / "RunEndRiftMobCombatLive.ps1"


def test_probe_reports_attempts_accepted_hits_and_positive_mob_damage_separately():
    source = RUNNER.read_text(encoding="utf-8")

    assert "$playerAttackAttempts =" in source
    assert "$playerMobDamageEvents =" in source
    assert "$acceptedPlayerMobHits =" in source
    assert "$positiveMobDamageDeltas =" in source
    assert "PLAYER_ATTACK" in source
    assert "WAVE_MOB_PLAYER_DAMAGE event=" in source
    assert "WAVE_MOB_PLAYER_DAMAGE_APPLIED" in source
    assert "accepted_before=true" in source
    assert "delta=([0-9]+(?:\\.[0-9]+)?)" in source
    assert "attacks_attempted=$playerAttackAttempts" in source
    assert "player_mob_hit_events=$playerMobDamageEvents" in source
    assert "player_mob_hits_accepted=$acceptedPlayerMobHits" in source
    assert "positive_mob_damage_deltas=$positiveMobDamageDeltas" in source
    assert "function ReadAppendedText" in source
    assert "$paper = ReadAppendedText $paperLog $paperLogOffset" in source
    assert source.index("$paperLogOffset = (Get-Item -LiteralPath $paperLog).Length") < source.index("Rcon 'cmend test wave 1'")

    assert "$attacks =" not in source
    assert "$playerDamageApplied =" not in source
    assert "player_damage_applied=" not in source


def test_probe_distinguishes_mob_hit_events_from_positive_player_health_damage():
    source = RUNNER.read_text(encoding="utf-8")

    assert "$incomingMobHitEvents =" in source
    assert "$acceptedIncomingMobHitEvents =" in source
    assert "$positiveIncomingMobDamageEvents =" in source
    assert "$positivePlayerHealthTransitions =" in source
    assert "WAVE_MOB_DAMAGE event=" in source
    assert "cancelled=false" in source
    assert "final=([0-9]+(?:\\.[0-9]+)?)" in source
    assert "PLAYER_HURT" in source
    assert r"before=([0-9]+(?:\.[0-9]+)?)\s+after=([0-9]+(?:\.[0-9]+)?)" in source
    assert "incoming_mob_hit_events=$incomingMobHitEvents" in source
    assert "incoming_mob_hits_accepted=$acceptedIncomingMobHitEvents" in source
    assert "positive_incoming_mob_damage_events=$positiveIncomingMobDamageEvents" in source
    assert "positive_player_health_transitions=$positivePlayerHealthTransitions" in source
    assert "player_hurt=" not in source
    assert "$hurt =" not in source


def test_probe_does_not_mask_incoming_damage_with_resistance():
    source = RUNNER.read_text(encoding="utf-8")

    assert 'Rcon "effect clear $BotName minecraft:resistance"' in source
    assert 'effect give $BotName minecraft:resistance' not in source
    assert "if ($acceptedPlayerMobHits -lt 1)" in source
    assert "if ($positiveMobDamageDeltas -lt 1)" in source
    assert "if ($acceptedIncomingMobHitEvents -lt 1)" in source
    assert "if ($positiveIncomingMobDamageEvents -lt 1)" in source
    assert "if ($positivePlayerHealthTransitions -lt 1)" in source
