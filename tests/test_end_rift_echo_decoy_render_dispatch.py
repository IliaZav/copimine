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


MODEL = (ROOT / "tools/minecraft-26.3/client/src/main/java/me/copimine/client"
         / "RiftGuardianModel.java")


def test_the_guardian_body_accepts_a_pillager_carrier():
    """A decoy is a Pillager, so its state is an IllagerRenderState.

    ``Model.setupAnim`` is invoked through the erased ``setupAnim(Object)``
    bridge. While the model was typed to ``EndermanRenderState`` that bridge
    cast the illager state and threw ``ClassCastException`` once per decoy per
    frame. Typing the body to the base ``LivingEntityRenderState`` removes the
    narrowing that made a Pillager carrier illegal.
    """
    text = MODEL.read_text("utf-8")
    declaration = text[text.index("public final class RiftGuardianModel"):]
    declaration = declaration[: declaration.index("{")]
    assert "extends EntityModel<LivingEntityRenderState>" in declaration, (
        "the Guardian body must accept every living render state, not only an "
        "Enderman one; a Pillager carrier otherwise fails the model bridge"
    )
    assert "EndermanModel" not in declaration, (
        "no Guardian-body declaration may inherit the Enderman-only model"
    )
    assert "setupAnim(LivingEntityRenderState" in text, (
        "the pose advance must be defined for the base living render state"
    )
    assert "setupAnim(EndermanRenderState" not in text, (
        "a narrowed setupAnim would cast a Pillager carrier again"
    )


def test_the_guardian_mesh_is_submitted_for_every_living_carrier():
    """The body submission must be keyed on the model, not the carrier type."""
    text = source()
    start = text.index("private void copimine$submitImportedGuardian(")
    body = text[start: text.index("collector.submitModel(model, state, matrices, renderType", start)]
    assert "model instanceof RiftGuardianModel" in body, (
        "the guardian mesh must be selected by model identity"
    )
    assert "state instanceof LivingEntityRenderState" in body, (
        "any living carrier (Enderman, Pillager, player view) must be able to "
        "submit the authored body"
    )
    assert "state instanceof EndermanRenderState" not in body.replace(
        "state instanceof EndermanRenderState\n                    ?", ""
    )
    # The Enderman texture route is kept, but it may not gate the submission.
    submission_gate = body.split("RiftGuardianModel guardian", 1)[1].split("\n", 1)[0]
    assert "EndermanRenderState" not in submission_gate, (
        "the guardian mesh must not be submitted only for EndermanRenderState"
    )
    assert "ChameleonGuardianRenderer.submit" in body, (
        "the authored mesh is the only visible body of a pilfered carrier"
    )
    assert "setupAnim(livingState)" in body, (
        "the suppressed carrier layers no longer advance the pose, so the "
        "submission path must advance it before emitting geometry"
    )


def test_carrier_layers_are_suppressed_for_a_pillager_guardian_body():
    """Illager layers cast getParentModel() to IllagerModel; that must not run."""
    text = source()
    assert 'shouldRenderLayers(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;)Z' in text, (
        "the shared renderer must intercept the layer gate"
    )
    start = text.index("copimine$suppressGuardianCarrierLayers(")
    body = text[start: text.index("\n    }", start)]
    assert "setReturnValue(false)" in body, (
        "a Guardian body on a non-Enderman carrier must skip that carrier's layers"
    )
    assert "copimine$wearsGuardianBody" in body, (
        "the layer gate must use the same Guardian-body list as model selection"
    )
    assert "EndermanRenderState" in body, (
        "the boss Enderman path keeps its layers, so it must be excluded"
    )