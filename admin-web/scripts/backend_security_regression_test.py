#!/usr/bin/env python3
"""Focused regressions for security boundaries in the admin backend.

The test uses a disposable SQLite store and never opens the real server files.
Run with the admin-web virtual-environment Python interpreter.
"""
from __future__ import annotations

import asyncio
import importlib
import json
import os
import sys
import tempfile
import time
from pathlib import Path
from types import SimpleNamespace


ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))


def load_modules(temp: Path):
    server = temp / "minecraft-server"
    world = server / "CopiMine"
    (world / "playerdata").mkdir(parents=True)
    (server / "logs").mkdir(parents=True)
    (server / "plugins").mkdir()
    (server / "usercache.json").write_text(
        json.dumps([{"uuid": "11111111-1111-1111-1111-111111111111", "name": "ExistingHero"}]),
        encoding="utf-8",
    )
    (server / "whitelist.json").write_text("[]", encoding="utf-8")
    (server / "ops.json").write_text("[]", encoding="utf-8")
    (server / "server.properties").write_text("level-name=CopiMine\n", encoding="utf-8")
    (server / "logs" / "latest.log").write_text("[INFO] ready\n", encoding="utf-8")
    env_file = temp / "test.env"
    env_file.write_text(
        "\n".join(
            [
                "COPIMINE_AUTH_STORAGE=sqlite",
                f"COPIMINE_AUTH_DB={(temp / 'auth.sqlite3').as_posix()}",
                f"DATABASE_URL=sqlite:///{(temp / 'auth.sqlite3').as_posix()}",
                f"COPIMINE_ADMIN_DATA={(temp / 'data').as_posix()}",
                "SECRET_KEY=" + ("t" * 64),
                "ADMIN_PUBLIC_BASE_URL=https://panel.example.test",
                "COPIMINE_STARTUP_STRICT=0",
                "REQUIRE_OP_FOR_LOGIN=0",
                "REQUIRE_WHITELIST_FOR_LOGIN=0",
                "PLUGIN_API_KEY=test-plugin-key",
                "DISCORD_BOT_TOKEN=test-discord-token",
                "DISCORD_APPLICATIONS_CHANNEL_ID=111111111111111111",
                "DISCORD_REPORTS_CHANNEL_ID=222222222222222222",
                "DISCORD_BRIDGE_PLAYER_LIMIT=2",
                "DISCORD_BRIDGE_PLAYER_WINDOW_SECONDS=60",
                "ALLOW_INSECURE_HTTP_AUTH=0",
                f"MC_SERVER_DIR={server.as_posix()}",
                f"MC_WORLD_DIR={world.as_posix()}",
                f"MC_LOG_FILE={(server / 'logs' / 'latest.log').as_posix()}",
            ]
        ),
        encoding="utf-8",
    )
    os.environ.update(
        {
            "COPIMINE_ENV_FILE": str(env_file),
            "COPIMINE_AUTH_STORAGE": "sqlite",
            "COPIMINE_AUTH_DB": str(temp / "auth.sqlite3"),
            "DATABASE_URL": f"sqlite:///{(temp / 'auth.sqlite3').as_posix()}",
            "COPIMINE_ADMIN_DATA": str(temp / "data"),
            "SECRET_KEY": "t" * 64,
            "ADMIN_PUBLIC_BASE_URL": "https://panel.example.test",
            "COPIMINE_STARTUP_STRICT": "0",
            "REQUIRE_OP_FOR_LOGIN": "0",
            "REQUIRE_WHITELIST_FOR_LOGIN": "0",
            "PLUGIN_API_KEY": "test-plugin-key",
            "DISCORD_BOT_TOKEN": "test-discord-token",
            "DISCORD_APPLICATIONS_CHANNEL_ID": "111111111111111111",
            "DISCORD_REPORTS_CHANNEL_ID": "222222222222222222",
            "DISCORD_BRIDGE_PLAYER_LIMIT": "2",
            "DISCORD_BRIDGE_PLAYER_WINDOW_SECONDS": "60",
            "ALLOW_INSECURE_HTTP_AUTH": "0",
            "MC_SERVER_DIR": str(server),
            "MC_WORLD_DIR": str(world),
            "MC_LOG_FILE": str(server / "logs" / "latest.log"),
        }
    )
    for module_name in (
        "backend.main",
        "backend.discord_bot",
        "backend.minecraft_discord_bridge",
        "backend.deploy_runtime",
    ):
        sys.modules.pop(module_name, None)
    main = importlib.import_module("backend.main")
    runtime = importlib.import_module("backend.deploy_runtime")
    discord_bot = importlib.import_module("backend.discord_bot")
    bridge = importlib.import_module("backend.minecraft_discord_bridge")
    return main, runtime, discord_bot, bridge


