"""Runtime-level regressions for the admin report queue and recipe picker."""

import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "admin-web"))

from backend import main  # noqa: E402


def test_recipe_picker_uses_russian_canonical_labels_and_sprites() -> None:
    """Configured ingredients must not be inferred from flattened texture names."""
    previous_cache = main._NARCOTICS_RECIPE_ITEMS_CACHE
    try:
        main._NARCOTICS_RECIPE_ITEMS_CACHE = (0, [])
        catalog = {row["id"]: row for row in main._minecraft_recipe_item_catalog()}
    finally:
        main._NARCOTICS_RECIPE_ITEMS_CACHE = previous_cache

    expected = {
        "SUGAR": ("Сахар", "/assets/mc-icons/item/sugar.png"),
        "LARGE_FERN": ("Большой папоротник", "/assets/mc-icons/item/large_fern_top.png"),
        "DRIED_KELP_BLOCK": ("Блок сушёной ламинарии", "/assets/mc-icons/item/dried_kelp_side.png"),
    }
    for material, (name, icon_url) in expected.items():
        assert catalog[material]["name"] == name
        assert catalog[material]["iconUrl"] == icon_url


def test_canonical_report_row_keeps_structured_bug_metadata() -> None:
    """The admin queue must render a persisted automatic bug report as a bug."""
    row = main.admin_request_to_report_row(
        {
            "id": "bug-1",
            "player_uuid": "00000000-0000-0000-0000-000000000001",
            "player_name": "Tester",
            "message": "[BUG ABCD1234] Автоматический отчёт",
            "status": "OPEN",
            "created_at": 100,
            "updated_at": 101,
            "snapshot": '{"id":"bug-1","reportType":"bug","errorCode":"ABCD1234","errorSummary":"Database request failed","metadata":{"reportKind":"bug"}}',
        }
    )

    assert row["id"] == "bug-1"
    assert row["reportType"] == "bug"
    assert row["errorCode"] == "ABCD1234"
    assert row["metadata"] == {"reportKind": "bug"}


def test_recipe_editor_prefers_the_configured_russian_label_over_catalog_fallback() -> None:
    editor = (ROOT / "admin-web" / "frontend" / "assets" / "js" / "admin" / "narcotics-recipe-pages.js").read_text(encoding="utf-8")

    assert "const RECIPE_TOKEN_LABELS = new Map" in editor
    assert "const canonical = RECIPE_TOKEN_LABELS.get(value.toUpperCase());" in editor
    assert "if (canonical) return canonical;" in editor
    assert editor.index("if (canonical) return canonical;") < editor.index("const source = kind === \"POTION\"")
