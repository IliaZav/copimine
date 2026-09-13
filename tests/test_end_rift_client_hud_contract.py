from pathlib import Path
import re


ROOT = Path(__file__).resolve().parents[1]
HUD = ROOT / "CopiMineClient" / "src" / "main" / "java" / "me" / "copimine" / "client" / "EndRiftBossBarHud.java"


def _constant(source: str, name: str) -> int:
    match = re.search(rf"private static final int {name} = (\d+);", source)
    assert match, f"missing {name} constant"
    return int(match.group(1))


def test_end_rift_boss_hud_is_compact_and_displays_authoritative_health():
    source = HUD.read_text(encoding="utf-8")

    # The old 384x128 solid purple panel occupied most of a normal GUI-scale
    # screen. Keep the bespoke HUD present but bounded and readable.
    assert _constant(source, "WIDTH") <= 320
    assert _constant(source, "HEIGHT") <= 100
    assert "private static final int SEGMENT_COUNT" in source
    assert "private static final float[] PHASE_MARKERS" in source
    assert "PHASE_MARKERS.length == 5" in source
    assert "state.health()" in source
    assert "state.maxHealth()" in source
    assert "SOURCE_WIDTH = 256" in source
    assert "SOURCE_HEIGHT = 32" in source
    assert "formatHealth(" in source
    assert "drawPhaseMarker(" in source
