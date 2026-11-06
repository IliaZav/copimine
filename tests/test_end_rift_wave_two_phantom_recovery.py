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


def spawn_block(root: str) -> str:
    start = root.index('if (waveSpawnWave == 2 && groupNumber >= WaveTwoStagePolicy.FIRST_LATE_GROUP')
    return root[start:start + 2600]


def test_a_subwave_that_spawned_without_phantoms_owes_them_later():
    # The phantom layout only resolves once a participant is marked AND stands
    # on a position that passes isCurrentWaveTwoPhantomPosition. A party that
    # cleared the opening groups before that settled spawned the late group with
    # "action=spawn-without-phantom" and never tried again, so the whole wave ran
    # with no phantoms and no veil. The failure has to become a debt.
    root = SRC.read_text(encoding='utf-8')
    block = spawn_block(root)

    assert 'waveTwoPhantomActivationPendingGroup = groupNumber' in block, (
        'a skipped activation must be remembered, not silently dropped'
    )
    assert block.index('WAVE_TWO_PHANTOM_ACTIVATION_UNAVAILABLE') < block.index(
        'waveTwoPhantomActivationPendingGroup = groupNumber'), (
        'the debt is recorded on the unavailable path, after the diagnostic'
    )


def test_the_pending_activation_is_retried_from_the_hunt_tick():
    root = SRC.read_text(encoding='utf-8')
    tick = method(root, 'private void tickCurrentHuntObjective(')

    assert 'retryPendingWaveTwoPhantomActivation(now)' in tick, (
        'something has to drain the debt; the spawn path runs only once per subwave'
    )
    # The mark update must run first: the retry needs the mark to exist.
    assert tick.index('updateMarkedTargetObjective(now)') < tick.index(
        'retryPendingWaveTwoPhantomActivation(now)'), (
        'marks are what a phantom layout is built from, so they come first'
    )


def test_the_retry_cannot_outlive_its_wave():
    root = SRC.read_text(encoding='utf-8')
    retry = method(root, 'private void retryPendingWaveTwoPhantomActivation(')

    assert 'activeWave != 2 || phase != EventPhase.WAVE_2' in retry, (
        'a pending group id from wave 2 must never fire during a later wave'
    )
    assert 'waveTwoPhantomActivationPendingGroup = 0' in retry, (
        'leaving wave 2 has to clear the debt'
    )
    assert 'waveTwoPhantomRetryAtMillis' in retry, (
        'an unbounded retry would call the layout engine every single tick'
    )
    assert 'waveTwoMarkedParticipants.isEmpty()' in retry, (
        'with nobody marked no layout can exist, so the debt has to survive'
    )
    assert 'pendingGroup > waveSpawnSchedule.size()' in retry, (
        'a group id past the schedule is stale and must not index the list'
    )


def test_the_retry_does_not_double_start_an_activation():
    root = SRC.read_text(encoding='utf-8')
    retry = method(root, 'private void retryPendingWaveTwoPhantomActivation(')

    assert 'waveTwoPhantomWarningTask != null' in retry, (
        'a warning already scheduled owns the activation'
    )
    assert 'Phase.WARNING' in retry and 'Phase.TARGETABLE' in retry, (
        'both in-flight phases must block a second start'
    )
    assert retry.index('waveTwoPhantomWarningTask != null') < retry.index(
        'tryBeginWaveTwoPhantomActivation('), (
        'the in-flight guard has to run before the attempt'
    )


def test_a_recovered_activation_clears_the_debt():
    root = SRC.read_text(encoding='utf-8')
    retry = method(root, 'private void retryPendingWaveTwoPhantomActivation(')
    tail = retry[retry.index('tryBeginWaveTwoPhantomActivation('):]

    assert 'waveTwoPhantomActivationPendingGroup = 0' in tail, (
        'a successful retry must not keep retrying forever'
    )
    assert 'WAVE_TWO_PHANTOM_ACTIVATION_RECOVERED' in tail, (
        'the recovery has to be visible in the log to be diagnosable'
    )
