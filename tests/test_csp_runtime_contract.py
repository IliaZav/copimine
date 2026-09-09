"""Contracts for cabinet markup that must remain compatible with the CSP."""

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def read(relative: str) -> str:
    return (ROOT / relative).read_text(encoding="utf-8")


def test_dashboard_bars_use_data_attributes_and_the_shared_runtime_styler() -> None:
    runtime = read("admin-web/frontend/assets/js/cabinet-runtime.js")

    assert 'class="week-bar primary" data-height=' in runtime
    assert 'class="week-bar secondary" data-height=' in runtime
    assert 'style="--height:' not in runtime
    assert 'root.querySelectorAll(".week-bar[data-height]")' in runtime


def test_recipe_icons_do_not_use_csp_blocked_inline_event_handlers() -> None:
    recipes = read("admin-web/frontend/assets/js/admin/narcotics-recipe-pages.js")
    runtime = read("admin-web/frontend/assets/js/cabinet-runtime.js")

    assert "data-fallback-icon" in recipes
    assert "onerror=" not in recipes
    assert "is-broken-image" in runtime
    assert "fallbackIcon" in runtime
