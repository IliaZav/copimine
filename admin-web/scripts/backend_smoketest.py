#!/usr/bin/env python3
"""Backend smoke-test on a fake Minecraft directory. Does not touch real server files."""
from __future__ import annotations
import importlib
import json
import os
import tempfile
import hashlib
import sys
from pathlib import Path
from typing import Optional


ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))
VENV_SITE = ROOT / ".venv" / "lib"
if VENV_SITE.exists():
    for candidate in VENV_SITE.glob("python*/site-packages"):
        if candidate.exists() and str(candidate) not in sys.path:
            sys.path.insert(0, str(candidate))


def expected_venv_python() -> Optional[str]:
    if not VENV_SITE.exists():
        return None
    versions = sorted(
        {
            candidate.parent.name
            for candidate in VENV_SITE.glob("python*/site-packages")
            if candidate.exists() and candidate.parent.name.startswith("python")
        }
    )
    if not versions:
        return None
    return versions[-1].replace("python", "", 1)


def import_backend_main():
    try:
        return importlib.import_module("backend.main")
    except ModuleNotFoundError as exc:
        expected = expected_venv_python()
        current = f"{sys.version_info.major}.{sys.version_info.minor}"
        compiled_suffix = "._pydantic_core"
        mismatch = expected and expected != current and (
            exc.name == "pydantic_core._pydantic_core" or compiled_suffix in str(exc)
        )
        if mismatch and os.getenv("COPIMINE_STRICT_SMOKE", "0") != "1":
            print(
                "Backend smoketest skipped: current Python "
                f"{current} is incompatible with project venv packages built for Python {expected}. "
                "Run the smoketest with the project venv interpreter on Ubuntu, for example "
                "`/opt/copimine/admin-web/.venv/bin/python scripts/backend_smoketest.py`, "
                "or set COPIMINE_STRICT_SMOKE=1 to force a hard failure."
            )
            return None
        raise


