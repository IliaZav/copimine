from datetime import datetime, timezone
import hashlib
import json

import pytest
from fastapi.testclient import TestClient

from backend.app import create_app
from backend.config import Settings


@pytest.fixture
def client(tmp_path):
    settings = Settings(
        database_url="sqlite:///" + str(tmp_path / "analytics.sqlite3"),
        secret_key="test-only-analytics-secret-" * 3,
        public_url="http://testserver",
        secure_cookies=False,
        bootstrap_owner="TestOwner",
        bootstrap_password="This-is-only-a-test-owner-password-2026",
    )
    with TestClient(create_app(settings)) as session:
        yield session


def csrf_headers(client):
    csrf = client.get("/api/v6/session").json()["csrf"]
    return {"Origin": "http://testserver", "X-CSRF-Token": csrf}


def login_owner(client):
    headers = csrf_headers(client)
    response = client.post(
        "/api/v6/auth/login",
        headers=headers,
        json={"name": "TestOwner", "password": "This-is-only-a-test-owner-password-2026", "remember": False},
    )
    assert response.status_code == 200, response.text
    return csrf_headers(client)


def test_analytics_events_are_rejected_without_explicit_consent(client):
    response = client.post(
        "/api/v6/analytics/events",
        headers=csrf_headers(client),
        json={"consent": False, "events": [{"type": "page_view", "page": "home", "visitor": "session-visitor-123456789"}]},
    )

    assert response.status_code == 403


def test_analytics_collector_rejects_non_object_payloads(client):
    response = client.post(
        "/api/v6/analytics/events",
        headers=csrf_headers(client),
        json=[{"consent": True, "events": []}],
    )

    assert response.status_code == 422


def test_consent_events_feed_the_admin_report_without_storing_visitor_tokens(client):
    visitor = "session-visitor-123456789"
    events = [
        {"type": "page_view", "page": "home", "visitor": visitor},
        {"type": "page_view", "page": "shop", "visitor": visitor},
        {"type": "product_view", "page": "shop", "visitor": visitor, "itemId": "diamond_kit"},
        {"type": "add_to_cart", "page": "shop", "visitor": visitor, "itemId": "diamond_kit", "quantity": 1},
        {"type": "page_view", "page": "cart", "visitor": visitor},
        {"type": "checkout_start", "page": "cart", "visitor": visitor},
        {"type": "page_view", "page": "shop", "visitor": "second-visitor-123456789"},
    ]
    response = client.post(
        "/api/v6/analytics/events",
        headers=csrf_headers(client),
        json={"consent": True, "events": events},
    )

    assert response.status_code == 200, response.text
    assert response.json() == {"accepted": 7}

    headers = login_owner(client)
    today = datetime.now(timezone.utc).date().isoformat()
    report = client.get(f"/api/v6/admin/analytics?from={today}&to={today}", headers=headers)

    assert report.status_code == 200, report.text
    data = report.json()["data"]
    assert data["visits"] == 4
    assert data["visitors"] == 2
    assert {row["page"]: row["count"] for row in data["pages"]} == {"home": 1, "shop": 2, "cart": 1}
    action_counts = {(row["type"], row["page"]): row["count"] for row in data["events"]}
    assert action_counts[("product_view", "shop")] == 1
    assert action_counts[("add_to_cart", "shop")] == 1
    assert action_counts[("checkout_start", "cart")] == 1

    with client.app.state.database.transaction() as db:
        stored = db.execute("SELECT visitor_hash FROM cm_site_analytics_events").fetchall()
    stored_tokens = {row["visitor_hash"] for row in stored}
    assert visitor not in stored_tokens
    assert hashlib.sha256(visitor.encode()).hexdigest() in stored_tokens


def test_analytics_report_requires_an_authorized_account(client):
    today = datetime.now(timezone.utc).date().isoformat()
    response = client.get(f"/api/v6/admin/analytics?from={today}&to={today}")

    assert response.status_code == 401


def test_admin_report_counts_real_pending_delivery_status_and_confirmed_sales(client):
    now = datetime.now(timezone.utc)
    now_s = int(now.timestamp())
    now_ms = int(now.timestamp() * 1000)
    receipts = [
        ("pending-player", "pending-order", "PENDING_DELIVERY", 100, "AR", now_ms),
        ("sold-player", "delivered-order", "DELIVERED", 500, "CM", now_ms),
    ]
    with client.app.state.database.transaction() as db:
        for actor_id, order_id, status, total, currency, order_at in receipts:
            result = {
                "orders": [{
                    "id": order_id,
                    "playerId": actor_id,
                    "itemId": "test_item",
                    "qty": 1,
                    "status": status,
                    "total": total,
                    "currency": currency,
                    "at": order_at,
                }]
            }
            db.execute(
                "INSERT INTO cm_site_receipts(actor_id,key,operation,fingerprint,status,result,created_s) "
                "VALUES(%s,%s,'checkout','test','COMMITTED',%s,%s)",
                (actor_id, order_id, json.dumps(result), now_s),
            )

    headers = login_owner(client)
    today = now.date().isoformat()
    report = client.get(f"/api/v6/admin/analytics?from={today}&to={today}", headers=headers)

    assert report.status_code == 200, report.text
    data = report.json()["data"]
    assert data["pending"] == 1
    assert data["orders"] == 1
    assert data["items"] == 1
    assert data["revenue"] == {"AR": 0, "CM": 500}
    assert data["paymentsAvailable"] is False
