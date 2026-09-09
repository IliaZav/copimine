"""Read-only admin integrations must fail with a usable diagnostic payload."""

from __future__ import annotations

import asyncio
import importlib
import sys
from pathlib import Path


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
    missing_coreprotect = tmp_path / "missing-coreprotect.db"
    for name, value in {
        "COPIMINE_STARTUP_STRICT": "0",
        "SECRET_KEY": "s" * 64,
        "COPIMINE_AUTH_STORAGE": "sqlite",
        "COPIMINE_AUTH_DB": str(tmp_path / "admin-data" / "auth.sqlite3"),
        "COPIMINE_ADMIN_DATA": str(tmp_path / "admin-data"),
        "MC_SERVER_DIR": str(server),
        "MC_WORLD_DIR": str(world),
        "MC_LOG_FILE": str(server / "logs" / "latest.log"),
        "COREPROTECT_DB": str(missing_coreprotect),
        "POSTGRES_PASSWORD": "",
        "ADMIN_PUBLIC_BASE_URL": "https://testserver",
        "ALLOW_INSECURE_HTTP_AUTH": "0",
        "COPIMINE_LAUNCHER_CONTROL_DIR": str(tmp_path / "control"),
        "COPIMINE_LAUNCHER_PUBLIC_ROOT": str(tmp_path / "public"),
    }.items():
        monkeypatch.setenv(name, value)
    sys.modules.pop("backend.main", None)
    return importlib.import_module("backend.main")


def test_missing_coreprotect_is_reported_without_turning_the_read_endpoint_into_a_404(monkeypatch, tmp_path: Path) -> None:
    main = load_main(monkeypatch, tmp_path)

    result = asyncio.run(main.investigations_block_logs(limit=10, _="qa-owner"))

    assert result["rows"] == []
    assert result["source"]["exists"] is False
    assert result["error"]


def test_economy_selector_does_not_probe_full_profile_for_roster_only_players() -> None:
    source = (ROOT / "admin-web" / "frontend" / "assets" / "js" / "admin" / "commerce-pages.js").read_text(encoding="utf-8")

    assert "hasPlayerData" in source
    assert "data-has-player-data" in source
    assert "player.hasPlayerData === false" in source
