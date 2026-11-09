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


def test_loading_a_charge_is_a_fifteen_second_hold_not_a_click():
    # The player asked for the Core charge to be loaded by holding for fifteen
    # seconds. A single right-click used to spend one of the three deliveries,
    # so a misclick cost the wave a pack with no way back.
    root = SRC.read_text(encoding='utf-8')

    assert 'CORE_CHARGE_HOLD_MILLIS = 15_000L' in root, (
        'the hold duration is part of the requested mechanic'
    )
    interact = method(root, 'private boolean onCarrierChargeCoreDelivery(')
    assert 'beginCoreChargeHold(player)' in interact, (
        'the right-click must start the channel rather than deliver'
    )
    assert 'deliverCurrentCarrierCharge(player);' not in interact, (
        'delivering on the click would bypass the hold entirely'
    )


def test_the_hold_shows_a_filling_ring_while_it_runs():
    root = SRC.read_text(encoding='utf-8')
    ring = method(root, 'private void renderCoreChargeHoldRing(')

    assert 'CORE_CHARGE_RING_SEGMENTS' in ring
    # The source spells the glyphs as Java escapes, so the contract is that
    # both a filled and an empty circle are emitted.
    assert 'u25CF' in ring and 'u25CB' in ring, (
        'the ring needs a filled and an empty glyph to read as filling'
    )
    assert 'sendActionBar' in ring, 'the ring is drawn on the action bar'
    assert 'percent' in ring, 'the numeric percentage keeps the ring readable'


def test_the_ring_only_redraws_when_it_actually_changes():
    # The tick runs twenty times a second; redrawing an identical ring would
    # send an action-bar packet every tick for no visible change.
    root = SRC.read_text(encoding='utf-8')
    ring = method(root, 'private void renderCoreChargeHoldRing(')

    assert 'filled == coreChargeHoldLastSegment' in ring, (
        'an unchanged ring must not be resent'
    )
    assert ring.index('filled == coreChargeHoldLastSegment') < ring.index('sendActionBar'), (
        'the early return has to come before the packet is built'
    )


def test_the_hold_is_cancelled_when_it_stops_being_valid():
    root = SRC.read_text(encoding='utf-8')
    tick = method(root, 'private void tickCoreChargeHold(')

    for reason in ('offline', 'holder-changed', 'charge-missing', 'left-core-range'):
        assert '"' + reason + '"' in tick, (
            'the channel must drop for ' + reason + ' without spending a delivery'
        )
    assert 'deliverCurrentCarrierCharge(player)' in tick, (
        'the delivery happens only once the hold completes'
    )
    assert tick.index('CORE_CHARGE_HOLD_MILLIS') < tick.index('deliverCurrentCarrierCharge('), (
        'the duration check has to gate the delivery'
    )
    # Leaving the Core must drop the channel; otherwise a player could start
    # the hold at the Core and finish it from anywhere in the arena.
    assert 'distanceSquared(core) > 6.25D' in tick, (
        'the hold is only valid while the player still stands at the Core'
    )


def test_completing_the_hold_clears_the_state_before_delivering():
    # deliverCurrentCarrierCharge re-enters the carrier state machine; leaving
    # the hold id set would make the next tick cancel an attempt that has just
    # succeeded, and the player would see "interrupted" after a success.
    root = SRC.read_text(encoding='utf-8')
    tick = method(root, 'private void tickCoreChargeHold(')
    body = tick[tick.index('elapsed >= CORE_CHARGE_HOLD_MILLIS'):]

    assert body.index('coreChargeHoldPlayer = null') < body.index('deliverCurrentCarrierCharge('), (
        'the hold must be cleared before the delivery runs'
    )


def test_a_disconnect_drops_the_hold():
    root = SRC.read_text(encoding='utf-8')
    quit_body = method(root, 'public void onPlayerQuit(')

    assert 'cancelCoreChargeHold(uuid, "disconnect")' in quit_body, (
        'a player who leaves mid-channel must not keep the hold alive'
    )
    assert quit_body.index('releaseCurrentCarrierHolder(') < quit_body.index('cancelCoreChargeHold('), (
        'the holder is released first, then the hold that referenced it'
    )
