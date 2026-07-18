"""Create the tiny pixel-art status icons used by the narcotics UI and client mod."""
from pathlib import Path

from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parents[1]
TARGETS = [
    ROOT / "resourcepacks" / "src" / "assets" / "copimine" / "textures" / "mob_effect",
    ROOT / "CopiMineClient" / "src" / "main" / "resources" / "assets" / "copimineclient" / "textures" / "effects",
    ROOT / "admin-web" / "frontend" / "assets" / "effect-icons",
]


def icon(kind: str) -> Image.Image:
    image = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    if kind == "overdose":
        outer, inner, accent = (48, 14, 42, 255), (208, 58, 119, 255), (255, 184, 74, 255)
        draw.rectangle((4, 4, 27, 27), fill=outer)
        draw.rectangle((7, 7, 24, 24), fill=inner)
        draw.rectangle((10, 11, 13, 15), fill=outer)
        draw.rectangle((18, 11, 21, 15), fill=outer)
        draw.rectangle((12, 19, 19, 22), fill=outer)
        draw.rectangle((14, 8, 17, 10), fill=accent)
        draw.point((8, 8), fill=accent)
        draw.point((23, 8), fill=accent)
    else:
        outer, inner, accent = (13, 48, 55, 255), (64, 190, 169, 255), (128, 232, 216, 255)
        draw.rectangle((4, 4, 27, 27), fill=outer)
        draw.rectangle((7, 7, 24, 24), fill=inner)
        draw.arc((9, 9, 23, 23), 25, 315, fill=outer, width=3)
        draw.line((16, 9, 16, 23), fill=accent, width=2)
        draw.line((10, 16, 22, 16), fill=accent, width=2)
        draw.point((8, 8), fill=accent)
        draw.point((23, 8), fill=accent)
    return image.resize((16, 16), Image.Resampling.NEAREST)


def main() -> None:
    for target in TARGETS:
        target.mkdir(parents=True, exist_ok=True)
        for kind in ("overdose", "zhuzevo_trip"):
            icon(kind).save(target / f"{kind}.png", format="PNG", optimize=True)


if __name__ == "__main__":
    main()
