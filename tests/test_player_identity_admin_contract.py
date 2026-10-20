"""Candidate iteration and identity revalidation before account mutations."""
from __future__ import annotations

import importlib.util
from pathlib import Path
from unittest.mock import patch

import pytest

ROOT = Path(__file__).resolve().parents[1]
REQUESTED_UUID = "66666666-6666-6666-6666-666666666666"
OTHER_UUID = "77777777-7777-7777-7777-777777777777"


@pytest.fixture
def backend(tmp_path):
    spec = importlib.util.spec_from_file_location("identity_regression_support", ROOT / "admin-web/scripts/backend_security_regression_test.py")
    support = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(support)
    main, *_ = support.load_modules(tmp_path)
    with main.auth_conn() as conn:
        main.ensure_v4_schema(conn)
        # Legacy relation tables are present in production migrations; provide
        # empty equivalents for the isolated SQLite deletion fixture.
        for table in ("player_web_accounts", "login_sessions", "password_hashes", "player_settings", "bank_pin_hashes", "temporary_pin_resets", "failed_pin_attempts", "cm_refresh_sessions", "cmv4_account_links", "auth_users_imported", "auth_whitelist_sync"):
            conn.execute(f"CREATE TABLE IF NOT EXISTS {table}(site_account_id TEXT,account_id TEXT,subject_id TEXT,site_user_id TEXT,minecraft_uuid TEXT)")
        conn.commit()
    return main


def account(conn, account_id, uuid, name, priority=0):
    conn.execute(
        "INSERT INTO site_accounts(id,username,username_norm,password_hash,role,enabled,minecraft_uuid,minecraft_name,created_at,updated_at,last_login_at) VALUES(%s,%s,%s,'test-hash','player',1,%s,%s,%s,%s,%s)",
        (account_id, account_id, account_id, uuid, name, priority, priority, priority),
    )


@pytest.mark.parametrize("lookup", ["uuid", "name"])
def test_cache_lookup_continues_after_rejected_first_candidate(backend, lookup):
    with backend.auth_conn() as conn:
        backend.ensure_v4_schema(conn)
        account(conn, "stale", REQUESTED_UUID, "RequestedHero", 2)
        account(conn, "valid", REQUESTED_UUID, "RequestedHero", 1)
        conn.execute(
            "INSERT INTO minecraft_account_links(minecraft_uuid,minecraft_name,site_account_id,status,linked_at,updated_at) VALUES(%s,'OtherHero','stale','ACTIVE',2,2)",
            (OTHER_UUID,),
        )
        args = {"minecraft_uuid": REQUESTED_UUID} if lookup == "uuid" else {"minecraft_name": "RequestedHero"}
        result = backend.player_site_account_for_identity(conn, **args)
        assert result is not None and result["id"] == "valid"


@pytest.mark.parametrize("table", ["minecraft_account_links", "whitelist_account_links"])
def test_name_lookup_continues_through_durable_candidates(backend, table):
    with backend.auth_conn() as conn:
        backend.ensure_v4_schema(conn)
        account(conn, "stale", "", "", 2)
        account(conn, "valid", "", "", 1)
        if table == "minecraft_account_links":
            for uuid, owner, timestamp in [(REQUESTED_UUID, "stale", 1), (OTHER_UUID, "valid", 1), ("88888888-8888-8888-8888-888888888888", "stale", 2)]:
                name = "OtherHero" if timestamp == 2 else "RequestedHero"
                conn.execute("INSERT INTO minecraft_account_links VALUES(%s,%s,%s,'ACTIVE',%s,%s)", (uuid, name, owner, timestamp, timestamp))
        else:
            for uuid, owner, timestamp in [(REQUESTED_UUID, "stale", 1), (OTHER_UUID, "valid", 1), ("88888888-8888-8888-8888-888888888888", "stale", 2)]:
                name = "OtherHero" if timestamp == 2 else "RequestedHero"
                conn.execute("INSERT INTO whitelist_account_links VALUES(%s,%s,%s,1,%s)", (uuid, name, owner, timestamp))
        result = backend.player_site_account_for_identity(conn, minecraft_name="RequestedHero")
        assert result is not None and result["id"] == "valid"


@pytest.mark.parametrize("operation", ["delete", "update"])
@pytest.mark.parametrize("mutation", ["cache-and-link", "durable-link-only"])
def test_mutation_rejects_rebind_after_resolution_before_lock(backend, mutation, operation):
    with backend.auth_conn() as conn:
        backend.ensure_v4_schema(conn)
        account(conn, "racing", REQUESTED_UUID, "RequestedHero")
        conn.execute("INSERT INTO minecraft_account_links VALUES(%s,'RequestedHero','racing','ACTIVE',1,1)", (REQUESTED_UUID,))
        conn.commit()
    original = backend.player_site_account_for_identity

    def resolve_then_rebind(conn, *args, **kwargs):
        result = original(conn, *args, **kwargs)
        # Deterministically reproduce the state visible after another writer
        # finishes between identity resolution and the mutation's row lock.
        conn.execute("UPDATE minecraft_account_links SET minecraft_uuid=%s,minecraft_name='OtherHero',updated_at=2 WHERE site_account_id='racing'", (OTHER_UUID,))
        if mutation == "cache-and-link":
            conn.execute("UPDATE site_accounts SET minecraft_uuid=%s,minecraft_name='OtherHero' WHERE id='racing'", (OTHER_UUID,))
        return result

    with patch.object(backend, "find_player_uuid", return_value=REQUESTED_UUID), patch.object(backend, "player_site_account_for_identity", side_effect=resolve_then_rebind), patch.object(backend, "remove_player_from_whitelist_sync") as remove:
        with pytest.raises(backend.HTTPException) as error:
            if operation == "delete":
                backend.admin_delete_player_account_sync("RequestedHero", "Admin")
            else:
                backend.admin_update_player_account_sync("RequestedHero", "Admin", backend.AdminPlayerAccountUpdateIn(username="changed"))
        assert error.value.status_code == 409
        remove.assert_not_called()
    with backend.auth_conn() as conn:
        assert conn.execute("SELECT username FROM site_accounts WHERE id='racing'").fetchone()["username"] == "racing"


def test_delete_valid_link_removes_only_requested_whitelist_identity(backend):
    with backend.auth_conn() as conn:
        backend.ensure_v4_schema(conn)
        account(conn, "valid-delete", REQUESTED_UUID, "RequestedHero")
        conn.execute("INSERT INTO minecraft_account_links VALUES(%s,'RequestedHero','valid-delete','ACTIVE',1,1)", (REQUESTED_UUID,))
        conn.commit()
    with patch.object(backend, "find_player_uuid", return_value=REQUESTED_UUID), patch.object(backend, "remove_player_from_whitelist_sync", return_value={"removed": True}) as remove:
        result = backend.admin_delete_player_account_sync("RequestedHero", "Admin")
    assert result["ok"] is True
    remove.assert_called_once_with(REQUESTED_UUID, "RequestedHero")
