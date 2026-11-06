"""The supplied boss animations must actually name bones the geometry declares.

Complaint (1) was "the boss has no model from my assets". The geometry and all seven clips ship in
the client jar, and ChameleonGuardianGeometry throws when the resource is missing - so a missing
file fails loudly. A MISNAMED bone does not: the clip loads, the loader finds no bone to drive, and
the animation plays into nothing. The boss stands perfectly still and looks broken while every
resource is present and every check passes.

That is the silent failure this test exists for. It reads the geometry the client actually ships,
collects the declared bone names, then walks every animation and asserts each bone it addresses is
one the geometry provides. A rename on either side is caught here instead of on the player's screen.

It also pins the envelope the renderer assumes: the source texture is 16x16 while the carrier layer
is 128x128, and the renderer maps one to the other. Losing that relationship would stretch every
face of the model.
"""
from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CLIENT = ROOT / 'tools' / 'minecraft-26.3' / 'client' / 'src' / 'main' / 'java' / 'me' / 'copimine' / 'client'
ASSETS = (ROOT / 'CopiMineClient' / 'src' / 'main' / 'resources' / 'assets' / 'copimineclient'
          / 'models' / 'entity' / 'end_rift_guardian')

EXPECTED_CLIPS = {
    'idle', 'hurt', 'dying', 'running', 'swipe', 'udar_iz_grudi',
}


def geometry() -> dict:
    path = ASSETS / 'geometry.json'
    assert path.is_file(), f'the supplied geometry is missing: {path}'
    return json.loads(path.read_text(encoding='utf-8'))


def declared_bones() -> set[str]:
    document = geometry()
    definitions = document['minecraft:geometry']
    assert len(definitions) == 1, 'the renderer expects exactly one geometry definition'
    return {bone['name'] for bone in definitions[0]['bones']}


def clip_files() -> list[Path]:
    directory = ASSETS / 'animations'
    assert directory.is_dir(), f'the supplied animations are missing: {directory}'
    files = sorted(directory.glob('*.json'))
    assert files, 'the boss ships no animation clips at all'
    return files


def clip_id(path: Path) -> str:
    return path.name.removesuffix('.json').removesuffix('.animation')


def test_every_clip_the_renderer_loads_is_present() -> None:
    # The renderer loads a fixed set; a missing member means a silently absent animation.
    present = {clip_id(path) for path in clip_files()}
    missing = EXPECTED_CLIPS - present
    assert not missing, f'clips the renderer expects are missing: {sorted(missing)}'


def test_every_clip_addresses_only_declared_bones() -> None:
    # This is the whole point: a misnamed bone loads without error and animates nothing.
    bones = declared_bones()
    assert len(bones) >= 10, f'the supplied geometry looks truncated: {len(bones)} bones'
    for path in clip_files():
        document = json.loads(path.read_text(encoding='utf-8'))
        animations = document.get('animations')
        assert isinstance(animations, dict) and animations, f'{path.name} declares no animation'
        driven: set[str] = set()
        for clip in animations.values():
            for bone in (clip.get('bones') or {}):
                driven.add(bone)
        assert driven, f'{path.name} drives no bone at all'
        unknown = driven - bones
        assert not unknown, (
            f'{path.name} drives bones the geometry does not declare: {sorted(unknown)}. '
            'Such an animation loads cleanly and animates nothing.'
        )


def test_clips_have_a_positive_length_and_a_name() -> None:
    for path in clip_files():
        document = json.loads(path.read_text(encoding='utf-8'))
        for name, clip in document['animations'].items():
            assert isinstance(name, str) and name, f'{path.name} has an unnamed animation'
            length = clip.get('animation_length')
            assert isinstance(length, (int, float)) and length > 0, (
                f'{path.name}/{name} has no positive animation_length'
            )


def test_the_geometry_declares_the_source_and_carrier_texture_sizes() -> None:
    document = geometry()
    description = document['minecraft:geometry'][0]['description']
    assert description['texture_width'] == 16 and description['texture_height'] == 16, (
        'the supplied source texture is 16x16; the renderer rescales it to the carrier'
    )
    assert description['visible_bounds_height'] > 0, 'the model needs a visible envelope'

    model_data = (CLIENT / 'UserEndBossModelData.java').read_text(encoding='utf-8')
    assert 'SOURCE_TEXTURE_WIDTH = 16' in model_data
    assert 'SOURCE_TEXTURE_HEIGHT = 16' in model_data
    assert 'TEXTURE_WIDTH = 128' in model_data, 'the carrier layer stays 128 wide'
    assert 'TEXTURE_HEIGHT = 128' in model_data
    assert 'end_rift_user_boss.png' in model_data, (
        'the boss must use the texture supplied by the player'
    )


def test_a_missing_resource_fails_loudly_instead_of_rendering_nothing() -> None:
    # A silent null stream would ship a boss made of nothing. The guard has to be tied to the
    # stream read itself: this class throws IllegalStateException in four other places, so a bare
    # 'IllegalStateException is present' assertion survives the guard being deleted. The block
    # below is the one that matters.
    model_data = (CLIENT / 'UserEndBossModelData.java').read_text(encoding='utf-8')
    assert 'getResourceAsStream(RESOURCE)' in model_data
    assert 'end_rift_guardian/geometry.json' in model_data

    start = model_data.index('getResourceAsStream(RESOURCE)')
    block = model_data[start:start + 400]
    assert 'if (stream == null)' in block, (
        'the read must be followed by a null check on the stream'
    )
    assert 'Missing supplied End Rift geometry resource' in block, (
        'the null check must throw a message that names the missing resource'
    )
    assert 'return null' not in block, 'a missing resource must not degrade to a null document'
