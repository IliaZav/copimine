"""Rift Chains must remain bound to its selected active spell target."""

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"


def test_rift_chains_effects_only_follow_the_selected_valid_target() -> None:
    source = SOURCE.read_text(encoding="utf-8")
    active_start = source.index("private void tickActiveRitualSpell")
    active_end = source.index("private RitualCasterTacticsPolicy.State ritualCasterTacticsState", active_start)
    active = source[active_start:active_end]
    chains_start = source.index("private void tickRitualChains")
    chains_end = source.index("private RitualCasterTacticsPolicy.State ritualCasterTacticsState", chains_start)
    chains = source[chains_start:chains_end]

    assert "UUID targetId = ritualSpellTargetUuid;" in active
    assert "tickRitualChains(target);" in active
    assert "tickRitualChains(now);" not in active
    assert "ritualFreeTargets(activeLivingPlayers())" not in chains
    assert "Player target" in chains
    assert "isFreeRitualTarget(target)" in chains
    assert "ritualSpellActiveUntilTick = eventTickCounter;" in chains
    assert "target.setVelocity(velocity);" in chains
    assert "target.addPotionEffect(" in chains
