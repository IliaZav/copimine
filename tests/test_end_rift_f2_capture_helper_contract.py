from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CAPTURE = ROOT / "tools" / "end-rift-native-visual" / "CaptureEndRiftF2.ps1"


def test_f2_capture_helper_is_locked_to_the_isolated_end_rift_server():
    script = CAPTURE.read_text(encoding="utf-8")

    assert "codex/end-rift-event" in script
    assert "127.0.0.1:25566" in script
    assert "25576" in script
    assert "local-runtime" in script
    assert "environment:\\s*local" in script
    assert "InvokeEndRiftLocalRcon.ps1" in script
    assert "branch --show-current" in script
    assert "server-ip" in script and "127.0.0.1" in script
    assert "rcon.ip" in script


def test_f2_capture_helper_requires_a_live_camera_and_recreates_the_showroom():
    script = CAPTURE.read_text(encoding="utf-8")

    assert "Get-MinecraftWindow" in script
    assert "Win32_Process" in script
    assert "--gameDir" in script
    assert "ClientGameDirectory" in script
    assert "Invoke-EndRiftRcon" in script
    assert "list" in script
    assert "CameraPlayer" in script
    assert r"\u00A7." in script
    assert "Set CameraPlayer in the local visual config" in script
    assert "Configured CameraPlayer is not connected" in script
    assert "cmend test showroom" in script
    assert "cmend test visuals mobs" in script
    assert "boundViewers" in script
    assert "minecraft:tp" in script
    assert "07_CaptureViaMinecraftF2.ps1" in script
    assert "$expectedServerDir" in script
    assert "OutputDirectory must stay under" in script or "artifacts directory" in script
    assert "Refused to overwrite existing evidence file" in script


def test_f2_capture_helper_captures_named_elites_and_hashes_the_original_pngs():
    script = CAPTURE.read_text(encoding="utf-8")

    for name in (
        "showroom-overview",
        "elite-enderman",
        "elite-skeleton",
        "elite-spider",
        "tentacles-ready",
    ):
        assert name in script
    for visual_id in (
        "END_RIFT_ELITE_V1",
        "END_RIFT_ELITE_SKELETON_V1",
        "END_RIFT_ELITE_SPIDER_V1",
    ):
        assert visual_id in script
    for texture in (
        "end_rift_elite.png",
        "end_rift_elite_skeleton.png",
        "end_rift_elite_spider.png",
    ):
        assert texture in script
    assert "Get-FileHash" in script
    assert "SHA256" in script
    assert "manifest.json" in script


def test_f2_capture_helper_frames_targets_with_night_vision_and_restores_camera_state():
    script = CAPTURE.read_text(encoding="utf-8")

    assert "time query daytime" in script
    assert "time set midnight" in script
    assert "time set $originalDayTime" in script
    assert "minecraft:night_vision" in script
    assert "InitialPerspective" in script
    assert "HudVisibleAtStart" in script
    assert "f5ToFirstPerson" in script
    assert "viewTogglesApplied" in script
    assert "finally" in script
    assert "-Chord 'F5'" in script
    assert "-Chord 'F1'" in script
    assert "coreZ - 16.0" in script
    assert "CameraDistance" in script
    assert "NoAI:1b,CustomNameVisible:0b" in script


def test_tentacle_capture_faces_from_the_east_camera_back_toward_the_core():
    script = CAPTURE.read_text(encoding="utf-8")

    assert "Move-Camera ($coreX + 10.0) $coreY $coreZ 90 0" in script


def test_elite_capture_frames_each_mob_in_place_from_outside_its_radial_position():
    script = CAPTURE.read_text(encoding="utf-8")

    assert "$radialX = $entityPosition.X - $coreX" in script
    assert "$radialZ = $entityPosition.Z - $coreZ" in script
    assert "$cameraYaw = [int][Math]::Round([Math]::Atan2($radialX, -$radialZ)" in script
    assert "Move-Camera $cameraX $entityPosition.Y $cameraZ $cameraYaw $expected.Pitch" in script
    assert "$targetAfter = Get-Vector (Invoke-Local \"data get entity $entityUuid Pos\")" in script
    assert "$targetPosition =" not in script
    assert "$restoreEntityPosition =" not in script
