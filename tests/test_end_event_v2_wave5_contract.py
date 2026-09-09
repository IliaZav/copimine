from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MAIN = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(
    encoding="utf-8"
)


def _body(start_marker: str, end_marker: str) -> str:
    start = MAIN.index(start_marker)
    end = MAIN.index(end_marker, start)
    return MAIN[start:end]


def test_wave5_roles_have_server_side_target_and_damage_guards():
    assert "public void onV2Wave5RoleTarget(EntityTargetLivingEntityEvent event)" in MAIN
    target = _body(
        "public void onV2Wave5RoleTarget(EntityTargetLivingEntityEvent event)",
        "/** Tag every ordinary skeleton arrow",
    )
    assert "wave5TargetAllowed(source, player)" in target
    assert "event.setCancelled(true)" in target
    assert "mob.setTarget(null)" in target

    damage = _body(
        "public void onWaveMobPlayerDamageAuthoritative(EntityDamageByEntityEvent event)",
        "public void onWaveMobDamagedByPlayer",
    )
    assert "isV2Wave5Elite(victim)" in damage
    assert "Wave5EncounterPolicy.guardShieldActive" in damage
    assert "WAVE5_ELITE_DAMAGE_BLOCKED" in damage


def test_wave5_death_callbacks_open_elite_and_release_prisoner():
    death = _body("public void onOwnedEntityDeath(EntityDeathEvent event)", "private void addConfiguredDrops")
    assert "Wave5EncounterPolicy.guardDefeated" in death
    assert "Wave5EncounterPolicy.eliteDefeated" in death
    assert "clearV2Wave5PrisonerVisuals();" in death
    assert "elite_vulnerable" in death


def test_wave5_cleanup_restores_prisoner_ice_and_drops_runtime_state():
    cleanup = _body("private void clearWaveObjectiveState()", "private void scheduleOfficialBossSpawn")
    assert "clearV2Wave5PrisonerVisuals();" in cleanup
    assert "v2Wave5EncounterState = null;" in cleanup
    assert "v2Wave5GuardUuids.clear();" in cleanup
    assert "v2Wave5GuardSlots.clear();" in cleanup
    assert "v2Wave5EliteUuid = null;" in cleanup


def test_wave5_ring_displays_use_the_owned_teleport_permit():
    ring = _body("private void tickV2RingObjective", "/** Advance the server-authoritative prisoner")
    assert "teleportCombatEntity(entity, next.add(" in ring
    assert "entity.teleport(next.add(" not in ring


def test_wave5_runtime_applies_declared_health_profiles_and_reconstruction_pair():
    spawn = _body("private void spawnV2Wave5FinalPack", "private void clearV2Wave5CombatEntities")
    ring = _body("private void tickV2RingObjective", "/** Advance the server-authoritative prisoner")
    assert "applyWave5HealthProfile(guard, Wave5EncounterPolicy.GUARD_HP_MULTIPLIER)" in spawn
    assert "applyWave5HealthProfile(elite, Wave5EncounterPolicy.ELITE_HP_MULTIPLIER)" in spawn
    assert "spawnV2Wave5ReconstructionGuards" in ring
    assert "RECON_GUARD" in MAIN


def test_wave5_prisoner_strength_is_bounded_and_logged():
    assert "prisonerStrengthStacks" in MAIN
    assert "MAX_PRISONER_STRENGTH_STACKS" in MAIN
    assert "V2_WAVE5_PRISONER_STRENGTH" in MAIN


def test_all_dead_wipe_is_generation_fenced_and_rebuilds_start_runes():
    assert "private void wipeOfficialAttemptIfAllDead(String reason)" in MAIN
    death = _body("public void onPlayerDeath(PlayerDeathEvent event)", "public void onPlayerRespawn")
    assert "attemptLifecycle.markDead" in death
    assert "wipeOfficialAttemptIfAllDead" in death
    wipe = _body(
        "private void wipeOfficialAttemptIfAllDead",
        "public void onShardChannelDamage",
    )
    assert "performAttemptWipe" in wipe
    assert "cancelSessionTasks" in wipe
    assert "cleanupOwnedEntities(eventId, staleGeneration)" in wipe
    assert "generation = result.nextGeneration()" in wipe
    assert "calculateAndPlacePads(world)" in wipe
    assert "coreCharged" in wipe
    assert "EventPhase.READY_FOR_PLAYERS" in wipe


def test_disconnect_uses_bounded_reconnect_grace_before_wipe():
    assert "OFFLINE_RECONNECT_GRACE_MILLIS = 25_000L" in MAIN
    quit_handler = _body("public void onPlayerQuit(PlayerQuitEvent event)", "public void onPlayerDeath")
    tick = _body("private void tickOfflineRosterGrace()", "private void teleportRespawnedOfficialParticipant")
    assert "offlineRosterGraceUntilMillis.put" in quit_handler
    assert "tickOfflineRosterGrace();" in MAIN
    assert "attemptLifecycle.markDead" in tick
    assert "wipeOfficialAttemptIfAllDead" in tick
