from __future__ import annotations

import importlib
import sys

import pytest
from fastapi import HTTPException


def load_backend(tmp_path, monkeypatch):
    data_dir = tmp_path / "admin-data"
    monkeypatch.setenv("COPIMINE_ADMIN_DATA", str(data_dir))
    monkeypatch.setenv("COPIMINE_AUTH_DB", str(data_dir / "auth.sqlite"))
    monkeypatch.setenv("COPIMINE_AUTH_STORAGE", "sqlite")
    monkeypatch.setenv("RCON_PASSWORD", "")
    sys.path.insert(0, str(__import__("pathlib").Path(__file__).resolve().parents[1] / "admin-web"))
    main = importlib.reload(importlib.import_module("backend.main"))
    main.V4_SCHEMA_READY = False
    main.AUTH_SCHEMA_READY = False
    return main


def seed_recovery(main, account_id: str = "account-1", username: str = "old_login") -> str:
    now = main.donation_now_ms()
    code = "RECOVERY42"
    with main.auth_conn() as conn:
        main.ensure_v4_schema(conn)
        conn.execute(
            """
            INSERT INTO site_accounts(
                id,username,username_norm,password_hash,role,enabled,
                minecraft_uuid,minecraft_name,created_at,updated_at,last_login_at,registration_ip
            ) VALUES(%s,%s,%s,%s,'player',1,%s,%s,%s,%s,%s,'')
            """,
            (
                account_id,
                username,
                username.lower(),
                main.make_password_hash("old-password"),
                main.offline_uuid_for_name("PlayerOne"),
                "PlayerOne",
                now,
                now,
                now,
            ),
        )
        conn.execute(
            """
            INSERT INTO one_time_link_codes(
                id,site_account_id,minecraft_name,minecraft_uuid,code_hash,status,created_at,expires_at
            ) VALUES(%s,%s,%s,%s,%s,'PENDING',%s,%s)
            """,
            (
                "recovery-1",
                account_id,
                "PlayerOne",
                main.offline_uuid_for_name("PlayerOne"),
                main.sha256_hex(code),
                now,
                now + 600_000,
            ),
        )
        conn.commit()
    return code


def test_recovery_uses_explicit_site_login_and_preserves_minecraft_identity(tmp_path, monkeypatch):
    main = load_backend(tmp_path, monkeypatch)
    code = seed_recovery(main)
    monkeypatch.setattr(main, "current_admin_users_nonblocking", lambda: {"PanelOwner": {"enabled": True}})

    result = main.confirm_player_recovery_code_sync(
        "PlayerOne",
        "restored_login",
        code,
        "new-password-123",
    )

    assert result["account"]["username"] == "restored_login"
    assert result["account"]["minecraftName"] == "PlayerOne"
    with main.auth_conn() as conn:
        row = conn.execute(
            "SELECT username,username_norm,minecraft_name,password_hash FROM site_accounts WHERE id=%s",
            ("account-1",),
        ).fetchone()
        code_row = conn.execute("SELECT status FROM one_time_link_codes WHERE id=%s", ("recovery-1",)).fetchone()
    assert tuple(row[:3]) == ("restored_login", "restored_login", "PlayerOne")
    assert main.verify_password_hash(str(row[3]), "new-password-123")
    assert code_row[0] == "USED"


def test_recovery_rejects_login_collision_without_consuming_code(tmp_path, monkeypatch):
    main = load_backend(tmp_path, monkeypatch)
    code = seed_recovery(main)
    now = main.donation_now_ms()
    with main.auth_conn() as conn:
        conn.execute(
            """
            INSERT INTO site_accounts(
                id,username,username_norm,password_hash,role,enabled,
                minecraft_uuid,minecraft_name,created_at,updated_at,last_login_at,registration_ip
            ) VALUES(%s,%s,%s,%s,'player',1,%s,%s,%s,%s,%s,'')
            """,
            ("account-2", "taken_login", "taken_login", main.make_password_hash("other-password"), "", "", now, now, now),
        )
        conn.commit()

    with pytest.raises(HTTPException) as error:
        main.confirm_player_recovery_code_sync("PlayerOne", "taken_login", code, "new-password-123")
    assert error.value.status_code == 409
    assert "уже занят" in str(error.value.detail)
    with main.auth_conn() as conn:
        account = conn.execute("SELECT username FROM site_accounts WHERE id=%s", ("account-1",)).fetchone()
        code_row = conn.execute("SELECT status FROM one_time_link_codes WHERE id=%s", ("recovery-1",)).fetchone()
    assert account[0] == "old_login"
    assert code_row[0] == "PENDING"


def test_recovery_rejects_active_admin_login_collision(tmp_path, monkeypatch):
    main = load_backend(tmp_path, monkeypatch)
    code = seed_recovery(main)
    monkeypatch.setattr(main, "current_admin_users_nonblocking", lambda: {"PanelOwner": {"enabled": True}})

    with pytest.raises(HTTPException) as error:
        main.confirm_player_recovery_code_sync("PlayerOne", "PanelOwner", code, "new-password-123")
    assert error.value.status_code == 409


def test_recovery_rejects_disabled_admin_login_collision_too(tmp_path, monkeypatch):
    main = load_backend(tmp_path, monkeypatch)
    code = seed_recovery(main)
    monkeypatch.setattr(main, "current_admin_users_nonblocking", lambda: {"FormerOwner": {"enabled": False}})

    with pytest.raises(HTTPException) as error:
        main.confirm_player_recovery_code_sync("PlayerOne", "FormerOwner", code, "new-password-123")
    assert error.value.status_code == 409
