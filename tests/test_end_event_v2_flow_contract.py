from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MAIN = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(encoding="utf-8")


def _method(start_marker: str, end_marker: str) -> str:
    start = MAIN.index(start_marker)
    end = MAIN.index(end_marker, start)
    return MAIN[start:end]


def test_official_wave_driver_enters_the_v2_controller_before_legacy_objectives() -> None:
    spawn = _method("private void spawnWave(int wave, boolean test)", "private void startWaveFrontAnimation")
    objective = _method("private void startWaveObjective", "private void planRiftStorm")
    tick = _method("private boolean tickWaveObjective", "private void updateCorePulseObjective")
    assert "V2WaveObjectivePolicy" in spawn
    assert "isOfficialV2Attempt()" in spawn
    assert "spawnV2Wave(wave, world, core)" in spawn
    assert "V2WaveObjectivePolicy" in objective
    assert "tickV2WaveObjective" in tick
    official_prefix = tick[:tick.index("if (testCombatAiMode")]
    assert "updateTowerObjective" not in official_prefix
    assert "updateRiftStormObjective" not in official_prefix


def test_v2_wave_objectives_are_not_old_tower_or_storm_objectives() -> None:
    assert "case BLACK_FOG" in MAIN
    assert "case COLLAPSE_RINGS" in MAIN
    assert "case CHAMBERS" in MAIN
    v2 = _method("private void v2PrepareSequentialPortals", "private boolean tickV2WaveObjective")
    assert "V2WaveObjectivePolicy.PORTAL_COUNT" in v2
    assert "WaveMechanicsPolicy.portalCount" not in v2


def test_wave_six_does_not_open_the_passage_between_staggered_groups() -> None:
    chamber = _method("private void tickV2ChamberObjective", "private void renderV2WaveObjective")
    assert "V2WaveObjectivePolicy.chambersComplete" in chamber
    assert "v2WaveSpawnGroupIndex >= v2WaveSpawnSchedule.size()" in chamber
    assert "countLiveWaveEntitiesForWave(chamberWaveNumber())" in chamber


def test_official_boss_damage_commits_an_accepted_real_health_hit_atomically() -> None:
    damage = _method("private void handleV2BossDamage", "private void applyBossDamage")
    assert "BossRealHealthDamagePolicy.apply(" in damage
    assert "event.setCancelled(true);" in damage
    assert "boss.setHealth(result.remainingHealth())" in damage
    assert "event.setDamage(adjustedBaseDamage);" not in damage
    assert "cancelled=true" in damage
    assert "authority=entity-health" in damage


def test_official_boss_configuration_has_no_virtual_health_authority() -> None:
    configure = _method("private boolean configureBoss(Enderman boss, boolean test)", "private void ensureBossBar")
    official = configure[configure.index("if (!test)"):configure.index("        }", configure.index("if (!test)") + 1)]
    assert "maxHealth.setBaseValue(configuredMaxHealth)" in configure
    assert "boss.setHealth(configuredMaxHealth)" in configure
    assert "setBossVirtualHealth(boss, configuredMaxHealth)" not in official
    assert "BossVirtualHealthPolicy" not in official


def test_natural_v2_entity_death_commits_victory_from_boss_active() -> None:
    death = _method("public void onOwnedEntityDeath", "private void addConfiguredDrops")
    assert "naturalV2Death" in death
    assert "phase == EventPhase.BOSS_ACTIVE" in death
    assert "isV2OfficialBoss(entity)" in death
    assert "V2 boss entity health reached zero" in death
    assert "transition(EventPhase.BOSS_FINISH" in death
    assert "beginVictory();" in death


def test_wave_scaling_does_not_add_six_extra_mobs_to_the_duo_baseline() -> None:
    policy = (ROOT / "copimine-end-event/src/me/copimine/endevent/domain/WaveScalingPolicy.java").read_text(encoding="utf-8")
    assert "MIN_EXTRA_MOBS = 0" in policy
    assert "safeConfigured + MIN_EXTRA_MOBS" in policy
