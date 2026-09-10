from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CLIENT = ROOT / "CopiMineClient" / "src" / "main" / "java" / "me" / "copimine" / "client"
RESOURCES = ROOT / "resourcepacks" / "src" / "assets" / "copimine"


def _read(relative: str) -> str:
    return (CLIENT / relative).read_text(encoding="utf-8")


def test_tentacle_client_has_a_real_articulated_rig_and_animator():
    for name in ("EndRiftTentacleRig.java", "EndRiftTentaclePose.java", "EndRiftTentacleAnimator.java"):
        assert (CLIENT / name).is_file(), name
    source = "\n".join(_read(name) for name in (
        "EndRiftTentacleRig.java", "EndRiftTentaclePose.java", "EndRiftTentacleAnimator.java"))
    for bone in ("root", "base", "seg_01", "seg_02", "seg_03", "seg_04", "seg_05",
                 "tip", "tip_claw_1", "tip_claw_2", "tip_claw_3", "tip_claw_4",
                 "grab_socket"):
        assert bone in source, bone
    assert "parent" in source
    assert "socket" in source.lower()
    assert "isFinite" in source


def test_tentacle_renderer_draws_parts_independently_and_skips_vanilla_carrier():
    renderer = _read("EndRiftTentacleRenderer.java")
    mixin = (CLIENT / "mixin" / "DisplayEntityRendererMixin.java").read_text(encoding="utf-8")
    assert "MatrixStack" in renderer
    assert "push()" in renderer and "pop()" in renderer
    assert "EndRiftTentacleRig.RenderRig" in renderer
    assert "RIG.render" in renderer
    assert "endEventVisualEntityIds" in renderer
    assert "getEntity" in renderer
    assert "ci.cancel()" in mixin
    assert "EndRiftTentacleModel.VISUAL_ID" in mixin
    assert "tintForHealthState" in renderer
    assert "root.render(matrices, buffer, light, overlay, color)" in _read("EndRiftTentacleRig.java")


def test_tentacle_animation_timeline_is_carried_by_the_client_state():
    state = _read("EndEventClientState.java")
    assert "stateStartServerTick" in state
    assert "startedAtMillis" in state
    assert "durationMillis" in state
    assert "TentacleAnimationPolicy" not in state
    assert "healthState" in state
    assert "normalizeHealthState" in state


def test_tentacle_asset_contract_has_five_segments_four_claws_and_socket_metadata():
    model = (RESOURCES / "models" / "item" / "end_event_rift_tentacle.json").read_text(encoding="utf-8")
    animation = (RESOURCES / "animations" / "end_event_rift_tentacle.json").read_text(encoding="utf-8")
    generator = (ROOT / "resourcepacks" / "generate_end_rift_tentacle_assets.py").read_text(encoding="utf-8")
    for text in (model, animation, generator):
        for token in ("seg_05", "tip_claw_4", "grab_socket"):
            assert token in text, token
    assert '"elements"' in model
    assert '"animations"' in animation
    ys = [coord for element in __import__("json").loads(model)["elements"]
          for coord in (element["from"][1], element["to"][1])]
    assert max(ys) == 16
    main = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(
        encoding="utf-8")
    assert "TENTACLE_FALLBACK_SCALE = 4.75F" in main
    assert "new Vector3f(TENTACLE_FALLBACK_SCALE, TENTACLE_FALLBACK_SCALE" in main


def test_tentacle_protocol_exposes_the_v2_lifecycle_states():
    policy = (ROOT / "copimine-end-event/src/me/copimine/endevent/domain/TentacleAnimationPolicy.java").read_text(encoding="utf-8")
    animator = _read("EndRiftTentacleAnimator.java")
    required = (
        "EMERGING", "READY", "TELEGRAPH_GRAB", "GRAB_SUCCESS", "HOLD", "THROW",
        "MISS_RECOVERY", "HIT_RECOVERY", "DYING", "DEAD_RESPAWN",
        "RETRACT", "SPAWN_UNDER_PLAYER", "SHIELD_CHANNEL", "RECOVERY",
    )
    for state in required:
        assert state in policy, state
        assert state in animator, state
    assert "EMERGING" in policy and "READY" in policy
    assert "MISS_RECOVERY" in policy and "HIT_RECOVERY" in policy
    assert "DYING" in policy and "DEAD_RESPAWN" in policy


def test_under_player_runtime_retracts_without_becoming_a_grab_attempt():
    main = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(
        encoding="utf-8")
    start = main.index("private void tickTentacle(")
    end = main.index("private Player playerForTentacle", start)
    under_player = main[main.index("case SPAWN_UNDER_PLAYER", start):
                        main.index("case TELEGRAPH_GRAB", main.index("case SPAWN_UNDER_PLAYER", start))]
    assert "State.RECOVERY" in under_player
    assert "State.TELEGRAPH_GRAB" not in under_player
    assert "State.RETRACT" in main[end - 5000:end]
