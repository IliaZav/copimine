from __future__ import annotations

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def read(relative: str) -> str:
    return (ROOT / relative).read_text(encoding="utf-8")


def test_backend_exposes_public_and_admin_event_contracts() -> None:
    backend = read("admin-web/backend/main.py")
    for token in (
        "/api/public/events",
        "/api/admin/events",
        "/api/admin/events/{slug}",
        "/videos",
        "site_event_pages",
        "site_event_media",
        "require_admin",
        "require_sensitive_confirm",
        "check_rate_limit",
    ):
        assert token in backend
    assert "UploadFile" in backend
    assert "sha256" in backend
    assert "os.replace" in backend


def test_admin_runtime_has_events_section_and_video_editor() -> None:
    runtime = read("admin-web/frontend/assets/js/cabinet-runtime.js")
    pages = read("admin-web/frontend/assets/js/admin/events-pages.js")
    for token in ('"events"', "createAdminEventsPages", "/api/admin/events"):
        assert token in runtime or token in pages
    for token in ("FormData", "/videos", "EVENTS_SAVE", "EVENTS_MEDIA_DELETE"):
        assert token in pages
    assert "innerHTML" not in pages
    assert "onclick=" not in pages


def test_event_video_upload_is_streamed_and_confined_to_frontend_assets() -> None:
    backend = read("admin-web/backend/main.py")
    for token in ("await file.read", "assets/events", "Path(file.filename).name", ".part"):
        assert token in backend
    assert "UPLOAD_VIDEO_EXTENSIONS" in backend
    assert "is_relative_to" in backend
