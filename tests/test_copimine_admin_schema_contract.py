from __future__ import annotations

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def read(relative: str) -> str:
    return (ROOT / relative).read_text(encoding="utf-8")


def test_admin_plugin_does_not_recreate_a_bigserial_sequence_owned_by_economy_core() -> None:
    source = read(
        "copimine-admin-plugin/src/me/copimine/ultimateplus/CopiMineUltimateAdminPlus.java"
    )

    assert "CREATE TABLE IF NOT EXISTS plugin_events(id BIGSERIAL" not in source
    assert "CREATE TABLE IF NOT EXISTS plugin_events(id BIGINT PRIMARY KEY" in source
    assert "CREATE SEQUENCE IF NOT EXISTS plugin_events_id_seq" in source
    assert "ALTER TABLE plugin_events ALTER COLUMN id SET DEFAULT nextval('plugin_events_id_seq')" in source


def test_admin_plugin_schema_contract_keeps_the_existing_event_columns() -> None:
    source = read(
        "copimine-admin-plugin/src/me/copimine/ultimateplus/CopiMineUltimateAdminPlus.java"
    )

    for column in (
        "source TEXT NOT NULL DEFAULT ''",
        "event_type TEXT NOT NULL",
        "actor TEXT NOT NULL DEFAULT ''",
        "target TEXT NOT NULL DEFAULT ''",
        "created_at BIGINT NOT NULL",
        "details TEXT NOT NULL DEFAULT ''",
    ):
        assert column in source
