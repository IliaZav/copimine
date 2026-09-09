from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SERVER = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"
CLIENT = ROOT / "CopiMineClient/src/main/java/me/copimine/client"
PACKET = CLIENT / "EndEventPacket.java"
BRIDGE = CLIENT / "ClientBridgeProtocol.java"
VFX = CLIENT / "EndEventWorldVfxManager.java"
MAIN = CLIENT / "CopiMineClient.java"


def _read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def test_server_has_bounded_world_space_beam_transport_and_cleanup():
    body = _read(SERVER)
    assert '"END_WORLD_BEAM"' in body
    assert '"END_WORLD_VFX_CLEAR"' in body
    assert "sendWorldBeamPacket" in body
    assert "clearWorldVfx" in body
    assert "activeWorldVfxInstances" in body
    assert "MAX_ACTIVE_WORLD_VFX" in body
    assert "sendWorldBeamPacket(viewer" in body
    assert "sendWorldBeamPacket(player" in body


def test_client_parses_and_renders_bounded_world_space_beams():
    packet = _read(PACKET)
    bridge = _read(BRIDGE)
    vfx = _read(VFX)
    main = _read(MAIN)
    assert '"END_WORLD_BEAM"' in packet
    assert '"END_WORLD_VFX_CLEAR"' in packet
    assert "EndEventWorldVfxManager" in bridge
    assert "WorldRenderEvents.LAST" in main
    assert "RenderLayer.getLines" in vfx
    assert "MAX_ACTIVE_BEAMS" in vfx
    assert "clearEvent" in vfx
    assert "durationMillis" in vfx
    assert "startedAtMillis" in vfx
    assert "drawFadedLine" in vfx
    assert "flowPhase" in vfx
    assert "finite" in vfx.lower()


def test_official_beams_do_not_use_particle_line_helper():
    body = _read(SERVER)
    carrier = body[body.index("private void tickV2CarrierObjective"):body.index("private void tickV2HuntObjective")]
    cast = body[body.index("private void renderBossCastState"):body.index("private void cancelBossCastTask")]
    final_strike = body[body.index("private void renderBossFinalStrike"):body.index("private void executeBossFinalStrikeImpact")]
    defeat = body[body.index("private void renderBossDefeatCinematic"):body.index("private void completeBossDefeatCinematic")]
    final_scene = body[body.index("private void renderFinalArenaScene"):body.index("private void clearFinalArenaScene")]
    final_ritual = body[body.index("private void scheduleFinalRitualVisual"):body.index("private void castBossSpell")]
    wave5 = body[body.index("private void tickV2Wave5Encounter"):body.index("private void tickV2Wave5GuardTargets")]
    safe_zones = body[body.index("private void renderV2WaveObjective"):body.index("private void startWaveObjective")]
    obelisks = body[body.index("private void tickRiftObelisks"):body.index("private void tickRiftFireballs")]
    boss_cue = body[body.index("private void renderBossVisualCue"):body.index("private void resetBossVisualCue")]
    assert "spawnPatternSegment" not in carrier
    assert "spawnParticleLine" not in cast
    assert "spawnParticleLine" not in final_strike
    assert "sendWorldBeamPacket" in carrier
    assert "sendWorldBeamPacket" in cast
    assert "sendWorldBeamPacket" in final_strike
    assert "spawnParticleLine" not in defeat
    assert "spawnParticleLine" not in final_scene
    assert "spawnParticleLine" not in final_ritual
    assert "sendWorldBeamPacket" in defeat
    assert "sendWorldBeamPacket" in final_scene
    assert "sendWorldBeamPacket" in final_ritual
    assert "sendWorldBeamPacket" in wave5
    assert "wave5-prisoner-core" in wave5
    assert "wave5-guard-elite-" in wave5
    assert "wave5-core-buff-" in wave5
    assert "sendWorldBeamPacket" in safe_zones
    assert "wave4-safe-zone-" in safe_zones
    assert "sendWorldBeamPacket" in obelisks
    assert "obelisk-link-" in obelisks
    assert "sendWorldBeamPacket" in boss_cue
    assert "spawnPatternSegment" not in boss_cue
    assert "spawnParticleLine" not in body
