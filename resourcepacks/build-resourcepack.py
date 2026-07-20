from __future__ import annotations

import hashlib
import json
import re
import shutil
import struct
import zlib
from pathlib import Path
from zipfile import ZIP_STORED, ZipFile, ZipInfo

try:
    from PIL import Image
except ModuleNotFoundError as exc:  # pragma: no cover - release environment check
    raise RuntimeError("Pillow is required to build directional compass/clock frames") from exc


ROOT = Path(__file__).resolve().parent
SRC = ROOT / "src"
BUILD = ROOT / "build"
STAGE = BUILD / "_stage"
COMBINED_MANIFEST = ROOT / "models_manifest.json"
TEXTURE_SOURCES = ROOT / "item_texture_sources.json"
SERVER_PROPERTIES = ROOT.parent / "minecraft" / "server" / "server.properties"
DEFAULT_RESOURCE_PACK_URL = r"http\://copimine.ru\:18080/resourcepacks/CopiMineResourcePack.zip"
DEFAULT_WORLD_SEED = "-1861153001556076901"
RESOURCE_PACK_ZIP_TIMESTAMP = (2024, 1, 1, 0, 0, 0)
RESOURCE_PACK_TEXT_SUFFIXES = {".json", ".mcmeta"}

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
    write_utf8_lf(path, json.dumps(payload, ensure_ascii=False, indent=2) + "\n")


def write_utf8_lf(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="\n") as handle:
        handle.write(content)


def normalize_stage_text_files() -> None:
    for path in sorted(STAGE.rglob("*")):
        if not path.is_file() or path.suffix.lower() not in RESOURCE_PACK_TEXT_SUFFIXES:
            continue
        normalized = path.read_text(encoding="utf-8-sig").replace("\r\n", "\n").replace("\r", "\n")
        write_utf8_lf(path, normalized)


def update_server_properties_sha1(sha1: str) -> None:
    if not SERVER_PROPERTIES.is_file():
        return
    lines = SERVER_PROPERTIES.read_text(encoding="utf-8").splitlines()
    output: list[str] = []
    updated = False
    seen: set[str] = set()
    for line in lines:
        if line.startswith("resource-pack-sha1="):
            output.append(f"resource-pack-sha1={sha1}")
            seen.add("resource-pack-sha1")
        elif line.startswith("resource-pack="):
            output.append(f"resource-pack={DEFAULT_RESOURCE_PACK_URL}")
            seen.add("resource-pack")
        elif line.startswith("level-seed="):
            output.append(f"level-seed={DEFAULT_WORLD_SEED}")
            seen.add("level-seed")
        else:
            output.append(line)
    if "resource-pack-sha1" not in seen:
        output.append(f"resource-pack-sha1={sha1}")
    if "resource-pack" not in seen:
        output.append(f"resource-pack={DEFAULT_RESOURCE_PACK_URL}")
    if "level-seed" not in seen:
        output.append(f"level-seed={DEFAULT_WORLD_SEED}")
    # Keep the tracked properties file LF-normalized on Windows and Linux;
    # otherwise every pack build leaves a spurious CRLF-only worktree change.
    write_utf8_lf(SERVER_PROPERTIES, "\n".join(output) + "\n")


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

    validate_catalog_mapping()


def validate_catalog_mapping() -> None:
    artifacts = ROOT.parent / "copimine-artifacts" / "items.yml"
    if not artifacts.is_file() or not TEXTURE_SOURCES.is_file():
        raise FileNotFoundError("Artifact catalog and item_texture_sources.json are required")
    catalog_items = read_catalog_items(artifacts)
    sources = json.loads(TEXTURE_SOURCES.read_text(encoding="utf-8-sig"))
    manifest = json.loads(COMBINED_MANIFEST.read_text(encoding="utf-8-sig"))
    source_rows = {str(row.get("id")): row for row in sources.get("items", [])}
    manifest_rows = manifest.get("items", [])
    manifest_keys = {(str(row["base_material"]).upper(), int(row["custom_model_data"])) for row in manifest_rows}
    if len(manifest_keys) != len(manifest_rows):
        raise ValueError("Duplicate (base_material, custom_model_data) in models_manifest.json")
    missing = []
    for item in catalog_items:
        item_id = str(item.get("id") or item.get("item-id") or "")
        material = str(item.get("material") or item.get("base-material") or item.get("base_material") or "").upper()
        model_data = int(item.get("custom_model_data") or item.get("custom-model-data") or 0)
        if not item_id or model_data <= 0:
            missing.append(f"{item_id or '<unnamed>'}: custom model data must be positive")
            continue
        source = source_rows.get(item_id)
        if source is None:
            missing.append(f"{item_id}: missing item_texture_sources.json row")
        elif str(source.get("base_material", "")).upper() != material or int(source.get("custom_model_data", 0)) != model_data:
            missing.append(f"{item_id}: source mapping does not match catalog material/model data")
        if (material, model_data) not in manifest_keys:
            missing.append(f"{item_id} ({material}, {model_data}): missing manifest override")
    if len(source_rows) != len(catalog_items):
        missing.append("item_texture_sources.json must contain exactly one row per catalog item")
    if missing:
        raise ValueError("Artifact texture mappings are invalid:\n - " + "\n - ".join(missing))


