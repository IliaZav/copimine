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
BACKEND_ROOT = ROOT / "backend"
if str(BACKEND_ROOT) not in sys.path:
    sys.path.insert(0, str(BACKEND_ROOT))
VENV_SITE = ROOT / ".venv" / "lib"


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


def current_python_version() -> str:
    return f"{sys.version_info.major}.{sys.version_info.minor}"


def venv_layout_is_compatible() -> bool:
    if not VENV_SITE.exists():
        return True
    if os.name == "nt":
        return not ((ROOT / ".venv" / "bin").exists() and not (ROOT / ".venv" / "Scripts").exists())
    return True


def venv_python_is_compatible() -> bool:
    expected = expected_venv_python()
    return (not expected or expected == current_python_version()) and venv_layout_is_compatible()


if VENV_SITE.exists() and (venv_python_is_compatible() or os.getenv("COPIMINE_STRICT_SMOKE", "0") == "1"):
    for candidate in VENV_SITE.glob("python*/site-packages"):
        if candidate.exists() and str(candidate) not in sys.path:
            sys.path.insert(0, str(candidate))


def import_backend_main():
    expected = expected_venv_python()
    current = current_python_version()
    if expected and expected != current and os.getenv("COPIMINE_STRICT_SMOKE", "0") != "1":
        print(
            "Backend smoketest skipped: current Python "
            f"{current} is incompatible with project venv packages built for Python {expected}. "
            "Run the smoketest with the project venv interpreter on Ubuntu, for example "
            "`/opt/copimine/admin-web/.venv/bin/python scripts/backend_smoketest.py`, "
            "or set COPIMINE_STRICT_SMOKE=1 to force a hard failure."
        )
        return None
    try:
        return importlib.import_module("backend.main")
    except (ModuleNotFoundError, ImportError) as exc:
        compiled_suffix = "._pydantic_core"
        exc_name = str(getattr(exc, "name", "") or "")
        exc_text = str(exc)
        mismatch = expected and expected != current and (
            exc_name == "pydantic_core._pydantic_core"
            or compiled_suffix in exc_name
            or compiled_suffix in exc_text
            or "pydantic_core" in exc_text
            or "compiled for Python" in exc_text
        )
        posix_layout_mismatch = not venv_layout_is_compatible() and (
            "fastapi" in exc_text.lower()
            or "pydantic" in exc_text.lower()
            or "starlette" in exc_text.lower()
            or "discord" in exc_text.lower()
            or "psycopg" in exc_text.lower()
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
        if posix_layout_mismatch and os.getenv("COPIMINE_STRICT_SMOKE", "0") != "1":
            print(
                "Backend smoketest skipped: project .venv uses a POSIX layout that cannot satisfy this Windows interpreter, "
                "and the current interpreter does not have the required backend packages installed. "
                "Run the smoketest with the project venv interpreter on Ubuntu, or use a separate Windows venv with admin-web requirements."
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
        narcotics_dir = base / "plugins" / "CopiMineNarcotics"
        narcotics_dir.mkdir(parents=True)
        narcotics_config = narcotics_dir / "config.yml"
        narcotics_config.write_text(
            """items:
  test_item:
    display_name: Test recipe
    material: SUGAR
    recipe:
      - material:sugar
      - material:redstone
      - potion:water
  zhuzevo:
    display_name: Hidden base item
    material: PAPER
    recipe:
      - material:paper
      - material:paper
      - material:paper
""",
            encoding="utf-8",
        )
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
            "ADMIN_PUBLIC_BASE_URL": "http://127.0.0.1:18080",
            # The smoke test intentionally models a trusted local HTTP setup.
            # Production public sessions stay HTTPS-only unless this opt-in is set.
            "ALLOW_INSECURE_HTTP_AUTH": "1",
            "COPIMINE_AUTH_STORAGE": "sqlite",
            "COPIMINE_AUTH_DB": str(data_dir / "auth.sqlite3"),
            "DATABASE_URL": f"sqlite:///{(data_dir / 'auth.sqlite3').as_posix()}",
            "PLUGIN_API_KEY": "test-plugin-key",
            "DISCORD_BOT_API_KEY": "test-discord-key",
            "COPIMINE_STARTUP_STRICT": "0",
            "RCON_PASSWORD": "",
            "REQUIRE_OP_FOR_LOGIN": "1",
            "REQUIRE_WHITELIST_FOR_LOGIN": "1",
            "COPIMINE_ADMIN_DATA": str(data_dir),
            "COPIMINE_BACKUPS_DIR": str(Path(tmp) / "backups"),
            "RCON_PASSWORD": "test-rcon-password",
        })
        appmod = import_backend_main()
        if appmod is None:
            return
        rcon_commands: list[str] = []

        def fake_rcon(command: str) -> str:
            rcon_commands.append(command)
            return "CopiMineNarcotics reloaded"

        appmod.rcon_quick = fake_rcon
        from fastapi.testclient import TestClient
        with TestClient(appmod.app) as c:
            assert c.get("/api/health").status_code == 200
            if (
                os.getenv("COPIMINE_AUTH_STORAGE", "").strip().lower() != "sqlite"
                and not os.getenv("POSTGRES_PASSWORD", "").strip()
                and os.getenv("COPIMINE_STRICT_SMOKE", "0") != "1"
            ):
                print(
                    "Backend smoketest partially skipped: cookie-auth and role-protected flows require a real PostgreSQL auth storage "
                    "password in the environment. Public import/health checks passed; run this smoketest on Ubuntu with admin-web PostgreSQL env "
                    "to exercise login and protected routes."
                )
                return
            r = c.post("/api/auth/login", json={"username":"SudoKillDash9", "password":test_password})
            assert r.status_code == 200, r.text
            assert r.json()["cookieAuth"] is True
            csrf_boot = c.get("/api/auth/csrf")
            assert csrf_boot.status_code == 200, csrf_boot.text
            csrf_token = c.cookies.get(appmod.CSRF_COOKIE_NAME)
            assert csrf_token, "CSRF cookie was not issued"
            h = {appmod.CSRF_HEADER_NAME: csrf_token}
            recipes = c.get("/api/admin/narcotics/recipes")
            assert recipes.status_code == 200, recipes.text
            assert [row["id"] for row in recipes.json()["recipes"]] == ["test_item"]
            minecraft_items = recipes.json().get("minecraftItems") or []
            assert len(minecraft_items) >= 100, len(minecraft_items)
            assert all(str(row.get("iconUrl") or "").startswith("/assets/mc-icons/item/") for row in minecraft_items[:20])
            recipe_payload = {
                "recipes": {
                    "test_item": ["material:sugar", "material:glowstone_dust", "potion:water"],
                },
                "apply_mode": "apply",
            }
            missing_confirm = c.post("/api/admin/narcotics/recipes", headers=h, json=recipe_payload)
            assert missing_confirm.status_code == 409, missing_confirm.text
            save_only_payload = {**recipe_payload, "apply_mode": "save"}
            save_only_headers = {**h, appmod.SENSITIVE_CONFIRM_HEADER: "NARCOTICS_RECIPES_SAVE"}
            save_only = c.post("/api/admin/narcotics/recipes", headers=save_only_headers, json=save_only_payload)
            assert save_only.status_code == 200, save_only.text
            assert save_only.json()["reload"]["applyMode"] == "save-only", save_only.json()
            assert save_only.json()["reload"]["reloaded"] is False, save_only.json()
            assert rcon_commands == [], rcon_commands
            recipe_headers = {**h, appmod.SENSITIVE_CONFIRM_HEADER: "NARCOTICS_RECIPES_APPLY"}
            saved_recipe = c.post("/api/admin/narcotics/recipes", headers=recipe_headers, json=recipe_payload)
            assert saved_recipe.status_code == 200, saved_recipe.text
            saved_payload = saved_recipe.json()
            assert saved_payload["reload"]["reloaded"] is True, saved_payload
            assert saved_payload["reload"]["manual"] is False, saved_payload
            assert rcon_commands == ["cmnarcotics reload"], rcon_commands
            assert "material:glowstone_dust" in narcotics_config.read_text(encoding="utf-8")
            assert list(narcotics_dir.glob("config.yml.bak-*")), "Recipe save did not create a backup"
            blocked_recipe = c.post(
                "/api/admin/narcotics/recipes",
                headers=recipe_headers,
                json={"recipes": {"test_item": ["material:sugar", "material:diamond_ore", "potion:water"]}, "apply_mode": "apply"},
            )
            assert blocked_recipe.status_code == 400, blocked_recipe.text
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
            if appmod.pg_ready():
                with TestClient(appmod.app) as player_client:
                    registered = player_client.post("/api/player/register", json={
                        "username": "shopcartuser",
                        "password": "CartPassword123!",
                        "minecraft_name": "ShopCartUser",
                    })
                    assert registered.status_code == 200, registered.text
                    # A fresh registration intentionally keeps ownership fields
                    # empty until the in-game link is proven.  The shop test
                    # still needs a concrete target, so derive it from the
                    # nickname selected for this isolated test account.
                    player_uuid = appmod.offline_uuid_for_name("ShopCartUser")
                    player_name = "ShopCartUser"

                    admin_ar_headers = {**h, appmod.SENSITIVE_CONFIRM_HEADER: "AR_ADD_BALANCE"}
                    rr = c.post("/api/admin/economy/ar/add-balance", headers=admin_ar_headers, json={
                        "minecraft_uuid": player_uuid,
                        "minecraft_name": player_name,
                        "amount": 100000,
                        "reason": "cart smoke funding",
                        "idempotency_key": "cart-smoke-ar-funding-001",
                    })
                    assert rr.status_code == 200, rr.text
                    admin_donation_headers = {**h, appmod.SENSITIVE_CONFIRM_HEADER: "DONATION_ADD_BALANCE"}
                    rr = c.post("/api/admin/donation/add-balance", headers=admin_donation_headers, json={
                        "minecraft_uuid": player_uuid,
                        "minecraft_name": player_name,
                        "amount": 100000,
                        "reason": "cart smoke funding",
                        "idempotency_key": "cart-smoke-donation-funding-001",
                    })
                    assert rr.status_code == 200, rr.text

                    csrf_boot = player_client.get("/api/auth/csrf")
                    assert csrf_boot.status_code == 200, csrf_boot.text
                    player_csrf = player_client.cookies.get(appmod.CSRF_COOKIE_NAME)
                    assert player_csrf, "Player CSRF cookie was not issued"
                    player_headers = {appmod.CSRF_HEADER_NAME: player_csrf}
                    rr = player_client.post("/api/player/bank/pin", headers=player_headers, json={"new_pin": "1234"})
                    assert rr.status_code == 200, rr.text

                    ar_cart = {
                        "item_ids": ["zmei_gorynych", "smena_bez_perekura_pickaxe"],
                        "pin": "1234",
                        "expected_total": 3000,
                        "idempotency_key": "cart-smoke-ar-checkout-001",
                    }
                    rr = player_client.post("/api/player/shop/cart/ar/checkout", headers=player_headers, json=ar_cart)
                    assert rr.status_code == 200, rr.text
                    ar_checkout = rr.json()
                    assert len(ar_checkout["items"]) == 2, ar_checkout
                    assert all(item["status"] == "PENDING_DELIVERY" for item in ar_checkout["items"]), ar_checkout
                    rr = player_client.post("/api/player/shop/cart/ar/checkout", headers=player_headers, json=ar_cart)
                    assert rr.status_code == 200 and rr.json().get("idempotent") is True, rr.text
                    duplicate_ar = player_client.post("/api/player/shop/cart/ar/checkout", headers=player_headers, json={
                        "item_ids": ["zmei_gorynych", "zmei_gorynych"],
                        "pin": "1234",
                        "idempotency_key": "cart-smoke-ar-duplicate-001",
                    })
                    assert duplicate_ar.status_code == 400, duplicate_ar.text

                    donation_cart = {
                        "item_ids": ["batin_remen_sudnogo_dnya", "nu_ty_i_nakopal_blyat_pickaxe"],
                        "pin": "1234",
                        "expected_total": 950,
                        "idempotency_key": "cart-smoke-donation-checkout-001",
                    }
                    rr = player_client.post("/api/player/shop/cart/donation/checkout", headers=player_headers, json=donation_cart)
                    assert rr.status_code == 200, rr.text
                    donation_checkout = rr.json()
                    assert len(donation_checkout["items"]) == 2, donation_checkout
                    assert all(item["claimStatus"] == "UNCLAIMED" for item in donation_checkout["items"]), donation_checkout
                    rr = player_client.post("/api/player/shop/cart/donation/checkout", headers=player_headers, json=donation_cart)
                    assert rr.status_code == 200 and rr.json().get("idempotent") is True, rr.text
                    artifacts = player_client.get("/api/player/artifacts")
                    assert artifacts.status_code == 200 and len(artifacts.json()["pending"]) == 2, artifacts.text
                    owned = player_client.get("/api/player/shop/owned")
                    assert owned.status_code == 200 and len(owned.json()["claims"]) == 2, owned.text
            else:
                print("Cart checkout smoke skipped: PostgreSQL is required for real balance and delivery checks.")
            rr = c.post("/api/rcon", headers=h, json={"command":"op TestUser"})
            assert rr.status_code == 403, rr.text
            rr = c.post("/api/backups", headers=h, json={"scope":"configs", "include_logs":True, "include_world":False})
            assert rr.status_code == 200, rr.text
            assert "path" not in rr.json()
    print("Backend smoketest OK")


if __name__ == "__main__":
    main()
