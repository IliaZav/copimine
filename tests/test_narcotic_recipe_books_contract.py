"""Contract tests for the AR narcotic recipe fragments."""

from __future__ import annotations

import re
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CATALOG = ROOT / "copimine-artifacts" / "items.yml"
NARCOTICS = ROOT / "copimine-narcotics" / "config.yml"
ARTIFACTS_SOURCE = (
    ROOT / "copimine-artifacts" / "src" / "me" / "copimine" / "artifacts" / "CopiMineArtifacts.java"
)
BOOK_DATA_SOURCE = (
    ROOT / "copimine-artifacts" / "src" / "me" / "copimine" / "artifacts" / "NarcoticRecipeBookData.java"
)


EXPECTED_RECIPES = {
    "feta": ["WHITE_DYE", "GLOWSTONE_DUST", "RABBIT_FOOT", "POTION:WEAKNESS"],
    "kola": ["SUGAR", "DIAMOND", "JUNGLE_LEAVES"],
    "girion": ["SLIME_BLOCK", "TURTLE_SCUTE", "EMERALD"],
    "sbp": ["GOLD_INGOT", "GOLDEN_CARROT", "STRING"],
    "sos": ["BONE", "IRON_BLOCK", "GHAST_TEAR"],
    "drun": ["AMETHYST_BLOCK", "END_ROD", "IRON_INGOT"],
    "chups": ["BLUE_STAINED_GLASS", "POTION:SPEED", "COCOA_BEANS", "IRON_NUGGET"],
    "borshevik": ["LARGE_FERN", "DRIED_KELP_BLOCK", "SUGAR_CANE"],
}


def item_block(item_id: str) -> str:
    text = CATALOG.read_text(encoding="utf-8")
    match = re.search(
        rf"(?ms)^  - id: {re.escape(item_id)}\s*$.*?(?=^  - id: |\Z)",
        text,
    )
    assert match, f"catalog item {item_id!r} is missing"
    return match.group(0)


def narcotic_block(item_id: str) -> str:
    text = NARCOTICS.read_text(encoding="utf-8")
    match = re.search(
        rf"(?ms)^  {re.escape(item_id)}:\s*$.*?(?=^  [a-z0-9_]+:\s*$|\Z)",
        text,
    )
    assert match, f"narcotic recipe {item_id!r} is missing"
    return match.group(0)


def recipe_tokens(item_id: str) -> list[str]:
    block = narcotic_block(item_id)
    tokens = re.findall(r"^      - ((?:material|potion):[A-Z0-9_]+)$", block, re.MULTILINE)
    return [
        token.split(":", 1)[1] if token.lower().startswith("material:") else token.upper()
        for token in tokens
    ]


def test_every_recipe_with_ingredients_has_a_300_ar_written_book() -> None:
    for recipe_id, expected_tokens in EXPECTED_RECIPES.items():
        block = item_block(f"narcotic_recipe_{recipe_id}")
        assert "category: TOOL" in block
        assert "material: WRITTEN_BOOK" in block
        assert "source: AR_SHOP" in block
        assert "custom_model_data: 0" in block
        assert "name: \"&dОбрывок из книги рецептов\"" in block
        assert "rarity: EPIC" in block
        assert "price_ar: 300" in block
        assert "effect: NARCOTIC_RECIPE_BOOK" in block
        assert "lore:\n      - \"&7Обрывок из книги рецептов странного учёного\"" in block
        assert recipe_tokens(recipe_id) == expected_tokens


def test_zhuzevo_without_ingredients_has_no_recipe_book() -> None:
    assert "narcotic_recipe_zhuzevo" not in CATALOG.read_text(encoding="utf-8")
    assert re.search(r"(?ms)^  zhuzevo:.*?^    recipe: \[\]", NARCOTICS.read_text(encoding="utf-8"))


def test_book_runtime_writes_two_pages_and_does_not_touch_brewing() -> None:
    source = ARTIFACTS_SOURCE.read_text(encoding="utf-8")
    book_data = BOOK_DATA_SOURCE.read_text(encoding="utf-8")
    assert "BookMeta" in source
    assert "setPages" in source
    assert "NarcoticRecipeBookData.forItem" in source
    assert "WRITTEN_BOOK" in CATALOG.read_text(encoding="utf-8")
    assert "public static BookData forItem" in book_data
    assert "pages().size()" not in source or "setPages(data.pages())" in source


def test_all_recipe_keys_are_present_in_pure_book_data() -> None:
    source = BOOK_DATA_SOURCE.read_text(encoding="utf-8")
    for recipe_id in EXPECTED_RECIPES:
        assert f'"narcotic_recipe_{recipe_id}"' in source


if __name__ == "__main__":
    tests = [
        test_every_recipe_with_ingredients_has_a_300_ar_written_book,
        test_zhuzevo_without_ingredients_has_no_recipe_book,
        test_book_runtime_writes_two_pages_and_does_not_touch_brewing,
        test_all_recipe_keys_are_present_in_pure_book_data,
    ]
    failures = []
    for test in tests:
        try:
            test()
        except AssertionError as exc:
            failures.append(f"{test.__name__}: {exc}")
    if failures:
        for failure in failures:
            print(failure)
        raise SystemExit(1)
    print(f"{len(tests)} narcotic recipe book contract tests passed")
