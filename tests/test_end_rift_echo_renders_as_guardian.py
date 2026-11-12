"""Phase 5 echo copies and decoys must wear the authored Guardian body.

The server binds them with their own visual ids, END_RIFT_ECHO_REPLICA_V1 and
END_RIFT_ECHO_DECOY_V1, so that identity stays distinct on the wire. The client
decides what a visual id renders as, and it did it in three separate places:

  EndEventTextureCatalog.textureForVisual   - which texture
  EndermanRendererSelection.selectVisual    - which render kind
  EndermanEntityRendererMixin               - whether the Guardian model is used

None of the three knew the two new ids. The catalog returned null, the
exhaustive switch fell to vanilla(), and the mixin's guardian flag was false, so
an echo rendered as a plain vanilla Enderman. The deception is the whole point
of the mechanic: showing a vanilla mob instead of the authored god-body both
hides the model and tells the player instantly which one is real.

These tests read the real client sources and hold all three sites, because a fix
to any two of them still leaves vanilla on screen.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CLIENT = ROOT / "tools/minecraft-26.3/client/src/main/java/me/copimine/client"

ECHO_IDS = ("END_RIFT_ECHO_REPLICA_V1", "END_RIFT_ECHO_DECOY_V1")


def source(name):
    return (CLIENT / name).read_text(encoding="utf-8")


def test_the_texture_catalog_aliases_both_echo_ids_to_the_guardian_skin():
    text = source("EndEventTextureCatalog.java")
    for visual_id in ECHO_IDS:
        assert f'"{visual_id}"' in text, (
            visual_id + " must be known to the texture catalog, or textureForVisual"
            " returns null and the mixin has nothing to apply")
    # The alias has to land on the Guardian skin, not merely be mentioned.
    alias_block = text[text.index("END_RIFT_GUARDIAN".__str__()):]
    assert "END_RIFT_ECHO_REPLICA_V1" in alias_block or "END_RIFT_ECHO_DECOY_V1" in alias_block, (
        "the echo ids must be aliased alongside the Guardian resolution")


def test_the_renderer_selection_switch_has_an_arm_for_both_echo_ids():
    text = source("EndermanRendererSelection.java")
    for visual_id in ECHO_IDS:
        arm = f'case "{visual_id}" ->'
        assert arm in text, (
            visual_id + " needs its own case arm: the switch is exhaustive and"
            " ends in default -> vanilla(), so a missing arm renders a vanilla Enderman")
    # Each arm must ask for the GUARDIAN kind and the guardian model family.
    for visual_id in ECHO_IDS:
        start = text.index(f'case "{visual_id}" ->')
        end = text.index(";", start)
        arm = text[start:end]
        assert "Kind.GUARDIAN" in arm, visual_id + " must select Kind.GUARDIAN"
        assert '"end_rift_guardian"' in arm, (
            visual_id + " must use the guardian model family, not a generic mesh")


def test_the_enderman_mixin_treats_echo_ids_as_the_guardian_family():
    text = source("mixin/EndermanEntityRendererMixin.java")
    for visual_id in ECHO_IDS:
        assert f'"{visual_id}"' in text, (
            visual_id + " must be recognised by the mixin, or the guardian branch"
            " stays false and the authored model is never drawn")
    # It has to be part of the guardian predicate itself, not a loose mention.
    start = text.index("boolean guardian")
    end = text.index(";", start)
    predicate = text[start:end]
    assert "echoFamily" in predicate or all(v in predicate for v in ECHO_IDS), (
        "the echo ids must sit inside the guardian predicate: a check placed"
        " anywhere else leaves the Guardian model unrendered")


def test_the_echo_ids_do_not_reuse_the_real_boss_id():
    """Identity must stay separable even though the model is shared."""
    for visual_id in ECHO_IDS:
        assert visual_id != "END_RIFT_GUARDIAN_V1", (
            "sharing the boss visual id would erase the server-side distinction"
            " between a decoy and the real Guardian")
