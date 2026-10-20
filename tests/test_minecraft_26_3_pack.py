"""Target format routing must preserve vanilla states and exact registered IDs."""
import copy
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import stat
import struct
import zipfile
import pytest

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("migration_pack", ROOT / "resourcepacks/build-migration-pack.py")
pack = importlib.util.module_from_spec(spec)
spec.loader.exec_module(pack)


def choose(tree, identifier):
    selected = tree["fallback"]
    for entry in tree["entries"]:
        if identifier >= entry["threshold"]:
            selected = entry["model"]
    return selected


def png_bytes(size=(16, 16), color=(190, 35, 80, 128)):
    image_module = pytest.importorskip("PIL.Image")
    buffer = __import__("io").BytesIO()
    image_module.new("RGBA", size, color).save(buffer, format="PNG")
    return buffer.getvalue()


def test_unknown_and_adjacent_custom_model_ids_never_gain_another_artifact():
    native = pack.model("minecraft:item/paper")
    items = [{"base_material": "paper", "custom_model_data": value, "model": f"copimine:item/token_{value}"}
             for value in (100, 101, 110)]
    tree = pack.dispatch(items, native, None)
    for identifier in (0, 99, 100.5, 102, 109, 111, 830000):
        assert choose(tree, identifier) == native
    for identifier in (100, 101, 110):
        assert choose(tree, identifier) == pack.model(f"copimine:item/token_{identifier}")
    assert native == pack.model("minecraft:item/paper")


def test_fractional_custom_model_data_after_registered_float_id_resets_to_vanilla():
    native = pack.model("minecraft:item/paper")
    tree = pack.dispatch([
        {"base_material": "paper", "custom_model_data": 100, "model": "copimine:item/token"},
    ], native, None)

    # CustomModelData.floats stores IEEE-754 single-precision values. The first
    # representable float after 100 must reset before a later value can inherit
    # the registered model through range_dispatch.
    next_float = struct.unpack(">f", struct.pack(">I", struct.unpack(">I", struct.pack(">f", 100.0))[0] + 1))[0]
    assert choose(tree, 100) == pack.model("copimine:item/token")
    assert choose(tree, next_float) == native
    assert choose(tree, 100.5) == native


def test_authored_post_effect_shaders_use_defined_smoothstep_edges_and_invert_falloff():
    shaders = ROOT / "tools/minecraft-26.3/client/src/main/resources/assets/copimineclient/shaders/post"
    expected = {"blobs.fsh": 2, "scan_pincushion.fsh": 1}
    for filename, inverted_calls in expected.items():
        source = (shaders / filename).read_text(encoding="utf-8")
        calls = list(re.finditer(
            r"smoothstep\(\s*([-+0-9.]+)\s*,\s*([-+0-9.]+)\s*,", source
        ))
        assert len(calls) == inverted_calls, f"unexpected smoothstep call in {filename}"
        assert all(float(match.group(1)) < float(match.group(2)) for match in calls), filename
        assert source.count("1.0 - smoothstep(") == inverted_calls, filename


@pytest.mark.parametrize("material,property_name,extra", [
    ("clock", "minecraft:time", {"source": "random"}),
    ("compass", "minecraft:compass", {"target": "lodestone"})])
def test_directional_models_keep_target_release_time_and_lodestone_semantics(material, property_name, extra):
    native = {"type": "minecraft:range_dispatch", "property": property_name, "scale": 32, **extra,
              "entries": [{"threshold": 0, "model": pack.model(f"minecraft:item/{material}_00")},
                          {"threshold": .5, "model": pack.model(f"minecraft:item/{material}_17")}],
              "fallback": pack.model(f"minecraft:item/{material}_16")}
    original = copy.deepcopy(native)
    entry = {"base_material": material, "animation": {"frame_count": 32}, "model": f"copimine:item/custom_{material}"}
    result = pack.custom_definition(entry, native, None)
    assert result["property"] == property_name and result["scale"] == 32
    assert all(result[key] == value for key, value in extra.items())
    assert list(pack.referenced_models(result)) == [f"copimine:item/custom_{material}_{frame}" for frame in ("00", "17", "16")]
    assert native == original


def test_crossbow_keeps_charge_and_firework_states_without_mutating_vanilla_fallback():
    native = {"type": "minecraft:select", "property": "minecraft:charge_type", "cases": [
        {"when": "arrow", "model": pack.model("minecraft:item/crossbow_arrow")},
        {"when": "rocket", "model": pack.model("minecraft:item/crossbow_firework")}],
        "fallback": {"type": "minecraft:condition", "property": "minecraft:using_item",
                     "on_false": pack.model("minecraft:item/crossbow"),
                     "on_true": pack.model("minecraft:item/crossbow_pulling_0")}}
    original = copy.deepcopy(native)
    tree = pack.dispatch([{"base_material": "crossbow", "custom_model_data": 200,
                          "model": "copimine:item/crossbow"}], native, None)
    custom = choose(tree, 200)
    assert custom["cases"][0]["model"]["model"] == "copimine:item/crossbow_charged"
    assert custom["cases"][1]["model"]["model"] == "copimine:item/crossbow_charged_firework"
    assert custom["fallback"]["property"] == "minecraft:using_item"
    assert choose(tree, 0) == native == original


