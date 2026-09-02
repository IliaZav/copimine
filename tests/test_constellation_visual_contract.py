from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
FRONTEND = ROOT / "admin-web" / "frontend"


def read(relative: str) -> str:
    return (ROOT / relative).read_text(encoding="utf-8")


def test_constellation_layer_is_the_last_shared_visual_contract() -> None:
    style = read("admin-web/frontend/assets/style.css")
    cabinet = read("admin-web/frontend/assets/cabinet.css")
    import_line = '@import url("./css/copimine-constellation.css?v=20260903constellation4");'

    assert import_line in style
    assert import_line in cabinet
    assert style.rfind(import_line) > style.rfind("aurora-redesign.css")
    assert cabinet.rfind(import_line) > cabinet.rfind("aurora-redesign.css")


def test_constellation_palette_and_flat_layout_are_explicit() -> None:
    source = read("admin-web/frontend/assets/css/copimine-constellation.css")

    for token in (
        "--constellation-ink",
        "--constellation-cyan",
        "--constellation-violet",
        "--constellation-bg-image",
        "--constellation-heading-font",
        "--constellation-accent-font",
        "--constellation-body-font",
    ):
        assert token in source

    assert "#25f06f" not in source.lower()
    assert "rgba(37, 214, 111" not in source.lower()
    assert "border-radius: 0 !important" in source
    assert "background: transparent !important" in source
    assert "box-shadow: none !important" in source


def test_constellation_covers_public_cabinet_and_reduced_motion_states() -> None:
    source = read("admin-web/frontend/assets/css/copimine-constellation.css")

    for selector in (
        'body[data-page-kind^="public"]',
        'body[data-page-kind="cabinet"]',
        ".public-nav",
        ".public-section",
        ".sidebar",
        ".topbar",
        ".view > *",
        "@keyframes constellationReveal",
        "@keyframes constellationSweep",
        "@media (prefers-reduced-motion: reduce)",
    ):
        assert selector in source


def test_constellation_background_beats_the_legacy_page_pseudo_element_rules() -> None:
    source = read("admin-web/frontend/assets/css/copimine-constellation.css")

    assert 'body[data-page-kind]::before' in source
    assert 'body[data-page-kind]::after' in source
    assert 'body[data-page-kind^="public"]::before' in source
    assert 'body[data-page-kind^="public"]::after' in source
    assert ':root[data-theme] body[data-page-kind^="public"]::before' in source
    assert ':root[data-theme] body[data-page-kind^="public"]::after' in source
    assert ':root[data-theme="light"] body[data-page-kind]::before' in source


def test_constellation_uses_the_regenerated_balanced_font_trio() -> None:
    source = read("admin-web/frontend/assets/css/copimine-constellation.css")

    assert '--constellation-heading-font: "Space Grotesk"' in source
    assert '--constellation-accent-font: "Plus Jakarta Sans"' in source
    assert '--constellation-body-font: "Inter"' in source


def test_constellation_flattens_the_remaining_cabinet_chrome() -> None:
    source = read("admin-web/frontend/assets/css/copimine-constellation.css")

    for selector in (
        'body[data-page-kind="cabinet"] .nav-item',
        'body[data-page-kind="cabinet"] .hero-board',
        'body[data-page-kind="cabinet"] .admin-search-panel',
        'body[data-page-kind="cabinet"] .last-update',
        ':root[data-theme] body[data-page-kind="cabinet"].panel-admin-mode .nav-item',
        ':root[data-theme] body[data-page-kind="cabinet"].player-mode .nav-item',
        ':root[data-theme] body[data-page-kind="cabinet"].junior-admin-mode .nav-item',
    ):
        assert selector in source

    assert source.count("border-radius: 0 !important") >= 12
    assert source.count("background: transparent !important") >= 8


def test_constellation_replaces_legacy_lime_accents_with_the_cyan_signal() -> None:
    source = read("admin-web/frontend/assets/css/copimine-constellation.css")

    assert "--site-launcher-lime: var(--constellation-cyan) !important;" in source
    assert "--site-moss: var(--constellation-cyan) !important;" in source
    assert "--cabinet-site-moss: var(--constellation-cyan) !important;" in source
    assert ':root[data-theme] body[data-page-kind^="public"] .public-site :where(.hero-kicker' in source
    assert "#d8ffb2" not in source.lower()
