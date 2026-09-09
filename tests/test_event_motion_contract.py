from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def test_event_reveal_has_a_visibility_fallback_for_idle_and_full_page_views() -> None:
    source = (ROOT / "admin-web" / "frontend" / "assets" / "js" / "public" / "events-page.js").read_text(encoding="utf-8")

    assert "revealFallback" in source
    assert "1800" in source
    assert "classList.add(\"is-visible\")" in source


def test_event_flat_layer_keeps_only_media_frames_rounded() -> None:
    source = (ROOT / "admin-web" / "frontend" / "assets" / "css" / "aurora-redesign.css").read_text(encoding="utf-8")

    assert ':root[data-theme] body[data-page-kind="public-events"] .public-site .event-hero' in source
    assert ':root[data-theme] body[data-page-kind="public-events"] .public-site .event-calendar-stage' in source
    assert ':root[data-theme] body[data-page-kind="public-events"] .public-site .event-calendar-card' in source
    assert ':root[data-theme] body[data-page-kind="public-events"] .public-site .event-mystery-card' in source
    assert ':root[data-theme] body[data-page-kind="public-events"] .public-site .event-gallery-item' in source
    assert "background: transparent !important" in source
