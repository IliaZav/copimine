"""Behavioral contracts for the cabinet shell and generated cabinet controls."""

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CABINET = ROOT / "admin-web" / "frontend" / "cabinet"


def read(relative: str) -> str:
    return (ROOT / relative).read_text(encoding="utf-8")


def cabinet_templates() -> list[Path]:
    return sorted(
        path
        for path in CABINET.glob("*.html")
        if 'data-page-kind="cabinet"' in path.read_text(encoding="utf-8")
    )


def test_cabinet_templates_have_distinct_public_and_cabinet_nav_controls() -> None:
    for path in cabinet_templates():
        source = path.read_text(encoding="utf-8")
        assert source.count('id="mobileNavToggle"') == 1, path.name
        assert source.count('id="cabinetNavToggle"') == 1, path.name
        assert 'id="cabinetNavToggle" class="btn icon-btn mobile-only"' in source, path.name
        assert 'aria-controls="nav"' in source, path.name
        assert 'aria-expanded="false"' in source, path.name


def test_cabinet_shell_uses_one_responsive_breakpoint_for_sidebar_and_toggle() -> None:
    css = read("admin-web/frontend/assets/css/ui-audit.css")
    polish = read("admin-web/frontend/assets/js/cabinet-polish.js")
    runtime = read("admin-web/frontend/assets/js/cabinet-runtime.js")
    assert "@media (max-width: 980px)" in css
    assert ".cabinet-nav-toggle" in css
    assert 'getElementById("cabinetNavToggle")' in polish
    assert '$("cabinetNavToggle")' in runtime


def test_generated_tables_have_keyboard_sorting_and_named_filters() -> None:
    runtime = read("admin-web/frontend/assets/js/cabinet-runtime.js")
    assert '<th scope="col"' in runtime
    assert 'class="table-sort"' in runtime
    assert 'aria-sort="' in runtime
    assert 'aria-label="Поиск по таблице"' in runtime


def test_active_cabinet_route_is_exposed_to_assistive_technology() -> None:
    runtime = read("admin-web/frontend/assets/js/cabinet-runtime.js")
    assert 'setAttribute("aria-current", "page")' in runtime


def test_role_specific_site_button_is_not_forced_visible_for_players() -> None:
    polish = read("admin-web/frontend/assets/js/cabinet-polish.js")
    css = read("admin-web/frontend/assets/css/ui-polish.css")
    assert "isPlayer" in polish
    assert "syncGuestSiteButton(auth);" in polish
    assert "syncGuestSiteButton();" not in polish
    assert "body.player-mode #guestPagesBtn" in css
    assert "display: none !important" in css


def test_cabinet_loading_subtitle_is_not_the_same_internal_copy_on_every_route() -> None:
    repeated = "Рабочие разделы, счета, история и игровые сервисы."
    for path in cabinet_templates():
        assert repeated not in path.read_text(encoding="utf-8"), path.name
