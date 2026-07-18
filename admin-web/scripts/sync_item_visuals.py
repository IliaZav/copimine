#!/usr/bin/env python3
"""Build browser-ready previews from the verified CopiMine resource-pack textures."""
from __future__ import annotations

import json
import sys
from pathlib import Path

try:
    from PIL import Image, ImageChops, ImageDraw
except ImportError as exc:  # pragma: no cover - documented deployment dependency
    raise SystemExit("Pillow is required. Install admin-web/requirements.txt first.") from exc


ROOT = Path(__file__).resolve().parents[2]
MAPPING_FILE = ROOT / "resourcepacks" / "item_texture_sources.json"
MODELS_FILE = ROOT / "resourcepacks" / "models_manifest.json"
TEXTURES_DIR = ROOT / "resourcepacks" / "src" / "assets" / "copimine" / "textures" / "item" / "artifacts"
OUTPUT_DIR = ROOT / "admin-web" / "frontend" / "assets" / "item-textures"
SHIELD_PREVIEW_ITEM_ID = "ne_segodnya_suka_shield"


def read_json(path: Path) -> dict:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise SystemExit(f"Cannot read {path}: {exc}") from exc


def preview_image(source: Path, destination: Path) -> None:
    with Image.open(source) as image:
        image.load()
        width, height = image.size
        if width <= 0 or height < width:
            raise SystemExit(f"Unexpected texture size for {source}: {image.size}")
        # Minecraft animation strips are vertical. The website deliberately shows
        # the first complete frame so a card never renders as a tall sprite sheet.
        preview = image.crop((0, 0, width, width)) if height > width else image.copy()
        preview.convert("RGBA").save(destination, format="PNG", optimize=True)


def shield_preview(source: Path, destination: Path) -> None:
    """Turn the shield entity UV texture into the compact icon used by the website."""
    with Image.open(source) as image:
        source_image = image.convert("RGBA")
        if source_image.width < 17 or source_image.height < 23:
            raise SystemExit(f"Unexpected shield texture size for {source}: {source_image.size}")

        # The left panel is the visible front of the shield. The remaining parts
        # are UV faces and the handle, which are useful in Minecraft but not in a shop card.
        front = source_image.crop((3, 0, 15, 23)).resize((20, 27), Image.Resampling.NEAREST)
        preview = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
        preview.paste(front, (6, 2))

        mask = Image.new("L", preview.size, 0)
        ImageDraw.Draw(mask).polygon(
            [(7, 2), (24, 2), (24, 20), (22, 25), (19, 28), (16, 30), (13, 28), (9, 25), (7, 20)],
            fill=255,
        )
        preview.putalpha(ImageChops.multiply(preview.getchannel("A"), mask))
        preview.save(destination, format="PNG", optimize=True)


def main() -> None:
    mapping = read_json(MAPPING_FILE)
    models = read_json(MODELS_FILE)
    model_ids = {
        str(entry.get("id") or "").strip().lower()
        for entry in models.get("items", [])
        if isinstance(entry, dict)
    }
    entries = [entry for entry in mapping.get("items", []) if isinstance(entry, dict)]
    if not entries:
        raise SystemExit("Texture mapping has no items.")

    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    synced = 0
    for entry in entries:
        item_id = str(entry.get("id") or "").strip().lower()
        if not item_id:
            raise SystemExit("Texture mapping contains an empty item id.")
        if item_id not in model_ids:
            raise SystemExit(f"Resource-pack model is missing for {item_id}.")
        source = TEXTURES_DIR / f"{item_id}.png"
        if not source.is_file():
            raise SystemExit(f"Resource-pack texture is missing for {item_id}: {source}")
        destination = OUTPUT_DIR / f"{item_id}.png"
        if item_id == SHIELD_PREVIEW_ITEM_ID:
            shield_preview(source, destination)
        else:
            preview_image(source, destination)
        synced += 1
    print(f"Synced {synced} website item textures to {OUTPUT_DIR}")


if __name__ == "__main__":
    main()
