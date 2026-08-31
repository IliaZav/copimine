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


def test_events_page_is_calendar_first_and_keeps_event_copy_compact() -> None:
    page = read("admin-web/frontend/events.html")
    runtime = read("admin-web/frontend/assets/js/public/events-page.js")
    styles = read("admin-web/frontend/assets/css/public-events.css")
    payload = json.loads(
        (FRONTEND / "assets" / "public-data" / "events.json").read_text(encoding="utf-8")
    )

    assert "event-calendar" in runtime
    assert "buildCalendar" in runtime
    assert "event-card-art" in runtime
    assert "event-card-art" in styles
    assert "event-mystery" in runtime
    assert "event-gallery" in runtime
    assert "event-dragon-flight" in runtime
    assert "end-landscape.png" in runtime
    assert "IntersectionObserver" in runtime
    assert "event-clock" in styles
    assert "event-clock-hand" in styles
    assert "event-reveal" in styles
    assert "event-vines" in runtime
    assert "event-vine" in styles
    assert "/assets/mc-icons/item/vine.png" in styles
    assert "Собери ресурсы" not in page
    assert "Собери ресурсы" not in runtime
    assert "buildRequirements" not in runtime
    assert "Одно событие уже открыто. Ещё два ждут своей очереди." not in runtime
    assert "Детали останутся за дверью до самого события." not in runtime
    assert "Настоящие игровые кадры — без постановочных рендеров." not in runtime
    assert "Когда время придёт, календарь сам покажет путь." not in runtime
    assert [event["title"] for event in payload["events"] if event["status"] == "upcoming"] == ["Скоро", "Скоро"]


def test_current_event_uses_real_end_capture_as_dragon_motion_source() -> None:
    runtime = read("admin-web/frontend/assets/js/public/events-page.js")
    payload = json.loads(
        (FRONTEND / "assets" / "public-data" / "events.json").read_text(encoding="utf-8")
    )
    current = next(event for event in payload["events"] if event["slug"] == "end-rift")
    asset = FRONTEND / "assets" / "events" / "end-rift" / "end-landscape.png"

    assert asset.is_file() and asset.stat().st_size > 1000
    assert current["heroImage"].endswith("end-landscape.png")
    assert "Screenshot_from_the_Minecraft_End.png" in current["creditsHtml"]
    assert "event-dragon-flight" in runtime
    assert "eventDragonFlight" in read("admin-web/frontend/assets/css/public-events.css")


def test_current_event_uses_curated_public_copy_and_keeps_dragon_fallback() -> None:
    runtime = read("admin-web/frontend/assets/js/public/events-page.js")

    assert 'const publicCopy = copy.status === "current" ? copy : editorialRecord;' in runtime
    assert 'const dragonImage = localAsset(editorialRecord.dragonImage) || copy.dragonImage;' in runtime
    assert "publicCopy.title" in runtime
    assert "publicCopy.summary" in runtime
    assert "publicCopy.body" in runtime
    assert "editorialRecord.title" not in runtime


def test_event_payload_does_not_publish_the_old_spoiler_copy() -> None:
    payload = json.loads(
        (FRONTEND / "assets" / "public-data" / "events.json").read_text(encoding="utf-8")
    )
    serialized = json.dumps(payload, ensure_ascii=False).lower()

    assert "собери команду" not in serialized
    assert "собери ресурсы" not in serialized
    assert "финальный бой начинается только" not in serialized


def test_event_route_change_restores_scroll_to_the_start_of_the_new_view() -> None:
    runtime = read("admin-web/frontend/assets/js/public/events-page.js")

    assert "window.scrollTo(0, 0)" in runtime