def assert_untrusted_report_metadata_is_normal(main) -> None:
    forged = {
        "reportKind": "bug",
        "errorCode": "FAKE-9000",
        "errorSummary": "forged technical incident",
        "bugReport": {
            "technical": {"stackTrace": "private stack trace", "details": "database details"},
            "diagnostics": {"requestId": "forged-request"},
        },
    }
    item = main.create_report_sync(
        main.DiscordReportIn(
            reporter="PlayerOne",
            reporter_uuid="11111111-1111-1111-1111-111111111111",
            message="I found a problem in the shop",
            metadata=forged,
        ),
        "player-site",
        "PlayerOne",
    )
    assert item["reportType"] == "report", item
    assert "bugReport" not in item["metadata"], item
    assert "errorCode" not in item["metadata"], item


def assert_only_correlated_plugin_reports_keep_technical_context(main) -> None:
    metadata = {
        "reportKind": "bug",
        "errorCode": "AB12CD34",
        "errorSummary": "Database request failed",
        "bugReport": {
            "errorCode": "AB12CD34",
            "errorSummary": "Database request failed",
            "capturedAt": 123,
            "technical": {"exceptionClass": "SQLException", "details": "server-only details"},
            "diagnostics": {"requestId": "a1b2c3d4", "actionId": "shop-buy"},
        },
    }
    valid = main.create_report_sync(
        main.DiscordReportIn(
            reporter="PlayerOne",
            reporter_uuid="11111111-1111-1111-1111-111111111111",
            message="[BUG AB12CD34] The buy action failed",
            metadata=metadata,
        ),
        "plugin",
        "PlayerOne",
    )
    assert valid["reportType"] == "bug", valid
    assert valid["metadata"]["bugReport"]["technical"]["details"] == "server-only details", valid

    uncorrelated = main.create_report_sync(
        main.DiscordReportIn(
            reporter="PlayerOne",
            reporter_uuid="11111111-1111-1111-1111-111111111111",
            message="I want to pretend this is a server error",
            metadata=metadata,
        ),
        "plugin",
        "PlayerOne",
    )
    assert uncorrelated["reportType"] == "report", uncorrelated
    assert "bugReport" not in uncorrelated["metadata"], uncorrelated


def assert_public_health_has_no_runtime_diagnostics(main) -> None:
    main._STARTUP_REPORT = {
        "ok": True,
        "summary": {"total": 1, "failures": 0, "warnings": 0},
        "checks": [
            {
                "key": "postgres",
                "status": "ok",
                "required": True,
                "summary": "postgres connection ok",
                "details": {"host": "db.internal", "path": "/srv/copimine/.env", "password": "never-public"},
            }
        ],
    }
    payload = asyncio.run(main.health())
    serialized = json.dumps(payload, ensure_ascii=False)
    assert "details" not in payload["checks"][0], payload
    assert "/srv/copimine" not in serialized, payload
    assert "db.internal" not in serialized, payload
    assert "never-public" not in serialized, payload
    transport = payload.get("transport", {})
    assert transport.get("authenticatedSessions") == "https-required", payload
    assert transport.get("http") == "public-only", payload


