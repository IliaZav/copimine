import time

from fastapi.testclient import TestClient

from backend.app import create_app
from backend.auth import create_user
from backend.config import Settings


def make_client(tmp_path):
    settings = Settings(
        database_url="sqlite:///" + str(tmp_path / "notifications.sqlite3"),
        secret_key="test-only-notifications-secret-" * 3,
        public_url="http://testserver",
        secure_cookies=False,
        bootstrap_owner="TestOwner",
        bootstrap_password="This-is-only-a-test-owner-password-2026",
    )
    return TestClient(create_app(settings))


def login(client, name="TestOwner", password="This-is-only-a-test-owner-password-2026"):
    headers = {"Origin": "http://testserver", "X-CSRF-Token": client.get("/api/v6/session").json()["csrf"]}
    response = client.post(
        "/api/v6/auth/login",
        headers=headers,
        json={"name": name, "password": password, "remember": False},
    )
    assert response.status_code == 200, response.text
    return {"Origin": "http://testserver", "X-CSRF-Token": client.get("/api/v6/session").json()["csrf"]}


def test_incoming_transfer_creates_readable_account_notification(tmp_path, monkeypatch):
    with make_client(tmp_path) as client:
        headers = login(client)
        database = client.app.state.database
        with database.transaction() as db:
            owner = db.execute("SELECT id FROM cm_site_users WHERE name_norm='testowner'").fetchone()
            owner_id = owner["id"]
            db.execute("UPDATE cm_site_users SET minecraft_uuid=%s,minecraft_name=%s WHERE id=%s", ("owner-uuid", "TestOwner", owner_id))
        recipient = create_user(database, "TransferTarget", "another-test-password-2026")
        with database.transaction() as db:
            db.execute("UPDATE cm_site_users SET minecraft_uuid=%s,minecraft_name=%s WHERE id=%s", ("target-uuid", "TransferTarget", recipient["id"]))
        monkeypatch.setattr(client.app.state.game, "transfer", lambda *args, **kwargs: {"txId": "tx-transfer-001"})

        sent = client.post(
            "/api/v7/transfer",
            headers=headers,
            json={"to": recipient["id"], "amount": 125, "note": "За стройку", "pin": "1234", "key": "transfer-key-001"},
        )
        assert sent.status_code == 200, sent.text

        headers = login(client, "TransferTarget", "another-test-password-2026")

        response = client.get("/api/v6/account/notifications")
        assert response.status_code == 200, response.text
        payload = response.json()
        assert payload["unread"] == 1
        assert payload["notifications"][0]["title"] == "Входящий перевод"
        assert payload["notifications"][0]["href"] == "#account/transfers?history=1&transfer=tx-transfer-001"
        assert "125" in payload["notifications"][0]["body"]
        assert "TestOwner" in payload["notifications"][0]["body"]

        notification_id = payload["notifications"][0]["id"]
        read_one = client.post(f"/api/v7/notifications/{notification_id}/read", headers=headers, json={})
        assert read_one.status_code == 200, read_one.text
        assert read_one.json()["updated"] is True
        assert client.get("/api/v6/account/notifications").json()["unread"] == 0

        with database.transaction() as db:
            db.execute(
                "INSERT INTO cm_site_notifications(id,user_id,title,body,created_s) VALUES(%s,%s,%s,%s,%s)",
                ("second-unread", recipient["id"], "Событие аккаунта", "Проверка прочтения всех уведомлений.", int(time.time())),
            )

        read = client.post("/api/v7/transfers-read", headers=headers, json={})
        assert read.status_code == 200, read.text
        after_read = client.get("/api/v6/account/notifications")
        assert after_read.status_code == 200, after_read.text
        assert after_read.json()["unread"] == 0


