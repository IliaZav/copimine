from __future__ import annotations

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MAIN = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(encoding="utf-8")
CONFIG = (ROOT / "copimine-end-event/config.yml").read_text(encoding="utf-8")


def test_boss_health_and_phase_thresholds_are_explicitly_balanced_for_1200_hp() -> None:
    assert "health: 1200.0" in CONFIG
    assert "half-health: 600.0" in CONFIG
    assert "final-threshold: 120.0" in CONFIG
    assert "bossFinalHealth()" in MAIN


def test_boss_ai_has_one_controller_with_telegraph_and_generation_guards() -> None:
    for marker in (
        "EndRiftAiPolicy",
        "recentBossTargets",
        "rotateBossTarget",
        "telegraphBossSpell",
        "BOSS_SPELL_TELEGRAPH",
        "BOSS_SPELL_CAST",
        "taskRegistry.owns(callbackGeneration)",
        "BOSS_AI_TARGET",
        "BOSS_AI_LEASH",
        "MAX_ACTIVE_VOID_MARKS = 2",
        "VOID_MARK_DURATION_SECONDS = 6",
        "VOID_MARK_RADIUS_BLOCKS = 3",
        "runTaskTimer(this",
        "BOSS_VOID_MARK_CLEANUP",
        "clearVoidMarkZones",
        "Snowball",
        "EVENT_KIND_PROJECTILE",
        "MAX_ACTIVE_RIFT_PROJECTILES = 8",
        "BOSS_PROJECTILE_SPAWN",
        "BOSS_PROJECTILE_HIT",
        "cleanupRiftProjectile",
    ):
        assert marker in MAIN
    assert "random.nextInt(4)" not in MAIN
    assert "player.damage(2.0D, boss)" in MAIN
    assert "player.damage(7.0D, boss)" in MAIN


def test_wave_elites_have_one_bound_spell_and_are_ticked_by_the_same_controller() -> None:
    for marker in (
        "MiniBossSpell",
        "miniBossSpell(wave, abilityIndex)",
        "tickMiniBosses",
        "telegraphMiniBossSpell",
        "MINIBOSS_SPELL_TELEGRAPH",
        "MINIBOSS_SPELL_CAST",
        "EVENT_KIND_ELITE",
    ):
        assert marker in MAIN
    assert "miniBossSpell = null" not in MAIN


def test_ai_config_has_separate_cooldowns_and_telegraphs_for_boss_and_minibosses() -> None:
    for marker in (
        "recent-target-memory: 3",
        "teleport-cooldown-seconds: 12",
        "mini-bosses:",
        "spell-cooldown-seconds: [8, 12]",
        "spell-telegraph-ticks: 20",
        "rift-step:",
        "void-snare:",
        "echo-pulse:",
    ):
        assert marker in CONFIG


def test_summon_spell_is_only_offered_at_the_two_health_windows() -> None:
    assert "servantSummonWindow(boss)" in MAIN
    assert "available.remove(EndRiftAiPolicy.BossSpell.SUMMON_SERVANTS)" in MAIN
    assert "servantsSummonedAt70" in MAIN
    assert "servantsSummonedAt35" in MAIN


def test_local_ai_harness_runs_real_controllers_without_touching_official_phase() -> None:
    for marker in (
        '"ai"',
        "spawnTestAi",
        "testCombatAiMode",
        "official phase/roster/victory не изменены",
        "boolean testBossAi = testCombatAiMode && isTestBoss(boss)",
        "isMiniBossCombatPhase()",
    ):
        assert marker in MAIN


def test_local_ai_harness_ticks_boss_controller_outside_official_boss_phase() -> None:
    """The local harness deliberately stays out of the official state machine."""
    assert "if (testCombatAiMode && liveBoss() != null)" in MAIN
    assert "tickBoss();" in MAIN
    assert "&& !(testCombatAiMode && isTestBoss(boss))" in MAIN
    assert "phase != EventPhase.BOSS_ACTIVE && !testBossAi" in MAIN


def test_clearing_a_boss_resets_target_and_spell_cooldown_state() -> None:
    assert "nextTargetMillis = 0L;" in MAIN
    assert "nextSpellMillis = 0L;" in MAIN
    assert "private void clearBossOnly()" in MAIN
    assert "private void clearCombatAiState()" in MAIN


def test_combat_containment_allows_twenty_blocks_but_keeps_teleports_on_core_level() -> None:
    assert "boss-radius: 20.0" in CONFIG
    assert "containment-radius: 20.0" in CONFIG
    assert "config.containmentRadius()" in MAIN
    assert "candidate.setY(center.getBlockY())" in MAIN
    assert "horizontalDistanceSquared" in MAIN
    assert "offCoreLevel" in MAIN
    assert "The pad coordinate is the air block above this floor" in MAIN


def test_wave_containment_watchdog_runs_for_test_waves_before_phase_gate() -> None:
    start = MAIN.index("private void tickWaveMobAi()")
    end = MAIN.index("private boolean isWaveCombatKind", start)
    body = MAIN[start:end]
    assert "enforceWaveMobContainment();" in body
    assert body.index("enforceWaveMobContainment();") < body.index("if (!isCombatPhase())")
    assert "private void enforceWaveMobContainment()" in MAIN
    assert "boundedCombatRadius(config.containmentRadius())" in MAIN
    assert "target.getBlockY() != anchor.getBlockY()" in MAIN


def test_wave_teleports_normalize_to_the_core_block_top_instead_of_stacking_inside_it() -> None:
    assert "private boolean isCoreBlockPosition(Location location)" in MAIN
    assert "private Location coreBlockTopLocation()" in MAIN
    assert "if (isCoreBlockPosition(target))" in MAIN
    assert "event.setTo(coreBlockTopLocation());" in MAIN


def test_boss_and_miniboss_spells_have_a_visible_particle_flight_before_impact() -> None:
    for marker in (
        "launchSpellFlight",
        "BOSS_SPELL_FLIGHT",
        "MINIBOSS_SPELL_FLIGHT",
        "spell-flight",
        "spawnParticle",
        "taskRegistry.owns(callbackGeneration)",
    ):
        assert marker in MAIN
