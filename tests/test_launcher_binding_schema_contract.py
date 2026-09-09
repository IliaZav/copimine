from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
BACKEND = (ROOT / "admin-web/backend/main.py").read_text(encoding="utf-8")
RUNNER = (ROOT / "scripts/apply_launcher_binding_migration.sh").read_text(encoding="utf-8")


def test_binding_requests_use_the_isolated_schema_guard() -> None:
    assert "def ensure_launcher_binding_schema" in BACKEND
    assert "LAUNCHER_LINK_SCHEMA_UNAVAILABLE" in BACKEND
    assert BACKEND.count("ensure_launcher_binding_schema(conn)") >= 4
    binding_block = BACKEND[BACKEND.index("def create_launcher_link_challenge_sync"):BACKEND.index("def require_identity_rcon_ack")]
    assert "ensure_v4_schema(conn)" not in binding_block


def test_binding_migration_runner_requires_verified_backup_and_only_checked_in_sql() -> None:
    for marker in (
        "COPIMINE_ALLOW_LAUNCHER_BINDING_MIGRATION",
        "COPIMINE_LAUNCHER_BINDING_BACKUP_FILE",
        "sha256sum --check",
        "psql \"$DATABASE_URL\" -X -v ON_ERROR_STOP=1",
        "LAUNCHER_BINDING_MIGRATION=PASS tables=2 indexes=5",
        "COPIMINE_LAUNCHER_BINDING_MIGRATION_FILE",
    ):
        assert marker in RUNNER
    assert "ensure_v4_schema" not in RUNNER
    assert "world" not in RUNNER.lower()
    assert "playerdata" not in RUNNER.lower()
    assert "DELETE" not in RUNNER
    assert "DROP" not in RUNNER
