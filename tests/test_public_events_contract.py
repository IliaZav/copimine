from __future__ import annotations

import json
import re
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
FRONTEND = ROOT / "admin-web" / "frontend"


def read(relative: str) -> str:
    return (ROOT / relative).read_text(encoding="utf-8")


def test_events_route_and_navigation_are_first_class_public_surfaces() -> None:
    page = read("admin-web/frontend/events.html")
    assert 'data-page-kind="public-events"' in page
    assert 'href="/events.html"' in page
    assert "Ивенты" in page
    assert 'public-events.css' in page
    assert 'public-page.js' in page

    nav_runtime = read("admin-web/frontend/assets/js/public/public-nav.js")
    assert "ensureEventsLink" in nav_runtime
    assert 'href = "/events.html"' in nav_runtime


def test_events_data_has_one_current_event_and_two_future_placeholders() -> None:
    payload = json.loads(
        (FRONTEND / "assets" / "public-data" / "events.json").read_text(encoding="utf-8")
    )
    events = payload["events"]
    assert {event["slug"] for event in events} == {"end-rift", "future-1", "future-2"}
    assert sum(event.get("status") == "current" for event in events) == 1
    current = next(event for event in events if event["slug"] == "end-rift")
    assert len(current["waves"]) == 6
    assert len(current["bossPhases"]) == 5
    assert len(current["requirements"]) >= 3
    assert len(current["rewards"]) >= 3
    assert "commons.wikimedia.org" in current["creditsHtml"]
    assert "нейросет" not in json.dumps(payload, ensure_ascii=False).lower()
    assert "покупай" not in json.dumps(payload, ensure_ascii=False).lower()


def test_events_client_uses_api_with_static_fallback_and_keeps_data_dom_safe() -> None:
    data = read("admin-web/frontend/assets/js/public/site-data.js")
    page = read("admin-web/frontend/assets/js/public/events-page.js")
    bootstrap = read("admin-web/frontend/assets/js/public/public-page.js")
    assert '"/api/public/events"' in data
    assert '"/assets/public-data/events.json"' in data
    assert "loadPublicEventsPageData" in data
    assert "initEventsPage" in bootstrap
    assert "public-events" in bootstrap
    assert "textContent" in page
    assert "innerHTML" not in page
    assert "insertAdjacentHTML" not in page


def test_events_assets_and_motion_contract_exist() -> None:
    assets = FRONTEND / "assets" / "events" / "end-rift"
    for name in ("end-landscape.png", "end-city.jpg", "enderman.png"):
        path = assets / name
        assert path.is_file() and path.stat().st_size > 1000, name

    styles = read("admin-web/frontend/assets/css/public-events.css")
    assert "@keyframes" in styles
    assert "prefers-reduced-motion" in styles
    assert re.search(r"\.event-hero", styles)
