"""Phase 5 echo decoys must actually render as the Guardian on the client.

Contract 15.3 wants a decoy to be indistinguishable from the real boss, and the
server is deliberate about that: it spawns a PILLAGER and binds the visual id
END_RIFT_ECHO_DECOY_V1 to it, so decoys never share the boss entity type, HP,
boss bar or defeat side effects.

The client, however, only reached the Guardian model from ONE place. In
LivingEntityRendererMixin.copimine$selectWaveModel the Guardian branch is gated on
"state instanceof EndermanRenderState", and a Pillager extracts an
IllagerRenderState. So no branch matched, copimine$activeModel stayed null, and
vanilla drew the Pillager - a small brown illager in a cloak with a crossbow,
where the contract asks for a copy of the Guardian.

EndermanRendererSelection and EndermanEntityRendererMixin both model the decoy
correctly. They are simply never reached by a Pillager. That is the defect:
policy correct, dispatch unreachable.

This test pins the DISPATCH, not the policy: the Guardian model selection must
not be reachable only from EndermanRenderState.
"""

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MIXIN = (ROOT / "tools/minecraft-26.3/client/src/main/java/me/copimine/client/mixin"
         / "LivingEntityRendererMixin.java")

DECOY = "END_RIFT_ECHO_DECOY_V1"
REPLICA = "END_RIFT_ECHO_REPLICA_V1"


def source():
    return MIXIN.read_text("utf-8")


def test_the_decoy_visual_has_an_illager_admitted_route():
    """A Pillager decoy must have a branch that claims it."""
    text = source()
    assert "IllagerRenderState" in text, (
        "the shared renderer never mentions IllagerRenderState, so a Pillager "
        "decoy cannot select the Guardian model and renders as a vanilla Pillager"
    )
    # And that route must actually select the Guardian, not merely exist.
    illager_at = text.index("IllagerRenderState")
    tail = text[illager_at:]
    assert "copimine$guardianRenderer" in tail, (
        "the Illager route must select the Guardian model renderer"
    )


def test_the_guardian_model_is_selectable_from_more_than_one_render_state():
    """One CarrierRenderer type must not be the only way to reach the Guardian.

    A decoy is a Pillager and the boss is an Enderman, so the shared renderer
    needs a route for each. Counting the routes is what makes the enderman-only
    regression visible; the ordering assertion below pins the decoy route in
    front of the enderman one so the decoy is claimed by its own branch.
    """
    text = source()
    routes = text.count("copimine$guardianRenderer.modelForPhase(")
    assert routes >= 2, (
        "the Guardian model is selected from " + str(routes) + " place(s); a "
        "Pillager decoy cannot reach the enderman-only route"
    )
    # Anchor on the BRANCH, not the import: the file imports both state types
    # at the top, so a bare type name matches the import first.
    illager_at = text.index("} else if (state instanceof IllagerRenderState")
    enderman_at = text.index("} else if (state instanceof EndermanRenderState")
    assert illager_at < enderman_at, (
        "the Illager route must be tested before the Enderman route so a decoy "
        "is claimed by the branch written for it"
    )


def test_both_echo_visual_ids_route_to_the_guardian():
    """A copy and a decoy are different ids and both wear the Guardian body."""
    mixin = source()
    selection = (ROOT / "tools/minecraft-26.3/client/src/main/java/me/copimine/client"
                 / "EndermanRendererSelection.java").read_text("utf-8")
    for visual in (DECOY, REPLICA):
        assert visual in selection, (
            visual + " must map to the Guardian in EndermanRendererSelection"
        )
        # The shared renderer has to know the family too, or the id never arrives.
        assert visual in mixin, (
            visual + " must be recognised by the shared LivingEntityRenderer mixin"
        )


def test_a_guardian_identity_check_covers_the_decoy():
    """Scale and model selection share one "is this a Guardian" predicate."""
    text = source()
    start = text.index("private static boolean copimine$isGuardianState(")
    depth = 0
    for index in range(text.index("{", start), len(text)):
        if text[index] == "{":
            depth += 1
        elif text[index] == "}":
            depth -= 1
            if depth == 0:
                body = text[start: index + 1]
                break
    else:
        raise AssertionError("unbalanced copimine$isGuardianState")
    assert "copimine$visual()" in body, (
        "the predicate must consider the bound visual id, not only the boss flag"
    )