from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
FRONTEND = ROOT / "admin-web" / "frontend"
CABINET_CSS = FRONTEND / "assets" / "cabinet.css"
THEME_CSS = FRONTEND / "assets" / "css" / "cabinet-launcher-theme.css"


def test_cabinet_loads_the_shared_launcher_visual_layer_last():
    source = CABINET_CSS.read_text(encoding="utf-8")
    assert '@import url("./css/cabinet-launcher-theme.css?v=20260902cabinetui30");' in source
    assert source.index("cabinet-atmosphere.css") < source.index("cabinet-launcher-theme.css")


def test_cabinet_theme_uses_launcher_palette_and_semantic_motion_hooks():
    source = THEME_CSS.read_text(encoding="utf-8")
    for token in ("--site-launcher-bg", "--site-launcher-cyan", "--site-launcher-lime", "--site-launcher-violet"):
        assert token in source
    for hook in ("cabinetSignalSweep", "cabinetCardIn", "cabinetStatusPulse"):
        assert f"@keyframes {hook}" in source
    assert "prefers-reduced-motion: reduce" in source
    assert "animation: none !important" in source


def test_cabinet_theme_has_accessible_focus_and_no_layout_overflow_rule():
    source = THEME_CSS.read_text(encoding="utf-8")
    assert ":focus-visible" in source
    assert "overflow-x: clip" in source
    assert "min-width: 0" in source


def test_cabinet_theme_restores_data_layout_primitives():
    source = THEME_CSS.read_text(encoding="utf-8")
    assert ".layout-grid" in source
    assert ".grid-4" in source
    assert ".grid-3" in source
    assert ".grid-2" in source
    assert "grid-template-columns: repeat(4, minmax(0, 1fr));" in source
    assert "grid-template-columns: repeat(3, minmax(0, 1fr));" in source
    assert "grid-template-columns: repeat(2, minmax(0, 1fr));" in source
    assert ".metric {" in source
    assert "display: grid;" in source
    assert ".metric-label" in source
    assert ".metric-value" in source
    assert ".metric-detail" in source


def test_cabinet_data_grids_collapse_at_real_breakpoints():
    source = THEME_CSS.read_text(encoding="utf-8")
    assert "@media (max-width: 1240px)" in source
    assert "@media (max-width: 680px)" in source
    assert "@media (max-width: 680px) {\n  body.player-mode .grid-4" in source
    assert "grid-template-columns: 1fr;" in source


def test_cabinet_mobile_drawer_is_out_of_flow_until_opened():
    source = THEME_CSS.read_text(encoding="utf-8")
    assert "position: fixed;" in source
    assert "transform: translateX(-104%);" in source
    assert ".nav-open .sidebar" in source
    assert "transform: translateX(0);" in source


def test_cabinet_html_uses_current_visual_cache_key():
    pages = list((FRONTEND / "cabinet").glob("*.html"))
    assert pages
    for page in pages:
        source = page.read_text(encoding="utf-8")
        if "assets/cabinet.css" in source:
            assert "cabinet.css?v=20260902flat6" in source


def test_cabinet_and_admin_share_the_final_flat_workspace_composition():
    source = (ROOT / "admin-web" / "frontend" / "assets" / "css" / "aurora-redesign.css").read_text(encoding="utf-8")

    assert "/* Final cabinet composition: one workspace, two role accents. */" in source
    assert 'body[data-page-kind="cabinet"] .sidebar' in source
    assert 'body[data-page-kind="cabinet"] .topbar' in source
    assert 'body[data-page-kind="cabinet"] .view > *' in source
    assert 'body.panel-admin-mode .nav-item.active' in source
    assert 'body.player-mode .nav-item.active' in source
    assert "grid-template-columns: repeat(4, minmax(0, 1fr)) !important;" in source
    assert "border-top: 1px solid var(--aurora-flat-rule) !important;" in source
    assert "background: transparent !important;" in source
    assert "box-shadow: none !important;" in source
    assert 'body[data-page-kind="cabinet"] .top-actions > *' in source
    assert "width: auto !important;" in source


def test_cabinet_mobile_navigation_stays_a_drawer_until_requested():
    source = (ROOT / "admin-web" / "frontend" / "assets" / "css" / "aurora-redesign.css").read_text(encoding="utf-8")

    assert "/* Final cabinet mobile navigation: content first, menu on demand. */" in source
    assert 'body[data-page-kind="cabinet"] .sidebar' in source
    assert "inset: 0 auto 0 0 !important;" in source
    assert "height: 100dvh !important;" in source
    assert "transform: translateX(-104%) !important;" in source
    assert 'body[data-page-kind="cabinet"] #app.nav-open .sidebar' in source
    assert "transform: translateX(0) !important;" in source
    assert 'body[data-page-kind="cabinet"] .layout-grid.grid-2' in source
    assert 'body[data-page-kind="cabinet"] .layout-grid.grid-3' in source
