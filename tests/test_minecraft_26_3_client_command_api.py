from pathlib import Path
import json
import os
import re
import shutil
import subprocess

import pytest


ROOT = Path(__file__).resolve().parents[1]
CLIENT_ENTRYPOINT = (
    ROOT
    / "tools/minecraft-26.3/client/src/main/java/me/copimine/client/CopiMineClient.java"
)
CLIENT_SOURCE_ROOT = ROOT / "tools/minecraft-26.3/client/src/main/java"
RENDER_SUBMISSION = (
    CLIENT_SOURCE_ROOT / "me/copimine/client/EndRiftRenderSubmission.java"
)
BRIDGE_PAYLOAD = CLIENT_SOURCE_ROOT / "me/copimine/client/BridgePayload.java"
MIGRATION_PROFILE_LOCK = ROOT / "tools/minecraft-26.3/profile.lock.json"
MIGRATION_MODS_LOCK = ROOT / "tools/minecraft-26.3/client-mods.lock.json"
MIGRATION_CANDIDATE_SCRIPT = ROOT / "scripts/minecraft/PrepareMigrationCandidates.ps1"


def test_built_26_3_client_is_installable_while_native_acceptance_stays_open():
    profile = json.loads(MIGRATION_PROFILE_LOCK.read_text(encoding="utf-8"))
    modules = json.loads(MIGRATION_MODS_LOCK.read_text(encoding="utf-8"))

    assert profile["status"] in {"migration-candidate", "migration-accepted"}
    assert profile["fabric"]["clientPortComplete"] is True
    assert modules["nativeVerified"] is (profile["status"] == "migration-accepted")


def test_prisoner_preview_uses_a_bounded_26_3_entity_query():
    selector = (
        CLIENT_SOURCE_ROOT / "me/copimine/client/PrisonerTargetSelector.java"
    ).read_text(encoding="utf-8")

    assert "MAX_QUERY_ENTITIES = 256" in selector
    assert "client.level.getEntities(EntityTypeTest.forClass(Entity.class), rayBounds," in selector
    compact_selector = re.sub(r"\s+", " ", selector)
    assert (
        "target != client.player && target instanceof LivingEntity living "
        "&& living.isAlive() && !living.isSpectator() && living.level() == client.level"
    ) in compact_selector
    assert "queryEntities, MAX_QUERY_ENTITIES" in selector
    assert "client.level.getEntities(client.player, rayBounds)" not in selector


def test_migration_candidate_gate_runs_client_tests_with_the_jar_build():
    script = MIGRATION_CANDIDATE_SCRIPT.read_text(encoding="utf-8")
    client_gradle_invocations = [
        line.strip()
        for line in script.splitlines()
        if "& $gradle --no-daemon" in line and "-p $clientProject" in line
    ]

    assert len(client_gradle_invocations) == 1
    client_gradle_invocation = client_gradle_invocations[0]
    assert "--project-cache-dir $clientGradleProjectCache" in client_gradle_invocation
    assert re.search(r"\btest\b", client_gradle_invocation)
    assert re.search(r"\bjar\b", client_gradle_invocation)
    assert "CopiMineClient 26.3 build and tests failed" in script


def test_minecraft_26_3_client_commands_use_fabric_3_api():
    source = CLIENT_ENTRYPOINT.read_text(encoding="utf-8")

    assert "import net.fabricmc.fabric.api.client.command.v2.ClientCommands;" in source
    assert "ClientCommandManager" not in source
    assert "ClientCommandRegistrationCallback.EVENT.register" in source
    assert 'ClientCommands.literal("copimineclient")' in source
    assert 'ClientCommands.literal("status")' in source
    assert 'ClientCommands.literal("shader")' in source
    assert 'ClientCommands.literal("visual")' in source


def test_unbound_end_event_displays_cannot_be_recovered_from_stale_item_markers():
    client_protocol = (
        CLIENT_SOURCE_ROOT / "me/copimine/client/ClientBridgeProtocol.java"
    ).read_text(encoding="utf-8")
    marker_renderers = (
        "EndRiftTentacleRenderer.java",
        "EndRiftGuardianShieldRenderer.java",
        "RitualSphereRenderer.java",
    )

    assert "isEndEventEntityRenderSuppressed" in client_protocol
    for renderer_name in marker_renderers:
        renderer = (
            CLIENT_SOURCE_ROOT / "me/copimine/client" / renderer_name
        ).read_text(encoding="utf-8")
        assert "isEndEventEntityRenderSuppressed" in renderer, renderer_name


