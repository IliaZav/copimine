from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
FRONTEND = ROOT / "admin-web" / "frontend"
CABINET_CSS = FRONTEND / "assets" / "cabinet.css"
THEME_CSS = FRONTEND / "assets" / "css" / "cabinet-launcher-theme.css"


def test_cabinet_loads_the_shared_launcher_visual_layer_last():
    source = CABINET_CSS.read_text(encoding="utf-8")
    assert '@import url("./css/cabinet-launcher-theme.css?v=20260901cabinetui28");' in source
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
            assert "cabinetui28" in source
