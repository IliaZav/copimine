import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / 'copimine-end-event' / 'src' / 'me' / 'copimine' / 'endevent'


def source() -> str:
    return (SRC / 'CopiMineEndEvent.java').read_text(encoding='utf-8')


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


def test_warden_arrives_with_its_own_emergence_instead_of_popping_in():
    root = source()
    spawn = method(root, 'private boolean ensureNamedRealitySplitTrialRoom(')
    assert 'beginWardenEmerge(actor, spawn)' in spawn, (
        'the Warden must start its emergence sequence the moment it is created'
    )

    emerge = method(root, 'private void beginWardenEmerge(')
    # The body is buried, untouchable and inert while the sequence runs.
    assert 'setInvulnerable(true)' in emerge
    assert 'mob.setAI(false)' in emerge
    assert 'Wave7WardenEmergePolicy.verticalOffset(0)' in emerge
    # Its own authored sound as it starts.
    assert 'Sound.ENTITY_WARDEN_EMERGE' in emerge

    # SUPERSEDED (2026-11-14): this block used to demand the whole lifecycle --
    # `setInvulnerable(false)` and `mob.setAI(true)` -- inside beginWardenEmerge.
    # Neither belongs there any more, and one of them must never come back:
    #   * the rise is driven by advanceWardenEmergence, so the hand-back lives
    #     in that method, not in the method that starts the sequence;
    #   * `mob.setAI(true)` was a real defect. Re-enabling native AI on a
    #     scripted Warden gave it a second target and a second damage owner
    #     after the roar. The wave 7 Warden is driven entirely by the trial
    #     runtime, so the emergence must end with NoAI still in force.
    rise = method(root, 'private boolean advanceWardenEmergence(')
    # Buried and protected for as long as the intro owns the body.
    assert 'warden.setInvulnerable(true)' in rise
    assert 'mob.setAI(false)' in rise
    # ...and handed back, but only the trial loop, when the sequence ends.
    assert 'warden.setInvulnerable(false)' in rise
    assert 'warden.setGravity(true)' in rise
    assert 'mob.setAI(true)' not in emerge and 'mob.setAI(true)' not in rise, (
        're-enabling vanilla AI would create a second target/damage owner; '
        'the scripted Warden must stay NoAI after the roar'
    )
    # The roar and the sculk dust belong to the frame loop that owns the body.
    assert 'ENTITY_WARDEN_ROAR' in rise
    assert 'Particle.SCULK_SOUL' in rise
    assert 'isEventParticleViewer' in rise


def test_warden_emergence_is_bounded_and_eases_out():
    policy = (SRC / 'domain' / 'Wave7WardenEmergePolicy.java').read_text(encoding='utf-8')
    assert 'EMERGE_TICKS' in policy and 'ROAR_TICKS' in policy
    # A bounded sequence: it must terminate, never loop forever.
    assert re.search(r'EMERGE_TICKS\s*=\s*\d+', policy)
    assert 'emerging(int elapsedTicks)' in policy
    assert 'elapsedTicks >= EMERGE_TICKS' in policy or 'elapsedTicks < EMERGE_TICKS' in policy
    # The body decelerates instead of sliding up at a constant rate.
    assert 'easeOut' in policy
    # One roar per emergence, not every tick.
    assert 'elapsedTicks == ROAR_TICKS' in policy


def test_every_warden_spawn_path_starts_the_same_emergence():
    # Two very different factories create the Wave 7 Warden (the named trial and
    # the plain trial room). One of them popping in while the other rises was the
    # original defect, so both must call the same sequence.
    root = source()
    calls = root.count('beginWardenEmerge(')
    assert calls >= 3, 'both spawn factories must start the emergence'
    named = method(root, 'private boolean ensureNamedRealitySplitTrialRoom(')
    plain = method(root, 'private boolean ensureRealitySplitTrialRoom(')
    assert 'beginWardenEmerge(' in named
    assert 'beginWardenEmerge(' in plain
