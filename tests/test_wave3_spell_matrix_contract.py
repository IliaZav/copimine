from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
RUNNER = ROOT / "tests/RunEndRiftSpellMatrixLive.ps1"
POLICY = ROOT / "copimine-end-event/src/me/copimine/endevent/domain/EliteAbilityPolicy.java"
EVENT = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"


def test_wave3_spell_matrix_accepts_the_current_elite_ability_ids() -> None:
    runner = RUNNER.read_text(encoding="utf-8")
    policy = POLICY.read_text(encoding="utf-8")

    wave_three_profile = policy.split("case 3 -> switch (role)", 1)[1].split("case 4 ->", 1)[0]
    assert "Ability.ARROW_RAIN" in wave_three_profile
    assert "Ability.RIFT_BOLT" in wave_three_profile
    assert "Ability.WEB_SHOT" in wave_three_profile
    assert "$requiredWave3SpellIds = @('arrow_rain', 'rift_bolt', 'web_shot')" in runner


def test_wave3_spell_matrix_proves_cast_release_and_physical_impact() -> None:
    runner = RUNNER.read_text(encoding="utf-8")
    event = EVENT.read_text(encoding="utf-8")

    assert '"MINIBOSS_SPELL_TELEGRAPH.*spell=$spell"' in runner
    assert '"MINIBOSS_SPELL_CAST.*spell=$spell"' in runner
    assert "ELITE_ARROW_RAIN_RELEASE" in runner
    assert "ELITE_ARROW_RAIN_PLAYER_HIT" in runner
    assert '"ELITE_PROJECTILE_RELEASE.*ability=$ability.*player_only=true.*block_damage=false"' in runner
    assert '"ELITE_PROJECTILE_IMPACT.*ability=$ability.*target=[0-9a-f-]{36}.*block=false.*player_only=true"' in runner
    assert '"ELITE_PROJECTILE_IMPACT event="' in event
