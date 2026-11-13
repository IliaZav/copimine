"""Asset contracts for the 26.3 End Rift projectile display models."""

import importlib.util
import json
from pathlib import Path

import pytest
from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
MANIFEST = ROOT / "resourcepacks" / "models_manifest.json"
MIGRATION_BUILDER = ROOT / "resourcepacks" / "build-migration-pack.py"
MESH_GENERATOR = ROOT / "scripts" / "minecraft" / "GenerateSpellMeshes.py"
ATLAS_GENERATOR = ROOT / "scripts" / "minecraft" / "GenerateSpellAtlases.py"

# The four elite/boss projectile spells registered under the elite spell block.
# The three skeleton-elite arrow spells sit directly after them.
PROJECTILE_ASSETS = (
    ("end_rift_web_shot", 830030, "end_rift_web_shot"),
    ("end_rift_rift_bolt", 830031, "end_rift_rift_bolt"),
    ("end_rift_dragon_breath", 830032, "end_rift_dragon_breath"),
    ("end_rift_void_burst", 830033, "end_rift_void_burst"),
    ("end_rift_triple_arrow", 830034, "end_rift_triple_arrow"),
    ("end_rift_arrow_rain", 830035, "end_rift_arrow_rain"),
    ("end_rift_explosive_arrow", 830036, "end_rift_explosive_arrow"),
)
PROJECTILE_IDS = frozenset(item_id for item_id, _, _ in PROJECTILE_ASSETS)


def _rows():
    return json.loads(MANIFEST.read_text(encoding="utf-8-sig"))["items"]


def _asset_path(kind, name):
    return ROOT / "resourcepacks" / "src" / "assets" / "copimine" / kind / f"{name}"


def _faces(element):
    return element["faces"]


def _load_pack_module(module_name):
    spec = importlib.util.spec_from_file_location(module_name, MIGRATION_BUILDER)
    migration_pack = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    spec.loader.exec_module(migration_pack)
    return migration_pack


def _chooser(definition):
    def choose(value):
        selected = definition["fallback"]
        for rule in definition["entries"]:
            if value >= rule["threshold"]:
                selected = rule["model"]
        return selected

    return choose


def test_every_spell_asset_is_a_registered_paper_model_with_its_own_code():
    rows = _rows()
    by_id = {row["id"]: row for row in rows}
    paper_ids = [row["custom_model_data"] for row in rows if row["base_material"] == "paper"]

    assert len(PROJECTILE_ASSETS) == 7
    assert len({model_data for _, model_data, _ in PROJECTILE_ASSETS}) == len(PROJECTILE_ASSETS)
    for item_id, model_data, name in PROJECTILE_ASSETS:
        assert by_id[item_id] == {
            "id": item_id,
            "custom_model_data": model_data,
            "base_material": "paper",
            "model": f"copimine:item/{name}",
            "texture": f"copimine:item/{name}",
        }
        assert paper_ids.count(model_data) == 1
        assert (_asset_path("models/item", name).parent / f"{name}.json").is_file()
        assert (_asset_path("textures/item", name).parent / f"{name}.png").is_file()

    # The custom display uses a separate Paper item; ordinary projectile sprites stay vanilla.
    assert not (ROOT / "resourcepacks" / "src" / "assets" / "minecraft" / "models" / "item" / "snowball.json").exists()
    assert all(row["base_material"] != "snowball" for row in rows)


def test_every_spell_is_a_closed_3d_mesh_sampling_its_own_64px_atlas():
    # Every elite/boss spell is a closed authored 3D mesh, not a flat 2D sprite:
    # the projectile flies as real geometry and therefore samples a UV atlas.
    # A shared texture pair made every ability look identical, so each spell owns
    # its own atlas and its own silhouette.
    rows = _rows()
    assert {row["id"] for row in rows if row["id"] in PROJECTILE_IDS} == PROJECTILE_IDS

    silhouettes = {}
    texture_owners = {}
    for item_id, model_data, name in PROJECTILE_ASSETS:
        model = json.loads(
            (_asset_path("models/item", name).parent / f"{name}.json").read_text(encoding="utf-8")
        )

        assert model["parent"] == "minecraft:block/block", item_id
        assert set(model["textures"].values()) == {f"copimine:item/{name}"}, item_id
        assert model["display"]["fixed"]["scale"] == [1, 1, 1]

        elements = model["elements"]
        assert len(elements) >= 6, item_id
        for element in elements:
            assert set(_faces(element)) == {"down", "up", "north", "south", "west", "east"}, item_id
            low, high = element["from"], element["to"]
            assert all(0 <= value <= 16 for value in low + high), item_id
            assert all(a < b for a, b in zip(low, high)), "a degenerate element is invisible"
            assert element["shade"] is False, "spell faces must stay readable at any angle"
            for face in _faces(element).values():
                uv = face["uv"]
                assert 0 <= uv[0] < uv[2] <= 64 and 0 <= uv[1] < uv[3] <= 64, item_id

        silhouettes[name] = frozenset(
            (tuple(element["from"]), tuple(element["to"])) for element in elements
        )
        texture_owners[name] = f"copimine:item/{name}"

    # Distinct silhouettes and distinct atlases: no two elite spells may be
    # interchangeable in flight.
    assert len(set(silhouettes.values())) == len(silhouettes)
    assert len(set(texture_owners.values())) == len(texture_owners)


