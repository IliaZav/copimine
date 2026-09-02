from __future__ import annotations

import asyncio
import importlib
import sys
from pathlib import Path

import pytest
from fastapi import HTTPException


ROOT = Path(__file__).resolve().parents[1]
ADMIN_ROOT = ROOT / "admin-web"
if str(ADMIN_ROOT) not in sys.path:
    sys.path.insert(0, str(ADMIN_ROOT))


def load_main(monkeypatch, tmp_path: Path):
    server = tmp_path / "server"
    world = server / "world"
    (world / "playerdata").mkdir(parents=True)
    (world / "stats").mkdir()
    (world / "advancements").mkdir()
    (server / "logs").mkdir(parents=True)
    (server / "logs" / "latest.log").write_text("[INFO] staging\n", encoding="utf-8")
    for name, value in {
        "COPIMINE_STARTUP_STRICT": "0",
        "SECRET_KEY": "s" * 64,
        "COPIMINE_AUTH_STORAGE": "sqlite",
        "COPIMINE_ADMIN_DATA": str(tmp_path / "admin-data"),
        "MC_SERVER_DIR": str(server),
        "MC_WORLD_DIR": str(world),
        "POSTGRES_PASSWORD": "",
        "ADMIN_PUBLIC_BASE_URL": "https://testserver",
        "ALLOW_INSECURE_HTTP_AUTH": "0",
    }.items():
        monkeypatch.setenv(name, value)
    sys.modules.pop("backend.main", None)
    return importlib.import_module("backend.main")


class FakeUpload:
    filename = "rift-trailer.mp4"
    content_type = "video/mp4"

    def __init__(self) -> None:
        self.chunks = [b"video", b"bytes", b""]

    async def read(self, _size: int) -> bytes:
        return self.chunks.pop(0)


def test_public_events_falls_back_to_static_contract_without_postgres(monkeypatch, tmp_path: Path) -> None:
    main = load_main(monkeypatch, tmp_path)

    payload = main.read_events_sync()

    assert {event["slug"] for event in payload["events"]} == {"end-rift", "future-1", "future-2"}
    assert all(event.get("videos") == [] for event in payload["events"])


def test_video_upload_reports_database_unavailable_and_removes_partial_file(monkeypatch, tmp_path: Path) -> None:
    main = load_main(monkeypatch, tmp_path)
    main.FRONTEND_DIR = tmp_path / "frontend"
    upload = FakeUpload()

    with pytest.raises(HTTPException) as error:
        asyncio.run(main.save_event_video(upload, "end-rift", "Трейлер", "", "staging-admin"))

    assert error.value.status_code == 503
    media_root = main.FRONTEND_DIR / "assets" / "events" / "end-rift"
    assert not list(media_root.glob("*.mp4"))
    assert not list(media_root.glob("*.part"))


def test_event_schema_bootstraps_on_staging_sqlite(monkeypatch, tmp_path: Path) -> None:
    main = load_main(monkeypatch, tmp_path)

    with main.auth_conn() as conn:
        main.ensure_v4_schema(conn)
        names = {
            row["name"]
            for row in conn.execute(
                "SELECT name FROM sqlite_master WHERE type='table' AND name IN ('site_event_pages','site_event_media')"
            ).fetchall()
        }

    assert names == {"site_event_pages", "site_event_media"}
