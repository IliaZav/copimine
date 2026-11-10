"""Actual generated frame and HUD sampling coordinates must describe the same asset."""
from pathlib import Path
import re
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]


def test_mouth_frame_is_the_real_transparent_hud_resource():
    source = (ROOT / "tools/minecraft-26.3/client/src/main/java/me/copimine/client/EndRiftBossBarHud.java").read_text(encoding="utf-8")
    name = "end_rift_bossbar_frame_v3.png"
    assert name in source, "the generated mouth frame is not used by the actual HUD"
    image = Image.open(ROOT / "CopiMineClient/src/main/resources/assets/copimineclient/textures/gui" / name)
    constants = {name: int(value) for name, value in re.findall(r"private static final int (\w+) = (\d+);", source)}
    assert image.mode == "RGBA"
    assert image.size == (constants["SOURCE_WIDTH"], constants["SOURCE_HEIGHT"])
    assert image.getchannel("A").getextrema() == (0, 255)
    left = round(constants["INNER_LEFT"] * image.width / constants["WIDTH"])
    right = round(constants["INNER_RIGHT"] * image.width / constants["WIDTH"])
    top = round((constants["INNER_TOP"] - constants["FRAME_Y"]) * image.height / constants["FRAME_HEIGHT"])
    bottom = round((constants["INNER_BOTTOM"] - constants["FRAME_Y"]) * image.height / constants["FRAME_HEIGHT"])
    assert 0 < left < right < image.width and 0 < top < bottom < image.height
    histogram = image.getchannel("A").crop((left, top, right, bottom)).histogram()
    assert sum(histogram[:16]) / sum(histogram) > 0.995, "mouth frame obscures dynamic health track"
    assert "REAL HP" not in source, "implementation jargon leaked into the player HUD"


def test_portrait_is_rendered_from_the_actual_supplied_head():
    import hashlib
    import json
    assets = ROOT / "CopiMineClient/src/main/resources/assets/copimineclient"
    portrait = assets / "textures/gui/end_rift_guardian_head_portrait.png"
    manifest = json.loads(portrait.with_suffix(".manifest.json").read_text())
    assert manifest["bone"] == "head" and manifest["cubes"] == 37
    assert manifest["geometrySha256"] == hashlib.sha256((assets / "models/entity/end_rift_guardian/geometry.json").read_bytes()).hexdigest()
    assert manifest["textureSha256"] == hashlib.sha256((assets / "textures/entity/end_rift_user_boss.png").read_bytes()).hexdigest()
    assert manifest["portraitSha256"] == hashlib.sha256(portrait.read_bytes()).hexdigest()
    image = Image.open(portrait)
    assert image.size == (128,128) and image.mode == "RGBA"
    assert image.getchannel("A").getextrema() == (0,255)
    source = (ROOT / "tools/minecraft-26.3/client/src/main/java/me/copimine/client/EndRiftBossBarHud.java").read_text(encoding="utf-8")
    assert portrait.name in source and 'GUI_TEXTURED, PORTRAIT' in source
