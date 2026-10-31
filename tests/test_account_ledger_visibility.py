"""Видимость операций владельца в кабинете игрока.

Требование: деньги можно начислить или списать игроку, но решать, увидит ли он
запись у себя, должен администратор. В админском журнале операция обязана
остаться в любом случае — скрывается только личный кабинет игрока.
"""
import json

from backend import domain


def test_absent_flag_keeps_record_visible():
    """Старые записи и обычные переводы не должны исчезать из кабинета."""
    assert domain.ledger_visible_to_player("{}") is True
    assert domain.ledger_visible_to_player(None) is True
    assert domain.ledger_visible_to_player("") is True
    assert domain.ledger_visible_to_player({"note": "перевод"}) is True


def test_only_explicit_false_hides_record():
    assert domain.ledger_visible_to_player({"visibleToPlayer": False}) is False
    assert domain.ledger_visible_to_player(json.dumps({"visibleToPlayer": False})) is False
    assert domain.ledger_visible_to_player(json.dumps({"visibleToPlayer": True})) is True


def test_broken_payload_does_not_hide_money_history():
    """Битый JSON не должен прятать историю: показываем, а не выбрасываем."""
    assert domain.ledger_visible_to_player("{это не json") is True
    assert domain.ledger_visible_to_player(["visibleToPlayer", False]) is True


def test_hidden_operation_is_absent_from_the_players_cabinet():
    """Скрытая операция не попадает в историю переводов игрока."""
    value = {
        "ledger": [
            {
                "tx_id": "admin-credit-1:out",
                "account_id": "ar:uuid-self",
                "counterparty_account_id": "ar:uuid-peer",
                "tx_type": "TRANSFER_OUT",
                "amount": 100,
                "balance_after": 900,
                "created_at": 1700000000,
                "details": json.dumps({"note": "видимый перевод"}),
                "status": "COMMITTED",
            },
            {
                "tx_id": "admin-credit-2:out",
                "account_id": "ar:uuid-self",
                "counterparty_account_id": "ar:uuid-peer",
                "tx_type": "TRANSFER_OUT",
                "amount": 500,
                "balance_after": 400,
                "created_at": 1700000100,
                "details": json.dumps({"note": "скрытая выдача", "visibleToPlayer": False}),
                "status": "COMMITTED",
            },
        ]
    }
    rows = domain.transfer_history(_FakeDatabase(), {"id": "acc-self", "name": "unix", "minecraft_name": "unix"}, value)
    assert [row["amount"] for row in rows] == [100], "скрытая операция обязана исчезнуть из кабинета игрока"


class _FakeDatabase:
    """Минимальная заглушка: кабинету нужен только список привязанных аккаунтов."""

    def transaction(self):
        return self

    def __enter__(self):
        return self

    def __exit__(self, *exc_info):
        return False

    def execute(self, *_args, **_kwargs):
        return _Empty()

    def fetchall(self):
        return []


class _Empty:
    def fetchall(self):
        return []
