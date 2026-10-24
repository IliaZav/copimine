from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
PLUGIN = ROOT / "copimine-end-event"
ROOT_SOURCE = PLUGIN / "src/me/copimine/endevent/CopiMineEndEvent.java"
POLICY_SOURCE = PLUGIN / "src/me/copimine/endevent/domain/WaveTwoEntityRecoveryPolicy.java"


def _method_body(source: str, signature: str) -> str:
    start = source.index(signature)
    open_brace = source.index("{", start)
    depth = 0
    for index in range(open_brace, len(source)):
        if source[index] == "{":
            depth += 1
        elif source[index] == "}":
            depth -= 1
            if depth == 0:
                return source[open_brace + 1:index]
    raise AssertionError(f"unclosed Java method: {signature}")


def test_wave2_cleanup_fence_is_durable_before_old_rosters_are_released() -> None:
    source = ROOT_SOURCE.read_text(encoding="utf-8")
    archive = _method_body(source, "private boolean archiveWaveTwoRosterForCleanup(")
    fence = _method_body(source, "private boolean advanceWaveTwoCleanupFence(")
    stale_cleanup = _method_body(source, "private boolean removeStaleWaveTwoEntitiesAfterCleanupFence(")
    snapshot = _method_body(source, "private Map<String, String> objectiveProgressSnapshot(")
    restore = _method_body(source, "private void applySnapshot(EventSnapshot snapshot)")
    tag = _method_body(source, "private void tag(Entity entity, String kind, int wave, boolean official)")
    disposition = _method_body(source, "private WaveTwoEntityRecoveryPolicy.LoadDisposition waveTwoEntityLoadDisposition(")
    configure_core = _method_body(source, "private void configureCore(")
    remove_core = _method_body(source, "private void removeCore(")

    assert "waveTwoOutstandingEntityUuids.isEmpty() && waveTwoCleanupEntityUuids.isEmpty()" in archive
    assert "advanceWaveTwoCleanupFence" in archive
    assert "nextCleanupEpoch" in fence
    assert "stateStore.save(snapshot())" in fence
    assert fence.index("stateStore.save(snapshot())") < fence.index("return true")
    assert "waveTwoCleanupEntityUuids.clear()" in fence
    assert "waveTwoOutstandingEntityUuids.clear()" in fence
    assert "previousCleanupEpoch" in fence
    assert "previousOutstanding" in fence
    assert "previousCleanup" in fence
    checkpoint_save = fence.index("stateStore.save(snapshot())")
    recovery_transition = fence.index("forcePhase(EventPhase.RECOVERY_REQUIRED")
    stale_removal = fence.index("removeStaleWaveTwoEntitiesAfterCleanupFence(reason)")
    assert checkpoint_save < recovery_transition < stale_removal
    assert "forcePhase(EventPhase.RECOVERY_REQUIRED" not in archive
    checkpoint_failure = fence.index("if (stateStore == null || !stateStore.save(snapshot()))")
    assert checkpoint_failure < recovery_transition
    assert fence.index("stateStore.save(snapshot())") < fence.index("removeStaleWaveTwoEntitiesAfterCleanupFence")
    assert "private boolean removeStaleWaveTwoEntitiesAfterCleanupFence(" in source
    assert "if (!removeStaleWaveTwoEntitiesAfterCleanupFence(reason)) return false;" in fence
    assert "return recoveryEntityId == null;" in stale_cleanup
    assert "forcePhase(EventPhase.RECOVERY_REQUIRED" in stale_cleanup
    assert 'WAVE_TWO_CLEANUP_EPOCH_PROGRESS_KEY' in snapshot
    assert 'WAVE_TWO_CLEANUP_EPOCH_PROGRESS_KEY' in restore
    assert "keyWaveTwoCleanupEpoch" in tag
    assert "keyWaveTwoCleanupEpoch" in disposition
    assert "advanceWaveTwoCleanupFence" in configure_core
    assert "advanceWaveTwoCleanupFence" in remove_core
    assert remove_core.index("advanceWaveTwoCleanupFence") < remove_core.index("cancelSessionTasks()")
    assert remove_core.index("advanceWaveTwoCleanupFence") < remove_core.index("clearWaveEntities()")
    assert configure_core.index("advanceWaveTwoCleanupFence") < configure_core.index("eventId = UUID.randomUUID()")


def test_wave2_entity_epoch_recovery_is_bounded_across_restarts_and_core_rotation() -> None:
    policy = POLICY_SOURCE.read_text(encoding="utf-8")
    java_test = (ROOT / "tests/WaveTwoEntityRecoveryPolicyTest.java").read_text(encoding="utf-8")

    assert "nextCleanupEpoch" in policy
    assert "taggedCleanupEpoch < expectedCleanupEpoch" in policy
    assert "taggedCleanupEpoch > expectedCleanupEpoch" in policy
    assert "Long.MIN_VALUE" not in policy
    assert "keyWaveTwoCleanupEpoch, PersistentDataType.LONG, 0L" in ROOT_SOURCE.read_text(encoding="utf-8")
    assert "cycle < 1000" in java_test
    assert "event ID rotated" in java_test
    assert "cleanup epoch overflow fails closed" in java_test


