from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
EVENT = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(
    encoding="utf-8"
)


def method_body(name: str) -> str:
    marker = f"private void {name}()"
    if name != "restorePersistedRitualSphereObjective":
        marker = f"private boolean {name}(World world, Location core)"
    start = EVENT.index(marker)
    body_start = EVENT.index("{", start)
    depth = 0
    for index in range(body_start, len(EVENT)):
        if EVENT[index] == "{":
            depth += 1
        elif EVENT[index] == "}":
            depth -= 1
            if depth == 0:
                return EVENT[body_start + 1:index]
    raise AssertionError(f"Unclosed method body for {name}")


def test_partial_wave6_progress_enters_the_restart_continuation_path():
    restore = method_body("restorePersistedRitualSphereObjective")
    valid_snapshot = restore.index("ritualSphereState.generation() != generation")
    count_mismatch = restore.index("liveRitualCasterCount() != profile.casterCount()")
    rehydrated = restore.index("ritualSphereStateRehydrated = true;")

    assert valid_snapshot < rehydrated < count_mismatch, (
        "a valid persisted Wave 6 must mark rehydration before partial caster/guard counts "
        "can route through startRitualSphereObjective"
    )
    assert "startRitualSphereObjective(Bukkit.getWorld(worldName), core);" in restore

    start = method_body("startRitualSphereObjective")
    assert "recoveredCasters == expectedLivingCasters" in start
    assert "recoveredGuards <= recoveredProfile.guardCount()" in start
    assert "ritualCasterDeathCount = recoveredProfile.casterCount() - recoveredCasters" not in start
    assert "ritualCasterDeathCount" in start


def test_unloaded_living_caster_is_not_counted_as_a_restart_death():
    start = method_body("startRitualSphereObjective")
    recovery_start = start.index("if (ritualSphereStateRehydrated")
    recovered_counts = start.index("int recoveredCasters = liveRitualCasterCount()")

    # A world.getEntities() count cannot distinguish a dead Caster from a
    # living one in an unloaded chunk. Recovery must first load/verify the
    # persisted roster, and progression must come from a durable death receipt.
    roster_gate = "ensureRitualCasterRosterAvailableForRecovery(recoveredProfile)"
    assert roster_gate in start[recovery_start:recovered_counts], (
        "Wave 6 restart checks caster counts before loading/verifying the complete "
        "arena roster; an unloaded living Caster could look dead"
    )
    assert "if (!ensureRitualCasterRosterAvailableForRecovery(recoveredProfile))" in start
    roster_gate_start = start.index(roster_gate, recovery_start)
    assert "return false;" in start[roster_gate_start:recovered_counts], (
        "an incomplete Caster roster must stop restart before it can alter progression"
    )
    assert "ritualCasterDeathCount = recoveredProfile.casterCount() - recoveredCasters" not in start
    assert "ritualSpellCursor = wave6.spellCursor();" in EVENT
    assert "RitualSphereEncounterSnapshot.encode(\n                            ritualSphereState, ritualCasterDeathCount, ritualSpellCursor)" in EVENT


def test_wave6_restart_loads_bounded_arena_and_rebuilds_unique_caster_and_guard_slots():
    helper_start = EVENT.index("private boolean ensureRitualCasterRosterAvailableForRecovery(")
    helper_body_start = EVENT.index("{", helper_start)
    depth = 0
    for index in range(helper_body_start, len(EVENT)):
        if EVENT[index] == "{":
            depth += 1
        elif EVENT[index] == "}":
            depth -= 1
            if depth == 0:
                helper = EVENT[helper_body_start + 1:index]
                break
    else:
        raise AssertionError("Unclosed Wave 6 recovery roster helper")

    assert "chunkCount > 256L" in helper
    assert "world.getChunkAt(chunkX, chunkZ).isLoaded()" in helper
    assert "castersBySlot.putIfAbsent(slot, entity) != null" in helper
    assert "castersBySlot.size() != expectedLivingCasters" in helper
    assert "guardSlotsByCaster" in helper
    assert "ritualCasterUuids.clear()" in helper
    assert "ritualGuardUuids.clear()" in helper


def test_wave6_roster_mismatch_persists_a_fail_closed_recovery_checkpoint():
    helper_start = EVENT.index("private void markWave6RecoveryRequired(")
    helper_body_start = EVENT.index("{", helper_start)
    depth = 0
    for index in range(helper_body_start, len(EVENT)):
        if EVENT[index] == "{":
            depth += 1
        elif EVENT[index] == "}":
            depth -= 1
            if depth == 0:
                helper = EVENT[helper_body_start + 1:index]
                break
    else:
        raise AssertionError("Unclosed Wave 6 recovery transition")

    assert "ritualSphereRecoveryCheckpoint" in helper
    assert "forcePhase(EventPhase.RECOVERY_REQUIRED, safeReason)" in helper
    assert "saveStateSync()" in helper
