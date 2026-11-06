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


def test_the_carrier_charge_is_bound_to_the_carrier_not_the_floor():
    # Players reported the charge lying flat on the arena floor instead of
    # hanging on the elite Enderman that carried it. A dropped Item with gravity
    # settles the moment the carrier moves on, so the drop must be weightless
    # and something must actively hold it on the mob.
    root = SRC.read_text(encoding='utf-8')
    spawn = method(root, 'private void spawnCurrentCarrierCharge(')

    assert 'value.setGravity(false)' in spawn, (
        'with gravity on the charge falls out of the carrier and lies in the arena'
    )
    assert 'value.setGravity(true)' not in spawn, (
        'gravity true would let the charge drop away from the carrier again'
    )

    tick = method(root, 'private void tickCurrentCarrierObjective(')
    assert 'carrierAnchor' in tick, (
        'the tick must compute an anchor above the living carrier'
    )
    assert 'currentCarrierState.carrier()' in tick, (
        'the anchor is derived from the carrier the policy selected'
    )
    assert 'teleportCombatEntity(liveChargeOnCarrier, carrierAnchor)' in tick, (
        'the charge has to be moved onto the carrier every tick, through the '
        'combat teleport permit so the anti-escape guard does not revert it'
    )


def test_the_charge_anchor_sits_above_the_carrier_body():
    root = SRC.read_text(encoding='utf-8')
    tick = method(root, 'private void tickCurrentCarrierObjective(')
    anchor = tick[tick.index('carrierAnchor'):tick.index('carrierAnchor') + 260]

    # Above the head keeps the model clear of the mob's own body; 1.35 blocks is
    # the height the carried-charge path already uses for the player.
    assert 'add(0.0D, 1.35D, 0.0D)' in anchor, (
        'the charge must float above the carrier, clear of its body'
    )


def test_charge_reposition_does_not_thrash_the_entity():
    root = SRC.read_text(encoding='utf-8')
    tick = method(root, 'private void tickCurrentCarrierObjective(')

    # Teleporting every tick regardless of distance fights the client's
    # interpolation and makes the item stutter. Only correct real drift.
    assert 'distanceSquared(carrierAnchor) > 0.0625D' in tick, (
        'only a real drift may trigger a teleport, otherwise the charge jitters'
    )
