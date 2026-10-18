from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
EVENT_SOURCE = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"


def method_body(source: str, signature: str) -> str:
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
    raise AssertionError(f"unclosed method: {signature}")


def test_echo_resume_requires_wave7_return_admission_in_tick_and_combat_context():
    source = EVENT_SOURCE.read_text(encoding="utf-8")
    tick = method_body(source, "private boolean tickNamedEchoDuel(")
    combat = method_body(source, "private EchoCombatAdmission.Context currentNamedEchoCombatContext(")

    assert "RealitySplitEchoDuelState.ownerMayResume(" in tick
    assert "attemptLifecycle.isCombatAdmitted(cell.owner(), generation)" in tick
    assert "RealitySplitEchoDuelState.ownerMayResume(" in combat
    assert "attemptLifecycle.isCombatAdmitted(pair.owner(), pair.generation())" in combat


def test_echo_repeated_disconnect_reuses_persisted_deadline_in_tick():
    source = EVENT_SOURCE.read_text(encoding="utf-8")
    tick = method_body(source, "private boolean tickNamedEchoDuel(")

    assert "RealitySplitEchoDuelState.ownerOfflineDeadlineMillis(" in tick
    assert "duel.returnDeadlineMillis()" in tick
    assert "offlineDeadline.getAsLong()" in tick
    assert "duel.ownerOffline(System.currentTimeMillis() + 120_000L" not in tick
