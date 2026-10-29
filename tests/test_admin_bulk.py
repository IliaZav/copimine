import time

from test_auth import client, login

HEADERS = {"Origin": "http://testserver"}


def auth(client_session):
    return {"Origin": "http://testserver", "X-CSRF-Token": client_session["csrf"]}


def seed_audit(database):
    now = int(time.time())
    rows = [
        ("audit-auth", "auth.login", "alice", "Alice", "Успешный вход"),
        ("audit-game", "game.kick", "bob", "Bob", "Нарушение правил чата"),
        ("audit-shop", "shop.purchase", "alice", "Alice", "Покупка кита"),
        ("audit-ticket", "ticket.replied", "carol", "Carol", "Ответ поддержки"),
        ("audit-map", "map.published", "alice", "Alice", "Публикация карты"),
    ]
    with database.transaction() as db:
        for ident, action, actor_id, actor_name, reason in rows:
            db.execute(
                "INSERT INTO cm_site_audit(id,actor_id,actor_name,action,target,reason,details,created_s)"
                " VALUES(%s,%s,%s,%s,%s,%s,%s,%s)",
                (ident, actor_id, actor_name, action, "", reason, "{}", now),
            )


def test_audit_filters_and_pages_on_the_server(client):
    session = login(client)
    seed_audit(client.app.state.database)
    query = "/api/v6/admin/audit"

    everything = client.get(query + "?limit=2").json()
    assert len(everything["entries"]) == 2
    assert everything["total"] >= 5
    assert everything["hasMore"] is True

    last = client.get(query + "?limit=2&offset=" + str(everything["total"] - 2)).json()
    assert last["hasMore"] is False

    buckets = {
        "account": "auth.login",
        "moderation": "game.kick",
        "economy": "shop.purchase",
        "content": "ticket.replied",
        "server": "map.published",
    }
    for bucket, action in buckets.items():
        payload = client.get(query + "?limit=50&bucket=" + bucket).json()
        kinds = {row["action"] for row in payload["entries"]}
        assert action in kinds, (bucket, kinds)
        assert all(row["action"] == action for row in payload["entries"] if row["action"] in {
            "auth.login", "game.kick", "shop.purchase", "ticket.replied", "map.published"}), bucket

    matched = client.get(query + "?limit=50&q=map").json()
    assert [row["action"] for row in matched["entries"]] == ["map.published"], matched

    by_actor = client.get(query + "?limit=50&actor=alice").json()
    assert by_actor["total"] >= 3
    assert {row["actor_id"] for row in by_actor["entries"]} == {"alice"}

    assert client.get(query + "?limit=abc").status_code == 400


def test_audit_target_matches_account_id_name_and_game_nick(client):
    session = login(client)
    database = client.app.state.database
    with database.transaction() as db:
        owner = db.execute("SELECT id,name FROM cm_site_users WHERE name_norm='testowner'").fetchone()
        db.execute("UPDATE cm_site_users SET minecraft_name=%s WHERE id=%s", ("OwnerNick", owner["id"]))
        db.execute(
            "INSERT INTO cm_site_audit(id,actor_id,actor_name,action,target,reason,details,created_s)"
            " VALUES(%s,%s,%s,%s,%s,%s,%s,%s)",
            ("audit-target", owner["id"], "TestOwner", "game.kick", "OwnerNick", "", "{}", int(time.time())),
        )
    for value in (owner["id"], owner["name"], "OwnerNick"):
        payload = client.get("/api/v6/admin/audit?limit=50&target=" + value).json()
        assert "audit-target" in [row["id"] for row in payload["entries"]], value
    # Собственные действия игрока тоже относятся к его истории.
    client.post("/api/v6/tickets", headers=auth(session), json={"title": "Свой тикет", "body": "Проверка истории игрока."})
    history = client.get("/api/v6/admin/audit?limit=50&target=" + owner["id"]).json()
    assert "ticket.created" in {row["action"] for row in history["entries"]}


