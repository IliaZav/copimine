from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"


def _method(source: str, name: str, next_name: str) -> str:
    start = source.index(name)
    end = source.index(next_name, start + len(name))
    return source[start:end]


def test_scheduler_dispatches_all_core_roles_with_live_amplifier_overlay():
    source = SOURCE.read_text(encoding="utf-8")
    scheduler = _method(source, "private void castNextRitualAbility", "private RitualCasterTacticsPolicy.State")

    assert "RitualAmplifierPolicy.projectileCount" in scheduler
    assert "startRitualZone(target, now, channelingAmplifiers)" in scheduler
    assert "startRitualReverse(target, now, false, channelingAmplifiers)" in scheduler
    assert "startRitualControlSwap(now, channelingAmplifiers)" in scheduler
    assert "RitualAmplifierPolicy.majorCooldownMillis" in scheduler
    assert "RitualAmplifierPolicy.effectiveIntensity" in scheduler


def test_all_effect_duration_paths_use_the_same_bounded_overlay():
    source = SOURCE.read_text(encoding="utf-8")
    zone = _method(source, "private void startRitualZone", "private void tickRitualZones")
    reverse = _method(source, "private boolean startRitualReverse", "private void clearRitualZoneReverse")
    swap = _method(source, "private void startRitualControlSwap", "private void tickRitualControls")

    assert "RitualAmplifierPolicy.effectDurationMultiplier" in zone
    assert "RitualAmplifierPolicy.effectDurationMultiplier" in reverse
    assert "RitualAmplifierPolicy.effectDurationMultiplier" in swap


def test_ritual_projectile_damage_keeps_launch_time_amplifier_intensity():
    source = SOURCE.read_text(encoding="utf-8")
    assert "keyRitualProjectileIntensity" in source
    assert "RitualAmplifierPolicy.projectileDamageMultiplier" in source
    assert "readInt(arrow, keyRitualProjectileIntensity" in source
