from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / 'copimine-end-event' / 'src' / 'me' / 'copimine' / 'endevent' / 'CopiMineEndEvent.java'


def method(root: str, signature: str) -> str:
    start = root.index(signature)
    depth = 0
    for index in range(start, len(root)):
        if root[index] == '{':
            depth += 1
        elif root[index] == '}':
            depth -= 1
            if depth == 0:
                return root[start:index + 1]
    raise AssertionError('unbalanced body for ' + signature)


def test_a_wave_with_nobody_left_is_closed():
    # Production sat in "phase=COLLECTING wave=2 event-mobs=14" with online=0
    # for the whole uptime of the server. The objective only advances on player
    # action, so nothing ended the wave, and the next party inherited 14 hostile
    # mobs from a run they had never started.
    root = SRC.read_text(encoding='utf-8')
    tick = method(root, 'private boolean tickCurrentObjective(')

    assert 'abandonWaveIfArenaEmpty(now)' in tick, (
        'the objective tick is the only place that runs for an idle wave'
    )
    guard = method(root, 'private void abandonWaveIfArenaEmpty(')

    assert 'activeWaveParticipants().isEmpty()' in guard
    assert 'WAVE_ABANDON_GRACE_MILLIS' in guard, (
        'without a grace period a single relog would abandon a live wave'
    )


def test_the_empty_arena_must_stay_empty_before_the_wave_is_dropped():
    root = SRC.read_text(encoding='utf-8')
    guard = method(root, 'private void abandonWaveIfArenaEmpty(')

    # An occupied arena, or an arena that becomes occupied again during the
    # grace window, has to reset the clock rather than continue it.
    assert guard.count('waveAbandonEmptySinceMillis = 0L') >= 2, (
        'both the non-wave branch and the occupied branch must clear the clock'
    )
    assert 'waveAbandonEmptySinceMillis = now' in guard, (
        'the clock starts when the arena is first seen empty'
    )
    assert guard.index('waveAbandonEmptySinceMillis <= 0L') < guard.index(
        'WAVE_ABANDON_GRACE_MILLIS'), (
        'arming comes before the deadline comparison'
    )


def test_an_official_attempt_is_never_abandoned_by_this_guard():
    root = SRC.read_text(encoding='utf-8')
    guard = method(root, 'private void abandonWaveIfArenaEmpty(')

    assert 'isOfficialAttemptActive()' in guard, (
        'official attempts have their own roster lifecycle and offline grace'
    )
    assert guard.index('isOfficialAttemptActive()') < guard.index(
        'abandonRunningWave('), (
        'the official guard has to run before the abandon'
    )


def test_abandoning_completes_the_wave_instead_of_just_clearing_mobs():
    # Clearing mobs alone would leave the objective open, so the phase would
    # never advance and the arena would sit on a wave nobody can finish.
    root = SRC.read_text(encoding='utf-8')
    abandon = method(root, 'private boolean abandonRunningWave(')

    assert 'completeWavePhase(waveNumber, reason, idempotencyKey, true)' in abandon, (
        'an abandoned wave must advance the phase like a fought one'
    )
    assert abandon.index('completeWavePhase(') < abandon.index('clearWaveEntities('), (
        'mobs are cleared after the phase moves, never before'
    )
    assert 'EndRiftObjective.isNumberedWave(waveNumber)' in abandon, (
        'only a numbered wave may be completed this way'
    )


def test_abandoning_does_not_double_complete_on_a_repeated_tick():
    # completeWavePhase is idempotent by key, and the clock reset plus the
    # phase move both stop a second run; the key must still be stable.
    root = SRC.read_text(encoding='utf-8')
    abandon = method(root, 'private boolean abandonRunningWave(')

    assert '"abandon-" + waveNumber + "-" + generation' in abandon, (
        'the idempotency key must be derived, not random, or a retry would '
        'complete the same wave twice'
    )