def test_bulk_actions_reject_server_scope_and_report_each_player(client):
    session = login(client)
    headers = auth(session)

    server = client.post("/api/v6/admin/actions/bulk", headers=headers,
                         json={"action": "time.day", "players": ["someone"], "reason": "Bulk server"})
    assert server.status_code == 400
    assert server.json()["error"]["code"] == "unsupported_action"

    support = client.post("/api/v6/admin/actions/bulk", headers=headers,
                          json={"action": "console", "players": ["someone"], "reason": "Bulk console"})
    assert support.status_code == 400 and support.json()["error"]["code"] == "unsupported_action"

    empty = client.post("/api/v6/admin/actions/bulk", headers=headers,
                        json={"action": "kick", "players": [], "reason": "Bulk empty"})
    assert empty.status_code == 400 and empty.json()["error"]["code"] == "invalid_players"

    too_many = client.post("/api/v6/admin/actions/bulk", headers=headers,
                           json={"action": "kick", "players": ["p%d" % i for i in range(101)], "reason": "Bulk many"})
    assert too_many.status_code == 400

    result = client.post("/api/v6/admin/actions/bulk", headers=headers,
                         json={"action": "kick", "players": ["NoSuchAccount", "NoSuchAccount"], "reason": "Bulk dedupe"})
    assert result.status_code == 200, result.text
    payload = result.json()
    assert payload["total"] == 1
    assert payload["done"] == 0 and payload["failed"] == 1
    assert payload["results"][0]["ok"] is False


def test_ticket_templates_are_stored_listed_and_deleted(client):
    session = login(client)
    headers = auth(session)

    created = client.post("/api/v6/admin/ticket-templates", headers=headers,
                          json={"title": "Отказ по правилам", "body": "К сожалению, заявка отклонена: нарушение правил."})
    assert created.status_code == 200, created.text
    template_id = created.json()["template"]["id"]

    listed = client.get("/api/v6/admin/ticket-templates").json()["templates"]
    assert [row["title"] for row in listed] == ["Отказ по правилам"]

    invalid = client.post("/api/v6/admin/ticket-templates", headers=headers, json={"title": "x", "body": ""})
    assert invalid.status_code == 400 and invalid.json()["error"]["code"] == "invalid_template"

    removed = client.delete("/api/v6/admin/ticket-templates/" + template_id, headers=headers)
    assert removed.status_code == 200, removed.text
    assert client.get("/api/v6/admin/ticket-templates").json()["templates"] == []
    assert client.delete("/api/v6/admin/ticket-templates/" + template_id, headers=headers).status_code == 404


def test_ticket_creation_notifies_staff_but_not_the_author(client):
    session = login(client)
    owner = session["user"]
    with client.app.state.database.transaction() as db:
        db.execute(
            "INSERT INTO cm_site_users(id,name,name_norm,display_name,role,password_hash,enabled,created_s,updated_s)"
            " VALUES(%s,%s,%s,%s,%s,%s,1,%s,%s)",
            ("helper-1", "Helper", "helper", "Helper", "helper", "x", int(time.time()), int(time.time())),
        )
    made = client.post("/api/v6/tickets", headers=auth(session),
                       json={"title": "Не работает вход", "body": "Проверьте, пожалуйста, мой аккаунт."})
    assert made.status_code == 200, made.text
    ticket_id = made.json()["ticket"]["id"]

    with client.app.state.database.transaction() as db:
        rows = db.execute("SELECT id,user_id FROM cm_site_notifications WHERE id LIKE %s", ("ticket-new%",)).fetchall()
    notified = {row["user_id"] for row in rows}
    assert "helper-1" in notified
    assert owner["id"] not in notified
    assert all(row["id"].startswith("ticket-new:" + ticket_id) for row in rows)


def test_roles_matrix_matches_server_permissions(client):
    session = login(client)
    payload = client.get("/api/v6/admin/roles").json()["roles"]
    by_role = {row["id"]: row for row in payload}
    assert by_role["owner"]["rank"] > by_role["admin"]["rank"] > by_role["player"]["rank"]
    assert by_role["player"]["permissions"] == []
    assert "logs.view" in by_role["admin"]["permissions"]
    assert "settings.manage" not in by_role["admin"]["permissions"]
    assert "settings.manage" in by_role["owner"]["permissions"]
    assert by_role["owner"]["accounts"] >= 1