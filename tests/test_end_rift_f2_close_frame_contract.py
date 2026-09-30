from pathlib import Path
import re


ROOT = Path(__file__).resolve().parents[1]
CAPTURE = ROOT / "tools" / "end-rift-native-visual" / "CaptureEndRiftF2.ps1"


def test_f2_showroom_waits_for_the_previous_screenshot_notice_to_clear():
    script = CAPTURE.read_text(encoding="utf-8")
    assert "[ValidateRange(0, 30)][int]$PostCaptureSettleSeconds = 8" in script

    save_f2 = script.split("function Save-F2", 1)[1].split("$dayTimeReply", 1)[0]
    settle = "Start-Sleep -Seconds $PostCaptureSettleSeconds"
    assert settle in save_f2
    assert save_f2.index(settle) > save_f2.index("$captures.Add")


def test_f2_showroom_frames_each_elite_at_close_camera_distance():
    script = CAPTURE.read_text(encoding="utf-8")
    limits = {
        "END_RIFT_ELITE_V1": 3.5,
        "END_RIFT_ELITE_SKELETON_V1": 3.2,
        "END_RIFT_ELITE_SPIDER_V1": 2.8,
    }

    for visual_id, maximum in limits.items():
        match = re.search(
            rf"Id = '{re.escape(visual_id)}';.*?CameraDistance = ([0-9.]+)",
            script,
        )
        assert match, f"missing camera distance for {visual_id}"
        assert float(match.group(1)) <= maximum, f"{visual_id} is framed too far away"