def assert_http_does_not_issue_reusable_auth_cookies(main) -> None:
    from fastapi.testclient import TestClient

    password = "CorrectHorseBatteryStaple!"
    main.ADMIN_USERS_FILE.write_text(
        json.dumps(
            {
                "AdminUser": {
                    "password_hash": main.make_password_hash(password),
                    "enabled": True,
                    "role": "admin",
                }
            }
        ),
        encoding="utf-8",
    )
    with TestClient(main.app) as client:
        response = client.post("/api/auth/login", json={"username": "AdminUser", "password": password})
    assert response.status_code == 426, response.text
    assert main.AUTH_COOKIE_NAME not in response.headers.get("set-cookie", ""), response.headers
    with TestClient(main.app, base_url="https://panel.example.test") as client:
        secure_response = client.post("/api/auth/login", json={"username": "AdminUser", "password": password})
    assert secure_response.status_code == 200, secure_response.text
    assert "secure" in secure_response.headers.get("set-cookie", "").lower(), secure_response.headers

    original_opt_in = main.ALLOW_INSECURE_HTTP_AUTH
    main.ALLOW_INSECURE_HTTP_AUTH = True
    try:
        with TestClient(main.app) as client:
            opt_in_response = client.post("/api/auth/login", json={"username": "AdminUser", "password": password})
        assert opt_in_response.status_code == 200, opt_in_response.text
        assert "secure" not in opt_in_response.headers.get("set-cookie", "").lower(), opt_in_response.headers
        assert main.public_auth_transport_state()["authenticatedSessions"] == "insecure-http-opt-in"
    finally:
        main.ALLOW_INSECURE_HTTP_AUTH = original_opt_in


def assert_untrusted_forwarded_origin_is_not_accepted(main) -> None:
    from starlette.requests import Request

    request = Request(
        {
            "type": "http",
            "method": "POST",
            "scheme": "http",
            "path": "/api/player/reports",
            "raw_path": b"/api/player/reports",
            "query_string": b"",
            "headers": [
                (b"host", b"panel.example.test"),
                (b"x-forwarded-host", b"attacker.example.test"),
                (b"x-forwarded-proto", b"https"),
            ],
            "client": ("198.51.100.44", 54321),
            "server": ("panel.example.test", 80),
        }
    )
    assert not main.origin_allowed(request, "https://attacker.example.test")


def assert_reverse_proxy_http_is_not_mistaken_for_a_local_login(main) -> None:
    from starlette.requests import Request

    request = Request(
        {
            "type": "http",
            "method": "POST",
            "scheme": "http",
            "path": "/api/auth/login",
            "raw_path": b"/api/auth/login",
            "query_string": b"",
            "headers": [
                (b"host", b"panel.example.test"),
                (b"x-forwarded-for", b"198.51.100.44"),
                (b"x-forwarded-proto", b"http"),
            ],
            "client": ("127.0.0.1", 54321),
            "server": ("127.0.0.1", 8090),
        }
    )
    assert not main.auth_transport_is_allowed(request)

    spoofed_loopback = Request(
        {
            "type": "http",
            "method": "POST",
            "scheme": "http",
            "path": "/api/auth/login",
            "raw_path": b"/api/auth/login",
            "query_string": b"",
            "headers": [
                (b"host", b"panel.example.test"),
                (b"x-forwarded-for", b"127.0.0.1"),
                (b"x-forwarded-proto", b"http"),
                (b"x-forwarded-host", b"panel.example.test"),
            ],
            "client": ("127.0.0.1", 54321),
            "server": ("127.0.0.1", 8090),
        }
    )
    assert not main.auth_transport_is_allowed(spoofed_loopback)

    direct_local = Request(
        {
            "type": "http",
            "method": "POST",
            "scheme": "http",
            "path": "/api/auth/login",
            "raw_path": b"/api/auth/login",
            "query_string": b"",
            "headers": [(b"host", b"127.0.0.1:8090")],
            "client": ("127.0.0.1", 54321),
            "server": ("127.0.0.1", 8090),
        }
    )
    assert main.auth_transport_is_allowed(direct_local)


