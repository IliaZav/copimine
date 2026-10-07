"""Build the isolated 26.3 candidate from validated legacy assets and pinned vanilla definitions.

Never rewrites the 1.21.1 source pack, installs a pack, or declares native acceptance.
"""
import argparse
import copy
import hashlib
import json
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parents[1]
LOCK = ROOT / "tools/minecraft-26.3/profile.lock.json"


def model(reference):
    return {"type": "minecraft:model", "model": reference}


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
    # Integer IDs must match exactly. Unknown values must not inherit the
    # preceding artifact, and consecutive registered IDs must still work.
    thresholds = {}
    identifiers = set()
    for entry in entries:
        identifier = entry["custom_model_data"]
        if not isinstance(identifier, int) or identifier < 1 or identifier >= 2 ** 24 or identifier in identifiers:
            raise ValueError(f"Invalid or duplicate CustomModelData: {identifier}")
        identifiers.add(identifier)
        thresholds[identifier + 1] = copy.deepcopy(vanilla)
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


def build(legacy_path, vanilla_path, output):
    profile = json.loads(LOCK.read_text(encoding="utf-8"))
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
        resource = [version["pack_version"]["resource_major"], version["pack_version"]["resource_minor"]]
        files["pack.mcmeta"] = json.dumps({"pack": {"description": "CopiMine 26.3 migration candidate",
                                          "min_format": resource, "max_format": resource}}, indent=2).encode()
        receipt = {"minecraftVersion": profile["minecraftVersion"], "resourceFormat": resource,
                   "legacyPackSha256": hashlib.sha256(legacy_path.read_bytes()).hexdigest(),
                   "vanillaClientSha1": profile["minecraftClient"]["sha1"],
                   "itemDefinitions": len(definitions), "registeredModels": len(entries),
                   "nativeVerified": False}
        files["assets/copimine/manifests/minecraft_26_3_migration.json"] = json.dumps(receipt, indent=2).encode()
        output.parent.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(output, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
            for name, data in sorted(files.items()):
                info = zipfile.ZipInfo(name, (2020, 1, 1, 0, 0, 0))
                info.compress_type = zipfile.ZIP_DEFLATED
                info.external_attr = 0o644 << 16
                archive.writestr(info, data)
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
