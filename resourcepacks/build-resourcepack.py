from __future__ import annotations

import hashlib
import json
import shutil
import struct
import zlib
from pathlib import Path
from zipfile import ZIP_DEFLATED, ZipFile


ROOT = Path(__file__).resolve().parent
SRC = ROOT / "src"
BUILD = ROOT / "build"
STAGE = BUILD / "_stage"
COMBINED_MANIFEST = ROOT / "models_manifest.json"
SERVER_PROPERTIES = ROOT.parent / "minecraft" / "server" / "server.properties"

REQUIRED_SOURCE_FILES = [
    "pack.mcmeta",
    "assets/minecraft/font/default.json",
    "assets/copimine/manifests/block_visuals_manifest.json",
    "assets/copimine/manifests/narcotics_items_manifest.json",
    "assets/copimine/manifests/narcotics_visuals_manifest.json",
    "assets/copimine/font/narcotics_overlay.json",
    "assets/copimine/font/logo.json",
    "assets/copimine/models/item/feta.json",
    "assets/copimine/models/item/kola.json",
    "assets/copimine/models/item/girion.json",
    "assets/copimine/models/item/sbp.json",
    "assets/copimine/models/item/sos.json",
    "assets/copimine/models/item/drun.json",
    "assets/copimine/models/item/chups.json",
    "assets/copimine/models/item/borshevik.json",
    "assets/copimine/models/item/zhuzevo.json",
    "assets/copimine/models/item/zmei_gorynych.json",
    "assets/copimine/models/item/block/atm_terminal.json",
    "assets/copimine/models/item/block/polling_station_marker.json",
    "assets/copimine/models/item/block/tax_office_marker.json",
    "assets/copimine/models/item/block/artifact_shop_marker.json",
    "assets/copimine/textures/item/narcotics/feta.png",
    "assets/copimine/textures/item/narcotics/kola.png",
    "assets/copimine/textures/item/narcotics/girion.png",
    "assets/copimine/textures/item/narcotics/sbp.png",
    "assets/copimine/textures/item/narcotics/sos.png",
    "assets/copimine/textures/item/narcotics/drun.png",
    "assets/copimine/textures/item/narcotics/chups.png",
    "assets/copimine/textures/item/narcotics/borshevik.png",
    "assets/copimine/textures/item/narcotics/zhuzevo.png",
    "assets/copimine/textures/item/zmei_gorynych.png",
    "assets/copimine/textures/block/atm_terminal.png",
    "assets/copimine/textures/block/polling_station_marker.png",
    "assets/copimine/textures/block/tax_office_marker.png",
    "assets/copimine/textures/block/artifact_shop_marker.png",
    "assets/copimine/textures/gui/tab/minecraft_title.png",
    "assets/copimine/textures/gui/tab/copimine_logo.png",
    "assets/copimine/textures/gui/narcotics/desaturate_overlay.png",
    "assets/copimine/textures/gui/narcotics/color_convolve_overlay.png",
    "assets/copimine/textures/gui/narcotics/scan_pincushion_overlay.png",
    "assets/copimine/textures/gui/narcotics/green_noise_overlay.png",
    "assets/copimine/textures/gui/narcotics/invert_overlay.png",
    "assets/copimine/textures/gui/narcotics/wobble_overlay.png",
    "assets/copimine/textures/gui/narcotics/blobs_overlay.png",
    "assets/copimine/textures/gui/narcotics/pencil_overlay.png",
    "assets/copimine/textures/gui/narcotics/chaos_overlay.png",
    "assets/copimine/shaders/narcotics/desaturate.json",
    "assets/copimine/shaders/narcotics/color_convolve.json",
    "assets/copimine/shaders/narcotics/scan_pincushion.json",
    "assets/copimine/shaders/narcotics/green_noise.json",
    "assets/copimine/shaders/narcotics/invert.json",
    "assets/copimine/shaders/narcotics/wobble.json",
    "assets/copimine/shaders/narcotics/blobs.json",
    "assets/copimine/shaders/narcotics/pencil.json",
    "assets/copimine/shaders/narcotics/chaos.json",
]


def png_chunk(chunk_type: bytes, data: bytes) -> bytes:
    return (
        struct.pack(">I", len(data))
        + chunk_type
        + data
        + struct.pack(">I", zlib.crc32(chunk_type + data) & 0xFFFFFFFF)
    )


def solid_png(path: Path, color: tuple[int, int, int, int], size: int = 32) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    row = bytes([0] + [component for _ in range(size) for component in color])
    raw = row * size
    payload = b"".join(
        [
            b"\x89PNG\r\n\x1a\n",
            png_chunk(b"IHDR", struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0)),
            png_chunk(b"IDAT", zlib.compress(raw, 9)),
            png_chunk(b"IEND", b""),
        ]
    )
    path.write_bytes(payload)


