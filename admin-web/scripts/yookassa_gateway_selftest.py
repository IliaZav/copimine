#!/usr/bin/env python3
"""Contract test for the YooKassa HTTP adapter without real provider credentials."""
from __future__ import annotations

import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from backend.yookassa_gateway import (  # noqa: E402
    YooKassaGateway,
    YooKassaGatewayError,
    YooKassaSettings,
)


class FakeResponse:
    def __init__(self, status_code: int, payload: dict[str, object]) -> None:
        self.status_code = status_code
        self._payload = payload
        self.text = str(payload)

    def json(self) -> dict[str, object]:
        return self._payload


class FakeClient:
    def __init__(self, responses: list[FakeResponse]) -> None:
        self.responses = responses
        self.requests: list[dict[str, object]] = []

    def request(self, method: str, url: str, **kwargs: object) -> FakeResponse:
        self.requests.append({"method": method, "url": url, **kwargs})
        return self.responses.pop(0)

    def close(self) -> None:
        return None


def payment_payload(*, status: str, amount: str = "250.00") -> dict[str, object]:
    return {
        "id": "2c0aef12-000f-5000-8000-1b68f0a9c444",
        "status": status,
        "paid": status == "succeeded",
        "amount": {"value": amount, "currency": "RUB"},
        "confirmation": {"type": "redirect", "confirmation_url": "https://yookassa.test/checkout"},
        "metadata": {"copimine_session_id": "don-session-safe"},
    }


def main() -> None:
    settings = YooKassaSettings(
        enabled=True,
        shop_id="123456",
        secret_key="test_secret",
        api_base_url="https://api.yookassa.test/v3",
        return_url="https://copimine.test/cabinet/donation-balance.html",
    )
    client = FakeClient([FakeResponse(200, payment_payload(status="pending")), FakeResponse(200, payment_payload(status="succeeded"))])
    gateway = YooKassaGateway(settings, client_factory=lambda: client)

    created = gateway.create_payment(
        idempotency_key="don-session-safe",
        amount_rub=250,
        description="Пополнение CopiMine Donation",
        session_id="don-session-safe",
        player_uuid="00000000-0000-0000-0000-000000000001",
    )
    assert created.payment_id == "2c0aef12-000f-5000-8000-1b68f0a9c444"
    assert created.confirmation_url == "https://yookassa.test/checkout"
    assert client.requests[0]["method"] == "POST"
    assert client.requests[0]["headers"]["Idempotence-Key"] == "don-session-safe"
    assert client.requests[0]["json"]["amount"] == {"value": "250.00", "currency": "RUB"}
    assert client.requests[0]["json"]["metadata"]["copimine_session_id"] == "don-session-safe"

    verified = gateway.verify_succeeded_payment(
        payment_id=created.payment_id,
        expected_amount_rub=250,
        expected_session_id="don-session-safe",
    )
    assert verified.payment_id == created.payment_id
    assert client.requests[1]["method"] == "GET"

    disabled = YooKassaGateway(YooKassaSettings(False, "", "", "https://api.yookassa.test/v3", ""), client_factory=lambda: client)
    try:
        disabled.create_payment("disabled-safe", 50, "x", "don-session-safe", "player")
    except YooKassaGatewayError as exc:
        assert exc.code == "not_configured"
    else:
        raise AssertionError("disabled provider must reject payment creation")

    mismatch = YooKassaGateway(settings, client_factory=lambda: FakeClient([FakeResponse(200, payment_payload(status="succeeded", amount="251.00"))]))
    try:
        mismatch.verify_succeeded_payment(created.payment_id, 250, "don-session-safe")
    except YooKassaGatewayError as exc:
        assert exc.code == "amount_mismatch"
    else:
        raise AssertionError("webhook verification must reject a mismatched amount")

    insecure_return = YooKassaGateway(
        YooKassaSettings(True, "123456", "test_secret", "https://api.yookassa.test/v3", "http://copimine.test/return"),
        client_factory=lambda: FakeClient([]),
    )
    try:
        insecure_return.create_payment("insecure-return", 50, "x", "don-session-safe", "player")
    except YooKassaGatewayError as exc:
        assert exc.code == "invalid_config"
    else:
        raise AssertionError("an insecure return URL must be rejected")

    missing_redirect = YooKassaGateway(
        settings,
        client_factory=lambda: FakeClient([FakeResponse(200, {**payment_payload(status="pending", amount="50.00"), "confirmation": {"type": "redirect"}})]),
    )
    try:
        missing_redirect.create_payment("missing-redirect", 50, "x", "don-session-safe", "player")
    except YooKassaGatewayError as exc:
        assert exc.code == "missing_confirmation"
    else:
        raise AssertionError("a pending redirect payment needs an HTTPS checkout URL")

    try:
        gateway.verify_succeeded_payment("../../malicious", 250, "don-session-safe")
    except YooKassaGatewayError as exc:
        assert exc.code == "invalid_payment"
    else:
        raise AssertionError("payment identifiers must not be interpreted as URL paths")

    print("YooKassa gateway selftest OK")


if __name__ == "__main__":
    main()
