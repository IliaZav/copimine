from pathlib import Path
import re
import struct


ROOT = Path(__file__).resolve().parents[1]
RENDERER = ROOT / "CopiMineClient/src/main/java/me/copimine/client/PrisonerHudRenderer.java"
RESOURCE_ROOT = ROOT / "CopiMineClient/src/main/resources/assets/copimineclient/textures/gui"


def test_prisoner_hud_icons_are_available_as_source_resources():
    renderer = RENDERER.read_text(encoding="utf-8")
    icon_names = re.findall(r'icon\("([^"]+\.png)"\)', renderer)

    assert icon_names == [
        "end_rift_prisoner_heal.png",
        "end_rift_prisoner_surge.png",
        "end_rift_prisoner_guardian.png",
        "end_rift_prisoner_turncoat.png",
    ]
    for icon_name in icon_names:
        image = (RESOURCE_ROOT / icon_name).read_bytes()
        assert image.startswith(b"\x89PNG\r\n\x1a\n"), f"{icon_name} is not a PNG"
        width, height = struct.unpack(">II", image[16:24])
        assert (width, height) == (32, 32), f"{icon_name} has unexpected dimensions"
