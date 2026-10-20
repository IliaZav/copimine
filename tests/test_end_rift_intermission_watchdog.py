from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"


def method(source, signature):
    start = source.index(signature)
    opening = source.index("{", start)
    depth, end = 1, opening + 1
    while depth:
        depth += (source[end] == "{") - (source[end] == "}")
        end += 1
    return source[start:end]


def test_intermission_watchdog_uses_the_same_ten_minute_deadline_after_restart():
    source = SOURCE.read_text(encoding="utf-8")
    bootstrap = method(source, "private void restorePersistedCombatRuntime")
    tick = method(source, "private void tickCurrentIntermission")

    assert "IntermissionWatchdogPolicy.recoverDeadline(" in bootstrap
    assert "phaseDeadlineMillis, now" in bootstrap
    assert "IntermissionWatchdogPolicy.isExpired(now, phaseDeadlineMillis)" in tick
    assert "intermissionSeconds() * 1000L" not in bootstrap


def test_intermission_timeout_invalidates_only_uncommitted_attempt_and_cleans_runtime():
    source = SOURCE.read_text(encoding="utf-8")
    tick = method(source, "private void tickCurrentIntermission")
    timeout = tick.split("if (IntermissionWatchdogPolicy.isExpired", 1)[1]
    timeout = timeout.split("        int completedWave", 1)[0]

    for required in (
        "cleanupOwnedEntities(eventId, generation)",
        "clearClientEffects()",
        "clearCombatAiState()",
        "attemptLifecycle.clear()",
        "discardUncommittedRoster()",
        "discardUncommittedRewardStatuses()",
        "forcePhase(EventPhase.READY_FOR_PLAYERS",
    ):
        assert required in timeout
    assert "officialBossDeathCommitted" not in timeout


def test_intermission_timeout_rebuilds_and_persists_start_runes_before_ready():
    source = SOURCE.read_text(encoding="utf-8")
    tick = method(source, "private void tickCurrentIntermission")
    timeout = tick.split("if (IntermissionWatchdogPolicy.isExpired", 1)[1]
    timeout = timeout.split("        int completedWave", 1)[0]

    setup = timeout.index("calculateAndPlacePads(world)")
    visuals = timeout.index("rebuildPersistedVisuals()", setup)
    ready = timeout.index("forcePhase(EventPhase.READY_FOR_PLAYERS", visuals)
    persistence = timeout.index("saveStateSync()", ready)

    assert "World world = Bukkit.getWorld(worldName)" in timeout
    assert setup < visuals < ready < persistence
    assert "INTERMISSION_TIMEOUT_START_RUNE_SETUP_FAILED" in timeout
    assert "INTERMISSION_TIMEOUT_START_RUNE_VISUALS_FAILED" in timeout


def test_watchdog_lifecycle_and_progress_are_documented():
    docs = ROOT / "docs/end-rift/INTERMISSION_WATCHDOG.md"
    assert docs.is_file(), "document the ten-minute restart and abort behavior"
    content = docs.read_text(encoding="utf-8").lower()
    assert "600,000" in content or "600000" in content
    assert "preserv" in content
    assert "uncommitted" in content
