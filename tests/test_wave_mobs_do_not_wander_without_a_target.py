import re
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


def test_a_wave_mob_without_a_target_holds_instead_of_wandering():
    # Restoring native AI is necessary (it is what lets a mob walk and attack),
    # but it also re-installs the vanilla random-stroll goal. With nothing to
    # chase an Enderman picks its own destination and walks off - the reported
    # "mobs run back and forth". The no-target branch must stop the mob.
    root = SRC.read_text(encoding='utf-8')
    body = method(root, 'private void tickWaveMobAi()')

    assert 'holdWaveMobWithoutTarget' in body, (
        'the no-target branch must actively hold the mob'
    )

    # Find the guard that handles the "target is null" case and confirm the
    # hold happens BEFORE the loop continues, not on some later tick.
    guard = body.index('target == null || !isWaveTargetAllowed(entity, target)')
    window = body[guard:guard + 600]
    assert 'holdWaveMobWithoutTarget' in window, (
        'the hold must run in the same no-target branch'
    )
    assert window.index('holdWaveMobWithoutTarget') < window.index('continue'), (
        'the hold must precede the continue'
    )


def test_the_hold_stops_pathing_and_does_not_move_the_mob():
    root = SRC.read_text(encoding='utf-8')
    body = method(root, 'private void holdWaveMobWithoutTarget(Mob mob)')

    assert 'stopPathfinding' in body, 'a stale path would keep the mob walking'
    assert 'setVelocity' in body, 'residual momentum must be cleared'
    # It must never assign a destination: holding means standing still.
    assert 'moveTo' not in body, 'a hold must not request a new path'
    assert 'setTarget' not in body, 'a hold must not invent a target'


def test_wandering_is_not_fought_by_disabling_ai():
    # Disabling AI would also disable knockback and damage reactions. The fix
    # must be positional, not an AI switch.
    root = SRC.read_text(encoding='utf-8')
    body = method(root, 'private void holdWaveMobWithoutTarget(Mob mob)')

    assert 'setAI(false)' not in body
    assert 'setAware(false)' not in body
