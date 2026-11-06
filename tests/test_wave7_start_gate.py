"""Wave 7 must refuse loudly on an empty room instead of starting a half-dead wave.

Complaint (11) was "the wave never starts". Wave 7 drives per-participant trials, so it cannot run
without at least one player; the start gate therefore refuses an empty roster and logs
WAVE_INSPECTION_REFUSED reason=empty-room-roster BEFORE any generation is consumed. That is the
correct behaviour, and it is also the exact symptom the player saw from the console - which is why
it must stay loud and must stay ahead of every side effect.

The order matters as much as the check: the roster is computed first, the empty-room guard returns
before preflightNextGeneration, before cancelSessionTasks, before the encounter controller restores,
and before initializeWaveGameplay. A guard placed later would burn a generation, cancel live tasks
and then abort, leaving the event in a worse state than before the command.

This test pins that ordering, the participant predicate behind it, and the failure path after
initialization. It cannot show the Warden emerging on a live client - only a player session can.
"""
from __future__ import annotations

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


def source() -> str:
    assert SRC.is_file(), f'the monolith is missing: {SRC}'
    return SRC.read_text(encoding='utf-8')


def start_gate() -> str:
    # The wave-inspection start path: it owns the empty-room guard.
    root = source()
    marker = 'if (wave == 7 && roster.isEmpty())'
    start = root.rindex('private boolean', 0, root.index(marker))
    return method(root, root[start:root.index('{', start) + 1])


def test_wave_seven_refuses_an_empty_room_before_any_side_effect() -> None:
    body = start_gate()
    guard = body.index('if (wave == 7 && roster.isEmpty())')
    # Everything below must come AFTER the guard, or the command mutates the encounter and then
    # bails out, which is worse than refusing.
    for later in ('preflightNextGeneration', 'cancelSessionTasks',
                  'encounterController.restore', 'initializeWaveGameplay'):
        assert body.index(later) > guard, f'{later} must run after the empty-room guard'


def test_the_refusal_is_loud_and_names_the_requirement() -> None:
    body = start_gate()
    guard = body[body.index('if (wave == 7 && roster.isEmpty())'):][:220]
    assert 'WAVE_INSPECTION_REFUSED' in guard, 'an empty room must not be refused silently'
    assert 'empty-room-roster' in guard, 'the log must name the real reason'
    assert 'required=1' in guard, 'the log must state how many participants are needed'
    assert 'return false' in guard, 'the refusal must not fall through to the initializer'


def test_the_roster_uses_the_physical_participant_predicate() -> None:
    # A spectating or dead player cannot run trials, so the roster must come from the physical
    # predicate rather than a raw online-player count.
    body = start_gate()
    roster = body[body.index('Set<UUID> roster ='):][:300]
    assert 'Bukkit.getOnlinePlayers()' in roster
    assert 'isPhysicallyEligibleWaveParticipant(player, true)' in roster, (
        'creative operators must be admitted, so the capture flag is passed explicitly'
    )

    root = source()
    eligible = method(root, 'private boolean isPhysicallyEligibleWaveParticipant(Player player, boolean captureTestRoster)')
    for guard in ('player.isOnline()', '!player.isDead()', 'player.getHealth() > 0.0D',
                  'GameMode.SPECTATOR', 'isArenaLocation(player.getLocation())'):
        assert guard in eligible, f'the participant predicate must enforce {guard}'


def test_a_failed_initialisation_clears_the_wave_and_reports_it() -> None:
    body = start_gate()
    assert 'boolean started = initializeWaveGameplay(' in body
    failure = body[body.index('if (!started) {'):]
    assert 'clearWaveEntities()' in failure, 'a half-started wave must be cleaned up'
    assert 'activeWave = 0' in failure, 'the event must not stay on a wave that never started'
    assert 'WAVE_INSPECTION_REFUSED' in failure
    assert 'reason=wave-mechanics-initialization-failed' in failure
    assert 'return started' in body, 'the caller must receive the real outcome'


def test_the_sandbox_keeps_a_playable_roster_and_its_ai() -> None:
    # /cmend wave spawn is a playable sandbox: it must ask the shared initializer for a real
    # sandbox roster (not an inspection-only one) and must not freeze the mobs.
    body = start_gate()
    call = body[body.index('initializeWaveGameplay('):][:220]
    assert 'true, false, true' in call, (
        'the playable sandbox passes captureTestRoster=true, combat=false, sandbox=true'
    )
