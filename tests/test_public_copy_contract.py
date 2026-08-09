from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]

PUBLIC_TEMPLATES = (
    "admin-web/frontend/index.html",
    "admin-web/frontend/server.html",
    "admin-web/frontend/elections.html",
    "admin-web/frontend/mods.html",
    "admin-web/frontend/404.html",
    "admin-web/frontend/error.html",
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

    assert "Скачать модпак" in index
    assert 'id="presidentLaws"' in index
    assert 'id="publicElectionRefresh"' in elections
    assert 'id="modpackMetaGrid"' in mods
    assert 'href="/index.html"' in errors
    assert 'href="/signin.html"' in errors