def main() -> None:
    with tempfile.TemporaryDirectory(ignore_cleanup_errors=True) as tmp:
        base = Path(tmp) / "server"
        world = base / "world"
        (world / "playerdata").mkdir(parents=True)
        (world / "stats").mkdir()
        (world / "advancements").mkdir()
        (base / "logs").mkdir(parents=True)
        (base / "ops.json").write_text(json.dumps([
            {"uuid":"u1","name":"SudoKillDash9","level":4},
            {"uuid":"u2","name":"Alvarez_227","level":4},
        ]), encoding="utf-8")
        (base / "whitelist.json").write_text(json.dumps([
            {"uuid":"u1","name":"SudoKillDash9"},
            {"uuid":"u2","name":"Alvarez_227"},
            {"uuid":"u3","name":"TestUser"},
        ]), encoding="utf-8")
        (base / "usercache.json").write_text(json.dumps([
            {"uuid":"u1","name":"SudoKillDash9"},
            {"uuid":"u2","name":"Alvarez_227"},
            {"uuid":"u3","name":"TestUser"},
        ]), encoding="utf-8")
        (base / "banned-players.json").write_text("[]", encoding="utf-8")
        (base / "banned-ips.json").write_text("[]", encoding="utf-8")
        (base / "server.properties").write_text("motd=CopiMine\nmax-players=20\nresource-pack=\nresource-pack-sha1=\nrequire-resource-pack=false\n", encoding="utf-8")
        (base / "logs" / "latest.log").write_text("[INFO] ok\n", encoding="utf-8")
        data_dir = Path(tmp) / "data"
        data_dir.mkdir()
        test_password = "TestPassword123!"
        test_hash = "sha256:" + hashlib.sha256(test_password.encode("utf-8")).hexdigest()
        (data_dir / "admin_users.json").write_text(json.dumps({
            "SudoKillDash9": {"password_hash": test_hash, "enabled": True, "role": "admin"}
        }), encoding="utf-8")
        os.environ.update({
            "MC_SERVER_DIR": str(base),
            "MC_WORLD_DIR": str(world),
            "MC_LOG_FILE": str(base / "logs" / "latest.log"),
            "SECRET_KEY": "x" * 64,
            "PLUGIN_API_KEY": "test-plugin-key",
            "DISCORD_BOT_API_KEY": "test-discord-key",
            "RCON_PASSWORD": "",
            "REQUIRE_OP_FOR_LOGIN": "1",
            "REQUIRE_WHITELIST_FOR_LOGIN": "1",
            "COPIMINE_ADMIN_DATA": str(data_dir),
            "COPIMINE_BACKUPS_DIR": str(Path(tmp) / "backups"),
        })
        appmod = import_backend_main()
        if appmod is None:
            return
        from fastapi.testclient import TestClient
        with TestClient(appmod.app) as c:
            assert c.get("/api/health").status_code == 200
            r = c.post("/api/auth/login", json={"username":"SudoKillDash9", "password":test_password})
            assert r.status_code == 200, r.text
            assert r.json()["cookieAuth"] is True
            csrf_boot = c.get("/api/auth/csrf")
            assert csrf_boot.status_code == 200, csrf_boot.text
            csrf_token = c.cookies.get(appmod.CSRF_COOKIE_NAME)
            assert csrf_token, "CSRF cookie was not issued"
            h = {appmod.CSRF_HEADER_NAME: csrf_token}
            for url in [
                "/api/auth/me", "/api/status", "/api/server/files", "/api/minecraft/access-lists",
                "/api/backups", "/api/resourcepack/status", "/api/security/admins",
                "/api/data-sources", "/api/system/services", "/api/audit", "/api/plugin/events",
                "/api/economy/ares/history", "/api/discord/status",
            ]:
                rr = c.get(url)
                assert rr.status_code == 200, (url, rr.status_code, rr.text)
                assert "/opt/copimine/" not in rr.text
                assert ".env" not in rr.text
                assert "CHANGE_ME" not in rr.text
            rr = c.post("/api/plugin/events", headers={"X-API-Key":"test-plugin-key"}, json={"source":"smoke","event_type":"smoke_test","metadata":{"token":"secret-value"}})
            assert rr.status_code == 200, rr.text
            rr = c.post("/api/applications", headers=h, json={
                "player": "TestUser",
                "uuid": "u3",
                "age": "18",
                "experience": "RP/SMP",
                "why": "wants to join the roleplay server",
                "discord_user_id": "1234567890",
            })
            assert rr.status_code == 200, rr.text
            application = rr.json()["application"]
            rr = c.post("/api/reports", headers=h, json={
                "reporter": "TestUser",
                "target": "BadActor",
                "message": "grief near spawn",
                "severity": "high",
                "world": "world",
                "x": 10,
                "y": 64,
                "z": -5,
            })
            assert rr.status_code == 200, rr.text
            report = rr.json()["report"]
            rr = c.get("/api/discord/outbox?status=pending", headers={"X-API-Key":"test-discord-key"})
            assert rr.status_code == 200, rr.text
            pending = rr.json()["items"]
            assert any(x.get("objectType") == "application" and x.get("objectId") == application["id"] for x in pending)
            assert any(x.get("objectType") == "report" and x.get("objectId") == report["id"] for x in pending)
            rr = c.get("/api/discord/applications", headers={"X-API-Key":"wrong-key"})
            assert rr.status_code == 403, rr.text
            rr = c.patch(f"/api/discord/applications/{application['id']}/status", headers={"X-API-Key":"test-discord-key"}, json={
                "status": "approved",
                "reason": "looks good",
                "discord_user_id": "1234567890",
                "discord_username": "Moderator#0001",
            })
            assert rr.status_code == 200, rr.text
            assert rr.json()["application"]["status"] == "approved"
            rr = c.patch(f"/api/discord/reports/{report['id']}/status", headers={"X-API-Key":"test-discord-key"}, json={
                "status": "in_progress",
                "reason": "claimed from Discord",
                "discord_user_id": "1234567890",
                "discord_username": "Moderator#0001",
            })
            assert rr.status_code == 200, rr.text
            assert rr.json()["report"]["status"] == "in_progress"
            rr = c.post("/api/discord/actions", headers={"X-API-Key":"test-discord-key"}, json={
                "action": "close",
                "object_type": "report",
                "object_id": report["id"],
                "discord_user_id": "1234567890",
                "discord_username": "Moderator#0001",
                "note": "resolved from button",
            })
            assert rr.status_code == 200, rr.text
            assert rr.json()["action"]["object"]["status"] == "closed"
            rr = c.get("/api/applications", headers=h)
            assert rr.status_code == 200 and rr.json()["count"] == 1, rr.text
            rr = c.get("/api/reports", headers=h)
            assert rr.status_code == 200 and rr.json()["count"] == 1, rr.text
            rr = c.post("/api/rcon", headers=h, json={"command":"op TestUser"})
            assert rr.status_code == 403, rr.text
            rr = c.post("/api/backups", headers=h, json={"scope":"configs", "include_logs":True, "include_world":False})
            assert rr.status_code == 200, rr.text
            assert "path" not in rr.json()
    print("Backend smoketest OK")


if __name__ == "__main__":
    main()
