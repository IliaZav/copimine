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


def test_clearing_a_wave_one_pack_owes_a_replacement_pack():
    # "Killing the mobs does nothing": wave 1 advanced only on a carrier
    # delivery, so a party that killed every Enderman stood in a silent room
    # with no charge to fight and no way forward. The death hook must open a
    # debt so resolveWaveOneReinforcements() can release a new pack.
    root = SRC.read_text(encoding='utf-8')
    body = method(root, 'public void onOwnedEntityDeath(EntityDeathEvent event)')

    assert 'commitWaveOneReinforcement' in body, (
        'a cleared wave-one pack must owe a replacement pack'
    )
    guard = body.index('commitWaveOneReinforcement')
    window = body[max(0, guard - 2000):guard]
    # Look at the guard CONDITION, not the prose around it: the comment above
    # the branch legitimately talks about wave 1 without being executable code.
    code = [line for line in window.splitlines() if not line.strip().startswith('//')]
    assert any('activeWave == 1' in line for line in code), (
        'the debt belongs to wave 1 only'
    )
    assert 'countLiveWaveEntitiesForWave(1) <= 0' in window, (
        'only the LAST body of a pack may open a debt, otherwise every corpse queues a pack'
    )


def test_the_wave_one_carrier_is_not_a_pack_clear_signal():
    # The carrier already drops its own charge on death; counting it as a
    # cleared pack would double-book the exchange.
    root = SRC.read_text(encoding='utf-8')
    body = method(root, 'public void onOwnedEntityDeath(EntityDeathEvent event)')
    guard = body.index('commitWaveOneReinforcement')
    window = body[max(0, guard - 2000):guard]

    assert 'carrierItself' in window
    assert 'currentCarrierState.carrier().equals(entity.getUniqueId())' in window


def test_wave_one_still_needs_three_real_deliveries():
    # Feeding the fight must never shorten the wave.
    root = SRC.read_text(encoding='utf-8')
    body = method(root, 'private boolean currentObjectiveProgressComplete(int objectiveWave)')

    assert 'REQUIRED_CARRIER_DELIVERIES' in body
    assert 'currentCarrierCharges >= EndRiftObjective.REQUIRED_CARRIER_DELIVERIES' in body