def read_catalog_items(path: Path) -> list[dict]:
    """Read only catalog item scalars without making PyYAML a pack-build dependency."""
    try:
        import yaml
        payload = yaml.safe_load(path.read_text(encoding="utf-8")) or {}
        return list(payload.get("items", [])) + list(payload.get("donation-catalog", {}).get("items", []))
    except ModuleNotFoundError:
        rows: list[dict] = []
        current: dict | None = None
        for line in path.read_text(encoding="utf-8").splitlines():
            start = re.match(r"^\s*-\s+(id|item-id):\s*(.*?)\s*$", line)
            if start:
                if current:
                    rows.append(current)
                current = {"id": start.group(2).strip().strip('"\'')}
                continue
            if current is None:
                continue
            value = re.match(r"^\s+(material|base-material|custom_model_data|custom-model-data):\s*(.*?)\s*$", line)
            if value:
                key = {"base-material": "base_material", "custom-model-data": "custom_model_data"}.get(value.group(1), value.group(1))
                current[key] = value.group(2).strip().strip('"\'')
        if current:
            rows.append(current)
        return rows


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
        if item.get("animation"):
            meta_path = texture_path.with_suffix(texture_path.suffix + ".mcmeta")
            if not meta_path.is_file():
                raise FileNotFoundError(f"Missing animation metadata referenced by models_manifest.json: {meta_path}")
            metadata = json.loads(meta_path.read_text(encoding="utf-8-sig"))
            animation = metadata.get("animation", {})
            if animation.get("frametime") != item["animation"].get("frametime", 1) or animation.get("interpolate") is not False:
                raise ValueError(f"Animation metadata mismatch for {item['id']}")

    for material, entries in grouped.items():
        overrides = vanilla_special_overrides(material)
        custom_overrides = []
        for entry in sorted(entries, key=lambda x: x["custom_model_data"]):
            model_ref = entry["model"]
            if material in {"clock", "compass"} and entry.get("animation"):
                model_ref = write_directional_animation_models(entry, material)
            custom_overrides.append(
                {
                    "predicate": {"custom_model_data": entry["custom_model_data"]},
                    "model": model_ref,
                }
            )
        # Custom-model-data overrides come after vanilla predicates.  A normal
        # compass/clock keeps its moving vanilla needle, while an official
        # donation item enters its own directional model tree.
        overrides.extend(custom_overrides)
        # Keep vanilla special-item rendering intact.  The vanilla clock and
        # compass textures are selected by time/angle predicates, while the
        # shield is a builtin entity model.  Replacing these roots with only a
        # custom-model-data override makes ordinary items static or gives them
        # a donation texture.
        if material == "clock":
            parent = "minecraft:item/generated"
            textures = {"layer0": "minecraft:item/clock_00"}
        elif material == "compass":
            parent = "minecraft:item/generated"
            textures = {"layer0": "minecraft:item/compass_16"}
        elif material == "shield":
            parent = "builtin/entity"
            textures = {"particle": "minecraft:block/dark_oak_planks"}
        else:
            parent = "minecraft:item/generated"
            textures = {"layer0": f"minecraft:item/{material}"}
        write_json(
            STAGE / "assets" / "minecraft" / "models" / "item" / f"{material}.json",
            {
                "parent": parent,
                "textures": textures,
                "overrides": overrides,
            },
        )

    normalize_stage_text_files()


def vanilla_special_overrides(material: str) -> list[dict]:
    """Return the vanilla 1.21.1 time/angle/blocking model predicates."""
    if material == "compass":
        values = [
            0.000000, 0.015625, 0.046875, 0.078125, 0.109375, 0.140625,
            0.171875, 0.203125, 0.234375, 0.265625, 0.296875, 0.328125,
            0.359375, 0.390625, 0.421875, 0.453125, 0.484375, 0.515625,
            0.546875, 0.578125, 0.609375, 0.640625, 0.671875, 0.703125,
            0.734375, 0.765625, 0.796875, 0.828125, 0.859375, 0.890625,
            0.921875, 0.953125, 0.984375,
        ]
        models = ["compass", *[f"compass_{i:02d}" for i in range(17, 32)],
                  *[f"compass_{i:02d}" for i in range(0, 16)], "compass"]
        return [{"predicate": {"angle": value}, "model": f"minecraft:item/{model}"}
                for value, model in zip(values, models)]
    if material == "clock":
        values = [0.000000] + [round(i / 128, 7) for i in range(1, 126, 2)] + [0.9921875]
        models = ["clock", *[f"clock_{i:02d}" for i in range(1, 64)], "clock"]
        return [{"predicate": {"time": value}, "model": f"minecraft:item/{model}"}
                for value, model in zip(values, models)]
    if material == "shield":
        return [{"predicate": {"blocking": 1}, "model": "minecraft:item/shield_blocking"}]
    return []


