"""The prisoner exclusion must never block the capture that creates the prisoner.

Complaint (10) wanted the sphere to stop attacking a sealed player, and the fix made
isCombatTarget refuse the prisoner. But the SAME predicate decides who may be captured in the first
place: attemptRitualPrisonerCapture marks each candidate with isCombatTarget and filters the winner
with it too. If the exclusion ever became true before a prisoner exists, nobody could ever be
captured and wave 6 would sit in WAITING_FOR_PRISONER forever - the exact failure the fix was
meant to prevent, arriving from the other side.

The two predicates are therefore load bearing in opposite directions, and this test pins both:
the exclusion keys off an existing prisoner, and it lifts the moment the prison breaks.
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


def test_the_exclusion_requires_an_existing_prisoner() -> None:
    # isSealedRitualPrisoner must be false while ritualPrisonerId is still null, otherwise the
    # capture path can never produce its first prisoner.
    root = source()
    sealed = method(root, 'private boolean isSealedRitualPrisoner(Player player)')
    assert 'isCurrentRitualPrisoner' in sealed, (
        'the exclusion must key off the CURRENT prisoner, not merely on wave 6 being active'
    )

    current = method(root, 'private boolean isCurrentRitualPrisoner(Player player)')
    assert 'prisonerId != null' in current, (
        'without the null guard an unassigned prisoner would compare equal to a null id'
    )
    assert 'ritualPrisonerId()' in current, (
        'the current prisoner must come from the stored id that capture assigns'
    )


def test_capture_admits_the_very_player_the_exclusion_will_later_refuse() -> None:
    # This is the ordering that makes the fix safe: capture runs first, and only once it stores the
    # id does isCurrentRitualPrisoner start answering true.
    root = source()
    capture = method(root, 'private void attemptRitualPrisonerCapture(long now)')

    assert 'isCombatTarget(player)' in capture, (
        'capture still filters candidates through isCombatTarget; that is expected and safe only '
        'because no prisoner exists yet'
    )
    assert 'isSealedRitualPrisoner' not in capture, (
        'capture must not consult the exclusion: it would deadlock the first capture'
    )


def test_the_exclusion_lifts_when_the_prison_breaks() -> None:
    root = source()
    sealed = method(root, 'private boolean isSealedRitualPrisoner(Player player)')
    assert '!wave6PrisonBroken' in sealed, (
        'a freed player must become targetable again, or the rescue ends in an untouchable ghost'
    )

    breaker = method(root, 'private void breakRitualPrison()')
    assert 'wave6PrisonBroken = true' in breaker, (
        'breaking the prison is what lifts the exclusion'
    )
    assert 'releaseRitualPrisoner' in breaker, (
        'the break must actually release the player, not only flip the flag'
    )


def test_the_prison_break_stops_hostile_casts_before_releasing() -> None:
    # Order inside the break: stop the casters first, place the player second. Reversed, a freed
    # player lands on the floor with a live major spell still tracking the shell.
    root = source()
    breaker = method(root, 'private void breakRitualPrison()')
    assert breaker.index('clearRitualSpellRuntimeCast') < breaker.index('releaseRitualPrisoner'), (
        'the ritual cast must be cleared before the prisoner is released'
    )
    assert 'ritualPrisonReleasePending' in breaker, (
        'a failed release must be retried rather than silently dropped'
    )
