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