def test_block_atlas_item_models_copy_item_textures_without_mixing_atlases():
    original_texture = png_bytes()
    files = {
        "assets/copimine/models/item/portal.json": json.dumps({
            "parent": "copimine:block/portal",
        }).encode(),
        "assets/copimine/models/block/portal.json": json.dumps({
            "parent": "minecraft:block/block",
            "textures": {
                "frame": "minecraft:block/obsidian",
                "rift": "copimine:item/end_event_portal",
            },
        }).encode(),
        "assets/copimine/textures/item/end_event_portal.png": original_texture,
    }

    copied_textures = pack.migrate_block_model_item_textures(files)

    block_model = json.loads(files["assets/copimine/models/block/portal.json"])
    assert block_model["textures"]["rift"] == "copimine:block/copimine_migrated_items/end_event_portal"
    assert files["assets/copimine/textures/item/end_event_portal.png"] == original_texture
    assert files["assets/copimine/textures/block/copimine_migrated_items/end_event_portal.png"] == original_texture
    assert copied_textures == 1


def test_block_atlas_textures_are_resized_to_mipmap_safe_dimensions():
    image_module = pytest.importorskip("PIL.Image")
    original_texture = png_bytes((35, 27))
    source = "assets/copimine/textures/item/odd_sized_portal.png"
    destination = "assets/copimine/textures/block/copimine_migrated_items/odd_sized_portal.png"
    files = {
        "assets/copimine/models/item/portal.json": json.dumps({"parent": "copimine:block/portal"}).encode(),
        "assets/copimine/models/block/portal.json": json.dumps({
            "parent": "minecraft:block/block",
            "textures": {"rift": "copimine:item/odd_sized_portal"},
        }).encode(),
        source: original_texture,
    }

    assert pack.migrate_block_model_item_textures(files) == 1

    migrated = image_module.open(__import__("io").BytesIO(files[destination]))
    assert migrated.size == (32, 16)
    pixel = migrated.getpixel((16, 8))
    assert all(abs(actual - expected) <= 1 for actual, expected in zip(pixel, (190, 35, 80, 128)))
    assert files[source] == original_texture


def test_block_model_item_migration_accepts_default_minecraft_references():
    original_texture = png_bytes()
    files = {
        "assets/copimine/models/item/legacy_portal.json": json.dumps({
            "parent": "block/portal",
            "textures": {"rift": "item/end_event_portal"},
        }).encode(),
        "assets/minecraft/textures/item/end_event_portal.png": original_texture,
    }

    copied = pack.migrate_block_model_item_textures(files)

    assert copied == 1
    assert files["assets/copimine/models/item/legacy_portal.json"] == json.dumps({
        "parent": "block/portal",
        "textures": {"rift": "minecraft:block/copimine_migrated_items/end_event_portal"},
    }, ensure_ascii=False, indent=2).encode("utf-8")
    assert files["assets/minecraft/textures/block/copimine_migrated_items/end_event_portal.png"] == original_texture


def test_64_pixel_model_uvs_are_scaled_to_the_26_3_sixteen_unit_grid():
    texture = b"\x89PNG\r\n\x1a\n" + b"\x00\x00\x00\x0dIHDR" + struct.pack(">II", 64, 64)
    model_name = "assets/copimine/models/item/rift_tentacle.json"
    texture_name = "assets/copimine/textures/item/rift_tentacle.png"
    files = {
        model_name: json.dumps({
            "textures": {"tentacle": "copimine:item/rift_tentacle"},
            "elements": [{
                "from": [0, 0, 0],
                "to": [16, 16, 16],
                "faces": {
                    "down": {"texture": "#tentacle", "uv": [48, 0, 32, 16]},
                    "up": {"texture": "#tentacle", "uv": [0, 0, 16, 16]},
                },
            }],
        }).encode(),
        texture_name: texture,
    }

    normalized_faces = pack.normalize_legacy_model_uvs(files)

    result = json.loads(files[model_name])
    faces = result["elements"][0]["faces"]
    assert faces["down"]["uv"] == [12, 0, 8, 16]
    assert faces["up"]["uv"] == [0, 0, 4, 16]
    assert files[texture_name] == texture
    assert normalized_faces == 2


