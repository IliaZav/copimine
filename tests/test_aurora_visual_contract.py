from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
FRONTEND = ROOT / "admin-web" / "frontend"


def test_aurora_theme_is_the_last_public_and_cabinet_layer() -> None:
    public_css = (FRONTEND / "assets" / "style.css").read_text(encoding="utf-8")
    cabinet_css = (FRONTEND / "assets" / "cabinet.css").read_text(encoding="utf-8")

    public_import = next(line for line in public_css.splitlines() if "aurora-redesign.css" in line)
    cabinet_import = next(line for line in cabinet_css.splitlines() if "aurora-redesign.css" in line)

    assert "@import url(\"./css/aurora-redesign.css" in public_import
    assert "@import url(\"./css/aurora-redesign.css" in cabinet_import
    assert public_css.rfind("aurora-redesign.css") > public_css.rfind("./css/site-launcher-theme")
    assert cabinet_css.rfind("aurora-redesign.css") > cabinet_css.rfind("./css/cabinet-motion")


def test_aurora_theme_replaces_green_brand_accents_with_cyan_violet_system() -> None:
    path = FRONTEND / "assets" / "css" / "aurora-redesign.css"
    assert path.exists()
    source = path.read_text(encoding="utf-8").lower()

    for token in ("--aurora-cyan", "--aurora-violet", "--aurora-blue", "--aurora-ink"):
        assert token in source
    assert "#35f07f" not in source
    assert "rgba(37, 214, 111" not in source
    for selector in (".public-nav", ".public-hero", ".btn", ".sidebar", ".topbar", ".panel", ".metric"):
        assert selector in source
    assert "@keyframes aurora" in source


def test_aurora_uses_a_full_bleed_flat_canvas_instead_of_card_surfaces() -> None:
    source = (FRONTEND / "assets" / "css" / "aurora-redesign.css").read_text(encoding="utf-8").lower()

    assert "--aurora-flat-canvas" in source
    assert "--aurora-full-bleed-image" in source
    assert "body[data-page-kind^=\"public\"]::before" in source
    assert "inset: 0 !important" in source
    assert ".aurora-flat-canvas" in source
    assert "background: transparent !important" in source
    assert "border-radius: 0 !important" in source
    assert "box-shadow: none !important" in source


def test_flat_surface_override_matches_legacy_dark_theme_specificity() -> None:
    source = (FRONTEND / "assets" / "css" / "aurora-redesign.css").read_text(encoding="utf-8")

    assert ':root[data-theme] body[data-page-kind^="public"] .public-site .public-hero' in source
    assert ':root[data-theme] body[data-page-kind^="public"] .public-site .public-panel' in source
    assert ':root[data-theme] body[data-page-kind^="public"] .public-site .launcher-feature-card' in source
    assert ':root[data-theme] body[data-page-kind="cabinet"]' in source
    assert ':root[data-theme] body.auth-screen .public-site .auth-card' in source
    assert ':root[data-theme] body.launcher-link-page .launcher-link-card' in source
    assert ':root[data-theme] body.error-screen .error-card' in source
    assert ':root[data-theme] body.preview-shell .preview-bank-balance' in source


def test_account_status_page_does_not_fall_back_to_a_legacy_card() -> None:
    source = (FRONTEND / "assets" / "css" / "aurora-redesign.css").read_text(encoding="utf-8")

    assert "body.auth-screen .demoted-card" in source
    assert "body.auth-screen .demoted-card" in source[source.index("/* Auth and error routes"):]


def test_selected_font_trio_and_public_nav_override_are_canonical() -> None:
    source = (FRONTEND / "assets" / "css" / "aurora-redesign.css").read_text(encoding="utf-8")

    assert '--aurora-display: "Sora"' in source
    assert '--aurora-accent-font: "Manrope"' in source
    assert '--aurora-body: "Inter"' in source
    assert '--aurora-flat-display: "Sora"' in source
    assert '--aurora-flat-body: "Inter"' in source
    assert '--aurora-flat-data: "Inter"' in source
    assert ':root[data-theme] body[data-page-kind^="public"] .public-nav nav > a' in source
    assert ':root[data-theme] body.auth-screen .public-nav' in source
    assert 'border-radius: 0 !important;' in source


def test_flat_theme_has_one_motion_policy_for_reduced_motion() -> None:
    source = (FRONTEND / "assets" / "css" / "aurora-redesign.css").read_text(encoding="utf-8")
    assert "@media (prefers-reduced-motion: reduce)" in source
    assert "animation: none !important;" in source
    assert "transition-duration: 0.01ms !important;" in source


def test_all_rendered_pages_bust_the_shared_theme_cache_after_a_visual_revision() -> None:
    pages = list(FRONTEND.glob("*.html")) + list((FRONTEND / "news").glob("*.html"))
    for page in pages:
        source = page.read_text(encoding="utf-8")
        if "/assets/style.css" in source:
            assert "/assets/style.css?v=20260902flat6" in source, page.name
    style = (FRONTEND / "assets" / "style.css").read_text(encoding="utf-8")
    assert "aurora-redesign.css?v=20260902flat6" in style


def test_shared_visual_layers_do_not_reintroduce_the_retired_forest_palette() -> None:
    paths = (
        "admin-web/frontend/assets/css/site-atmosphere.css",
        "admin-web/frontend/assets/css/site-launcher-theme.css",
        "admin-web/frontend/assets/css/cabinet-atmosphere.css",
        "admin-web/frontend/assets/css/cabinet-shell-polish.css",
        "admin-web/frontend/assets/css/cabinet-launcher-theme.css",
        "admin-web/frontend/assets/css/preview-atmosphere.css",
    )
    retired = ("#0d2a1e", "#0a2016", "#70e39b", "#7650c7", "#53cdb8", "rgba(112, 227, 155")
    for relative in paths:
        source = (ROOT / relative).read_text(encoding="utf-8").lower()
        for token in retired:
            assert token not in source, f"{token} remains in {relative}"