def test_cleanup_fence_checkpoint_serializes_recovery_phase_before_transition() -> None:
    source = ROOT_SOURCE.read_text(encoding="utf-8")
    policy = POLICY_SOURCE.read_text(encoding="utf-8")
    snapshot = _method_body(source, "private EventSnapshot snapshot()")
    fence = _method_body(source, "private boolean advanceWaveTwoCleanupFence(")

    assert "phaseForCleanupFenceSnapshot(phase.name(), recoveryReason)" in snapshot
    assert "CLEANUP_FENCE_REQUIRED_REASON" in policy
    assert "recoveryReason = WaveTwoEntityRecoveryPolicy.CLEANUP_FENCE_REQUIRED_REASON" in fence
    assert fence.index("stateStore.save(snapshot())") < fence.index(
        "forcePhase(EventPhase.RECOVERY_REQUIRED"
    )


def test_wave2_final_elite_is_stamped_with_the_current_durable_cleanup_epoch() -> None:
    source = ROOT_SOURCE.read_text(encoding="utf-8")
    elite_spawn = _method_body(source, "private Skeleton spawnWaveTwoFinalEliteSkeleton(")

    marker = elite_spawn.index('set(keyWaveTwoFinalElite,')
    group = elite_spawn.index('set(keyWaveSpawnGroup,')
    epoch = elite_spawn.index('set(keyWaveTwoCleanupEpoch,')
    assert marker < group < epoch
    assert 'PersistentDataType.LONG, waveTwoCleanupEpoch' in elite_spawn


def test_wave2_legacy_final_elite_migration_requires_the_exact_active_durable_member() -> None:
    source = ROOT_SOURCE.read_text(encoding="utf-8")
    policy = POLICY_SOURCE.read_text(encoding="utf-8")
    disposition = _method_body(source, "private WaveTwoEntityRecoveryPolicy.LoadDisposition waveTwoEntityLoadDisposition(")
    reindex = _method_body(source, "private void reindexPersistedCombatEntities()")
    load_event = _method_body(source, "public void onPersistedCombatEntitiesLoad(EntitiesLoadEvent event)")
    stamp = _method_body(source, "private void stampCurrentWaveTwoCleanupEpochIfMissing(Entity entity)")

    assert "data.getKeys().contains(keyWaveTwoCleanupEpoch)" in disposition
    assert "isApprovedWaveTwoFinalElite(entity)" in disposition
    assert "canMigrateLegacyFinalEliteEpoch(" in disposition
    assert "waveTwoGroupsSpawned" in disposition
    assert "waveTwoOutstandingEntityUuids.contains(entity.getUniqueId())" in disposition
    assert "taggedCleanupEpoch = waveTwoCleanupEpoch" in disposition
    assert "cleanupTombstone" in policy
    assert "taggedGeneration == expectedGeneration" in policy
    assert "taggedGroup == WaveTwoStagePolicy.FIRST_LATE_GROUP" in policy
    assert "activeGroup == WaveTwoStagePolicy.FIRST_LATE_GROUP" in policy
    assert "outstandingMember" in policy
    assert "stampCurrentWaveTwoCleanupEpochIfMissing(entity)" in reindex
    assert "stampCurrentWaveTwoCleanupEpochIfMissing(entity)" in load_event
    assert "PersistentDataType.LONG, waveTwoCleanupEpoch" in stamp
    assert "data.getKeys().contains(keyWaveTwoCleanupEpoch)" in stamp


def test_wave_combat_mobs_cannot_despawn_or_be_swept_as_unexpected_removals() -> None:
    source = ROOT_SOURCE.read_text(encoding="utf-8")
    tag = _method_body(source, "private void tag(Entity entity, String kind, int wave, boolean official)")

    assert "isWaveCombatKind(kind)" in tag
    assert tag.index("living.setPersistent(true)") < tag.index("registerOwnedEntity(entity)")
    assert tag.index("living.setRemoveWhenFarAway(false)") < tag.index("registerOwnedEntity(entity)")
    assert 'entity.addScoreboardTag("clearlag_exclude")' in tag


def test_confirmed_admin_cleanup_archives_wave2_roster_before_removing_entities() -> None:
    source = ROOT_SOURCE.read_text(encoding="utf-8")
    on_command = _method_body(
        source,
        "public boolean onCommand(CommandSender sender, Command command, String label, String[] args)",
    )
    ritual = _method_body(source, "private void handleRitual(CommandSender sender, String[] args)")

    command_cleanup = on_command[
        on_command.index('case "cleanup" -> {') : on_command.index('case "reset" ->')
    ]
    ritual_cleanup = ritual[
        ritual.index('case "cleanup" -> {') : ritual.index('case "reset" ->')
    ]

    for route, reason, stop in (
        (command_cleanup, "admin-cleanup", "return true;"),
        (ritual_cleanup, "admin-ritual-cleanup", "return;"),
    ):
        archive_guard = f'if (!archiveWaveTwoRosterForCleanup("{reason}"))'
        guard_index = route.index(archive_guard)
        stop_index = route.index(stop, guard_index)
        cleanup_index = route.index("cleanupOwnedEntities(eventId, generation)")

        assert guard_index < stop_index < cleanup_index