def test_minecraft_26_3_client_textures_use_gui_pipeline_and_source_coordinates():
    boss_hud = (
        CLIENT_SOURCE_ROOT / "me/copimine/client/EndRiftBossBarHud.java"
    ).read_text(encoding="utf-8")
    prisoner_hud = (
        CLIENT_SOURCE_ROOT / "me/copimine/client/PrisonerHudRenderer.java"
    ).read_text(encoding="utf-8")
    event_vfx = (
        CLIENT_SOURCE_ROOT / "me/copimine/client/EndEventWorldVfxManager.java"
    ).read_text(encoding="utf-8")
    compact = lambda source: re.sub(r"\s+", " ", source)

    assert "context.blit(RenderPipelines.GUI_TEXTURED, FRAME" in compact(boss_hud)
    assert (
        "x, y + FRAME_Y, 0F, 0F, WIDTH, FRAME_HEIGHT, "
        "SOURCE_WIDTH, SOURCE_HEIGHT, SOURCE_WIDTH, SOURCE_HEIGHT"
    ) in compact(boss_hud)
    assert "context.blit(RenderPipelines.GUI_TEXTURED, ICONS[index]" in compact(prisoner_hud)
    assert "iconX, iconY, 0F, 0F, ICON_SIZE, ICON_SIZE," in compact(prisoner_hud)
    assert "context.blit(ICONS[index]," not in prisoner_hud
    assert "panel.left() + 2, panel.top() + 3, 0F, 0F, 20, 20," in compact(event_vfx)


def test_minecraft_26_3_build_does_not_register_the_same_java_root_twice():
    build_script = (ROOT / "tools/minecraft-26.3/client/build.gradle").read_text(
        encoding="utf-8"
    )

    assert "java.srcDir('src/main/java')" not in build_script
    assert "resources.srcDir(new File(checkout, 'CopiMineClient/src/main/resources'))" in build_script


def test_minecraft_26_3_build_uses_a_version_specific_mixin_config():
    build_script = (ROOT / "tools/minecraft-26.3/client/build.gradle").read_text(
        encoding="utf-8"
    )
    config = CLIENT_SOURCE_ROOT.parent / "resources/copimineclient-26.3.mixins.json"
    legacy_config = (
        ROOT / "CopiMineClient/src/main/resources/copimineclient.mixins.json"
    ).read_text(encoding="utf-8")

    assert "parsed.mixins = ['copimineclient-26.3.mixins.json']" in build_script
    assert config.is_file()
    assert '"GameRendererPostProcessMixin"' in config.read_text(encoding="utf-8")
    assert '"GameRendererAccessor"' not in config.read_text(encoding="utf-8")
    assert '"GameRendererAccessor"' in legacy_config


def test_minecraft_26_3_mixin_compatibility_matches_java_25_bytecode():
    config = json.loads(
        (CLIENT_SOURCE_ROOT.parent / "resources/copimineclient-26.3.mixins.json").read_text(
            encoding="utf-8"
        )
    )
    build_script = (ROOT / "tools/minecraft-26.3/client/build.gradle").read_text(
        encoding="utf-8"
    )

    assert config["compatibilityLevel"] == "JAVA_25"
    assert "options.release = 25" in build_script


