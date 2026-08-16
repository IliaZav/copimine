import importlib
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "admin-web"))


def test_linked_launcher_nickname_rebind_updates_identity_without_password_boundary(tmp_path, monkeypatch):
    data_dir = tmp_path / "admin-data"
    server_dir = tmp_path / "server"
    monkeypatch.setenv("COPIMINE_ADMIN_DATA", str(data_dir))
    monkeypatch.setenv("COPIMINE_AUTH_DB", str(data_dir / "auth.sqlite"))
    monkeypatch.setenv("COPIMINE_AUTH_STORAGE", "sqlite")
    monkeypatch.setenv("MC_SERVER_DIR", str(server_dir))
    monkeypatch.setenv("RCON_PASSWORD", "test-local-rcon")

    main = importlib.import_module("backend.main")
    main.V4_SCHEMA_READY = False
    main.AUTH_SCHEMA_READY = False
    device_id = "cm-device-1234567890"
    access_token = "poll-token-abcdefghijklmnopqrstuvwxyz-123456"
    old_uuid = "ea87bd75-4c8a-3ea7-83ad-f78afc2b1a8f"
    old_name = "IdentityOld"
    new_name = "IdentityNew"
    new_uuid = main.offline_uuid_for_name(new_name)
    now = main.donation_now_ms()
    device_hash = main.launcher_secret_hash("device", device_id)
    poll_hash = main.launcher_secret_hash("poll", access_token)

    with main.auth_conn() as conn:
        main.ensure_v4_schema(conn)
        conn.execute(
            """
            INSERT INTO site_accounts(
                id,username,username_norm,password_hash,role,enabled,
                minecraft_uuid,minecraft_name,created_at,updated_at,last_login_at,registration_ip
            ) VALUES(%s,%s,%s,%s,'player',1,%s,%s,%s,%s,%s,'')
            """,
            ("account-1", "identity-site", "identity-site", "site-hash", old_uuid, old_name, now, now, now),
        )
        conn.execute(
            """
            INSERT INTO launcher_link_challenges(
                challenge_id,device_id_hash,minecraft_name,launcher_version,
                code_hash,poll_token_hash,status,site_account_id,created_at,expires_at,authorized_at
            ) VALUES(%s,%s,%s,%s,%s,%s,'AUTHORIZED',%s,%s,%s,%s)
            """,
            ("challenge-identity-1", device_hash, old_name, "1.0.0", "code-hash", poll_hash, "account-1", now, now + 900000, now),
        )
        conn.execute(
            """
            INSERT INTO launcher_account_links(
                device_id_hash,site_account_id,minecraft_name,launcher_version,linked_at,updated_at
            ) VALUES(%s,%s,%s,%s,%s,%s)
            """,
            (device_hash, "account-1", old_name, "1.0.0", now, now),
        )
        conn.execute(
            """
            INSERT INTO minecraft_account_links(
                minecraft_uuid,minecraft_name,site_account_id,status,linked_at,updated_at
            ) VALUES(%s,%s,%s,'ACTIVE',%s,%s)
            """,
            (old_uuid, old_name, "account-1", now, now),
        )
        conn.execute(
            """
            INSERT INTO whitelist_account_links(
                minecraft_uuid,minecraft_name,site_account_id,whitelisted,synced_at
            ) VALUES(%s,%s,%s,1,%s)
            """,
            (old_uuid, old_name, "account-1", now),
        )
        conn.commit()

    commands = []
    monkeypatch.setattr(main, "require_identity_rcon_ack", lambda command: commands.append(command) or "IDENTITY_REBIND_OK")
    monkeypatch.setattr(main, "remove_player_from_whitelist_sync", lambda *_: {"removed": True, "rconState": "RCON_AND_FILE"})
    monkeypatch.setattr(main, "add_player_to_whitelist_sync", lambda *_: {"whitelisted": True, "rconState": "RCON_AND_FILE"})
    monkeypatch.setattr(main, "audit_event", lambda *args, **kwargs: None)

    result = main.launcher_nickname_change_sync(
        main.LauncherNicknameChangeIn(
            device_id=device_id,
            access_token=access_token,
            old_minecraft_name=old_name,
            new_minecraft_name=new_name,
        )
    )

    assert result["changed"] is True
    assert result["preserve_player_state"] is True
    assert result["authmePasswordPreserved"] is True
    assert commands == [f"cmworld identity rebind {old_uuid} {new_uuid} {old_name} {new_name} confirm"]
    with main.auth_conn() as conn:
        account = conn.execute("SELECT minecraft_uuid,minecraft_name FROM site_accounts WHERE id=%s", ("account-1",)).fetchone()
        launcher = conn.execute("SELECT minecraft_name FROM launcher_account_links WHERE device_id_hash=%s", (device_hash,)).fetchone()
        old_link = conn.execute("SELECT status FROM minecraft_account_links WHERE minecraft_uuid=%s", (old_uuid,)).fetchone()
        new_link = conn.execute("SELECT minecraft_name,status FROM minecraft_account_links WHERE minecraft_uuid=%s", (new_uuid,)).fetchone()

    assert tuple(account) == (new_uuid, new_name)
    assert tuple(launcher) == (new_name,)
    assert tuple(old_link) == ("REVOKED",)
    assert tuple(new_link) == (new_name, "ACTIVE")
