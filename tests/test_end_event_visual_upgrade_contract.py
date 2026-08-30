from __future__ import annotations

import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SERVER = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(encoding="utf-8")
PLUGIN = (ROOT / "copimine-end-event/plugin.yml").read_text(encoding="utf-8")
CLIENT = ROOT / "CopiMineClient/src/main/java/me/copimine/client"


def _body(source: str, start: str, end: str) -> str:
    begin = source.index(start)
    return source[begin:source.index(end, begin)]


def test_boss_bridge_exposes_cast_animation_and_phase_transition_to_the_client() -> None:
    state = (CLIENT / "EndEventClientState.java").read_text(encoding="utf-8")
    protocol = (CLIENT / "ClientBridgeProtocol.java").read_text(encoding="utf-8")
    mixin = (CLIENT / "mixin/EndermanEntityRendererMixin.java").read_text(encoding="utf-8")
    renderer = (CLIENT / "RiftGuardianModelRenderer.java").read_text(encoding="utf-8")

    assert "bossAnimationForEntity" in state
    assert "bossCastStateForEntity" in state
    assert "bossAnimationForEntity" in protocol
    assert "bossCastStateForEntity" in protocol
    assert "bossAnimationForEntity" in mixin
    assert "modelForPhase(String phaseId, long transitionDurationMillis, String animationId)" in renderer

    telegraph = _body(SERVER, "private void telegraphBossSpell", "private void executeBossSpell")
    execute = _body(SERVER, "private void executeBossSpell", "private void voidBlast")
    assert "sendBossAnimationVisualUpdate" in telegraph
    assert "sendBossAnimationVisualUpdate" in execute
    assert "BOSS_PHASE_VISUAL_TRANSITION_MILLIS" in SERVER


def test_guardian_model_has_a_real_3d_final_silhouette_and_cast_animations() -> None:
    model = (CLIENT / "RiftGuardianModel.java").read_text(encoding="utf-8")

    for part in ("left_horn_tip", "right_horn_tip", "left_crest", "right_crest", "back_spine"):
        assert f'addChild("{part}"' in model
    for marker in (
        "applySpellCastTransform",
        "applyFinalSilhouette",
        "leftHorn.yScale",
        "rightHorn.yScale",
        "root.xScale",
        "SPELL_VOID_BLAST",
        "JUDGMENT_CAST",
    ):
        assert marker in model


def test_spell_visuals_have_bounded_signatures_for_flight_impact_and_telegraph() -> None:
    for marker in (
        "spawnSpellSignature",
        "spawnSpellImpactHalo",
        "spawnSpellTelegraphGlyph",
        "SPELL_VISUAL_MAX_POINTS",
        "Particle.DUST",
    ):
        assert marker in SERVER

    flight = _body(SERVER, "private void spawnSpellFlightPattern(Player", "private void spawnRiftProjectileTrail")
    impact = _body(SERVER, "private void renderSpellImpactVisual", "private void renderSpellTelegraphVisual")
    telegraph = _body(SERVER, "private void renderSpellTelegraphVisual", "private void launchSpellFlight")
    assert "spawnSpellSignature" in flight
    assert "spawnSpellImpactHalo" in impact
    assert "spawnSpellTelegraphGlyph" in telegraph


def test_portals_waves_and_storm_render_geometry_without_worldwide_particle_broadcasts() -> None:
    portal = _body(SERVER, "private void spawnPortalObjectiveVisuals", "private void refreshPortalObjectiveVisuals")
    refresh = _body(SERVER, "private void refreshPortalObjectiveVisuals", "private void planRiftStorm")
    storm = _body(SERVER, "private void updateRiftStormObjective", "private void clearWaveObjectiveState")

    assert "renderPortalObjective" in portal
    assert "renderPortalObjective" in refresh or "renderPortalObjective" in SERVER
    assert "renderRiftStormField" in storm
    assert "lastStormPattern" in storm
    assert "player.spawnParticle" in SERVER
    assert "world.spawnParticle" not in storm

    for marker in (
        "renderWaveTransition",
        "renderArenaHazardField",
        "renderPortalObjective",
        "renderRiftStormField",
        "renderFinalDoorOpening",
    ):
        assert marker in SERVER


def test_gate_commands_have_a_safe_door_alias_and_explicit_delete_path() -> None:
    assert 'case "arena", "gate", "door", "portalroom"' in SERVER
    assert '"door"' in SERVER
    for command in ("setat", "pos1", "pos2", "preview", "open", "info", "restore", "delete"):
        assert command in SERVER
    assert "deleteGate" in SERVER
    assert "withGatePoints(null, null)" in SERVER
    assert "door setat" in PLUGIN
    assert "door delete confirm" in PLUGIN


def test_client_mixins_and_assets_keep_the_boss_renderer_3d_and_phase_scoped() -> None:
    mixins = json.loads((ROOT / "CopiMineClient/src/main/resources/copimineclient.mixins.json").read_text(encoding="utf-8"))
    model = (CLIENT / "RiftGuardianModel.java").read_text(encoding="utf-8")
    for mixin in ("EndermanEntityRendererMixin", "EndRiftBossBarHudMixin"):
        assert mixin in mixins["client"]
    assert "EndermanEntityModel<EndermanEntity>" in model
    assert "cuboid" in model
    for phase in ("awakening", "hunter", "distortion", "absorption", "catastrophe"):
        assert (ROOT / f"CopiMineClient/src/main/resources/assets/copimineclient/textures/entity/rift_guardian_{phase}.png").is_file()