def assert_automatic_whitelist_cannot_be_approved_into_identity_ownership(main) -> None:
    from fastapi import HTTPException

    account_id = "auto-whitelist-account"
    minecraft_name = "AutoOnlyHero"
    minecraft_uuid = main.offline_uuid_for_name(minecraft_name)
    request_id = "wl-auto-only"
    now = main.now_ts()
    with main.auth_conn() as conn:
        main.ensure_v4_schema(conn)
        conn.execute(
            "INSERT INTO site_accounts(id,username,username_norm,password_hash,role,enabled,minecraft_uuid,minecraft_name,created_at,updated_at,last_login_at,registration_ip) VALUES(%s,%s,%s,%s,'player',1,'','',%s,%s,%s,'')",
            (account_id, "autowhitelist", "autowhitelist", main.make_password_hash("CorrectHorseBatteryStaple!"), now, now, now),
        )
        conn.execute(
            "INSERT INTO whitelist_requests(id,site_account_id,minecraft_uuid,minecraft_name,request_ip,status,created_at,updated_at,approved_at,approved_by,note) VALUES(%s,%s,%s,%s,'127.0.0.1','AUTO_APPROVED',%s,%s,%s,'automatic-registration','Automatic whitelist only')",
            (request_id, account_id, minecraft_uuid, minecraft_name, now, now, now),
        )
        conn.commit()
    rejected = False
    try:
        main.approve_whitelist_request_sync(request_id, "AdminUser", "manual click", "web")
    except HTTPException as exc:
        rejected = exc.status_code == 409
    assert rejected, "An AUTO_APPROVED whitelist row must not be convertible into an identity link"
    with main.auth_conn() as conn:
        row = conn.execute("SELECT status FROM whitelist_requests WHERE id=%s", (request_id,)).fetchone()
        link = conn.execute("SELECT site_account_id FROM whitelist_account_links WHERE minecraft_uuid=%s", (minecraft_uuid,)).fetchone()
        account = conn.execute("SELECT minecraft_uuid FROM site_accounts WHERE id=%s", (account_id,)).fetchone()
        conn.commit()
    assert row["status"] == "AUTO_APPROVED", row
    assert link is None, link
    assert account["minecraft_uuid"] == "", account


def assert_registration_does_not_claim_an_existing_minecraft_identity(main) -> None:
    from fastapi.testclient import TestClient

    with TestClient(main.app, base_url="https://panel.example.test") as client:
        response = client.post(
            "/api/player/register",
            json={
                "username": "newowner",
                "password": "CorrectHorseBatteryStaple!",
                "minecraft_name": "ExistingHero",
            },
        )
    assert response.status_code == 200, response.text
    payload = response.json()
    assert payload["account"]["minecraftUuid"] == "", payload
    assert payload["account"]["minecraftName"] == "", payload
    with main.auth_conn() as conn:
        account = main.player_account_by_username(conn, "newowner")
        linked = conn.execute(
            "SELECT site_account_id FROM minecraft_account_links WHERE minecraft_uuid=%s",
            (main.offline_uuid_for_name("ExistingHero"),),
        ).fetchone()
        whitelist_link = conn.execute(
            "SELECT site_account_id FROM whitelist_account_links WHERE minecraft_uuid=%s",
            (main.offline_uuid_for_name("ExistingHero"),),
        ).fetchone()
        conn.commit()
    assert account["minecraft_uuid"] == "", account
    assert linked is None, linked
    assert whitelist_link is None, whitelist_link
    whitelist = json.loads((main.MC_SERVER_DIR / "whitelist.json").read_text(encoding="utf-8"))
    assert not any(row.get("name") == "ExistingHero" for row in whitelist), whitelist


def assert_fresh_registration_keeps_automatic_whitelist_without_bank_link(main) -> None:
    from fastapi.testclient import TestClient

    with TestClient(main.app, base_url="https://panel.example.test") as client:
        response = client.post(
            "/api/player/register",
            json={
                "username": "freshowner",
                "password": "CorrectHorseBatteryStaple!",
                "minecraft_name": "FreshHero",
            },
        )
        csrf_token = client.cookies.get(main.CSRF_COOKIE_NAME)
        report_response = client.post(
            "/api/player/reports",
            headers={main.CSRF_HEADER_NAME: str(csrf_token or "")},
            json={
                "message": "I found a normal issue",
                "metadata": {
                    "reportKind": "bug",
                    "errorCode": "FAKE-9000",
                    "bugReport": {"technical": {"stackTrace": "forged"}},
                },
                "attached_events": [{"technical": "forged"}],
            },
        )
    assert response.status_code == 200, response.text
    assert report_response.status_code == 200, report_response.text
    report = report_response.json()["report"]
    assert report["reportType"] == "report", report
    assert report["attached_events"] == [], report
    payload = response.json()
    assert payload["account"]["minecraftUuid"] == "", payload
    with main.auth_conn() as conn:
        account = main.player_account_by_username(conn, "freshowner")
        linked = conn.execute(
            "SELECT site_account_id FROM minecraft_account_links WHERE minecraft_uuid=%s",
            (main.offline_uuid_for_name("FreshHero"),),
        ).fetchone()
        whitelist_link = conn.execute(
            "SELECT site_account_id FROM whitelist_account_links WHERE minecraft_uuid=%s",
            (main.offline_uuid_for_name("FreshHero"),),
        ).fetchone()
        conn.commit()
    assert account["minecraft_uuid"] == "", account
    assert linked is None, linked
    assert whitelist_link is None, whitelist_link
    whitelist = json.loads((main.MC_SERVER_DIR / "whitelist.json").read_text(encoding="utf-8"))
    assert any(row.get("name") == "FreshHero" for row in whitelist), whitelist


