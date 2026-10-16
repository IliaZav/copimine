from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
EVENT = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"


def _declaration(source: str, signature: str) -> str:
    start = source.index(signature)
    opening = source.index("{", start)
    depth = 0
    for index in range(opening, len(source)):
        if source[index] == "{":
            depth += 1
        elif source[index] == "}":
            depth -= 1
            if depth == 0:
                return source[start:index + 1]
    raise AssertionError(f"unterminated method: {signature}")


def _block(method: str, marker: str) -> str:
    start = method.index(marker)
    opening = method.index("{", start)
    depth = 0
    for index in range(opening, len(method)):
        if method[index] == "{":
            depth += 1
        elif method[index] == "}":
            depth -= 1
            if depth == 0:
                return method[opening + 1:index]
    raise AssertionError(f"unterminated block: {marker}")


def test_wave2_rejects_an_unlaunchable_scaled_roster_before_objective_or_spawn():
    source = EVENT.read_text(encoding="utf-8")
    initialize = _declaration(source, "private boolean initializeWaveGameplay(")
    objective_start = initialize.index("startWaveObjective(wave, world, core)")
    first_group_spawn = initialize.index("spawnNextWaveGroup(world, core)")
    gate = "if (wave == 2 && !WaveTwoStagePolicy.canStart(scaled))"
    rejection = _block(initialize, gate)

    assert initialize.index(gate) < objective_start < first_group_spawn
    assert "waveSpawnSchedule = List.of()" in rejection
    assert "waveSpawnGroupIndex = 0" in rejection
    assert "activeWave = 0" in rejection
    assert "return false" in rejection


def test_official_wave2_refusal_enters_existing_recovery_and_sandbox_cleans_up():
    source = EVENT.read_text(encoding="utf-8")
    official = _declaration(source, "private void spawnWaveForObjective(")
    sandbox = _declaration(source, "private boolean spawnTestWave(")
    failed_sandbox = _block(sandbox, "if (!started)")

    assert 'recoveryReason = "END_RIFT_WAVE_START_FAILED:" + wave' in official
    assert "forcePhase(EventPhase.RECOVERY_REQUIRED" in official
    assert "clearWaveEntities()" in failed_sandbox
    assert "activeWave = 0" in failed_sandbox
    assert "waveSpawnSchedule = List.of()" in failed_sandbox


def test_wave2_restart_rejects_unlaunchable_scaled_roster_before_replaying_a_group():
    source = EVENT.read_text(encoding="utf-8")
    restore = _declaration(source, "private void restorePersistedWaveSchedule()")
    gate = "if (wave == 2 && !WaveTwoStagePolicy.canStart(scaled))"
    rejection = _block(restore, gate)

    schedule = restore.index("waveSpawnSchedule = wave == 2 ? WaveTwoStagePolicy.split(scaled)")
    first_possible_spawn = restore.index("if (world != null && core != null) spawnNextWaveGroup(world, core)")
    assert restore.index(gate) < schedule < first_possible_spawn
    assert 'recoveryReason = "WAVE_TWO_RESTORE_UNLAUNCHABLE"' in rejection
    assert "waveSpawnSchedule = List.of()" in rejection
    assert "waveSpawnGroupIndex = 0" in rejection
    assert "waveSpawnEntityOffset = 0" in rejection
    assert "forcePhase(EventPhase.RECOVERY_REQUIRED" in rejection
    assert "return" in rejection
