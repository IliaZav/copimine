from __future__ import annotations

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MAIN = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(encoding="utf-8")
VIRTUAL_POLICY = (ROOT / "copimine-end-event/src/me/copimine/endevent/domain/BossVirtualHealthPolicy.java").read_text(encoding="utf-8")


def _body(start_marker: str, end_marker: str) -> str:
    start = MAIN.index(start_marker)
    end = MAIN.index(end_marker, start)
    return MAIN[start:end]


def test_authoritative_damage_accumulator_keeps_every_independent_hit() -> None:
    damage = _body("private void applyBossDamage", "private void triggerHalfPhase")
    assert "BossVirtualHealthPolicy.applyFinalDamage" in damage
    assert "double currentHealth = bossVirtualHealth(boss)" in damage
    assert "double projectedHealth" in damage
    assert "setBossVirtualHealth(boss, projectedHealth)" in damage
    assert "applyHits(" in VIRTUAL_POLICY
    assert "for (Double damage : finalDamages)" in VIRTUAL_POLICY


def test_bukkit_adapter_uses_each_event_final_damage_and_cancels_physical_projection() -> None:
    damage = _body("public void onBossDamage", "private void applyBossDamage")
    assert damage.count("event.getFinalDamage()") >= 2
    assert damage.count("BossDamagePolicy.applyIncomingDamage") >= 2
    assert damage.count("event.setCancelled(true);") >= 3
    assert "applyBossDamage(boss" in damage
    assert "BOSS_DAMAGE_EVENT" in damage
    assert "source=" in damage
    assert "final=" in damage


def test_exhausted_multiplier_is_kept_once_per_hit_and_blocked_casts_still_fail_closed() -> None:
    damage_policy = (ROOT / "copimine-end-event/src/me/copimine/endevent/domain/BossDamagePolicy.java").read_text(encoding="utf-8")
    assert "EXHAUSTED ? 1.5D" in damage_policy
    assert "applyIncomingDamage" in MAIN
    assert "BOSS_DAMAGE_BLOCKED" in MAIN
    assert "BossCastState.ABSORPTION_CHANNEL" in MAIN
    assert "BossCastState.JUDGMENT_CAST" in MAIN


def test_virtual_health_never_delegates_authority_to_paper_health() -> None:
    configure = _body("private void configureBoss(Enderman boss, boolean test)", "private void ensureBossBar()")
    assert "setBossVirtualHealth(boss, configuredMaxHealth);" in configure
    assert "boss.setHealth(config.bossHealth());" not in configure
    assert "keyBossVirtualHealth" in MAIN
    assert "keyBossVirtualMaxHealth" in MAIN


def test_lethal_multi_hit_sequence_has_one_terminal_zero_and_cannot_resurrect() -> None:
    assert "Math.max(0.0D, before - applied)" in VIRTUAL_POLICY
    assert "commitOfficialBossDefeat(boss, source)" in MAIN
    assert "officialBossDeathCommitted" in MAIN


def test_multiplayer_probe_has_a_test_only_boss_freeze_for_stable_reach() -> None:
    probe = (ROOT / "tests/RunEndRiftBossMultiPlayerDamageLive.ps1").read_text(encoding="utf-8")
    assert "cmend boss freeze" in probe
    assert "testBossMovementFrozen" in MAIN
    assert "testCombatAiMode && testBossMovementFrozen" in MAIN
    assert "mob.setAI(false)" in MAIN