def test_26_3_boss_bar_and_prisoner_mixins_use_current_targets_and_are_reachable():
    config_path = CLIENT_SOURCE_ROOT.parent / "resources/copimineclient-26.3.mixins.json"
    config = json.loads(config_path.read_text(encoding="utf-8"))
    boss_mixin = (CLIENT_SOURCE_ROOT / "me/copimine/client/mixin/EndRiftBossBarHudMixin.java").read_text(encoding="utf-8")
    prisoner_mixin = (CLIENT_SOURCE_ROOT / "me/copimine/client/mixin/ClientPlayerInteractionManagerPrisonerMixin.java").read_text(encoding="utf-8")
    model_accessor = (CLIENT_SOURCE_ROOT / "me/copimine/client/mixin/ModelPartAccessor.java").read_text(encoding="utf-8")
    cube_accessor = (CLIENT_SOURCE_ROOT / "me/copimine/client/mixin/ModelPartCuboidAccessor.java").read_text(encoding="utf-8")
    client = CLIENT_ENTRYPOINT.read_text(encoding="utf-8")

    assert "EndRiftBossBarHudMixin" in config["client"]
    assert 'extractBar(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IILnet/minecraft/world/BossEvent;)V' in boss_mixin
    assert 'extractBar(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IILnet/minecraft/world/BossEvent;I[Lnet/minecraft/resources/Identifier;[Lnet/minecraft/resources/Identifier;)V' in boss_mixin
    assert "EndRiftBossBarHud.render(drawContext);" in client
    assert '@Accessor("cubes")' in model_accessor
    assert '@Accessor("polygons")' in cube_accessor
    for target in ("attack(", "startDestroyBlock(", "continueDestroyBlock(", "destroyBlock(",
                   "useItemOn(", "useItem(", "interact(", "piercingAttack("):
        assert target in prisoner_mixin
    for removed in ("attackEntity", "attackBlock", "updateBlockBreakingProgress", "breakBlock",
                    "interactBlock", "interactItem", "interactEntityAtLocation"):
        assert removed not in prisoner_mixin


def test_shaderpack_confirmation_does_not_sleep_on_the_client_executor():
    source = (CLIENT_SOURCE_ROOT / "me/copimine/client/IrisShaderpackRuntime.java").read_text(encoding="utf-8")

    assert "Thread.sleep" not in source
    assert "APPLY_POLL_ATTEMPTS" not in source


def test_prisoner_keyboard_mixin_targets_inherited_client_input_fields():
    source = (CLIENT_SOURCE_ROOT / "me/copimine/client/mixin/KeyboardInputPrisonerMixin.java").read_text(
        encoding="utf-8"
    )

    assert "class KeyboardInputPrisonerMixin extends ClientInput" in source
    assert "@Shadow" not in source
    assert "this.keyPresses = Input.EMPTY" in source
    assert "this.moveVector = new Vec2(0.0F, 0.0F)" in source


def test_minecraft_26_3_does_not_ship_the_removed_game_renderer_accessor():
    obsolete_accessor = (
        CLIENT_SOURCE_ROOT
        / "me/copimine/client/mixin/GameRendererAccessor.java"
    )

    assert not obsolete_accessor.exists()


def test_minecraft_26_3_client_uses_identifier_resource_keys():
    source_files = CLIENT_SOURCE_ROOT.rglob("*.java")
    source = "\n".join(path.read_text(encoding="utf-8") for path in source_files)

    assert "ResourceLocation" not in source
    assert "Identifier.fromNamespaceAndPath" in source


def test_minecraft_26_3_post_effects_use_current_ids_and_shader_assets():
    controller = (
        CLIENT_SOURCE_ROOT / "me/copimine/client/ClientPostProcessController.java"
    ).read_text(encoding="utf-8")
    custom_effects = re.findall(
        r'Map\.entry\("([A-Z_]+)", Identifier\.fromNamespaceAndPath\("([^"]+)", "([^"]+)"\)\)',
        controller,
    )
    expected_effects = {
        "copimine_desaturate",
        "copimine_color_convolve",
        "copimine_scan_pincushion",
        "copimine_green_noise",
        "copimine_wobble",
        "copimine_blobs",
        "copimine_pencil",
        "copimine_chaos",
    }
    assert {path for _, namespace, path in custom_effects if namespace == "copimineclient"} == expected_effects
    assert 'Identifier.withDefaultNamespace("invert")' in controller
    assert "shaders/post/" not in controller
    assert ".json\"" not in controller

    assets = ROOT / "tools/minecraft-26.3/client/src/main/resources/assets"
    for _, namespace, effect_id in custom_effects:
        definition_path = assets / namespace / "post_effect" / f"{effect_id}.json"
        definition = json.loads(definition_path.read_text(encoding="utf-8"))
        assert isinstance(definition.get("targets"), dict)
        assert definition.get("passes")
        for render_pass in definition["passes"]:
            assert render_pass["vertex_shader"]
            assert render_pass["fragment_shader"]
            assert [item["sampler_name"] for item in render_pass.get("inputs", [])] == ["In"]
            shader_namespace, shader_id = render_pass["fragment_shader"].split(":", 1)
            if shader_namespace == "minecraft":
                continue
            shader_path = assets / shader_namespace / "shaders" / f"{shader_id}.fsh"
            assert shader_path.is_file(), shader_path


