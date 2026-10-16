"""Build the isolated 26.3 candidate from validated legacy assets and pinned vanilla definitions.

Never rewrites the 1.21.1 source pack, installs a pack, or declares native acceptance.
"""
import argparse
import copy
import hashlib
from io import BytesIO
import json
import os
from pathlib import Path
import stat
import struct
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]
LOCK = ROOT / "tools/minecraft-26.3/profile.lock.json"


def set_archive_output_mode(temporary_output, output):
    """Make the atomically published archive readable while preserving local policy."""
    try:
        mode = stat.S_IMODE(output.stat().st_mode)
    except FileNotFoundError:
        mode = 0o644
    os.chmod(temporary_output, mode)


def model(reference):
    return {"type": "minecraft:model", "model": reference}


def next_float32(value):
    """Return the first representable CustomModelData float greater than value."""
    bits = struct.unpack(">I", struct.pack(">f", float(value)))[0]
    return struct.unpack(">f", struct.pack(">I", bits + 1))[0]


def split_reference(reference):
    """Resolve an unqualified resource location in Minecraft's default namespace."""
    if not isinstance(reference, str) or not reference:
        raise ValueError(f"Invalid resource reference: {reference!r}")
    return reference.split(":", 1) if ":" in reference else ("minecraft", reference)


def custom_definition(entry, vanilla, legacy):
    """Retain the material's native state rules and replace only authored model leaves."""
    material, reference = entry["base_material"], entry["model"]
    if material in {"clock", "compass"} and entry.get("animation"):
        result = copy.deepcopy(vanilla)

        def replace(node):
            if isinstance(node, dict):
                if node.get("type") == "minecraft:model":
                    native = node.get("model", "")
                    prefix = f"minecraft:item/{material}_"
                    if not native.startswith(prefix) or not native[len(prefix):].isdigit():
                        raise ValueError(f"Unknown vanilla {material} leaf: {native}")
                    node["model"] = reference + "_" + native[len(prefix):]
                for child in node.values():
                    replace(child)
            elif isinstance(node, list):
                for child in node:
                    replace(child)

        replace(result)
        return result
    if material not in {"bow", "crossbow"}:
        return model(reference)
    result = copy.deepcopy(vanilla)
    mapping = {f"minecraft:item/{material}": reference}
    for frame in range(3):
        mapping[f"minecraft:item/{material}_pulling_{frame}"] = reference + f"_pulling_{frame}"
    if material == "crossbow":
        mapping.update({"minecraft:item/crossbow_arrow": reference + "_charged",
                        "minecraft:item/crossbow_firework": reference + "_charged_firework"})

    def replace(node):
        if isinstance(node, dict):
            if node.get("type") == "minecraft:model":
                native = node.get("model")
                if native not in mapping:
                    raise ValueError(f"Unknown native {material} model: {native}")
                node["model"] = mapping[native]
            for child in node.values():
                replace(child)
        elif isinstance(node, list):
            for child in node:
                replace(child)

    replace(result)
    return result


def dispatch(entries, vanilla, legacy):
    # Integer IDs must match exactly. CustomModelData stores single-precision
    # floats, so reset at the next representable value rather than id + 1;
    # fractional/unknown component values must not inherit an artifact.
    thresholds = {}
    identifiers = set()
    for entry in entries:
        identifier = entry["custom_model_data"]
        if not isinstance(identifier, int) or identifier < 1 or identifier >= 2 ** 24 or identifier in identifiers:
            raise ValueError(f"Invalid or duplicate CustomModelData: {identifier}")
        identifiers.add(identifier)
        thresholds[next_float32(identifier)] = copy.deepcopy(vanilla)
    for entry in entries:
        thresholds[entry["custom_model_data"]] = custom_definition(entry, vanilla, legacy)
    return {"type": "minecraft:range_dispatch", "property": "minecraft:custom_model_data", "index": 0,
            "fallback": copy.deepcopy(vanilla),
            "entries": [{"threshold": key, "model": value} for key, value in sorted(thresholds.items())]}


