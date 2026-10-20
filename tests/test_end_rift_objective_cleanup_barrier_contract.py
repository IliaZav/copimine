from pathlib import Path
import re


ROOT = Path(__file__).resolve().parents[1]
EVENT = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"


def _method(source: str, signature: str) -> str:
    start = source.index(signature)
    opening = source.index("{", start)
    depth = 0
    for index in range(opening, len(source)):
        if source[index] == "{":
            depth += 1
        elif source[index] == "}":
            depth -= 1
            if depth == 0:
                return source[opening + 1:index]
    raise AssertionError(f"unterminated method: {signature}")


def test_wave_objective_cleanup_preserves_wave7_ownership_if_barrier_restore_fails():
    source = EVENT.read_text(encoding="utf-8")
    cleanup = _method(source, "private boolean clearWaveObjectiveState()")

    restore_guard = re.search(
        r'if\s*\(\s*!\s*clearRealitySplitBarriers\(\s*"wave-objective-reset"\s*\)\s*\)\s*return\s+false\s*;',
        cleanup,
    )
    assert restore_guard, (
        "failed Wave 7 barrier restoration must propagate false from objective cleanup"
    )

    guard_offset = restore_guard.start()
    ownership_cleanup = (
        'clearRealitySplitTrialEntities("wave-objective-reset");',
        "realitySplitTrialController.clear();",
        'clearLegacyWave7BarrierArtifacts("wave-objective-reset");',
        "finalSealBarrierHold = false;",
    )
    for operation in ownership_cleanup:
        operation_offset = cleanup.index(operation)
        assert guard_offset < operation_offset, (
            f"{operation} must remain untouched until Wave 7 barrier restoration succeeds"
        )
