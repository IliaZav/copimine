"""Contract of the Minecraft link code, tested against the legacy auth store.

The website keeps its own `cm_site_users` row, but the one-time code, the identity
link and the wallet live in the legacy store. These tests pin the rules the handoff
asks for: one use per code, one account per Minecraft identity, and no silent rebind.
"""
import pytest

from backend.legacy import main as legacy


@pytest.fixture
def store(tmp_path, monkeypatch):
    monkeypatch.setattr(legacy, "AUTH_DB_FILE", tmp_path / "auth.db")
    monkeypatch.setattr(legacy, "AUTH_STORAGE_MODE", "sqlite")
    monkeypatch.setattr(legacy, "POSTGRES_PASSWORD", "")
    monkeypatch.setattr(legacy, "V4_SCHEMA_READY", False)
    monkeypatch.setattr(legacy, "AUTH_SCHEMA_READY", False)
    with legacy.auth_conn() as db:
        legacy.ensure_v4_schema(db)
    return legacy


def account(store, identifier, name):
    now = store.donation_now_ms()
    with store.auth_conn() as db:
        db.execute(
            "INSERT INTO site_accounts(id,username,username_norm,password_hash,role,enabled,minecraft_uuid,minecraft_name,created_at,updated_at,last_login_at,registration_ip)"
            " VALUES(%s,%s,%s,'external-website-session','player',1,'','',%s,%s,0,'')",
            (identifier, name, name.lower(), now, now),
        )
        db.commit()
        return dict(db.execute("SELECT * FROM site_accounts WHERE id=%s", (identifier,)).fetchone())


def issue(store, account_row, minecraft_name, code, ttl_ms=300000):
    """Writes the same PENDING row GameAdapter.link_start would write after link.send."""
    now = store.donation_now_ms()
    with store.auth_conn() as db:
        db.execute(
            "UPDATE one_time_link_codes SET status='EXPIRED' WHERE site_account_id=%s AND status='PENDING'",
            (account_row["id"],),
        )
        db.execute(
            "INSERT INTO one_time_link_codes(id,site_account_id,minecraft_name,minecraft_uuid,code_hash,status,created_at,expires_at)"
            " VALUES(%s,%s,%s,%s,%s,'PENDING',%s,%s)",
            (store.secrets.token_hex(8), account_row["id"], minecraft_name,
             store.offline_uuid_for_name(minecraft_name), store.sha256_hex(code), now, now + ttl_ms),
        )
        db.commit()
    return code


def test_link_code_links_identity_and_opens_the_wallet(store):
    owner = account(store, "site-1", "Steve")
    result = store.confirm_link_code_sync(owner, issue(store, owner, "Steve", "ABCD2345"))
    assert result["ok"] is True
    assert result["account"]["minecraftName"] == "Steve"
    assert result["account"]["linked"] is True
    with store.auth_conn() as db:
        link = db.execute("SELECT * FROM minecraft_account_links WHERE minecraft_uuid=%s",
                          (store.offline_uuid_for_name("Steve"),)).fetchone()
        assert link["site_account_id"] == "site-1"
        bank = db.execute("SELECT * FROM cmv4_bank_accounts WHERE owner_uuid=%s",
                          (store.offline_uuid_for_name("Steve"),)).fetchone()
        assert bank is not None


def test_link_code_cannot_be_used_twice(store):
    owner = account(store, "site-1", "Steve")
    code = issue(store, owner, "Steve", "ABCD2345")
    store.confirm_link_code_sync(owner, code)
    with pytest.raises(Exception) as error:
        store.confirm_link_code_sync(owner, code)
    assert getattr(error.value, "status_code", None) == 403


def test_expired_link_code_is_rejected(store):
    owner = account(store, "site-1", "Steve")
    code = issue(store, owner, "Steve", "ABCD2345", ttl_ms=-1000)
    with pytest.raises(Exception) as error:
        store.confirm_link_code_sync(owner, code)
    assert getattr(error.value, "status_code", None) == 403


def test_one_minecraft_identity_cannot_move_to_a_second_account(store):
    first = account(store, "site-1", "Steve")
    second = account(store, "site-2", "Alex")
    store.confirm_link_code_sync(first, issue(store, first, "Steve", "ABCD2345"))
    with pytest.raises(Exception) as error:
        store.confirm_link_code_sync(second, issue(store, second, "Steve", "WXYZ6789"))
    assert getattr(error.value, "status_code", None) == 409
    with store.auth_conn() as db:
        link = db.execute("SELECT site_account_id FROM minecraft_account_links WHERE minecraft_uuid=%s",
                          (store.offline_uuid_for_name("Steve"),)).fetchone()
        assert link["site_account_id"] == "site-1"


def test_linked_account_cannot_be_rebound_to_another_identity(store):
    owner = account(store, "site-1", "Steve")
    store.confirm_link_code_sync(owner, issue(store, owner, "Steve", "ABCD2345"))
    with store.auth_conn() as db:
        linked = dict(db.execute("SELECT * FROM site_accounts WHERE id='site-1'").fetchone())
    with pytest.raises(Exception) as error:
        store.confirm_link_code_sync(linked, issue(store, linked, "Alex", "WXYZ6789"))
    assert getattr(error.value, "status_code", None) == 409