def referenced_models(node):
    if isinstance(node, dict):
        if node.get("type") == "minecraft:model":
            yield node["model"]
        for child in node.values():
            yield from referenced_models(child)
    elif isinstance(node, list):
        for child in node:
            yield from referenced_models(child)


def normalize_block_atlas_texture(data, source):
    """Resize PNGs to 16-pixel boundaries so block-atlas mipmaps remain available."""
    from PIL import Image

    try:
        with Image.open(BytesIO(data)) as original:
            if original.format != "PNG":
                raise ValueError(f"Block-atlas texture is not a PNG: {source}")
            width, height = original.size
            target_size = (width // 16 * 16, height // 16 * 16)
            if min(target_size) < 16:
                raise ValueError(f"Block-atlas texture is too small for mipmaps: {source} ({width}x{height})")
            if target_size == original.size:
                return data

            resized = original.resize(target_size, Image.Resampling.LANCZOS)
            save_options = {key: original.info[key] for key in ("icc_profile", "dpi", "transparency")
                            if key in original.info}
            output = BytesIO()
            resized.save(output, format="PNG", compress_level=9, **save_options)
            return output.getvalue()
    except (OSError, ValueError) as error:
        if isinstance(error, ValueError) and str(error).startswith("Block-atlas texture"):
            raise
        raise ValueError(f"Could not normalize block-atlas texture: {source}") from error


def migrate_block_model_item_textures(files):
    """Keep item-model block geometry on the single block atlas required by 26.3."""
    def uses_block_atlas(model_ref, seen):
        if not isinstance(model_ref, str) or not model_ref:
            return False
        namespace, path = split_reference(model_ref)
        if path.startswith("block/"):
            return True
        filename = f"assets/{namespace}/models/{path}.json"
        if filename in seen or filename not in files:
            return False
        seen.add(filename)
        model_data = json.loads(files[filename])
        parent = model_data.get("parent")
        return bool(parent and uses_block_atlas(parent, seen))

    copied = set()
    model_files = [name for name in files if name.startswith("assets/") and "/models/" in name and name.endswith(".json")]
    for filename in model_files:
        namespace, remainder = filename[len("assets/"):].split("/models/", 1)
        model_path = remainder[:-5]
        if not uses_block_atlas(f"{namespace}:{model_path}", set()):
            continue
        model_data = json.loads(files[filename])
        textures = model_data.get("textures", {})
        changed = False
        for key, texture in list(textures.items()):
            if not isinstance(texture, str) or texture.startswith("#"):
                continue
            texture_namespace, texture_path = split_reference(texture)
            if not texture_path.startswith("item/"):
                continue
            relative_path = texture_path[len("item/"):]
            source = f"assets/{texture_namespace}/textures/item/{relative_path}.png"
            if source not in files:
                raise ValueError(f"Cannot move block-model texture into the block atlas: {source}")
            migrated_path = f"copimine_migrated_items/{relative_path}"
            destination = f"assets/{texture_namespace}/textures/block/{migrated_path}.png"
            source_data = normalize_block_atlas_texture(files[source], source)
            if destination in files and files[destination] != source_data:
                raise ValueError(f"Conflicting migrated block texture: {destination}")
            files[destination] = source_data
            metadata_source = source + ".mcmeta"
            if metadata_source in files:
                metadata_destination = destination + ".mcmeta"
                if metadata_destination in files and files[metadata_destination] != files[metadata_source]:
                    raise ValueError(f"Conflicting migrated block texture metadata: {metadata_destination}")
                files[metadata_destination] = files[metadata_source]
            textures[key] = f"{texture_namespace}:block/{migrated_path}"
            changed = True
            copied.add(destination)
        if changed:
            files[filename] = json.dumps(model_data, ensure_ascii=False, indent=2).encode("utf-8")
    return len(copied)


def normalize_legacy_model_uvs(files):
    """Convert texture-pixel UVs on high-resolution legacy models to 26.3 model units."""
    def inherited_textures(model_ref, seen):
        namespace, path = split_reference(model_ref)
        filename = f"assets/{namespace}/models/{path}.json"
        if filename in seen or filename not in files:
            return {}
        seen.add(filename)
        model_data = json.loads(files[filename])
        textures = {}
        if model_data.get("parent"):
            textures.update(inherited_textures(model_data["parent"], seen))
        textures.update(model_data.get("textures", {}))
        return textures

    normalized = 0
    model_files = [name for name in files if name.startswith("assets/") and "/models/" in name and name.endswith(".json")]
    for filename in model_files:
        namespace, remainder = filename[len("assets/"):].split("/models/", 1)
        model_path = remainder[:-5]
        model_data = json.loads(files[filename])
        elements = model_data.get("elements")
        if not isinstance(elements, list):
            continue
        textures = inherited_textures(f"{namespace}:{model_path}", set())
        changed = False
        pending_faces = []
        texture_extents = {}
        for element in elements:
            for face in element.get("faces", {}).values():
                uv = face.get("uv")
                texture = face.get("texture")
                if (not isinstance(uv, list) or len(uv) != 4 or
                    not all(isinstance(value, (int, float)) for value in uv) or
                    not isinstance(texture, str)):
                    continue
                aliases = set()
                while texture.startswith("#") and texture[1:] not in aliases:
                    aliases.add(texture[1:])
                    texture = textures.get(texture[1:], "")
                if not texture or texture.startswith("#"):
                    continue
                texture_namespace, texture_path = split_reference(texture)
                texture_path = texture_path.removesuffix(".png")
                image = files.get(f"assets/{texture_namespace}/textures/{texture_path}.png")
                if not image or len(image) < 24 or image[:8] != b"\x89PNG\r\n\x1a\n":
                    continue
                width, height = struct.unpack(">II", image[16:24])
                texture_key = (texture_namespace, texture_path)
                previous_u, previous_v = texture_extents.get(texture_key, (0, 0))
                texture_extents[texture_key] = (
                    max(previous_u, abs(uv[0]), abs(uv[2])),
                    max(previous_v, abs(uv[1]), abs(uv[3])),
                )
                pending_faces.append((face, uv, texture_key, width, height))

        for face, uv, texture_key, width, height in pending_faces:
            max_u, max_v = texture_extents[texture_key]
            x_scale = width / 16 if width > 16 and max_u > 16 else 1
            y_scale = height / 16 if height > 16 and max_v > 16 else 1
            if x_scale == 1 and y_scale == 1:
                continue
            face["uv"] = [uv[0] / x_scale, uv[1] / y_scale,
                          uv[2] / x_scale, uv[3] / y_scale]
            normalized += 1
            changed = True
        if changed:
            files[filename] = json.dumps(model_data, ensure_ascii=False, indent=2).encode("utf-8")
    return normalized


def build(legacy_path, vanilla_path, output):
    profile = json.loads(LOCK.read_text(encoding="utf-8"))
    migration = profile["resourcePackMigration"]
    legacy_sha256 = hashlib.sha256(legacy_path.read_bytes()).hexdigest()
    if legacy_sha256 != migration["legacyPackSha256"]:
        raise ValueError("Legacy resource pack SHA-256 differs from the pinned source")
    if hashlib.sha1(vanilla_path.read_bytes()).hexdigest() != profile["minecraftClient"]["sha1"]:
        raise ValueError("Minecraft client SHA-1 differs from the pinned 26.3 release")
    entries = json.loads((ROOT / "resourcepacks/models_manifest.json").read_text(encoding="utf-8-sig"))["items"]
    grouped = {}
    for entry in entries:
        grouped.setdefault(entry["base_material"], []).append(entry)
    with zipfile.ZipFile(legacy_path) as legacy, zipfile.ZipFile(vanilla_path) as vanilla:
        version = json.loads(vanilla.read("version.json"))
        if version["id"] != profile["minecraftVersion"] or not version["stable"]:
            raise ValueError("Vanilla data must be the pinned stable release")
        native_names, custom_names = set(vanilla.namelist()), set(legacy.namelist())
        definitions = {}
        for material, items in grouped.items():
            path = f"assets/minecraft/items/{material}.json"
            definition = json.loads(vanilla.read(path))
            definition["model"] = dispatch(items, definition["model"], legacy)
            for reference in referenced_models(definition):
                namespace, model_path = reference.split(":", 1)
                location = f"assets/{namespace}/models/{model_path}.json"
                if location not in custom_names and location not in native_names:
                    raise ValueError(f"Missing model in migrated item definition: {reference}")
            definitions[path] = json.dumps(definition, ensure_ascii=False, indent=2).encode("utf-8")
        # Old vanilla root overrides can point at renamed textures. Native
        # fallbacks must use the target release's untouched models instead.
        excluded = {f"assets/minecraft/models/item/{material}.json" for material in grouped}
        files = {name: legacy.read(name) for name in custom_names
                 if not name.endswith("/") and name != "pack.mcmeta" and name not in excluded}
        files.update(definitions)
        migrated_block_textures = migrate_block_model_item_textures(files)
        normalized_model_uv_faces = normalize_legacy_model_uvs(files)
        resource = [version["pack_version"]["resource_major"], version["pack_version"]["resource_minor"]]
        files["pack.mcmeta"] = json.dumps({"pack": {"description": "CopiMine 26.3 migration candidate",
                                          "min_format": resource, "max_format": resource}}, indent=2).encode()
        receipt = {"minecraftVersion": profile["minecraftVersion"], "resourceFormat": resource,
                   "legacyPackSha256": legacy_sha256,
                   "vanillaClientSha1": profile["minecraftClient"]["sha1"],
                   "migratedBlockAtlasTextures": migrated_block_textures,
                   "normalizedModelUvFaces": normalized_model_uv_faces,
                   "itemDefinitions": len(definitions), "registeredModels": len(entries),
                   "nativeVerified": False}
        files["assets/copimine/manifests/minecraft_26_3_migration.json"] = json.dumps(receipt, indent=2).encode()
        output.parent.mkdir(parents=True, exist_ok=True)
        handle, temporary_name = tempfile.mkstemp(prefix=output.name + ".", suffix=".tmp", dir=output.parent)
        os.close(handle)
        temporary_output = Path(temporary_name)
        try:
            # Stored entries keep the candidate digest independent of the
            # zlib implementation bundled with the build interpreter.
            with zipfile.ZipFile(temporary_output, "w", zipfile.ZIP_STORED) as archive:
                for name, data in sorted(files.items()):
                    info = zipfile.ZipInfo(name, (2020, 1, 1, 0, 0, 0))
                    # ZipInfo defaults this field from the host OS. Pin the
                    # Windows/DOS creator value used by the migration lock so
                    # stored archive bytes remain portable across platforms.
                    info.create_system = 0
                    info.compress_type = zipfile.ZIP_STORED
                    info.external_attr = 0o644 << 16
                    archive.writestr(info, data)
            candidate_sha256 = hashlib.sha256(temporary_output.read_bytes()).hexdigest()
            if candidate_sha256 != migration["candidateSha256"]:
                raise ValueError(
                    "Migrated resource pack SHA-256 differs from the pinned candidate: "
                    f"expected {migration['candidateSha256']}, got {candidate_sha256}"
                )
            set_archive_output_mode(temporary_output, output)
            temporary_output.replace(output)
        finally:
            temporary_output.unlink(missing_ok=True)
    return receipt


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--legacy-pack", type=Path, default=ROOT / "resourcepacks/build/CopiMineResourcePack.zip")
    parser.add_argument("--minecraft-client", type=Path, default=ROOT / "build/minecraft-26.3/toolchain/minecraft-26.3-client.jar")
    parser.add_argument("--output", type=Path, default=ROOT / "build/minecraft-26.3/CopiMineResourcePack-26.3.zip")
    args = parser.parse_args()
    for source in (args.legacy_pack, args.minecraft_client):
        if args.output.resolve() == source.resolve():
            parser.error("The candidate must not overwrite either source archive")
    print(json.dumps(build(args.legacy_pack, args.minecraft_client, args.output), indent=2))
