"""Regression contracts for the admin recipe picker and vanilla pane models."""

import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def test_blue_pane_builder_uses_the_real_vanilla_item_model():
    builder = (ROOT / "resourcepacks" / "build-resourcepack.py").read_text(encoding="utf-8")
    start = builder.index('elif material == "blue_stained_glass_pane":')
    end = builder.index("else:", start)
    pane_case = builder[start:end]

    assert 'parent = "minecraft:item/generated"' in pane_case
    assert 'textures = {"layer0": "minecraft:block/blue_stained_glass"}' in pane_case
    assert 'minecraft:block/blue_stained_glass_pane' not in pane_case


def test_recipe_picker_has_russian_hover_labels_for_every_catalog_item():
    editor = (ROOT / "admin-web" / "frontend" / "assets" / "js" / "admin" / "narcotics-recipe-pages.js").read_text(encoding="utf-8")

    assert "function localizedItemName(token, item" in editor
    assert "const label = localizedItemName(token, item);" in editor
    assert 'title="${esc(label)}"' in editor
    assert '<span>${esc(label)}</span>' in editor


def test_recipe_catalog_contains_russian_names_and_a_visible_blue_pane_sprite():
    names = json.loads((ROOT / "admin-web" / "backend" / "minecraft_recipe_names_ru.json").read_text(encoding="utf-8"))
    backend = (ROOT / "admin-web" / "backend" / "main.py").read_text(encoding="utf-8")

    assert names["ACACIA_TRAPDOOR"] == "Акациевый люк"
    assert names["BLUE_STAINED_GLASS_PANE"] == "Синяя стеклянная панель"
    assert '"BLUE_STAINED_GLASS_PANE": ("Синяя стеклянная панель", "blue_stained_glass.png")' in backend
    assert 'NARCOTICS_RECIPE_RU_NAMES.get(item_id.upper(), "Предмет Minecraft")' in backend


def test_recipe_picker_buttons_and_sprites_are_large_enough_to_identify():
    css = (ROOT / "admin-web" / "frontend" / "assets" / "css" / "admin.css").read_text(encoding="utf-8")

    assert "grid-template-columns: repeat(auto-fill, minmax(64px, 64px));" in css
    assert "width: 64px;" in css
    assert "height: 64px;" in css
    assert "width: 52px;" in css
    assert "height: 52px;" in css
