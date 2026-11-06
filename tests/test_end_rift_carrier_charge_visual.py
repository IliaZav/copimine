from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / 'copimine-end-event' / 'src' / 'me' / 'copimine' / 'endevent' / 'CopiMineEndEvent.java'
PACK_ITEM = ROOT / 'resourcepacks' / 'src' / 'assets' / 'copimine' / 'models' / 'item'


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


def test_the_dropped_charge_renders_the_event_model_not_a_vanilla_item():
    # The charge that falls on the ground when the carrier dies was built from
    # a bare Material.NETHER_STAR, so players saw a vanilla star lying in the
    # arena while the same charge in a hand rendered the custom shell. Both
    # paths must select the event model.
    root = SRC.read_text(encoding='utf-8')
    spawn = method(root, 'private void spawnCurrentCarrierCharge(')

    assert 'Material.NETHER_STAR' not in spawn, (
        'a bare vanilla material has no custom_model_data, so the arena shows '
        'an ordinary Nether Star instead of the Rift charge'
    )
    assert 'overlayItem(MODEL_CARRIER_CHARGE' in spawn, (
        'the dropped charge must use the same model id as the carried charge'
    )


def test_every_charge_path_uses_one_model_id():
    root = SRC.read_text(encoding='utf-8')
    spawn = method(root, 'private void spawnCurrentCarrierCharge(')

    # Both the inventory copy and the ground copy exist in production logs, and
    # a divergent id silently renders a different shape for the same object.
    assert spawn.count('MODEL_CARRIER_CHARGE') >= 1
    assert 'end_event_carrier_charge' in spawn, (
        'the model name travels with the id so a broken pack is diagnosable'
    )


def test_the_carrier_charge_model_exists_in_the_resource_pack():
    model = PACK_ITEM / 'end_event_carrier_charge.json'
    assert model.is_file(), 'the model the code selects must ship in the pack'
    text = model.read_text(encoding='utf-8')
    assert '"elements"' in text, 'the model must define real geometry'
    assert '"textures"' in text, 'the model must bind a texture'
