"""The Wave 2 mark must use the game's own Enderman shader, and it must work on a bare client.

Two separate player requirements live in this area, and both are easy to break silently.

THE SHADER. The player asked for "the built-in Enderman shader, the one you see when you stand
inside an Enderman in spectator mode", applied to the marked Wave 2 player. In 26.3 that effect is
the vanilla post shader minecraft:invert: GameRenderer.checkEntityPostEffect applies exactly that
identifier to an Enderman. It must NOT be a shaderpack, and it must not need Iris, OptiFine or
Sodium - the mod has to put the effect in the frame itself.

THE MENU. The narcotics shaderpacks are event content. They were being written into shaderpacks/
itself, which is the exact folder Iris scans for its shaderpack menu, so a player could open the
menu, see the drug effects by name and switch them on outside any wave. They must stay withdrawn.

These are source contracts rather than compiled probes because the classes involved pull in
Minecraft render types and the Iris API; their invariants live in identifiers and control flow.
"""
from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CLIENT = ROOT / 'tools/minecraft-26.3/client/src/main/java/me/copimine/client'

POST_PROCESS = CLIENT / 'ClientPostProcessController.java'
POST_PROCESS_RUNTIME = CLIENT / 'ClientPostProcessRuntime.java'
ENDERMAN = CLIENT / 'EndermanMarkShaderController.java'
RENDERER = CLIENT / 'EndEventPlayerVisualRenderer.java'
EXPORTER = CLIENT / 'ShaderpackExporter.java'
PROTOCOL = CLIENT / 'ClientBridgeProtocol.java'


def read(path: Path) -> str:
    assert path.is_file(), f'missing client source: {path}'
    return path.read_text(encoding='utf-8')


def test_enderman_effect_is_the_vanilla_invert_post_shader() -> None:
    """The effect must be minecraft:invert, the identifier the game itself uses for Endermen."""
    source = read(POST_PROCESS)
    assert re.search(
        r'"ENDERMAN"\s*,\s*Identifier\.withDefaultNamespace\("invert"\)', source
    ), 'the Enderman effect must map to the vanilla invert post shader'
    # It must be reachable through the ordinary effect table, not a special case elsewhere.
    assert 'EFFECT_POST_PROCESSORS.get(' in source


def strip_comments(java: str) -> str:
    """Drop // and /* */ comments so a contract checks code, not prose about the code."""
    without_block = re.sub(r'/\*.*?\*/', '', java, flags=re.S)
    return re.sub(r'//[^\n]*', '', without_block)


def test_enderman_shader_needs_no_iris_or_shaderpack() -> None:
    """The vanilla post shader goes into render state, so no shaderpack dependency may appear."""
    controller = strip_comments(read(ENDERMAN))
    for forbidden in ('IrisShaderpackRuntime', 'ShaderpackRegistry', 'ShaderpackExporter', 'Iris'):
        assert forbidden not in controller, (
            f'the Enderman mark shader must not depend on {forbidden}: it has to work on a bare client'
        )
    # The controller must ask for the Enderman effect by name. Requesting any other effect would
    # still compile and still show *a* screen effect, which is exactly the kind of regression that
    # a "does it apply something" check would miss.
    assert re.search(r'EFFECT_ID\s*=\s*"ENDERMAN"', controller), (
        'the controller must request the ENDERMAN effect, not some other visual'
    )
    assert 'postProcess.apply(effectId' in controller, (
        'the controller must apply the selected vanilla effect or introductory fade stage'
    )
    runtime = read(POST_PROCESS_RUNTIME)
    assert 'requestedPostEffects' in runtime, (
        'the effect must be submitted through vanilla requestedPostEffects render state'
    )


def test_enderman_shader_follows_the_hunt_mark_only() -> None:
    """Applied while the mark is live, cleared otherwise - never a permanent screen effect."""
    controller = read(ENDERMAN)
    assert 'public void update(boolean marked)' in controller
    # The clear path must exist and must run when the mark is gone.
    assert 'postProcess.clear()' in controller
    # A missing controller must fail closed rather than throw on a stripped client.
    assert 'postProcess == null' in controller

    renderer = read(RENDERER)
    assert 'endermanMarkShader()' in renderer, 'the renderer must drive the mark shader'
    # The mark kind decides it: only HUNT carries the Enderman effect.
    assert 'Kind.HUNT' in renderer, 'only the Wave 2 hunt mark may enable the Enderman shader'
    # Dying or leaving the world must withdraw it.
    dead_branch = renderer.split('isDeadOrDying()')[1].split('return;')[0]
    assert 'update(false)' in dead_branch, (
        'leaving the world or dying must withdraw the mark shader'
    )


def test_mark_shader_has_its_own_controller_slot() -> None:
    """A narcotics effect must never share the slot, or one would clear the other."""
    protocol = read(PROTOCOL)
    assert 'ENDERMAN_MARK_SHADER' in protocol
    assert 'EndermanMarkShaderController endermanMarkShader()' in protocol
    block = protocol.split('ENDERMAN_MARK_SHADER')[1].split(';')[0]
    assert 'new ClientPostProcessController()' in block, (
        'the mark shader needs its own controller instance'
    )


def test_narcotics_packs_are_not_published_to_the_shaderpack_menu() -> None:
    """Nothing may be written into the top level of shaderpacks/, which Iris lists."""
    exporter = read(EXPORTER)
    # The runtime copy used to be written there. It must be withdrawn instead.
    assert 'removeIfDifferent(runtimeTarget' in exporter, (
        'the published shaderpack copy must be withdrawn, not written'
    )
    assert 'writeIfChanged(runtimeTarget' not in exporter, (
        'writing directly into shaderpacks/ publishes the pack in the Iris menu'
    )
    # The private subfolder is what the runtime switch reaches.
    assert 'runtimeShaderpackDir.resolve("CopiMine")' in exporter, (
        'event packs belong in the private CopiMine subfolder'
    )
