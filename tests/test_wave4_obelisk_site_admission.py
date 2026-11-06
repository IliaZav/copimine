"""Wave 4 obelisks must not vanish because one candidate site is unusable.

Complaint (7) was "the obelisks never appear, only sound and particles". The real blocks are the
whole feature: startWave4ObeliskAssault asks ObeliskPlacementPolicy.scatter for exactly
profile.obeliskCount() anchors, then validates each one against isSafeCurrentObeliskSite. A refused
anchor is skipped with a warning and NO replacement is offered, so a single blocked cell costs a
whole obelisk - and when every anchor is refused the wave aborts with
END_RIFT_OBELISK_STARTUP_FAILED before a single block is placed.

That is the failure shape the player described: warnings in the log, telegraph sound and particles
around the core, and no obelisk standing anywhere.

Two contracts therefore have to hold together:

- scatter must never hand back fewer anchors than the roster needs, or the shortfall is
  unrecoverable inside the loop;
- a refused anchor must be loud (a warning with the ordinal and the base coordinates) and the
  shortfall must abort the wave explicitly rather than leave it quietly short-handed.

This test pins the shape of that guarantee. It does not claim the sites are reachable in a live
world; only a player session can show the blocks standing.
"""
from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / 'copimine-end-event' / 'src' / 'me' / 'copimine' / 'endevent' / 'CopiMineEndEvent.java'
POLICY = (ROOT / 'copimine-end-event' / 'src' / 'me' / 'copimine' / 'endevent' / 'domain'
          / 'ObeliskPlacementPolicy.java')


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


def plan() -> str:
    assert SRC.is_file(), f'the monolith is missing: {SRC}'
    return method(SRC.read_text(encoding='utf-8'), 'private void startWave4ObeliskAssault(')


def test_the_layout_asks_for_exactly_the_roster_count() -> None:
    # scatter is called with profile.obeliskCount(), and the policy clamps to the same maximum, so
    # the loop below can never be handed fewer anchors than it must seat.
    body = plan()
    assert 'ObeliskPlacementPolicy.scatter(' in body, 'the layout must come from the policy'
    assert 'profile.obeliskCount()' in body.split('ObeliskPlacementPolicy.scatter(')[1][:120], (
        'the roster count must be the requested anchor count'
    )


def test_a_refused_anchor_is_reported_with_its_identity() -> None:
    # A silent skip is what made the failure invisible: the operator saw a wave with too few
    # obelisks and no way to tell which site was rejected.
    body = plan()
    assert 'END_RIFT_OBELISK_PLACEMENT_REFUSED' in body, 'a refused site must be logged'
    warning = body[body.index('END_RIFT_OBELISK_PLACEMENT_REFUSED'):][:400]
    assert 'ordinal' in warning, 'the log must name which obelisk was refused'
    assert 'base' in warning, 'the log must give the base coordinates so the site can be fixed'


def test_a_shortfall_aborts_the_wave_instead_of_hiding_it() -> None:
    # Without this, a wave could start with two of four obelisks and still report success.
    body = plan()
    assert 'planned.size() != profile.obeliskCount()' in body, (
        'the plan must verify it seated every obelisk'
    )
    shortfall = body[body.index('planned.size() != profile.obeliskCount()'):][:400]
    assert 'END_RIFT_OBELISK_STARTUP_FAILED' in shortfall
    assert 'currentWave4StartupFailed = true' in shortfall, (
        'the wave must be marked failed, not merely logged'
    )
    assert 'return' in shortfall, 'a failed plan must not fall through to placement'


def test_the_policy_seats_every_anchor_it_is_asked_for() -> None:
    # Rejection sampling may fail to seat every anchor; the policy then falls back to the
    # deterministic ring so it never returns a short list. Losing that fallback would make the
    # monolith's exact-count loop depend on luck.
    assert POLICY.is_file(), f'the placement policy is missing: {POLICY}'
    text = POLICY.read_text(encoding='utf-8')
    body = method(text, 'public static List<Point> scatter(')
    assert 'points.size() < safeCount' in body, (
        'the policy must detect an under-filled scatter'
    )
    tail = body[body.index('points.size() < safeCount'):]
    assert 'candidates(' in tail, 'the deterministic ring must fill the remainder'
    assert 'MAX_OBELISKS' in body, 'the requested count must be clamped to the supported maximum'


def test_placement_sites_reject_occupied_or_out_of_arena_blocks() -> None:
    # The validator is the only thing standing between the layout and a destroyed build. It must
    # refuse non-passable cells, liquids, gate cells and the core footprint, and it must record the
    # original block data so the arena can be restored.
    root = SRC.read_text(encoding='utf-8')
    body = method(root, 'private boolean isSafeCurrentObeliskSite(')
    assert 'isPassable()' in body, 'an occupied cell must be refused'
    assert 'isLiquid()' in body, 'a liquid surface must be refused'
    assert 'isGateLocation' in body, 'a gate cell must be refused'
    assert 'isCoreBlockPosition' in body, 'the core footprint must be refused'
    assert 'originals.put(' in body, 'the original block data must be captured before mutation'
    assert 'plannedCells::contains' in body, 'two obelisks must not overlap'