def write_directional_animation_models(entry: dict, material: str) -> str:
    """Split a vertical custom animation into directional item-model frames.

    Vanilla compass/clock movement is driven by model predicates, not by a
    .mcmeta timer.  The supplied donation art is a vertical strip, so create
    deterministic square PNGs and let Minecraft choose the frame from the
    real world angle/time.
    """
    frame_count = int(entry["animation"]["frame_count"])
    texture_ref = str(entry["texture"])
    model_ref = str(entry["model"])
    source_path = asset_path(STAGE, "textures", texture_ref, ".png")
    with Image.open(source_path) as source:
        image = source.convert("RGBA")
        if image.height % frame_count != 0:
            raise ValueError(f"{entry['id']}: animation strip height is not divisible by frame_count")
        frame_height = image.height // frame_count
        if frame_height != image.width:
            raise ValueError(f"{entry['id']}: animation frames must be square")
        for index in range(frame_count):
            frame_name = f"{Path(texture_ref).name}_{index:02d}"
            frame_ref = f"{texture_ref.rsplit('/', 1)[0]}/{frame_name}"
            frame_path = asset_path(STAGE, "textures", frame_ref, ".png")
            frame = image.crop((0, index * frame_height, image.width, (index + 1) * frame_height))
            frame_path.parent.mkdir(parents=True, exist_ok=True)
            frame.save(frame_path, format="PNG", optimize=False)
            write_json(
                asset_path(STAGE, "models", f"{model_ref}_{index:02d}", ".json"),
                {"parent": "minecraft:item/generated", "textures": {"layer0": f"{texture_ref}_{index:02d}"}},
            )

    if material == "compass":
        predicate_key = "angle"
        values = [
            0.000000, 0.015625, 0.046875, 0.078125, 0.109375, 0.140625,
            0.171875, 0.203125, 0.234375, 0.265625, 0.296875, 0.328125,
            0.359375, 0.390625, 0.421875, 0.453125, 0.484375, 0.515625,
            0.546875, 0.578125, 0.609375, 0.640625, 0.671875, 0.703125,
            0.734375, 0.765625, 0.796875, 0.828125, 0.859375, 0.890625,
            0.921875, 0.953125, 0.984375,
        ]
    else:
        predicate_key = "time"
        values = [0.000000] + [round(i / 128, 7) for i in range(1, 126, 2)] + [0.9921875]
    overrides = [
        {"predicate": {predicate_key: value}, "model": f"{model_ref}_{index:02d}"}
        for index, value in enumerate(values)
    ]
    write_json(
        asset_path(STAGE, "models", f"{model_ref}_directional", ".json"),
        {"parent": "minecraft:item/generated", "textures": {"layer0": f"{texture_ref}_00"}, "overrides": overrides},
    )
    return f"{model_ref}_directional"


def pack_zip() -> tuple[Path, str]:
    BUILD.mkdir(parents=True, exist_ok=True)
    zip_path = BUILD / "CopiMineResourcePack.zip"
    if zip_path.exists():
        zip_path.unlink()
    with ZipFile(zip_path, "w", compression=ZIP_STORED) as zf:
        for file in sorted(STAGE.rglob("*")):
            if file.is_file():
                archive_name = file.relative_to(STAGE).as_posix()
                entry = ZipInfo(archive_name, date_time=RESOURCE_PACK_ZIP_TIMESTAMP)
                entry.compress_type = ZIP_STORED
                entry.create_system = 3
                entry.external_attr = 0o100644 << 16
                zf.writestr(entry, file.read_bytes(), compress_type=ZIP_STORED)

    with ZipFile(zip_path, "r") as zf:
        broken = zf.testzip()
        if broken is not None:
            raise RuntimeError(f"Zip integrity check failed on {broken}")

    zip_bytes = zip_path.read_bytes()
    sha1 = hashlib.sha1(zip_bytes).hexdigest()
    sha256 = hashlib.sha256(zip_bytes).hexdigest()
    (BUILD / "CopiMineResourcePack.sha1").write_text(sha1 + "\n", encoding="utf-8")
    (BUILD / "CopiMineResourcePack.sha256").write_text(sha256 + "\n", encoding="utf-8")
    return zip_path, sha1


def main() -> None:
    build_stage()
    zip_path, sha1 = pack_zip()
    update_server_properties_sha1(sha1)
    print(f"Built {zip_path}")
    print(f"SHA1 {sha1}")


if __name__ == "__main__":
    main()