def test_minecraft_26_3_client_hud_uses_extraction_graphics():
    source = "\n".join(
        path.read_text(encoding="utf-8")
        for path in CLIENT_SOURCE_ROOT.rglob("*.java")
    )

    assert "net.minecraft.client.gui.GuiGraphics;" not in source
    assert "GuiGraphicsExtractor" in source


def test_minecraft_26_3_client_uses_mob_and_player_package_names():
    source = "\n".join(
        path.read_text(encoding="utf-8")
        for path in CLIENT_SOURCE_ROOT.rglob("*.java")
    )

    assert "EnderMan" not in source
    assert "net.minecraft.world.entity.monster.AbstractSkeleton;" not in source
    assert "net.minecraft.world.entity.monster.Spider;" not in source
    assert "net.minecraft.client.resources.PlayerSkin;" not in source
    assert "net.minecraft.client.model.EndermanModel;" not in source
    assert "net.minecraft.client.model.SkeletonModel;" not in source
    assert "net.minecraft.client.model.SpiderModel;" not in source
    assert "net.minecraft.client.model.monster.enderman.EndermanModel;" in source
    assert "net.minecraft.world.entity.player.PlayerSkin;" in source


def test_minecraft_26_3_client_hud_uses_current_screen_and_hud_state():
    source = "\n".join(
        path.read_text(encoding="utf-8")
        for path in CLIENT_SOURCE_ROOT.rglob("*.java")
    )

    assert ".options.hideGui" not in source
    assert ".screen != null" not in source
    assert ".gui.hud.isHidden()" in source
    assert ".gui.screen()" in source
    assert ".drawString(" not in source
    assert ".drawCenteredString(" not in source
    assert ".centeredText(" in source


def test_minecraft_26_3_hud_registry_keeps_all_client_overlays():
    source = CLIENT_ENTRYPOINT.read_text(encoding="utf-8")

    assert "HudRenderCallback" not in source
    assert "HudElementRegistry.addLast" in source
    assert "visualManager.render(drawContext)" in source
    assert "ClientBridgeProtocol.endEventWorldVfx().renderHud(drawContext)" in source
    assert "EndEventPlayerVisualRenderer.render(drawContext)" in source
    assert "PrisonerHudRenderer.render(drawContext)" in source


def test_minecraft_26_3_world_render_event_keeps_all_end_rift_layers():
    source = CLIENT_ENTRYPOINT.read_text(encoding="utf-8")

    assert (
        "import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;"
        in source
    )
    assert "WorldRenderEvents" not in source
    assert "LevelRenderEvents.COLLECT_SUBMITS.register" in source
    for layer in (
        "ClientBridgeProtocol.renderEndEventWorldVfx(context)",
        "ClientBridgeProtocol.renderEndEventTentacles(context)",
        "PrisonerTargetSelector.render(context)",
        "EndRiftGuardianShieldRenderer.render(context)",
        "RitualSphereRenderer.render(context)",
    ):
        assert layer in source


def test_minecraft_26_3_world_geometry_uses_deferred_submit_collector():
    assert RENDER_SUBMISSION.exists()
    source = RENDER_SUBMISSION.read_text(encoding="utf-8")

    assert "LevelRenderContext" in source
    assert "submitNodeCollector().submitCustomGeometry" in source
    assert "Geometry geometry" in source
    assert "cameraRelative.translate(-cameraPosition.x" in source


def test_minecraft_26_3_world_change_still_clears_client_event_state():
    source = CLIENT_ENTRYPOINT.read_text(encoding="utf-8")

    assert "ClientWorldEvents" not in source
    assert "ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register" in source
    assert 'visualManager.clearAll(ClientBridgeProtocol::sendVisualFinished, "world_change", true)' in source
    assert "ClientBridgeProtocol.clearEndEventState();" in source


