#!/usr/bin/env python3
"""Build browser-ready previews from the verified CopiMine resource-pack textures."""
from __future__ import annotations

import json
import sys
from pathlib import Path

try:
    from PIL import Image
except ImportError as exc:  # pragma: no cover - documented deployment dependency
    raise SystemExit("Pillow is required. Install admin-web/requirements.txt first.") from exc


ROOT = Path(__file__).resolve().parents[2]
MAPPING_FILE = ROOT / "resourcepacks" / "item_texture_sources.json"
MODELS_FILE = ROOT / "resourcepacks" / "models_manifest.json"
TEXTURES_DIR = ROOT / "resourcepacks" / "src" / "assets" / "copimine" / "textures" / "item" / "artifacts"
OUTPUT_DIR = ROOT / "admin-web" / "frontend" / "assets" / "item-textures"


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
        preview_image(source, OUTPUT_DIR / f"{item_id}.png")
        synced += 1
    print(f"Synced {synced} website item textures to {OUTPUT_DIR}")


if __name__ == "__main__":
    main()
