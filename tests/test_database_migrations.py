from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
ELECTION_HARDENING = ROOT / "db" / "migrations" / "20260612_006_elections_hardening.sql"


def test_optional_election_tables_are_checked_without_raising_regclass_casts():
    migration = ELECTION_HARDENING.read_text(encoding="utf-8")

    assert "::regclass" not in migration
    for table in (
        "cmv731_votes",
        "candidates",
        "applications",
        "cmv7_ballot_issues",
        "cmv7_polling_stations",
        "cmv7_election_settings",
        "cmv7_election_curators",
    ):
        assert f"attrelid = to_regclass('copimine.{table}')" in migration
