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


def test_wave4_pressure_pack_retries_only_missing_persistent_slots():
    source = SOURCE.read_text(encoding="utf-8")
    spawn = method(source, "private Wave4PressurePackResult spawnWave4PressurePack")
    reconcile = method(source, "private void reconcileWave4PressureSpawnSlots")
    tick = method(source, "private boolean tickWave4ObeliskAssault")

    assert "Wave4PressurePackPolicy.missing" in spawn
    assert "reconcileWave4PressureSpawnSlots(planned)" in spawn
    assert "tagWave4PressureSlot(entity, slot.key())" in spawn
    assert "currentWave4PressureSpawnSlots.add(slot.key())" in spawn
    assert "keyWave4PressureSlot" in reconcile
    assert "inferredWave4Role(entity)" in reconcile
    assert "Wave4PressurePackPolicy.shouldAttempt" in tick
    assert "Wave4PressurePackPolicy.retryDelayMillis" in tick
    assert "Wave4PressurePackResult" in spawn
    assert "PERMANENT_FAILURE" in spawn and "RETRYABLE_FAILURE" in spawn
    assert "if (packResult == Wave4PressurePackResult.COMPLETE)" in tick
    assert "else if (packResult == Wave4PressurePackResult.PERMANENT_FAILURE" in tick
    exhausted = tick.split("else if (packResult == Wave4PressurePackResult.PERMANENT_FAILURE", 1)[1]
    exhausted = exhausted.split("} else {", 1)[0]
    assert "currentWave4StartupFailed = true" in exhausted
    assert "clearWave4PressurePackEntities" in exhausted
    assert "cancelWaveSpawnTask()" in exhausted
    assert "currentWave4MobsStarted = true" not in exhausted
    assert "currentWave4PressureRetryAtMillis = now" in tick


def test_wave4_restart_cleans_the_transient_generation_before_retrying():
    source = SOURCE.read_text(encoding="utf-8")
    recovery = method(source, "private void recoverTransientSession")
    state_machine = (ROOT / "copimine-end-event/src/me/copimine/endevent/domain/EndEventStateMachine.java").read_text(
        encoding="utf-8")

    assert "cleanupOwnedEntities(eventId, staleGeneration)" in recovery
    assert "generation = Math.max(1L, staleGeneration + 1L)" in recovery
    assert "case COLLECTING, READY_FOR_PLAYERS -> persisted" in state_machine
    assert "default -> EventPhase.READY_FOR_PLAYERS" in state_machine