@pytest.mark.parametrize("values", [(100, 100), (0,), (2**24,), (1.5,)])
def test_invalid_or_ambiguous_float_component_ids_fail_closed(values):
    with pytest.raises(ValueError):
        pack.dispatch([{"base_material": "paper", "custom_model_data": value, "model": "copimine:item/x"}
                       for value in values], pack.model("minecraft:item/paper"), None)


def test_builder_rejects_unpinned_legacy_pack_before_replacing_candidate(tmp_path, monkeypatch):
    vanilla = tmp_path / "client.jar"
    vanilla.write_bytes(b"pinned test client")
    legacy = tmp_path / "legacy.zip"
    legacy.write_bytes(b"untrusted test pack")
    output = tmp_path / "candidate.zip"
    output.write_bytes(b"previous verified candidate")
    lock = tmp_path / "profile.lock.json"
    lock.write_text(json.dumps({
        "minecraftVersion": "26.3",
        "minecraftClient": {"sha1": hashlib.sha1(vanilla.read_bytes()).hexdigest()},
        "resourcePackMigration": {"legacyPackSha256": "0" * 64},
    }), encoding="utf-8")
    monkeypatch.setattr(pack, "LOCK", lock)

    with pytest.raises(ValueError, match="(?i)legacy resource pack SHA-256"):
        pack.build(legacy, vanilla, output)

    assert output.read_bytes() == b"previous verified candidate"


@pytest.mark.skipif(os.name == "nt", reason="POSIX archive mode bits are not represented on Windows")
def test_published_archive_gets_readable_default_mode_and_preserves_existing_mode(tmp_path):
    temporary = tmp_path / "candidate.tmp"
    output = tmp_path / "resource-pack.zip"
    temporary.write_bytes(b"candidate")

    pack.set_archive_output_mode(temporary, output)
    assert stat.S_IMODE(temporary.stat().st_mode) == 0o644

    output.write_bytes(b"existing")
    output.chmod(0o640)
    pack.set_archive_output_mode(temporary, output)
    assert stat.S_IMODE(temporary.stat().st_mode) == 0o640


def test_built_26_3_candidate_matches_locked_source_and_archive_digests(tmp_path, monkeypatch):
    profile = json.loads((ROOT / "tools/minecraft-26.3/profile.lock.json").read_text(encoding="utf-8"))
    migration = profile["resourcePackMigration"]
    legacy = ROOT / "resourcepacks/build/CopiMineResourcePack.zip"
    candidate = ROOT / "build/minecraft-26.3/CopiMineResourcePack-26.3.zip"
    vanilla_client = ROOT / "build/minecraft-26.3/toolchain/minecraft-26.3-client.jar"

    assert legacy.is_file(), "Build the canonical legacy resource pack before validating migration output"
    assert candidate.is_file(), "Build the pinned Minecraft 26.3 resource pack before validating it"
    assert vanilla_client.is_file(), "Download the pinned Minecraft 26.3 client before rebuilding the pack"
    assert hashlib.sha256(legacy.read_bytes()).hexdigest() == migration["legacyPackSha256"]
    assert hashlib.sha256(candidate.read_bytes()).hexdigest() == migration["candidateSha256"]
    rebuilt = tmp_path / "CopiMineResourcePack-26.3.zip"
    pack.build(legacy, vanilla_client, rebuilt)
    assert rebuilt.read_bytes() == candidate.read_bytes()
    with zipfile.ZipFile(candidate) as archive:
        assert all(entry.compress_type == zipfile.ZIP_STORED for entry in archive.infolist())
        assert all(entry.create_system == 0 for entry in archive.infolist())
        receipt = json.loads(archive.read("assets/copimine/manifests/minecraft_26_3_migration.json"))
        assert receipt["minecraftVersion"] == "26.3"
        assert receipt["legacyPackSha256"] == migration["legacyPackSha256"]
        assert receipt["vanillaClientSha1"] == profile["minecraftClient"]["sha1"]
        assert receipt["resourceFormat"] == [97, 1]

    wrong_lock = tmp_path / "wrong-profile.lock.json"
    profile["resourcePackMigration"]["candidateSha256"] = "0" * 64
    wrong_lock.write_text(json.dumps(profile), encoding="utf-8")
    monkeypatch.setattr(pack, "LOCK", wrong_lock)
    protected = tmp_path / "protected-candidate.zip"
    protected.write_bytes(b"previous verified candidate")
    with pytest.raises(ValueError, match="differs from the pinned candidate"):
        pack.build(legacy, vanilla_client, protected)
    assert protected.read_bytes() == b"previous verified candidate"
    assert not list(tmp_path.glob("protected-candidate.zip.*.tmp"))
