"""Keep the shared public navigation markup identical across static pages."""

from __future__ import annotations

import argparse
import re
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
FRONTEND = ROOT / "frontend"
HEADER_PATTERN = re.compile(
    r'<header\b[^>]*class="[^"]*\bpublic-nav\b[^"]*"[^>]*>[\s\S]*?</header>',
    flags=re.IGNORECASE,
)

PUBLIC_ROOT_PAGES = (
    "index.html",
    "server.html",
    "elections.html",
    "shops.html",
    "launcher.html",
    "news.html",
    "signin.html",
    "register.html",
    "cart.html",
    "404.html",
    "error.html",
)


def active_href(path: Path) -> str:
    if path.parent == FRONTEND / "news":
        return "/news.html"
    if path.parent == FRONTEND / "cabinet":
        return ""
    return f"/{path.name}" if path.name != "index.html" else "/index.html"


def nav_link(href: str, label: str, current: str, element_id: str = "") -> str:
    current_attr = ' aria-current="page"' if href == current else ""
    id_attr = f' id="{element_id}"' if element_id else ""
    return f'<a{id_attr} href="{href}"{current_attr}>{label}</a>'


def public_header(path: Path) -> str:
    current = active_href(path)
    links = (
        nav_link("/index.html", "Главная", current),
        nav_link("/server.html", "Сервер", current),
        nav_link("/elections.html", "Выборы", current),
        nav_link("/shops.html", "Лавки", current),
        nav_link("/launcher.html", "Лаунчер", current),
        nav_link("/news.html", "Новости", current),
        nav_link("/signin.html", "Войти", current, "publicSigninLink"),
        nav_link("/register.html", "Регистрация", current, "publicRegisterLink"),
    )
    cart = ""
    if path.name in {"shops.html", "cart.html"}:
        cart = (
            '<a id="shopCartButton" class="shop-cart-button" href="/cart.html" '
            'aria-label="Корзина пуста"><span>Корзина</span>'
            '<span id="shopCartCount" class="shop-cart-count" aria-live="polite">0</span></a>'
        )
    return (
        '<header class="public-nav public-nav-auth">'
        '<a class="public-brand" href="/index.html" aria-label="CopiMine">'
        '<img class="public-brand-logo" src="/assets/brand/copimine-logo.png" alt="" />'
        '<span class="public-brand-copy"><strong>CopiMine</strong><small>Сайт сервера</small></span>'
        "</a>"
        '<button id="mobileNavToggle" class="btn icon-btn mobile-only" type="button" '
        'aria-label="Открыть меню">&#9776;</button>'
        '<nav aria-label="Разделы сайта">'
        + "".join(links)
        + '<button id="publicCabinetBtn" class="btn btn-secondary hidden" type="button">Кабинет</button>'
        + '<button id="publicLogoutBtn" class="btn btn-ghost hidden" type="button">Выход</button>'
        + '<button class="btn btn-ghost theme-toggle public-theme-toggle" data-theme-toggle="true" '
        'data-theme-toggle-compact="true" type="button">Тема</button>'
        + cart
        + "</nav>"
        + (
            '<a class="shop-cart-button shop-cart-mobile-shortcut" href="/cart.html" '
            'aria-label="Корзина пуста"><span>Корзина</span>'
            '<span class="shop-cart-count" aria-live="polite">0</span></a>'
            if cart
            else ""
        )
        + "</header>"
    )


def target_pages() -> list[Path]:
    pages = [FRONTEND / name for name in PUBLIC_ROOT_PAGES]
    pages.extend(sorted((FRONTEND / "news").glob("*.html")))
    pages.extend(sorted((FRONTEND / "cabinet").glob("*.html")))
    return pages


def sync_page(path: Path, *, write: bool) -> bool:
    source = path.read_text(encoding="utf-8")
    updated, count = HEADER_PATTERN.subn(public_header(path), source, count=1)
    if count == 0 and path.name == "demoted.html":
        body_match = re.search(r"<body\b[^>]*>", source, flags=re.IGNORECASE)
        if not body_match:
            raise RuntimeError(f"missing body in {path.relative_to(ROOT)}")
        updated = source[: body_match.end()] + "\n  " + public_header(path) + source[body_match.end() :]
        count = 1
    if count != 1:
        raise RuntimeError(f"expected one public header in {path.relative_to(ROOT)}; found {count}")
    changed = updated != source
    if changed and write:
        path.write_text(updated, encoding="utf-8", newline="\n")
    return changed


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--write", action="store_true", help="rewrite static pages")
    args = parser.parse_args()
    changed = [path for path in target_pages() if sync_page(path, write=args.write)]
    mode = "updated" if args.write else "would update"
    print(f"{mode} {len(changed)} public pages")
    for path in changed:
        print(path.relative_to(ROOT))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