def test_economy_history_includes_incoming_and_outgoing_game_transfers(tmp_path, monkeypatch):
    with make_client(tmp_path) as client:
        login(client)
        database = client.app.state.database
        with database.transaction() as db:
            owner = db.execute("SELECT id FROM cm_site_users WHERE name_norm='testowner'").fetchone()
            owner_id = owner["id"]
            db.execute("UPDATE cm_site_users SET minecraft_uuid=%s,minecraft_name=%s WHERE id=%s", ("owner-uuid", "TestOwner", owner_id))
        sender = create_user(database, "TransferSender", "another-test-password-2026")
        recipient = create_user(database, "TransferTarget", "another-test-password-2026")
        with database.transaction() as db:
            db.execute("UPDATE cm_site_users SET minecraft_uuid=%s,minecraft_name=%s WHERE id=%s", ("sender-uuid", "TransferSender", sender["id"]))
            db.execute("UPDATE cm_site_users SET minecraft_uuid=%s,minecraft_name=%s WHERE id=%s", ("target-uuid", "TransferTarget", recipient["id"]))
        now_ms = int(time.time() * 1000)
        monkeypatch.setattr(
            client.app.state.game,
            "wallet",
            lambda _user: {
                "account": {"balance": 500},
                "pin": {"set": True},
                "ledger": [
                    {"tx_id": "incoming-001:in", "tx_type": "TRANSFER_IN", "amount": 75, "counterparty_account_id": "ar:sender-uuid", "counterparty_name": "TransferSender", "details": '{"note":"За помощь"}', "created_at": now_ms, "status": "COMMITTED"},
                    {"tx_id": "outgoing-002:out", "tx_type": "TRANSFER_OUT", "amount": 25, "counterparty_account_id": "ar:target-uuid", "counterparty_name": "TransferTarget", "details": '{"note":"Ресурсы","recipient":"TransferTarget"}', "created_at": now_ms, "status": "COMMITTED"},
                    {"tx_id": "voided-004:in", "tx_type": "TRANSFER_IN", "amount": 999, "counterparty_account_id": "ar:sender-uuid", "counterparty_name": "TransferSender", "details": "{}", "created_at": now_ms, "status": "VOIDED"},
                    {"tx_id": "shop-003", "tx_type": "AR_SHOP_CART", "amount": -100, "counterparty_account_id": "", "counterparty_name": "", "details": "{}", "created_at": now_ms, "status": "COMMITTED"},
                ],
            },
        )

        response = client.get("/api/v7/economy")

        assert response.status_code == 200, response.text
        transfers = response.json()["transfers"]
        assert [(row["id"], row["incoming"], row["amount"]) for row in transfers] == [
            ("incoming-001", True, 75),
            ("outgoing-002", False, 25),
        ]
        assert transfers[0]["sender"] == sender["id"]
        assert transfers[1]["recipient"] == recipient["id"]
        assert response.json()["unread"] == 1
        assert response.json()["notifications"][0]["title"] == "Входящий перевод"


def test_password_and_minecraft_changes_create_account_notifications(tmp_path, monkeypatch):
    with make_client(tmp_path) as client:
        headers = login(client)
        database = client.app.state.database

        def link_confirm(user, _code):
            with database.transaction() as db:
                db.execute("UPDATE cm_site_users SET minecraft_uuid=%s,minecraft_name=%s WHERE id=%s", ("linked-uuid", "LinkedPlayer", user["id"]))
            return {"ok": True}

        monkeypatch.setattr(client.app.state.game, "link_confirm", link_confirm)
        linked = client.post("/api/v6/account/minecraft/confirm", headers=headers, json={"code": "ABCD1234"})
        assert linked.status_code == 200, linked.text

        password = client.post(
            "/api/v7/password",
            headers=headers,
            json={"current": "This-is-only-a-test-owner-password-2026", "password": "New-test-owner-password-2026"},
        )
        assert password.status_code == 200, password.text

        payload = client.get("/api/v6/account/notifications").json()
        assert {item["title"] for item in payload["notifications"]} >= {"Minecraft привязан", "Пароль изменён"}
        assert payload["unread"] == 2
        routes = {item["title"]: item["href"] for item in payload["notifications"]}
        assert routes["Minecraft привязан"] == "#settings/connections"
        assert routes["Пароль изменён"] == "#settings/security"


def test_ticket_reply_creates_notification_for_account_owner(tmp_path):
    with make_client(tmp_path) as client:
        headers = login(client)
        database = client.app.state.database
        player = create_user(database, "TicketPlayer", "another-test-password-2026")
        ticket_id = "ticket-notification-test"
        with database.transaction() as db:
            db.execute(
                "INSERT INTO cm_site_tickets(id,user_id,title,body,type,status,created_s,updated_s) VALUES(%s,%s,%s,%s,%s,'open',%s,%s)",
                (ticket_id, player["id"], "Карта не открывается", "Подробное описание проблемы.", "support", int(time.time()), int(time.time())),
            )
        replied = client.patch(
            f"/api/v6/admin/tickets/{ticket_id}",
            headers=headers,
            json={"reply": "Проверили ссылку, теперь она открывается.", "status": "answered"},
        )
        assert replied.status_code == 200, replied.text

        login(client, "TicketPlayer", "another-test-password-2026")
        payload = client.get("/api/v6/account/notifications").json()
        assert payload["unread"] == 1
        assert payload["notifications"][0]["title"] == "Ответ по обращению"
        assert payload["notifications"][0]["href"] == f"#account/support?ticket={ticket_id}"