def assert_link_code_cannot_reassign_another_players_identity(main) -> None:
    from fastapi import HTTPException

    protected_name = "ProtectedHero"
    protected_uuid = main.offline_uuid_for_name(protected_name)
    now = main.donation_now_ms()
    owner_id = "owner-account"
    attacker_id = "attacker-account"
    with main.auth_conn() as conn:
        main.ensure_v4_schema(conn)
        for account_id, username, minecraft_uuid, minecraft_name in (
            (owner_id, "protectedowner", protected_uuid, protected_name),
            (attacker_id, "attacker", "", ""),
        ):
            conn.execute(
                "INSERT INTO site_accounts(id,username,username_norm,password_hash,role,enabled,minecraft_uuid,minecraft_name,created_at,updated_at,last_login_at,registration_ip) VALUES(%s,%s,%s,%s,'player',1,%s,%s,%s,%s,%s,'')",
                (account_id, username, username, main.make_password_hash("CorrectHorseBatteryStaple!"), minecraft_uuid, minecraft_name, now, now, now),
            )
        conn.execute(
            "INSERT INTO minecraft_account_links(minecraft_uuid,minecraft_name,site_account_id,status,linked_at,updated_at) VALUES(%s,%s,%s,'ACTIVE',%s,%s)",
            (protected_uuid, protected_name, owner_id, now, now),
        )
        conn.execute(
            "INSERT INTO one_time_link_codes(id,site_account_id,minecraft_name,minecraft_uuid,code_hash,status,created_at,expires_at) VALUES(%s,%s,%s,%s,%s,'PENDING',%s,%s)",
            ("attacker-code", attacker_id, protected_name, protected_uuid, main.sha256_hex("SAFE1234"), now, now + 60_000),
        )
        conn.commit()
    blocked = False
    try:
        main.confirm_link_code_sync({"id": attacker_id, "username": "attacker"}, "SAFE1234")
    except HTTPException as exc:
        blocked = exc.status_code == 409
    assert blocked, "A valid one-time code must not transfer a Minecraft identity already linked to another site account"
    with main.auth_conn() as conn:
        link = conn.execute("SELECT site_account_id FROM minecraft_account_links WHERE minecraft_uuid=%s", (protected_uuid,)).fetchone()
        conn.commit()
    assert link["site_account_id"] == owner_id, link


def assert_artifact_digest_is_cached(runtime, temp: Path) -> None:
    artifact_path = temp / "large-artifact.bin"
    artifact_path.write_bytes(b"copimine" * 4096)
    artifact = runtime.ManagedArtifact(
        key="test",
        bucket="downloads",
        filename="large-artifact.bin",
        path=artifact_path,
        url="/downloads/large-artifact.bin",
        media_type="application/octet-stream",
    )
    first = runtime.artifact_snapshot(artifact)
    original_digest = runtime.digest_file
    calls = []

    def count_digest(path: Path, algorithm: str) -> str:
        calls.append((path, algorithm))
        return original_digest(path, algorithm)

    runtime.digest_file = count_digest
    try:
        second = runtime.artifact_snapshot(artifact)
    finally:
        runtime.digest_file = original_digest
    assert first["sha256"] == second["sha256"], (first, second)
    assert not calls, calls

    artifact_path.write_bytes(b"copimine-updated" * 4096)
    refreshed_ns = time.time_ns() + 1_000_000
    os.utime(artifact_path, ns=(refreshed_ns, refreshed_ns))
    calls.clear()
    runtime.digest_file = count_digest
    try:
        changed = runtime.artifact_snapshot(artifact)
    finally:
        runtime.digest_file = original_digest
    assert changed["sha256"] != first["sha256"], (first, changed)
    assert len(calls) == 2, calls


