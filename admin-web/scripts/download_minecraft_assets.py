#!/usr/bin/env python3
"""Download real vanilla Minecraft item/block textures for CopiMine Admin inventory icons.

Usage:
  python scripts/download_minecraft_assets.py --version 1.21.1

It downloads the official client JAR from Mojang's version manifest, extracts
assets/minecraft/textures/item/*.png and assets/minecraft/textures/block/*.png,
and writes them to frontend/assets/mc-icons/item/.
"""
from __future__ import annotations
import argparse
import json
import shutil
import sys
import tempfile
import urllib.request
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "frontend" / "assets" / "mc-icons" / "item"
MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"


def get_json(url: str) -> dict:
    with urllib.request.urlopen(url, timeout=60) as r:
        return json.loads(r.read().decode("utf-8"))


def download(url: str, path: Path) -> None:
    with urllib.request.urlopen(url, timeout=180) as r, path.open("wb") as f:
        shutil.copyfileobj(r, f)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--version", default="1.21.1", help="Minecraft version, default: 1.21.1")
    ap.add_argument("--force", action="store_true", help="Clear existing icons first")
    args = ap.parse_args()

    OUT.mkdir(parents=True, exist_ok=True)
    if args.force and OUT.exists():
        shutil.rmtree(OUT)
        OUT.mkdir(parents=True, exist_ok=True)

    print(f"[1/4] Reading version manifest: {MANIFEST}")
    manifest = get_json(MANIFEST)
    item = next((v for v in manifest.get("versions", []) if v.get("id") == args.version), None)
    if not item:
        print(f"Version not found: {args.version}", file=sys.stderr)
        return 2

    print(f"[2/4] Reading version metadata: {item['url']}")
    meta = get_json(item["url"])
    client_url = meta.get("downloads", {}).get("client", {}).get("url")
    if not client_url:
        print("Client JAR URL not found in metadata", file=sys.stderr)
        return 3

    with tempfile.TemporaryDirectory() as td:
        jar = Path(td) / f"minecraft-{args.version}-client.jar"
        print(f"[3/4] Downloading client JAR: {client_url}")
        download(client_url, jar)
        count = 0
        print(f"[4/4] Extracting textures to: {OUT}")
        with zipfile.ZipFile(jar) as z:
            for info in z.infolist():
                name = info.filename
                if not name.endswith(".png"):
                    continue
                if name.startswith("assets/minecraft/textures/item/"):
                    target_name = Path(name).name
                elif name.startswith("assets/minecraft/textures/block/"):
                    # A lot of inventory items use block textures through model JSON.
                    target_name = Path(name).name
                else:
                    continue
                if target_name.endswith(".png"):
                    with z.open(info) as src, (OUT / target_name).open("wb") as dst:
                        shutil.copyfileobj(src, dst)
                    count += 1
    print(f"Done. Extracted {count} PNG textures.")
    print("Now transfer the whole copimine-admin folder/archive to the server.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