def write_json(path: Path, payload: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def update_server_properties_sha1(sha1: str) -> None:
    if not SERVER_PROPERTIES.is_file():
        return
    lines = SERVER_PROPERTIES.read_text(encoding="utf-8").splitlines()
    output: list[str] = []
    updated = False
    for line in lines:
        if line.startswith("resource-pack-sha1="):
            output.append(f"resource-pack-sha1={sha1}")
            updated = True
        else:
            output.append(line)
    if not updated:
        output.append(f"resource-pack-sha1={sha1}")
    SERVER_PROPERTIES.write_text("\n".join(output) + "\n", encoding="utf-8")


def asset_path(root: Path, kind: str, asset_ref: str, suffix: str) -> Path:
    namespace, relative = asset_ref.split(":", 1) if ":" in asset_ref else ("copimine", asset_ref)
    return root / "assets" / namespace / kind / Path(relative + suffix)


def validate_source_tree() -> None:
    missing = [relative for relative in REQUIRED_SOURCE_FILES if not (SRC / relative).is_file()]
    if missing:
        raise FileNotFoundError("Missing source resource pack files:\n - " + "\n - ".join(missing))

    pack_meta = json.loads((SRC / "pack.mcmeta").read_text(encoding="utf-8"))
    if pack_meta.get("pack", {}).get("pack_format") != 34:
        raise ValueError("pack.mcmeta pack_format must remain 34 for Minecraft 1.21.x / 1.21.1.")

    for relative in REQUIRED_SOURCE_FILES:
        if relative.endswith(".json") or relative.endswith(".mcmeta"):
            json.loads((SRC / relative).read_text(encoding="utf-8-sig"))

    forbidden = list((SRC / "assets" / "minecraft" / "textures" / "block").rglob("*")) if (SRC / "assets" / "minecraft" / "textures" / "block").exists() else []
    if forbidden:
        raise ValueError("Global vanilla block texture overrides are not allowed in this resource pack.")


def build_stage() -> None:
    validate_source_tree()
    if STAGE.exists():
        shutil.rmtree(STAGE)
    shutil.copytree(SRC, STAGE)

    pack_png = STAGE / "pack.png"
    if not pack_png.exists():
        solid_png(pack_png, (87, 132, 52, 255), 32)

    manifest = json.loads(COMBINED_MANIFEST.read_text(encoding="utf-8-sig"))
    grouped: dict[str, list[dict]] = {}
    for item in manifest["items"]:
        grouped.setdefault(item["base_material"], []).append(item)
        model_path = asset_path(STAGE, "models", item["model"], ".json")
        texture_path = asset_path(STAGE, "textures", item["texture"], ".png")
        if not model_path.is_file():
            raise FileNotFoundError(f"Missing model referenced by models_manifest.json: {model_path}")
        if not texture_path.is_file():
            raise FileNotFoundError(f"Missing texture referenced by models_manifest.json: {texture_path}")

    for material, entries in grouped.items():
        overrides = [
            {
                "predicate": {"custom_model_data": entry["custom_model_data"]},
                "model": entry["model"],
            }
            for entry in sorted(entries, key=lambda x: x["custom_model_data"])
        ]
        write_json(
            STAGE / "assets" / "minecraft" / "models" / "item" / f"{material}.json",
            {
                "parent": "minecraft:item/generated",
                "textures": {"layer0": f"minecraft:item/{material}"},
                "overrides": overrides,
            },
        )


def pack_zip() -> tuple[Path, str]:
    BUILD.mkdir(parents=True, exist_ok=True)
    zip_path = BUILD / "CopiMineResourcePack.zip"
    if zip_path.exists():
        zip_path.unlink()
    with ZipFile(zip_path, "w", compression=ZIP_DEFLATED) as zf:
        for file in sorted(STAGE.rglob("*")):
            if file.is_file():
                zf.write(file, file.relative_to(STAGE).as_posix())

    with ZipFile(zip_path, "r") as zf:
        broken = zf.testzip()
        if broken is not None:
            raise RuntimeError(f"Zip integrity check failed on {broken}")

    sha1 = hashlib.sha1(zip_path.read_bytes()).hexdigest()
    (BUILD / "CopiMineResourcePack.sha1").write_text(sha1 + "\n", encoding="utf-8")
    return zip_path, sha1


def main() -> None:
    build_stage()
    zip_path, sha1 = pack_zip()
    update_server_properties_sha1(sha1)
    print(f"Built {zip_path}")
    print(f"SHA1 {sha1}")


if __name__ == "__main__":
    main()