def test_every_spell_atlas_is_a_distinct_64px_uv_sheet():
    digests = set()
    palettes = {}
    for item_id, model_data, name in PROJECTILE_ASSETS:
        texture_path = _asset_path("textures/item", name).parent / f"{name}.png"
        with Image.open(texture_path) as image:
            assert image.mode == "RGBA", item_id
            assert image.size == (64, 64), item_id
            # A UV atlas needs more than the handful of flat colours a sprite used.
            colours = image.getcolors(64 * 64) or []
            assert len(colours) > 32, item_id
            assert image.getchannel("A").getextrema() == (0, 255), item_id
            palettes[item_id] = frozenset(colour for _, colour in colours)
            digests.add(texture_path.read_bytes())

    assert len(digests) == len(PROJECTILE_ASSETS), "each spell atlas must be its own image"
    assert len(set(palettes.values())) == len(palettes), "each spell atlas must use its own palette"


def test_paper_dispatch_selects_every_spell_model_and_resets_to_vanilla():
    migration_pack = _load_pack_module("end_rift_spell_migration_pack")

    rows = _rows()
    entries = [row for row in rows if row["id"] in PROJECTILE_IDS]
    assert len(entries) == len(PROJECTILE_ASSETS)
    native_paper = migration_pack.model("minecraft:item/paper")
    definition = migration_pack.dispatch(entries, native_paper, None)
    choose = _chooser(definition)

    for item_id, model_data, name in PROJECTILE_ASSETS:
        assert choose(model_data) == migration_pack.model(f"copimine:item/{name}")
        assert choose(migration_pack.next_float32(model_data)) == native_paper
    assert definition["fallback"] == native_paper
    assert choose(830037) == native_paper


def test_spell_mesh_generator_declares_all_seven_meshes():
    spec = importlib.util.spec_from_file_location("end_rift_spell_meshes", MESH_GENERATOR)
    meshes = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    spec.loader.exec_module(meshes)

    assert set(meshes.MESHES) == PROJECTILE_IDS
    assert len(meshes.MESHES) == 7
    silhouettes = {}
    for item_id in PROJECTILE_IDS:
        elements = meshes.MESHES[item_id]()
        assert len(elements) >= 6, item_id
        silhouettes[item_id] = frozenset((tuple(e["from"]), tuple(e["to"])) for e in elements)
    assert len(set(silhouettes.values())) == len(silhouettes)


def test_spell_atlas_generator_declares_all_seven_palettes():
    spec = importlib.util.spec_from_file_location("end_rift_spell_atlases", ATLAS_GENERATOR)
    atlases = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    spec.loader.exec_module(atlases)

    assert set(atlases.PALETTES) == PROJECTILE_IDS
    assert len(atlases.PALETTES) == 7
    assert len(set(atlases.PALETTES.values())) == 7


def test_thrown_web_shot_reads_as_a_tangled_cobweb_bundle():
    # The spider web shot used to be a flat 32px sprite. It is now an authored
    # thrown cobweb bundle: a solid core, tangled strands and an asymmetric
    # tangle so the bundle tumbles in flight instead of gliding flat.
    model = json.loads(
        (_asset_path("models/item", "end_rift_web_shot").parent / "end_rift_web_shot.json").read_text(
            encoding="utf-8"
        )
    )
    elements = model["elements"]
    boxes = [(*element["from"], *element["to"]) for element in elements]
    assert len(elements) >= 8

    def volume(box):
        return (box[3] - box[0]) * (box[4] - box[1]) * (box[5] - box[2])

    # A solid core: the densest element holds the middle of the bundle.
    core = max(boxes, key=volume)
    assert core[0] <= 8 <= core[3] and core[1] <= 8 <= core[4] and core[5] >= 12
    # Thin strands crossing the bundle in more than one axis.
    strands = [box for box in boxes if min(box[3] - box[0], box[4] - box[1], box[5] - box[2]) <= 2]
    assert len(strands) >= 4
    assert len({(box[3] - box[0] > box[5] - box[2], box[4] - box[1] > box[5] - box[2]) for box in strands}) >= 2
    # Chunky, not a flat sprite: the bundle keeps real depth so it tumbles
    # instead of gliding like a card.
    extents = [(box[3] - box[0], box[4] - box[1], box[5] - box[2]) for box in boxes]
    assert min(max(dx for dx, _, _ in extents), max(dy for _, dy, _ in extents),
               max(dz for _, _, dz in extents)) >= 6


@pytest.mark.parametrize("name", [name for _, _, name in PROJECTILE_ASSETS])
def test_generated_spell_definition_matches_its_mesh_and_atlas(name):
    # The registered item definitions are part of the pack, and the 26.3 client
    # copies them so the projectile resolves with the required client alone.
    definition = json.loads(
        (ROOT / "resourcepacks" / "src" / "assets" / "copimine" / "items" / f"{name}.json").read_text(
            encoding="utf-8"
        )
    )
    assert definition == {
        "model": {"type": "minecraft:model", "model": f"copimine:item/{name}"}
    }