def test_minecraft_26_3_bridge_keeps_string_message_type_and_payload_type():
    payload_source = BRIDGE_PAYLOAD.read_text(encoding="utf-8")
    bridge_source = "\n".join(
        (CLIENT_SOURCE_ROOT / f"me/copimine/client/{name}").read_text(encoding="utf-8")
        for name in (
            "ClientBridgeProtocol.java",
            "EndEventBlackFogManager.java",
            "EndEventWorldVfxManager.java",
            "EndEventPlayerVisualManager.java",
        )
    )

    assert "String messageType," in payload_source
    assert "public Type<? extends CustomPacketPayload> type()" in payload_source
    assert "out.writeUTF(messageType);" in payload_source
    assert "payload.messageType()" in bridge_source
    assert "payload.type()" not in bridge_source
    assert "PayloadTypeRegistry.serverboundPlay()" in bridge_source
    assert "PayloadTypeRegistry.clientboundPlay()" in bridge_source


def test_minecraft_26_3_renderers_read_main_camera_and_current_model_data():
    model_renderer_source = "\n".join(
        (CLIENT_SOURCE_ROOT / f"me/copimine/client/{name}").read_text(encoding="utf-8")
        for name in (
            "EndRiftGuardianShieldRenderer.java",
            "EndRiftTentacleRenderer.java",
            "RitualSphereRenderer.java",
            "EndRiftItemModelData.java",
        )
    )
    all_client_source = "\n".join(
        path.read_text(encoding="utf-8") for path in CLIENT_SOURCE_ROOT.rglob("*.java")
    )

    assert ".mainCamera()" in all_client_source
    assert "getMainCamera()" not in all_client_source
    assert "getFloat(0)" in model_renderer_source
    assert ".value()" not in model_renderer_source


def test_minecraft_26_3_event_models_consume_extracted_render_states():
    model_files = {
        "RiftEventEndermanModel.java": "EndermanRenderState",
        "RiftEventSkeletonModel.java": "SkeletonRenderState",
        "RiftSpiderModel.java": "LivingEntityRenderState",
    }
    for filename, state_type in model_files.items():
        source = (CLIENT_SOURCE_ROOT / f"me/copimine/client/{filename}").read_text(
            encoding="utf-8"
        )
        assert f"setupAnim({state_type} state)" in source
        assert "setAngles(" not in source
        assert "getStringUUID()" not in source


def test_minecraft_26_3_binds_wave_identity_during_entity_state_extraction():
    source = "\n".join(
        path.read_text(encoding="utf-8")
        for path in CLIENT_SOURCE_ROOT.rglob("*.java")
    )

    assert "LivingEntityRenderStateMixin" in (
        CLIENT_SOURCE_ROOT.parent / "resources/copimineclient-26.3.mixins.json"
    ).read_text(encoding="utf-8")
    assert "copimine$uuid" in source
    assert "copimine$wavePose" in source
    assert "extractRenderState" in source


