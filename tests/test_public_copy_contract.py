from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]

PUBLIC_TEMPLATES = (
    "admin-web/frontend/index.html",
    "admin-web/frontend/server.html",
    "admin-web/frontend/elections.html",
    "admin-web/frontend/shops.html",
    "admin-web/frontend/mods.html",
    "admin-web/frontend/signin.html",
    "admin-web/frontend/register.html",
    "admin-web/frontend/cart.html",
    "admin-web/frontend/404.html",
    "admin-web/frontend/error.html",
    "admin-web/frontend/preview-admin.html",
    "admin-web/frontend/preview-player.html",
)


def read(relative: str) -> str:
    return (ROOT / relative).read_text(encoding="utf-8")


def test_public_templates_do_not_expose_release_or_internal_copy():
    source = "\n".join(read(path) for path in PUBLIC_TEMPLATES)
    forbidden = (
        "Обновлено ",
        "Только просмотр",
        "только просмотр",
        "SHA1",
        "SHA архива",
        "контрольные суммы",
        "Размер архива",
        "дата обновления",
        "API health-check",
        "API-обработчик",
        "старой версии кабинета",
    )
    for phrase in forbidden:
        assert phrase not in source, phrase


def test_public_templates_do_not_ship_ai_sounding_or_operational_filler_copy():
    source = "\n".join(read(path) for path in PUBLIC_TEMPLATES)
    forbidden = (
        "Здесь собраны",
        "На странице собраны",
        "Здесь показывается только рабочая сводка",
        "Ниже показана открытая сводка",
        "Архив на сайте должен совпадать",
        "Здесь публикуется та же сборка",
        "Готовый комплект для входа",
        "Как это работает",
        "Что сделать дальше",
        "Что можно сделать",
        "рабочий раздел",
        "Resource pack hash",
        "Обновлено:",
    )
    for phrase in forbidden:
        assert phrase not in source, phrase


def test_public_renderer_uses_plain_short_fallbacks_without_release_copy():
    source = read("admin-web/frontend/assets/js/public/site-render.js")
    forbidden = (
        "Готовый комплект для входа",
        "Состояние архива модов пока не удалось получить",
        "Количество уже обновлено",
        "Как только состав будет утверждён",
        "Синхронизируется",
    )
    for phrase in forbidden:
        assert phrase not in source, phrase


def test_public_renderer_does_not_render_release_metadata_or_raw_mod_paths():
    source = read("admin-web/frontend/assets/js/public/site-render.js")
    start = source.index("function renderModpack")
    end = source.index("function renderServerHero", start)
    modpack_renderer = source[start:end]

    assert "shortSha" not in modpack_renderer
    assert "modpack.size" not in modpack_renderer
    assert "modpack.modified" not in modpack_renderer
    assert "file.path" not in modpack_renderer
    assert "file.license" not in modpack_renderer
    assert "Обновлён" not in modpack_renderer

    election_start = source.index("function renderElections")
    election_end = source.index("function renderPresidentLaws", election_start)
    election_renderer = source[election_start:election_end]
    assert "Только просмотр" not in election_renderer
    assert "только просмотр" not in election_renderer
    assert "Обновлено" not in election_renderer


def test_public_pages_keep_player_actions_and_data_mounts():
    index = read("admin-web/frontend/index.html")
    elections = read("admin-web/frontend/elections.html")
    mods = read("admin-web/frontend/mods.html")
    errors = read("admin-web/frontend/404.html") + read("admin-web/frontend/error.html")

    assert 'id="downloadLauncherBtn"' in index
    assert "Скачать лаунчер" in index
    assert 'id="presidentLaws"' in index
    assert 'id="publicElectionRefresh"' in elections
    assert 'href="/launcher.html"' in mods
    assert 'href="/index.html"' in errors
    assert 'href="/signin.html"' in errors


def test_public_modpack_meta_grid_has_loaded_layout_rules():
    css = read("admin-web/frontend/assets/css/release-ui.css")

    assert ".modpack-meta-grid" in css
    assert ".modpack-stat" in css
    assert "grid-template-columns: repeat(2, minmax(0, 1fr))" in css


def test_public_copy_audit_keeps_hero_and_sections_compact():
    css = read("admin-web/frontend/assets/css/ui-audit.css")

    assert ".public-site .public-hero-copy > p" in css
    assert ".public-site .scene-card-copy p" in css
    assert ".public-site .public-hero {" in css
    assert ".public-site .public-section {" in css


def test_public_templates_bust_the_cache_for_the_copy_audit_release():
    for path in PUBLIC_TEMPLATES:
        if path.endswith("/mods.html"):
            continue
        assert "style.css?v=20260831siteui25" in read(path), path

    public_page = read("admin-web/frontend/assets/js/public/public-page.js")
    homepage = read("admin-web/frontend/assets/js/public/homepage.js")
    assert "./homepage.js?v=20260825siteui19" in public_page
    assert "./site-render.js?v=20260825siteui19" in homepage
