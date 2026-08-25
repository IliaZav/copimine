from __future__ import annotations

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
FRONTEND = ROOT / "admin-web" / "frontend"

PUBLIC_PAGES = (
    "index.html",
    "server.html",
    "elections.html",
    "shops.html",
    "launcher.html",
    "news.html",
    "signin.html",
    "register.html",
    "cart.html",
    *(f"news/{path.name}" for path in sorted((FRONTEND / "news").glob("*.html"))),
)


def read(relative: str) -> str:
    return (ROOT / relative).read_text(encoding="utf-8")


def test_public_copy_uses_player_language_on_key_routes() -> None:
    index = read("admin-web/frontend/index.html")
    launcher = read("admin-web/frontend/launcher.html")
    news = read("admin-web/frontend/news.html")
    shops = read("admin-web/frontend/shops.html")
    server = read("admin-web/frontend/server.html")

    assert "Всё, что нужно для игры на CopiMine." in index
    assert "Установи лаунчер. Играй на CopiMine." in launcher
    assert "Что нового на CopiMine." in news
    assert "Выбери предметы для игры." in shops
    assert "Что сейчас на сервере." in server

    public_patch_json = sorted((FRONTEND / "assets" / "public-data" / "patches").glob("*.json"))
    public_source = "\n".join(read(f"admin-web/frontend/{page}") for page in PUBLIC_PAGES)
    public_source += "\n" + "\n".join(path.read_text(encoding="utf-8") for path in public_patch_json)
    public_source += "\n" + read("admin-web/frontend/assets/js/admin/launcher-pages.js")
    forbidden = (
        "managed-модов",
        "Клиентская сборка",
        "Текущий релиз",
        "Состав установки",
        "Предпросмотр интерфейса",
        "managed-модов",
        "admission",
        "protocolVersion",
        "CLIENT_READY handshake",
        "resource pack mapping",
        "комплексная платформа",
        "бесшовный",
        "погрузитесь",
        "production provisioning",
        "external mojang/fabric download fallback",
        "sha-256",
        "metadata",
        "servers.dat",
        "ready→ack",
        "патчноут",
        "технические изменения",
        "локальный staging",
        "статической раздаче",
        "текущий релиз",
        "подписанный stable manifest",
        "в управляемом черновике",
        "анонимная диагностика launcher",
        "Launcher устанавливает Java",
        "установщик Launcher",
        "Launcher получает Minecraft",
        "Launcher сначала проверяет",
        "окно Launcher открывается",
        "возвращает Launcher к проверке",
        "сбой Launcher заканчивает",
        "показ окна Launcher",
        "Установщики Launcher",
        "Установщик ставит Launcher",
        "папке Launcher",
        "локального копии",
    )
    for phrase in forbidden:
        assert phrase.lower() not in public_source.lower(), phrase


def test_public_motion_layer_is_loaded_once_and_has_reduced_motion_fallback() -> None:
    style = read("admin-web/frontend/assets/style.css")
    public_page = read("admin-web/frontend/assets/js/public/public-page.js")
    motion = read("admin-web/frontend/assets/js/public/public-motion.js")
    motion_css = read("admin-web/frontend/assets/css/public-motion.css")

    assert '@import url("./css/public-motion.css")' in style
    assert 'from "./public-motion.js?v=20260825siteui17"' in public_page
    assert "prefers-reduced-motion" in motion
    assert "copimineSceneDrift" in motion_css
    assert "copimineSignalSweep" in motion_css
    assert "overflow: clip" in motion_css
    assert "@media (prefers-reduced-motion: reduce)" in motion_css
    assert "--scene-x" in motion_css
    assert "--scene-y" in motion_css


def test_public_motion_script_does_not_add_a_second_navigation_or_dom_handlers() -> None:
    motion = read("admin-web/frontend/assets/js/public/public-motion.js")

    assert "publicMobileNavToggle" not in motion
    assert "createElement(\"nav\")" not in motion
    assert "addEventListener(\"pointermove\"" in motion
    assert "requestAnimationFrame" in motion


def test_admin_copy_uses_plain_russian_labels() -> None:
    preview = read("admin-web/frontend/preview-admin.html")
    runtime = read("admin-web/frontend/assets/js/cabinet-runtime.js")
    news = read("admin-web/frontend/assets/js/admin/news-pages.js")
    source = "\n".join((preview, news))

    for phrase in ("Patch notes", "item-aware", "public feed", "Сайт: online", "minecraft: active", "admin: active", "discord: standby"):
        assert phrase.lower() not in source.lower(), phrase

    for phrase in (
        "Релизы, моды и статистика доставки",
        "Patch notes и item-aware изменения",
        "Patch notes и item-aware текстуры",
        "Новости Launcher",
        "CMS и баннеры",
        "Проверка после обновления сервера: плагины, конфиги, база и ресурспак",
    ):
        assert phrase.lower() not in runtime.lower(), phrase

    assert "Короткие записи об обновлениях" in preview
    assert "Сайт: работает" in preview
    assert "Игра — работает" in preview
    assert "Discord — ждёт подключения" in preview
    assert "Версии, моды и загрузки" in runtime
    assert "Картинки предметов" in news


def test_public_actions_and_statuses_avoid_mixed_technical_copy() -> None:
    index = read("admin-web/frontend/index.html")
    server = read("admin-web/frontend/server.html")
    launcher = read("admin-web/frontend/launcher.html")
    site_render = read("admin-web/frontend/assets/js/public/site-render.js")
    launcher_render = read("admin-web/frontend/assets/js/public/launcher-render.js")
    patch_render = read("admin-web/frontend/assets/js/public/patch-render.js")
    cart = read("admin-web/frontend/cart.html")
    cart_render = read("admin-web/frontend/assets/js/public/cart-page.js")
    source = "\n".join((index, server, launcher, site_render, cart))

    for phrase in (
        "Скачать Launcher",
        "Скопировать copimine.ru",
        "Ресурспак обязателен",
        "Ресурспак опционален",
        "Установи Launcher. Играй",
        "Внутри Launcher",
        "AR и donation",
        "Donation-корзина",
        "PIN donation-баланса",
        "Оплатить donation-корзину",
    ):
        assert phrase.lower() not in source.lower(), phrase

    assert "Скачать лаунчер" in source
    assert "Скопировать адрес" in source
    assert "Пакет ресурсов" in source
    assert "локальной копии" in read("admin-web/frontend/assets/public-data/patches/index.json")
    assert "донат-магазина" in cart_render
    assert "донат-корзину" in cart_render
    assert "Лаунчер" in launcher_render
    assert "Лаунчер" in patch_render
