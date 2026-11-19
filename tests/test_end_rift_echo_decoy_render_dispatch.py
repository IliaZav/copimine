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


def test_each_guardian_route_keys_the_clip_to_its_own_body():
    """One model instance serves the boss and every Pillager decoy.

    The clip and blend state now live per body key, so every route that selects
    the Guardian model has to pass the owning entity's UUID. Without a key the
    renderer would fall back to the shared default and one decoy would animate
    with another decoy's clip.
    """
    text = source()
    routes = text.count("copimine$guardianRenderer.modelForPhase(")
    keyed = text.count("copimine$guardianRenderer.modelForPhase(uuid,")
    assert routes >= 2 and keyed == routes, (
        "every Guardian route must pass the body key: " + str(keyed) + " of "
        + str(routes) + " routes do"
    )

    renderer = (ROOT / "tools/minecraft-26.3/client/src/main/java/me/copimine/client"
                / "RiftGuardianModelRenderer.java").read_text("utf-8")
    assert "model.setAnimation(clipKey" in renderer, (
        "the renderer must forward the per-body key into the model clip state"
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


CHAMELEON = (ROOT / "tools/minecraft-26.3/client/src/main/java/me/copimine/client"
             / "ChameleonGuardianRenderer.java")


def test_the_submitted_pose_is_captured_before_the_deferred_lambda():
    """The geometry lambda runs at draw time, so it may not read the shared model.

    ``SubmitNodeCollection.submitCustomGeometry`` copies only the matrix and
    stores the ``CustomGeometryRenderer`` for later execution. Every echo decoy
    is a Pillager, so all decoys share ONE ``RiftGuardianModel`` instance on the
    single ``PillagerRenderer``. Reading ``guardian.currentPose()`` inside the
    deferred lambda would give every decoy the pose of whichever body was
    submitted last - the contract's "obviously broken animations" that expose a
    copy. The pose must be captured per submission, before the lambda.
    """
    text = CHAMELEON.read_text("utf-8")
    start = text.index("public static void submit(")
    body = text[start: text.index("\n    }\n", start)]
    assert "GuardianPose pose = guardian.currentPose();" in body, (
        "the pose must be captured eagerly, per submission"
    )
    assert "faces(pose)" in body, (
        "the deferred lambda must close over the captured pose, not the model"
    )
    assert "faces(guardian.currentPose())" not in body, (
        "reading the shared model inside the deferred lambda repeats the "
        "last-submitted pose on every decoy"
    )
    capture_at = body.index("GuardianPose pose = guardian.currentPose();")
    lambda_at = body.index("submitCustomGeometry(")
    assert capture_at < lambda_at, (
        "the pose must be captured before the lambda is handed to the collector"
    )


def test_a_copy_is_visibly_learnable_without_a_fake_label():
    """Contract 15.3 wants a subtle, learnable clue, not a pixel-identical twin.

    The real boss, an echo decoy and an attack replica share one mesh and one
    skin, so without a per-body difference a careful player had no way to learn
    which body was real - the clause had no implementation. A cool tint is the
    "consistent difference in Rift core energy" the contract names, and it is
    never the forbidden literal "FAKE" tag.
    """
    text = source()
    start = text.index("copimine$tintDetachedEcho(")
    body = text[start: text.index("\n    }", start)]
    assert "COPIER_TINT" in body, (
        "the decoy/replica tint must be applied by the model tint hook"
    )
    assert "copimine$isGuardianCopy" in body, (
        "the tint must key on the copy visual ids, not on every Guardian body"
    )
    copy_at = text.index("private static boolean copimine$isGuardianCopy(")
    copy_body = text[copy_at: text.index("\n    }", copy_at)]
    assert DECOY in copy_body and REPLICA in copy_body, (
        "both the decoy and the attack replica must carry the clue"
    )
    # The clue is a colour, not wording: it must not be the opaque identity
    # tint (that would be invisible) and it must be a fixed constant, not
    # something the server can flip per player.
    tint_at = text.index("COPIER_TINT =")
    tint_line = text[tint_at: text.index(";", tint_at)]
    assert "0x" in tint_line, "the clue must be a fixed ARGB colour"
    assert "0xFFFFFFFF" not in tint_line, (
        "an identity tint would hide the clue completely"
    )


CLIENT_STATE = (ROOT / "tools/minecraft-26.3/client/src/main/java/me/copimine/client"
                / "EndEventClientState.java")


def test_a_renewed_looping_decoy_cue_keeps_its_played_phase():
    """Contract 15.3: a decoy may not reveal itself with broken animation.

    The server caps one actor cue at ten seconds, so a decoy that is idling or
    running has its cue renewed under a new cast every ten seconds. If the
    client restarts the clip clock at that renewal, the idle breath or run
    cycle pops back to its first frame once every ten seconds on every copy.
    ``GuardianActorFrame`` has no loop flag, so the client must recognise the
    two looping actions itself and carry the played time across the renewal.
    """
    text = CLIENT_STATE.read_text("utf-8")
    assert "loopsGuardianAction" in text, (
        "the client must know which actions loop; without that a renewed cue "
        "restarts the loop mid-cycle"
    )
    start = text.index("private static boolean loopsGuardianAction(")
    body = text[start: text.index("\n    }", start)]
    assert "IDLE_BREATH" in body, (
        "idle is a looping clip and must be covered"
    )
    assert "RUN" in body, "the run cycle is a looping clip and must be covered"
    assert "loopPhaseMillis" in text, (
        "the binding must remember the time already played across a renewal"
    )
    assert "old.loopPhaseMillis()" in text and "elapsedAt(nowMillis - old.receivedAt())" in text, (
        "the renewal must continue from the previously played time"
    )
    elapsed_at = text.index("public synchronized long bossAnimationElapsedMillisForEntity(")
    elapsed_body = text[elapsed_at: text.index("\n    }", elapsed_at)]
    assert "loopPhaseMillis() +" in elapsed_body, (
        "the renderer clock must include the carried loop phase"
    )