def test_minecraft_26_3_prisoner_outline_mixins_target_live_minecraft_methods():
    named_jar = Path(
        os.environ.get(
            "MINECRAFT_26_3_NAMED_JAR",
            Path.home()
            / ".gradle/caches/fabric-loom/minecraftMaven/net/minecraft/"
            "minecraft-merged-deobf/26.3/minecraft-merged-deobf-26.3.jar",
        )
    )
    if not named_jar.is_file():
        pytest.skip("Loom's mapped Minecraft 26.3 jar is not cached")

    javap = shutil.which("javap")
    if not javap:
        pytest.skip("A JDK javap executable is required to inspect the mapped API")

    targets = (
        (
            "PrisonerTargetOutlineMixin.java",
            "net.minecraft.client.Minecraft",
            "shouldEntityAppearGlowing",
            "Inject",
            "shouldEntityAppearGlowing",
        ),
        (
            "PrisonerTargetOutlineColorMixin.java",
            "net.minecraft.world.entity.Entity",
            "getTeamColor",
            "Inject",
            "getTeamColor",
        ),
        (
            "ClientWorldAccessor.java",
            "net.minecraft.client.multiplayer.ClientLevel",
            "getEntities",
            "Invoker",
            "getEntities",
        ),
        (
            "WaveMusicMixMixin.java",
            "net.minecraft.client.sounds.SoundEngine",
            "calculateVolume(Lnet/minecraft/client/resources/sounds/SoundInstance;)F",
            "Inject",
            "calculateVolume",
        ),
        (
            "GameRendererPostProcessMixin.java",
            "net.minecraft.client.renderer.GameRenderer",
            "extract(Lnet/minecraft/client/DeltaTracker;Z)V",
            "Inject",
            "extract",
        ),
        (
            "LivingEntityRendererMixin.java",
            "net.minecraft.client.renderer.entity.LivingEntityRenderer",
            "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
            "Inject",
            "extractRenderState",
        ),
    )
    for filename, owner, method, annotation, api_method in targets:
        mixin = (
            CLIENT_SOURCE_ROOT / "me/copimine/client/mixin" / filename
        ).read_text(encoding="utf-8")
        api = subprocess.run(
            [javap, "-classpath", str(named_jar), "-p", owner],
            capture_output=True,
            text=True,
            check=True,
        ).stdout

        assert f'@{annotation}(method = "{method}"' in mixin or (
            annotation == "Invoker" and f'@Invoker("{method}")' in mixin
        )
        assert re.search(rf"\b{re.escape(api_method)}\s*\(", api)


def test_minecraft_26_3_does_not_load_unused_legacy_model_accessors():
    config = (CLIENT_SOURCE_ROOT.parent / "resources/copimineclient-26.3.mixins.json").read_text(
        encoding="utf-8"
    )

    assert '"ModelPartAccessor"' not in config
    assert '"ModelPartCuboidAccessor"' not in config


def test_minecraft_26_3_armor_state_helper_is_not_a_mixin_static_method():
    mixin = (
        CLIENT_SOURCE_ROOT / "me/copimine/client/mixin/ArmorFeatureRendererMixin.java"
    ).read_text(encoding="utf-8")
    texture_mixin = (
        CLIENT_SOURCE_ROOT / "me/copimine/client/mixin/EquipmentLayerRendererTextureMixin.java"
    ).read_text(encoding="utf-8")
    tracker = (
        CLIENT_SOURCE_ROOT / "me/copimine/client/ArmorStackTracker.java"
    ).read_text(encoding="utf-8")
    texture_policy = (
        CLIENT_SOURCE_ROOT / "me/copimine/client/ArtifactArmorTexturePolicy.java"
    ).read_text(encoding="utf-8")
    custom_data_mixin = (
        CLIENT_SOURCE_ROOT / "me/copimine/client/mixin/CustomDataReadAccessMixin.java"
    ).read_text(encoding="utf-8")
    mixin_config = (
        CLIENT_SOURCE_ROOT.parent / "resources/copimineclient-26.3.mixins.json"
    ).read_text(encoding="utf-8")

    assert "public static ItemStack copimine$currentArmorStack" not in mixin
    assert "ArmorStackTracker.remember(stack)" in mixin
    assert "ArtifactArmorTexturePolicy.textureFor(stack)" in tracker
    assert "ArmorStackTracker.currentTexture()" in texture_mixin
    assert "@Mixin(EquipmentLayerRenderer.class)" in texture_mixin
    assert 'ordinal = 0' in texture_mixin
    assert "lookup.apply(key)" in texture_mixin
    assert "copyTag()" not in texture_policy
    assert "@Mixin(CustomData.class)" in custom_data_mixin
    assert '"CustomDataReadAccessMixin"' in mixin_config


def test_minecraft_26_3_tentacle_suppression_and_render_share_bounded_admission():
    renderer = (
        CLIENT_SOURCE_ROOT / "me/copimine/client/EndRiftTentacleRenderer.java"
    ).read_text(encoding="utf-8")

    assert "candidateEntityIds(Minecraft.getInstance().level).contains(entity.getUUID())" in renderer
    assert "List<UUID> candidateIds = candidateEntityIds(world)" in renderer
    assert "EndRiftTentacleCandidatePolicy.MAX_CANDIDATES" in renderer