def assert_bridge_limits_distinct_messages(bridge, temp: Path) -> None:
    bridge.DEDUPE_FILE = temp / "bridge-dedupe.json"
    bridge.RATE_FILE = temp / "bridge-rate.json"
    sent = []
    original_send = bridge.send_discord
    bridge.send_discord = lambda *args, **kwargs: sent.append((args, kwargs))
    try:
        for index in range(5):
            bridge.process_line(f"[12:00:0{index}] [Server thread/INFO]: <PlayerOne> report issue-{index}")
    finally:
        bridge.send_discord = original_send
    assert len(sent) == 2, sent


def assert_discord_rejects_mutable_role_names(discord_bot) -> None:
    member = SimpleNamespace(
        id=42,
        roles=[SimpleNamespace(id=777, name="Operator")],
        guild_permissions=SimpleNamespace(administrator=False),
    )
    assert not discord_bot.can_approve_whitelist(member)


def assert_discord_cannot_reapprove_a_denied_request(discord_bot) -> None:
    class Cursor:
        def fetchone(self):
            return {
                "id": "request-1",
                "status": "DENIED",
                "minecraft_name": "PlayerOne",
                "minecraft_uuid": "11111111-1111-1111-1111-111111111111",
            }

    class DeniedRequestConnection:
        def __enter__(self):
            return self

        def __exit__(self, *_):
            return False

        def execute(self, sql, _args=()):
            assert "SELECT * FROM whitelist_requests" in sql
            return Cursor()

    original_db = discord_bot.db
    discord_bot.db = lambda: DeniedRequestConnection()
    try:
        raised = False
        try:
            discord_bot.approve_whitelist_request("request-1", "admin")
        except RuntimeError as exc:
            raised = str(exc) == "whitelist_request_not_pending"
    finally:
        discord_bot.db = original_db
    assert raised, "A stale Discord reaction must not reapprove a denied whitelist request"


def assert_public_discord_report_never_includes_snapshot(discord_bot) -> None:
    embed = discord_bot.Bot.report_embed(
        object(),
        {
            "id": "rep_123",
            "player_name": "PlayerOne",
            "message": "I found a problem",
            "status": "OPEN",
            "created_at": 1,
            "snapshot": "world=CopiMine error=SQLException password=never-public",
        },
        admin=False,
    )
    values = "\n".join(str(field.value) for field in embed.fields)
    names = {str(field.name) for field in embed.fields}
    assert "Snapshot" not in names, names
    assert "never-public" not in values, values


def main() -> None:
    with tempfile.TemporaryDirectory(prefix="copimine-backend-security-") as raw_temp:
        temp = Path(raw_temp)
        main_module, runtime, discord_bot, bridge = load_modules(temp)
        assert_untrusted_report_metadata_is_normal(main_module)
        assert_only_correlated_plugin_reports_keep_technical_context(main_module)
        assert_public_health_has_no_runtime_diagnostics(main_module)
        assert_http_does_not_issue_reusable_auth_cookies(main_module)
        assert_untrusted_forwarded_origin_is_not_accepted(main_module)
        assert_reverse_proxy_http_is_not_mistaken_for_a_local_login(main_module)
        assert_automatic_whitelist_cannot_be_approved_into_identity_ownership(main_module)
        assert_registration_does_not_claim_an_existing_minecraft_identity(main_module)
        assert_fresh_registration_keeps_automatic_whitelist_without_bank_link(main_module)
        assert_link_code_cannot_reassign_another_players_identity(main_module)
        assert_artifact_digest_is_cached(runtime, temp)
        assert_bridge_limits_distinct_messages(bridge, temp)
        assert_discord_rejects_mutable_role_names(discord_bot)
        assert_discord_cannot_reapprove_a_denied_request(discord_bot)
        assert_public_discord_report_never_includes_snapshot(discord_bot)
    print("Backend security regression test OK")


if __name__ == "__main__":
    main()
