"""Target format routing must preserve vanilla states and exact registered IDs."""
import copy
import importlib.util
from pathlib import Path
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


def test_unknown_and_adjacent_custom_model_ids_never_gain_another_artifact():
    native = pack.model("minecraft:item/paper")
    items = [{"base_material": "paper", "custom_model_data": value, "model": f"copimine:item/token_{value}"}
             for value in (100, 101, 110)]
    tree = pack.dispatch(items, native, None)
    for identifier in (0, 99, 102, 109, 111, 830000):
        assert choose(tree, identifier) == native
    for identifier in (100, 101, 110):
        assert choose(tree, identifier) == pack.model(f"copimine:item/token_{identifier}")
    assert native == pack.model("minecraft:item/paper")


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


@pytest.mark.parametrize("values", [(100, 100), (0,), (2**24,), (1.5,)])
def test_invalid_or_ambiguous_float_component_ids_fail_closed(values):
    with pytest.raises(ValueError):
        pack.dispatch([{"base_material": "paper", "custom_model_data": value, "model": "copimine:item/x"}
                       for value in values], pack.model("minecraft:item/paper"), None)
