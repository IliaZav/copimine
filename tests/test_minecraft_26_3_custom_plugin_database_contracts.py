"""Regression contracts for first-party Paper plugins on the 26.3 candidate."""

import ast
from pathlib import Path
import hashlib
import json
import os
import re
import shutil
import subprocess
import uuid
from urllib.parse import parse_qsl, urlencode, urlsplit, urlunsplit

import pytest


ROOT = Path(__file__).resolve().parents[1]


def _read(relative_path: str) -> str:
    return (ROOT / relative_path).read_text(encoding="utf-8")


def _election_candidate_duplicate_migration_statements():
    source = _read("copimine-election-core/src/me/copimine/electioncore/CopiMineElectionCore.java")
    update_call = re.compile(
        r'update\(connection,\s*("(?:\\.|[^"\\])*"(?:\s*\+\s*"(?:\\.|[^"\\])*")*)'
        r'(?:\s*,\s*([A-Za-z][A-Za-z0-9_]*)\s*)?\);',
        re.DOTALL,
    )
    statements = {}
    for match in update_call.finditer(source):
        literals = re.findall(r'"(?:\\.|[^"\\])*"', match.group(1))
        sql = "".join(ast.literal_eval(literal) for literal in literals)
        if "CREATE TABLE IF NOT EXISTS candidate_migration_duplicate_archive" in sql:
            statements["archive_schema"] = sql
        elif "INSERT INTO candidate_migration_duplicate_archive" in sql:
            statements["archive_rows"] = sql
            statements["archive_parameter"] = match.group(2)
        elif "total_raw_votes" in sql and "total_admin_adjustment" in sql:
            statements["sum_tallies"] = sql
        elif "DELETE FROM candidates c USING ranked_candidates" in sql:
            statements["remove_duplicates"] = sql

    assert statements.get("archive_parameter") == "duplicateCandidatesArchivedAt"
    assert {"archive_schema", "archive_rows", "sum_tallies", "remove_duplicates"} <= statements.keys()
    return statements


def _election_candidate_legacy_constraint_migration_statement():
    source = _read("copimine-election-core/src/me/copimine/electioncore/CopiMineElectionCore.java")
    update_call = re.compile(
        r'update\(connection,\s*("(?:\\.|[^"\\])*"(?:\s*\+\s*"(?:\\.|[^"\\])*")*)'
        r'(?:\s*,\s*([A-Za-z][A-Za-z0-9_]*)\s*)?\);',
        re.DOTALL,
    )
    for match in update_call.finditer(source):
        literals = re.findall(r'"(?:\\.|[^"\\])*"', match.group(1))
        sql = "".join(ast.literal_eval(literal) for literal in literals)
        if "legacy_candidate_constraint" in sql:
            return sql
    raise AssertionError("ElectionCore must migrate legacy candidates.uuid/name constraints")


def test_admin_plus_uses_election_core_candidate_schema_without_splitting_data():
    admin = _read("copimine-admin-plugin/src/me/copimine/ultimateplus/CopiMineUltimateAdminPlus.java")
    election = _read("copimine-election-core/src/me/copimine/electioncore/CopiMineElectionCore.java")

    assert "player_uuid TEXT NOT NULL" in election
    assert "player_name TEXT NOT NULL" in election
    assert "CREATE TABLE IF NOT EXISTS candidates(election_id TEXT,uuid TEXT,name TEXT" not in admin
    assert "CREATE INDEX IF NOT EXISTS idx_cmv7_candidates_election_uuid ON candidates(election_id,player_uuid)" in admin
    assert "CREATE UNIQUE INDEX IF NOT EXISTS uq_candidates_election_player ON candidates(election_id,player_uuid)" in admin
    assert "player_uuid AS uuid" in admin
    assert "player_name AS name" in admin
    assert not re.search(r"(?:FROM|JOIN|INTO|UPDATE)\s+candidates\b[^\"\n]*\buuid\s*=", admin, re.IGNORECASE)
    assert not re.search(r"(?:FROM|JOIN|INTO|UPDATE)\s+candidates\b[^\"\n]*\bname\s+ASC", admin, re.IGNORECASE)
    assert "DELETE FROM candidates" not in admin
    assert "UPDATE candidates SET active=0 WHERE election_id=? AND COALESCE(active,0)=1" in admin
    assert "ON CONFLICT(election_id,player_uuid)" in admin


def test_election_core_promotes_legacy_admin_candidates_before_unique_index():
    election = _read("copimine-election-core/src/me/copimine/electioncore/CopiMineElectionCore.java")
    validator = _read("tests/ValidateCopiMineElectionApplications.ps1")

    index = election.index("CREATE UNIQUE INDEX IF NOT EXISTS uq_candidates_election_player")
    legacy_uuid = election.index("column_name='uuid'", election.index("private void ensureSchema"))
    legacy_name = election.index("column_name='name'", election.index("private void ensureSchema"))
    legacy_removed = election.index("column_name='removed'", election.index("private void ensureSchema"))
    assert legacy_uuid < index
    assert legacy_name < index
    assert legacy_removed < index
    assert "UPDATE candidates SET player_uuid=uuid" in election
    assert "UPDATE candidates SET player_name=name" in election
    assert "UPDATE candidates SET active=0" in election
    assert "COALESCE(player_uuid,'')=''" in election
    assert "AND player_uuid=\\?" in validator
    assert "candidates.uuid=?" not in validator

    archive_schema = election.index("CREATE TABLE IF NOT EXISTS candidate_migration_duplicate_archive")
    archive_rows = election.index("INSERT INTO candidate_migration_duplicate_archive")
    archive_snapshot = election.index("to_jsonb(c)", archive_rows)
    remove_duplicates = election.index("DELETE FROM candidates c USING ranked_candidates r")
    unique_index = election.index("CREATE UNIQUE INDEX IF NOT EXISTS uq_candidates_election_player")
    assert archive_schema < archive_rows < archive_snapshot < remove_duplicates < unique_index
    assert "original_candidate_row JSONB NOT NULL" in election
    assert "ROW_NUMBER() OVER (PARTITION BY election_id,player_uuid" in election
    assert "ORDER BY COALESCE(active,0) DESC,COALESCE(created_at,0) DESC,id,ctid" in election


def test_election_core_preserves_duplicate_candidate_vote_and_adjustment_totals():
    election = _read("copimine-election-core/src/me/copimine/electioncore/CopiMineElectionCore.java")

    ensure_schema = election.split("private void ensureSchema", 1)[1]
    raw_votes_column = ensure_schema.index(
        "ALTER TABLE candidates ADD COLUMN IF NOT EXISTS raw_votes BIGINT NOT NULL DEFAULT 0"
    )
    adjustments_column = ensure_schema.index(
        "ALTER TABLE candidates ADD COLUMN IF NOT EXISTS admin_adjustment BIGINT NOT NULL DEFAULT 0"
    )
    archive_duplicates = ensure_schema.index("INSERT INTO candidate_migration_duplicate_archive")
    merge_totals = ensure_schema.index("UPDATE candidates c SET raw_votes=r.total_raw_votes")
    remove_duplicates = ensure_schema.index("DELETE FROM candidates c USING ranked_candidates r")

    assert raw_votes_column < archive_duplicates
    assert adjustments_column < archive_duplicates
    assert archive_duplicates < merge_totals < remove_duplicates
    assert "SUM(COALESCE(raw_votes,0)) OVER (PARTITION BY election_id,player_uuid)" in ensure_schema
    assert "SUM(COALESCE(admin_adjustment,0)) OVER (PARTITION BY election_id,player_uuid)" in ensure_schema


def test_candidate_duplicate_migration_preserves_and_archives_real_postgres_rows():
    dsn = os.environ.get("COPIMINE_TEST_POSTGRES_DSN", "").strip()
    if not dsn:
        pytest.skip("set COPIMINE_TEST_POSTGRES_DSN to the isolated migration test database")

    if dsn.startswith(("postgresql://", "postgres://")):
        parsed_dsn = urlsplit(dsn)
        # The server helper adds a plugin-only `schema` query key to DATABASE_URL;
        # libpq/psycopg rejects that nonstandard URI parameter. The test creates
        # and selects its own disposable schema below, so drop only that key.
        query = [(key, value) for key, value in parse_qsl(parsed_dsn.query) if key.casefold() != "schema"]
        dsn = urlunsplit(parsed_dsn._replace(query=urlencode(query)))

    import psycopg
    from psycopg import sql

    statements = _election_candidate_duplicate_migration_statements()
    schema = "candidate_duplicate_" + uuid.uuid4().hex
    with psycopg.connect(dsn, autocommit=True) as connection:
        with connection.cursor() as cursor:
            cursor.execute("SELECT current_database(), inet_server_addr()")
            database, server_address = cursor.fetchone()
            assert database == "copimine_migration_test"
            assert server_address is not None
            if not server_address.is_loopback:
                # Local acceptance always uses loopback. CI's ephemeral
                # PostgreSQL service is isolated on the runner's private
                # container network, so permit that only through an explicit
                # job-level opt-in and reject public service addresses.
                assert os.environ.get("COPIMINE_TEST_POSTGRES_ALLOW_PRIVATE_NETWORK") == "1"
                assert server_address.is_private
            cursor.execute(sql.SQL("CREATE SCHEMA {}").format(sql.Identifier(schema)))
            cursor.execute(sql.SQL("SET search_path TO {}").format(sql.Identifier(schema)))

        try:
            with connection.transaction():
                connection.execute(statements["archive_schema"])
                connection.execute(
                    "CREATE TABLE candidates ("
                    "id TEXT PRIMARY KEY,election_id TEXT NOT NULL,player_uuid TEXT NOT NULL,"
                    "player_name TEXT NOT NULL DEFAULT '',active INTEGER NOT NULL DEFAULT 1,"
                    "created_at BIGINT NOT NULL DEFAULT 0,raw_votes BIGINT NOT NULL DEFAULT 0,"
                    "admin_adjustment BIGINT NOT NULL DEFAULT 0)"
                )
                with connection.cursor() as seed_cursor:
                    seed_cursor.executemany(
                        "INSERT INTO candidates(id,election_id,player_uuid,player_name,active,created_at,raw_votes,admin_adjustment) "
                        "VALUES(%s,%s,%s,%s,%s,%s,%s,%s)",
                        [
                            ("candidate-old", "election-1", "player-1", "Old name", 1, 10, 4, 7),
                            ("candidate-new", "election-1", "player-1", "New name", 1, 20, 3, -2),
                            ("candidate-other", "election-1", "player-2", "Other", 1, 15, 5, -1),
                        ],
                    )

                def apply_candidate_migration(archived_at):
                    cursor = connection.execute(
                        statements["archive_rows"].replace("?", "%s"), (archived_at,)
                    )
                    archived_rows = cursor.rowcount
                    connection.execute(statements["sum_tallies"])
                    connection.execute(statements["remove_duplicates"])
                    connection.execute(
                        "CREATE UNIQUE INDEX IF NOT EXISTS uq_candidates_election_player "
                        "ON candidates(election_id,player_uuid)"
                    )
                    return archived_rows

                assert apply_candidate_migration(1_800_000_000_000) == 1
                retained = connection.execute(
                    "SELECT id,raw_votes,admin_adjustment FROM candidates "
                    "WHERE election_id='election-1' AND player_uuid='player-1'"
                ).fetchone()
                assert retained == ("candidate-new", 7, 5)
                unaffected = connection.execute(
                    "SELECT raw_votes,admin_adjustment FROM candidates WHERE id='candidate-other'"
                ).fetchone()
                assert unaffected == (5, -1)
                archived = connection.execute(
                    "SELECT original_candidate_row->>'id',original_candidate_row->>'raw_votes',"
                    "original_candidate_row->>'admin_adjustment' "
                    "FROM candidate_migration_duplicate_archive"
                ).fetchone()
                assert archived == ("candidate-old", "4", "7")

                assert apply_candidate_migration(1_800_000_000_001) == 0
                assert connection.execute(
                    "SELECT id,raw_votes,admin_adjustment FROM candidates "
                    "WHERE election_id='election-1' AND player_uuid='player-1'"
                ).fetchone() == ("candidate-new", 7, 5)
                assert connection.execute(
                    "SELECT COUNT(*) FROM candidate_migration_duplicate_archive"
                ).fetchone() == (1,)
        finally:
            connection.execute(
                sql.SQL("DROP SCHEMA IF EXISTS {} CASCADE").format(sql.Identifier(schema))
            )


def test_election_core_removes_legacy_candidate_uuid_key_before_canonical_inserts():
    migration = _election_candidate_legacy_constraint_migration_statement()
    dsn = os.environ.get("COPIMINE_TEST_POSTGRES_DSN", "").strip()
    if not dsn:
        pytest.skip("set COPIMINE_TEST_POSTGRES_DSN to the isolated migration test database")

    if dsn.startswith(("postgresql://", "postgres://")):
        parsed_dsn = urlsplit(dsn)
        query = [(key, value) for key, value in parse_qsl(parsed_dsn.query) if key.casefold() != "schema"]
        dsn = urlunsplit(parsed_dsn._replace(query=urlencode(query)))

    import psycopg
    from psycopg import sql

    schema = "candidate_legacy_constraints_" + uuid.uuid4().hex
    with psycopg.connect(dsn, autocommit=True) as connection:
        with connection.cursor() as cursor:
            cursor.execute("SELECT current_database(), inet_server_addr()")
            database, server_address = cursor.fetchone()
            assert database == "copimine_migration_test"
            assert server_address is not None
            if not server_address.is_loopback:
                assert os.environ.get("COPIMINE_TEST_POSTGRES_ALLOW_PRIVATE_NETWORK") == "1"
                assert server_address.is_private
            cursor.execute(sql.SQL("CREATE SCHEMA {}").format(sql.Identifier(schema)))
            cursor.execute(sql.SQL("SET search_path TO {}").format(sql.Identifier(schema)))

        try:
            with connection.transaction():
                connection.execute(
                    "CREATE TABLE candidates(id TEXT NOT NULL,election_id TEXT NOT NULL,"
                    "uuid TEXT NOT NULL,name TEXT NOT NULL,CONSTRAINT candidates_legacy_uuid_pkey PRIMARY KEY(uuid))"
                )
                connection.execute(
                    "CREATE UNIQUE INDEX candidates_legacy_name_uq ON candidates(name)"
                )
                connection.execute(
                    "CREATE UNIQUE INDEX candidates_legacy_lower_name_uq ON candidates(lower(name))"
                )
                connection.execute(
                    "INSERT INTO candidates(id,election_id,uuid,name) VALUES(%s,%s,%s,%s)",
                    ("legacy-candidate", "election-1", "shared-player", "Legacy Name"),
                )
                connection.execute(
                    "ALTER TABLE candidates ADD COLUMN player_uuid TEXT NOT NULL DEFAULT ''"
                )
                connection.execute(
                    "ALTER TABLE candidates ADD COLUMN player_name TEXT NOT NULL DEFAULT ''"
                )
                connection.execute(
                    "UPDATE candidates SET player_uuid=uuid WHERE COALESCE(player_uuid,'')='' AND COALESCE(uuid,'')<>''"
                )
                connection.execute(
                    "UPDATE candidates SET player_name=name WHERE COALESCE(player_name,'')='' AND COALESCE(name,'')<>''"
                )
                connection.execute(migration)

                connection.execute(
                    "INSERT INTO candidates(id,election_id,player_uuid,player_name) VALUES(%s,%s,%s,%s)",
                    ("candidate-next", "election-2", "shared-player", "Current Name"),
                )
                assert connection.execute(
                    "SELECT id,election_id,player_uuid,player_name FROM candidates ORDER BY election_id"
                ).fetchall() == [
                    ("legacy-candidate", "election-1", "shared-player", "Legacy Name"),
                    ("candidate-next", "election-2", "shared-player", "Current Name"),
                ]
                assert connection.execute(
                    "SELECT is_nullable FROM information_schema.columns "
                    "WHERE table_schema=current_schema() AND table_name='candidates' AND column_name IN ('uuid','name') "
                    "ORDER BY column_name"
                ).fetchall() == [("YES",), ("YES",)]
                assert connection.execute(
                    "SELECT COUNT(*) FROM pg_constraint WHERE conrelid='candidates'::regclass "
                    "AND conname='candidates_legacy_uuid_pkey'"
                ).fetchone() == (0,)
                assert connection.execute(
                    "SELECT COUNT(*) FROM pg_class WHERE relname='candidates_legacy_name_uq'"
                ).fetchone() == (0,)
                assert connection.execute(
                    "SELECT COUNT(*) FROM pg_class WHERE relname='candidates_legacy_lower_name_uq'"
                ).fetchone() == (0,)
        finally:
            connection.execute(
                sql.SQL("DROP SCHEMA IF EXISTS {} CASCADE").format(sql.Identifier(schema))
            )


def test_artifact_orphan_reconciliation_releases_its_single_run_guard():
    source = _read("copimine-artifacts/src/me/copimine/artifacts/CopiMineArtifacts.java")
    reconciliation = source.split("private void scheduleOrphanedShopTransferReconciliation()", 1)[1]
    reconciliation = reconciliation.split("private boolean refundOrSkipOrphanedShopTransfer", 1)[0]

    assert re.search(
        r"OrphanTransferReconciliationRunGate\s+reconciliationRunGate\s*=\s*"
        r"new OrphanTransferReconciliationRunGate\(\)",
        reconciliation,
    )
    assert "this.reconciliationRunGate.runIfIdle(this::reconcile);" in reconciliation
    assert "private void reconcile()" in reconciliation


def test_both_ci_providers_run_the_legacy_candidate_constraint_integration_test():
    test_name = "test_election_core_removes_legacy_candidate_uuid_key_before_canonical_inserts"
    github = _read(".github/workflows/ci.yml")
    gitlab = _read(".gitlab-ci.yml")

    assert test_name in github
    assert test_name in gitlab


def test_admin_plus_waits_until_shared_economy_and_election_schemas_are_ready():
    admin = _read("copimine-admin-plugin/src/me/copimine/ultimateplus/CopiMineUltimateAdminPlus.java")
    descriptor = _read("copimine-admin-plugin/plugin.yml")
    economy = _read("copimine-economy-core/src/me/copimine/economycore/CopiMineEconomyCore.java")
    election = _read("copimine-election-core/src/me/copimine/electioncore/CopiMineElectionCore.java")

    assert "  - CopiMineElectionCore" in descriptor
    assert "databaseReady()" in economy
    assert "databaseReady()" in election
    assert "scheduleDatabaseBootstrap" in admin
    assert "databaseReady()" in admin
    assert "postgresReady" in admin


def test_admin_plus_reads_bukkit_registry_on_server_thread_and_checks_health_async():
    admin = _read("copimine-admin-plugin/src/me/copimine/ultimateplus/CopiMineUltimateAdminPlus.java")
    bootstrap = admin.split("private void scheduleDatabaseBootstrap()", 1)[1].split("private void finishEnable()", 1)[0]
    registry_lookup = admin.split("private CopiMineEconomyCore.ArtifactsBridge sharedDatabaseBridgeOnServerThread()", 1)[1]
    registry_lookup = registry_lookup.split("private boolean sharedDatabaseSchemasReady", 1)[0]
    health_check = admin.split("private boolean sharedDatabaseSchemasReady", 1)[1].split("private void finishEnable()", 1)[0]

    assert ".runTaskTimer(this, 1L, 20L)" in bootstrap
    assert ".runTaskTimerAsynchronously" not in bootstrap
    assert "sharedDatabaseBridgeOnServerThread()" in bootstrap
    assert "dbExecutor.execute(() ->" in bootstrap
    assert "getServer().getServicesManager().load(" in registry_lookup
    assert "getServer().getPluginManager().getPlugin(" in registry_lookup
    assert ".health(null, \"admin-schema-startup\")" in health_check
    assert "getServer().getServicesManager().load(" not in health_check
    assert "getServer().getPluginManager().getPlugin(" not in health_check


def test_admin_plus_resolves_artifacts_bridge_from_the_economy_core_plugin():
    admin = _read("copimine-admin-plugin/src/me/copimine/ultimateplus/CopiMineUltimateAdminPlus.java")
    economy = _read("copimine-economy-core/src/me/copimine/economycore/CopiMineEconomyCore.java")

    assert 'Plugin economyPlugin = getServer().getPluginManager().getPlugin("CopiMineEconomyCore")' in admin
    assert "instanceof CopiMineEconomyCore economyCore" in admin
    assert "economyCore.artifactsBridge()" in admin
    assert "new ArtifactsBridgeImpl()" in economy
    assert "ServicesManager().load(CopiMineEconomyCore.ArtifactsBridge.class)" not in admin


def test_election_core_does_not_rerun_schema_migrations_after_database_readiness():
    election = _read("copimine-election-core/src/me/copimine/electioncore/CopiMineElectionCore.java")
    bootstrap = election.split("private void bootstrapDatabaseSafe()", 1)[1].split("private void runSync", 1)[0]

    ready_guard = bootstrap.index("databaseReady.get()")
    driver_load = bootstrap.index('Class.forName("org.postgresql.Driver")')
    schema_migration = bootstrap.index("ensureSchema()")
    assert ready_guard < driver_load < schema_migration


def test_first_party_candidate_hashes_cover_the_installed_runtime_upgrade_path():
    lock = json.loads(_read("tools/minecraft-26.3/server-plugins.lock.json"))
    entries = {record["pluginName"]: record for record in lock["unchangedBaseline"]}
    receipt_path = ROOT / "local-runtime/end-rift-server-26.3/migration-plugin-receipt.json"
    receipt = json.loads(receipt_path.read_text(encoding="utf-8")) if receipt_path.is_file() else None
    installed = {record["pluginName"]: record for record in receipt["installed"]} if receipt else {}

    for plugin_name, record in entries.items():
        if not plugin_name.startswith("CopiMine") or "candidate" not in record:
            continue
        candidate = record["candidate"]
        artifact = ROOT / candidate["buildArtifact"]
        if artifact.is_file():
            candidate_hash = hashlib.sha256(artifact.read_bytes()).hexdigest()
            assert candidate_hash == candidate["sha256"], f"stale candidate lock for {plugin_name}"
        installed_record = installed.get(plugin_name)
        if installed_record and installed_record["sha256"] != candidate["sha256"]:
            assert installed_record["sha256"] in record.get("replaceSha256", []), (
                f"the receipt-verified active {plugin_name} JAR is not allowed to upgrade"
            )


def test_artifacts_retries_startup_reconciliation_after_bridge_readiness():
    artifacts = _read("copimine-artifacts/src/me/copimine/artifacts/CopiMineArtifacts.java")
    economy = _read("copimine-economy-core/src/me/copimine/economycore/CopiMineEconomyCore.java")

    assert "scheduleOrphanedShopTransferReconciliation" in artifacts
    assert "health.bridgeReady()" in artifacts
    assert "health.postgresReady()" in artifacts
    assert "Artifact orphan transfer lookup failed" in economy
    lookup = economy.split("public List<Map<String, Object>> findOrphanedArtifactShopTransfers", 1)[1]
    lookup = lookup.split("@Override", 1)[0]
    assert "throw new IllegalStateException" in lookup
    assert "return List.of()" not in lookup


def test_artifact_orphan_reconciliation_pages_past_bad_rows_and_keeps_retrying_transient_refunds():
    artifacts = _read("copimine-artifacts/src/me/copimine/artifacts/CopiMineArtifacts.java")
    economy = _read("copimine-economy-core/src/me/copimine/economycore/CopiMineEconomyCore.java")
    state = _read("copimine-artifacts/src/me/copimine/artifacts/OrphanShopTransferRecoveryState.java")
    guard = _read("copimine-artifacts/src/me/copimine/artifacts/ArtifactShopPurchaseRecoveryGuard.java")
    plugin_ci = _read("scripts/minecraft/RunJavaPluginCi.ps1")

    assert "findOrphanedArtifactShopTransfers(int limit, long afterCreatedAt, String afterTxId)" in artifacts
    lookup = economy.split("public List<Map<String, Object>> findOrphanedArtifactShopTransfers", 1)[1]
    lookup = lookup.split("@Override", 1)[0]
    assert "t.created_at > ? OR (t.created_at = ? AND (? = '' OR t.tx_id > ?))" in lookup
    assert "ORDER BY t.created_at ASC, t.tx_id ASC LIMIT ?" in lookup

    reconciliation = artifacts.split("private void scheduleOrphanedShopTransferReconciliation()", 1)[1]
    reconciliation = reconciliation.split("private List<CopiMineArtifacts.OrphanedShopTransfer> readOrphanedShopTransfers", 1)[0]
    cursor_advance = reconciliation.index("recoveryState.recordFetchedPage")
    per_transfer_processing = reconciliation.index("for (CopiMineArtifacts.OrphanedShopTransfer transfer : transfers)", cursor_advance)
    assert cursor_advance < per_transfer_processing < reconciliation.index("refundOrSkipOrphanedShopTransfer(transfer)", per_transfer_processing)
    assert "recoveryState.pendingSnapshot()" in reconciliation
    assert "recoveryState.defer(" in reconciliation
    assert "recoveryState.resolved(" in reconciliation
    assert "recoveryState.isScanComplete()" in reconciliation
    assert "recoveryState.beginNextScan(System.currentTimeMillis(), 60_000L)" in reconciliation
    assert "completedScanPolls < 6" in reconciliation
    purchase_flow = artifacts.split("private void executePurchase(", 1)[1].split("private void persistPaidPurchase(", 1)[0]
    assert purchase_flow.index("activeArtifactShopPurchases.begin(var6)") < purchase_flow.index('"artifact-purchase-" + var6')
    assert "activeArtifactShopPurchases.finish(var6)" in purchase_flow
    assert "hasPersistedArtifactShopPurchase(idempotencyKey)" in artifacts
    refund_recovery = artifacts.split("private boolean refundOrSkipOrphanedShopTransfer(", 1)[1].split("private boolean hasPersistedArtifactShopPurchase", 1)[0]
    assert refund_recovery.index("isPurchaseInFlight(purchaseId)") < refund_recovery.index("hasPersistedArtifactShopPurchase(idempotencyKey)")
    assert "isPurchaseInFlight(String purchaseId)" in guard
    assert "decisionAfterPersistenceLookup(boolean purchasePersisted)" in guard
    assert "RecoveryDecision.SKIP" in guard
    assert "RecoveryDecision.REFUND" in guard
    assert "ArtifactShopPurchaseRecoveryGuardTest" in plugin_ci
    assert "Collections.unmodifiableMap" in state
    assert "OrphanShopTransferRecoveryStateTest" in plugin_ci


def test_artifact_purchase_rollback_and_orphan_recovery_share_one_refund_key():
    artifacts = _read("copimine-artifacts/src/me/copimine/artifacts/CopiMineArtifacts.java")
    guard = _read("copimine-artifacts/src/me/copimine/artifacts/ArtifactShopPurchaseRecoveryGuard.java")

    shared_key_call = "ArtifactShopPurchaseRecoveryGuard.refundIdempotencyKey("
    assert artifacts.count(shared_key_call) == 2, (
        "normal rollback and orphan recovery must use the same idempotency-key helper"
    )
    assert 'return "artifact-refund-" + requirePurchaseId(purchaseId);' in guard
    assert '"artifact-orphan-refund-" + purchaseId' not in artifacts


def test_migration_plugin_build_order_follows_shared_bridge_dependencies():
    plugin_ci = _read("scripts/minecraft/RunJavaPluginCi.ps1")
    event_gate = _read("tests/RunEndRiftEventChecks.ps1")

    for source in (plugin_ci, event_gate):
        if "foreach ($relativeDirectory in @(" in source:
            builds = source.split("foreach ($relativeDirectory in @(", 1)[1].split("\n))", 1)[0]
        else:
            builds = source.split("$firstPartyBuilds = @(", 1)[1].split("\n)", 1)[0]
        order = [
            builds.index("copimine-economy-core"),
            builds.index("copimine-election-core"),
            builds.index("copimine-admin-plugin"),
            builds.index("copimine-artifacts"),
            builds.index("copimine-end-event"),
            builds.index("copimine-narcotics"),
        ]
        assert order == sorted(order), "plugin CI must build bridge providers before their consumers"


def test_migration_server_provisions_a_dedicated_loopback_postgres_cluster():
    initializer_path = ROOT / "scripts/minecraft/InitializeMigrationPostgres.ps1"
    assert initializer_path.is_file(), "The migration server must provision its own local PostgreSQL cluster."
    initializer = initializer_path.read_text(encoding="utf-8")
    launcher = _read("scripts/minecraft/StartMigrationTestServer.ps1")

    assert "--auth-host=scram-sha-256" in initializer
    assert "listen_addresses=127.0.0.1" in initializer
    assert "copimine_migration_test" in initializer
    assert "InitializeMigrationPostgres.ps1" in launcher
    assert "55434" in initializer
    assert "55433" not in launcher
    assert "SHOW listen_addresses" in initializer
    assert "pg_hba_file_rules" in initializer
    assert "address='127.0.0.1' AND netmask='255.255.255.255'" in initializer
    assert "address='::1' AND netmask='ffff:ffff:ffff:ffff:ffff:ffff:ffff:ffff'" in initializer


def test_validate_only_server_check_does_not_initialize_or_start_postgres():
    launcher = _read("scripts/minecraft/StartMigrationTestServer.ps1")
    database_environment_start = launcher.index("$databaseEnvironmentFile = Join-Path $runtime 'migration-isolated-postgres.env'")
    validate_only_start = launcher.index("if ($ValidateOnly) {", database_environment_start)
    initializer_call = "$databaseSetupOutput = @(& $postgresInitializer @databaseSetupArguments)"
    database_environment_check = "$null = Assert-MigrationDatabaseEnvironment -Path $databaseEnvironmentFile -RequiredSettings $requiredDatabaseSettings"
    assert database_environment_start < validate_only_start < launcher.index(initializer_call)
    assert launcher.index(initializer_call) < launcher.index(database_environment_check)
    database_setup_flow = launcher[database_environment_start:launcher.index(database_environment_check)]
    validation_branch, startup_branch = re.split(r"(?m)^\s*}\s*else\s*{\s*$", database_setup_flow, maxsplit=1)

    assert "$databaseEnvironmentFile = Join-Path $runtime 'migration-isolated-postgres.env'" in database_setup_flow
    assert initializer_call not in validation_branch
    assert initializer_call in startup_branch
    assert database_environment_check in launcher


def test_migration_database_environment_rejects_duplicate_and_conflicting_keys(tmp_path):
    module_path = ROOT / "scripts/minecraft/MigrationDatabaseEnvironment.psm1"
    powershell = shutil.which("powershell.exe") or shutil.which("pwsh")
    if powershell is None:
        pytest.skip("PowerShell is required to exercise the migration database environment validator.")

    expected_settings = [
        "POSTGRES_HOST=127.0.0.1",
        "POSTGRES_PORT=55434",
        "POSTGRES_DB=copimine_migration_test",
        "POSTGRES_SCHEMA=copimine_migration_26_3_candidate",
        "POSTGRES_USER=copimine_migration_test",
    ]
    password = "a" * 64
    good_env = tmp_path / "good.env"
    duplicate_env = tmp_path / "duplicate.env"
    conflict_env = tmp_path / "conflict.env"
    good_env.write_text("\n".join([*expected_settings, f"POSTGRES_PASSWORD={password}", "DATABASE_URL=jdbc:postgresql://127.0.0.1:55434/copimine_migration_test"]) + "\n", encoding="utf-8")
    duplicate_env.write_text("\n".join([*expected_settings, f"POSTGRES_PASSWORD={password}", "POSTGRES_HOST=127.0.0.1"]) + "\n", encoding="utf-8")
    conflict_env.write_text("\n".join(["POSTGRES_HOST=192.0.2.1", *expected_settings[1:], f"POSTGRES_PASSWORD={password}"]) + "\n", encoding="utf-8")

    escaped_module = str(module_path).replace("'", "''")
    escaped_good = str(good_env).replace("'", "''")
    escaped_duplicate = str(duplicate_env).replace("'", "''")
    escaped_conflict = str(conflict_env).replace("'", "''")
    command = f"""
$ErrorActionPreference = 'Stop'
Import-Module -Name '{escaped_module}' -Force
$required = @({', '.join(repr(setting) for setting in expected_settings)})
$good = Assert-MigrationDatabaseEnvironment -Path '{escaped_good}' -RequiredSettings $required
$duplicateMessage = ''
try {{ $null = Assert-MigrationDatabaseEnvironment -Path '{escaped_duplicate}' -RequiredSettings $required }} catch {{ $duplicateMessage = $_.Exception.Message }}
$conflictMessage = ''
try {{ $null = Assert-MigrationDatabaseEnvironment -Path '{escaped_conflict}' -RequiredSettings $required }} catch {{ $conflictMessage = $_.Exception.Message }}
[PSCustomObject]@{{ goodEndpoint = ($good['POSTGRES_HOST'] -ceq '127.0.0.1' -and $good['POSTGRES_PASSWORD'].Length -eq 64); duplicateMessage = $duplicateMessage; conflictMessage = $conflictMessage }} | ConvertTo-Json -Compress
"""
    result = subprocess.run(
        [powershell, "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", command],
        check=False,
        capture_output=True,
        text=True,
        timeout=30,
    )

    assert result.returncode == 0, result.stderr
    report = json.loads(result.stdout)
    assert report["goodEndpoint"] is True
    assert "Duplicate migration database environment key: POSTGRES_HOST" in report["duplicateMessage"]
    assert "Migration database environment value does not match the isolated runtime for key POSTGRES_HOST" in report["conflictMessage"]

    start_script = _read("scripts/minecraft/StartMigrationTestServer.ps1")
    assert "Assert-MigrationDatabaseEnvironment -Path $databaseEnvironmentFile" in start_script
    assert "$databaseEnvironmentLines = Get-Content" not in start_script


def test_interrupted_migration_database_setup_recovers_missing_plugin_credentials(tmp_path):
    module_path = ROOT / "scripts/minecraft/MigrationDatabaseEnvironment.psm1"
    powershell = shutil.which("powershell.exe") or shutil.which("pwsh")
    if powershell is None:
        pytest.skip("PowerShell is required to exercise interrupted credential setup recovery.")

    admin_env = tmp_path / "migration-admin.env"
    plugin_env = tmp_path / "migration-plugin.env"
    admin_env.write_text("POSTGRES_SUPERUSER_PASSWORD=existing-admin-password\n", encoding="utf-8")
    escaped_module = str(module_path).replace("'", "''")
    escaped_admin = str(admin_env).replace("'", "''")
    escaped_plugin = str(plugin_env).replace("'", "''")
    command = f"""
$ErrorActionPreference = 'Stop'
Import-Module -Name '{escaped_module}' -Force
$first = Initialize-MigrationPostgresCredentialFiles -AdminPath '{escaped_admin}' -PluginPath '{escaped_plugin}' -DatabaseInitialized:$false -Port 55434 -Database 'copimine_migration_test' -Schema 'copimine_migration_26_3_candidate' -Role 'copimine_migration_test'
$adminAfterFirst = Read-MigrationPrivateEnvValue -Path '{escaped_admin}' -Name 'POSTGRES_SUPERUSER_PASSWORD'
$pluginAfterFirst = Read-MigrationPrivateEnvValue -Path '{escaped_plugin}' -Name 'POSTGRES_PASSWORD'
$second = Initialize-MigrationPostgresCredentialFiles -AdminPath '{escaped_admin}' -PluginPath '{escaped_plugin}' -DatabaseInitialized:$false -Port 55434 -Database 'copimine_migration_test' -Schema 'copimine_migration_26_3_candidate' -Role 'copimine_migration_test'
[PSCustomObject]@{{ adminPreserved = ($adminAfterFirst -ceq 'existing-admin-password' -and $second.AdminPassword -ceq 'existing-admin-password'); pluginPasswordValid = ($pluginAfterFirst -match '^[0-9a-f]{{64}}$' -and $second.PluginPassword -ceq $pluginAfterFirst); pluginEndpointCorrect = ((Get-Content -LiteralPath '{escaped_plugin}' -Raw).Contains('POSTGRES_HOST=127.0.0.1') -and (Get-Content -LiteralPath '{escaped_plugin}' -Raw).Contains('POSTGRES_PORT=55434') -and (Get-Content -LiteralPath '{escaped_plugin}' -Raw).Contains('POSTGRES_SCHEMA=copimine_migration_26_3_candidate')) }} | ConvertTo-Json -Compress
"""
    result = subprocess.run(
        [powershell, "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", command],
        check=False,
        capture_output=True,
        text=True,
        timeout=30,
    )

    assert result.returncode == 0, result.stderr
    report = json.loads(result.stdout)
    assert report["adminPreserved"] is True
    assert report["pluginPasswordValid"] is True
    assert report["pluginEndpointCorrect"] is True


def test_postgres_invocation_captures_native_stderr_and_uses_exit_code_for_failure():
    initializer = _read("scripts/minecraft/InitializeMigrationPostgres.ps1")
    invoke_psql = initializer.split("function Invoke-Psql", 1)[1].split("\n}", 1)[0]

    assert "$PSNativeCommandUseErrorActionPreference = $false" in invoke_psql
    assert "$LASTEXITCODE -ne 0" in invoke_psql
    assert "2>&1" in invoke_psql
    assert "SET client_min_messages TO warning;" in initializer


def test_migration_postgres_password_is_encoded_before_alter_role_sql():
    initializer = _read("scripts/minecraft/InitializeMigrationPostgres.ps1")
    module_text = _read("scripts/minecraft/MigrationDatabaseEnvironment.psm1")
    module_path = ROOT / "scripts/minecraft/MigrationDatabaseEnvironment.psm1"
    powershell = shutil.which("powershell.exe") or shutil.which("pwsh")
    if powershell is None:
        pytest.skip("PowerShell is required to exercise the migration PostgreSQL password encoder.")

    sample = "p'ass; SELECT 1; -- é"
    escaped_module = str(module_path).replace("'", "''")
    command = f"""
$ErrorActionPreference = 'Stop'
Import-Module -Name '{escaped_module}' -Force
$value = \"{sample}\"
$encoded = ConvertTo-MigrationPostgresPasswordHex -Value $value
$generatedA = New-MigrationPostgresPassword
$generatedB = New-MigrationPostgresPassword
[PSCustomObject]@{{ encoded = $encoded; generatedValid = ($generatedA -match '^[0-9a-f]{{64}}$' -and $generatedB -match '^[0-9a-f]{{64}}$'); generatedUnique = ($generatedA -cne $generatedB) }} | ConvertTo-Json -Compress
"""
    result = subprocess.run(
        [powershell, "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", command],
        check=False,
        capture_output=True,
        text=True,
        timeout=30,
    )

    assert result.returncode == 0, result.stderr
    generated = json.loads(result.stdout)
    assert generated["encoded"] == sample.encode("utf-8").hex()
    assert generated["generatedValid"] is True
    assert generated["generatedUnique"] is True
    assert "$pluginPasswordHex = ConvertTo-MigrationPostgresPasswordHex -Value $pluginPassword" in initializer
    assert "$adminPassword = New-MigrationPostgresPassword" in module_text
    assert "$pluginPassword = New-MigrationPostgresPassword" in module_text
    assert "[Convert]::ToHexString" not in initializer
    assert "RandomNumberGenerator]::GetBytes(32)" not in initializer
    assert "convert_from(decode('__PLUGIN_PASSWORD_HEX__','hex'),'UTF8')" in initializer
    assert "PASSWORD '$pluginPassword'" not in initializer


def test_migration_private_credential_files_are_acl_first_and_reparse_safe(tmp_path):
    module_path = ROOT / "scripts/minecraft/MigrationDatabaseEnvironment.psm1"
    module_text = module_path.read_text(encoding="utf-8")
    initializer = _read("scripts/minecraft/InitializeMigrationPostgres.ps1")
    reader = module_text.split("function Read-MigrationPrivateEnvValue", 1)[1].split("\n}", 1)[0]
    writer = module_text.split("function Write-MigrationPrivateEnvFile", 1)[1].split("\n}", 1)[0]

    assert "[IO.FileMode]::CreateNew" in writer
    secure_creation = re.search(r"\[IO\.FileStream\]::new\((.*?)\n\s*\)", writer, re.S)
    assert secure_creation is not None
    assert "[IO.FileMode]::CreateNew" in secure_creation.group(1)
    assert "$security" in secure_creation.group(1)
    assert secure_creation.start() < writer.index("$stream.Write(")
    assert writer.index("$createdByThisCall = $true") > secure_creation.end()
    assert "if ($createdByThisCall -and (Test-Path -LiteralPath $Path))" in writer
    assert "[IO.FileAttributes]::ReparsePoint" in reader
    assert reader.index("[IO.FileAttributes]::ReparsePoint") < reader.index("Get-Content -LiteralPath $Path")
    assert "Initialize-MigrationPostgresCredentialFiles" in initializer

    powershell = shutil.which("powershell.exe") or shutil.which("pwsh")
    if powershell is None:
        pytest.skip("PowerShell is required to exercise the migration credential file ACL.")
    escaped_module = str(module_path).replace("'", "''")
    destination = str(tmp_path / "migration-private.env").replace("'", "''")
    command = f"""
$ErrorActionPreference = 'Stop'
Import-Module -Name '{escaped_module}' -Force
$content = \"POSTGRES_PASSWORD=$('a' * 64)`n\"
Write-MigrationPrivateEnvFile -Path '{destination}' -Contents $content
$read = Read-MigrationPrivateEnvValue -Path '{destination}' -Name 'POSTGRES_PASSWORD'
$rules = [IO.File]::GetAccessControl('{destination}').GetAccessRules($true, $true, [Security.Principal.SecurityIdentifier])
$actual = @($rules | ForEach-Object {{ $_.IdentityReference.Value }} | Sort-Object -Unique)
$currentUser = [Security.Principal.WindowsIdentity]::GetCurrent().User.Value
$expected = @($currentUser, 'S-1-5-18', 'S-1-5-32-544') | Sort-Object -Unique
[PSCustomObject]@{{ contentMatches = ($read -ceq ('a' * 64)); aclMatches = (Compare-Object $expected $actual).Count -eq 0 }} | ConvertTo-Json -Compress
"""
    result = subprocess.run(
        [powershell, "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", command],
        check=False,
        capture_output=True,
        text=True,
        timeout=30,
    )

    assert result.returncode == 0, result.stderr
    assert json.loads(result.stdout) == {"contentMatches": True, "aclMatches": True}


def test_migration_postgres_initializer_rejects_junction_ancestor_before_writing(tmp_path):
    if os.name != "nt":
        pytest.skip("junction ancestor regression is Windows-specific")
    powershell = shutil.which("powershell.exe") or shutil.which("pwsh")
    if powershell is None:
        pytest.skip("PowerShell is required to exercise migration PostgreSQL path validation.")

    fake_repo = tmp_path / "repo"
    scripts = fake_repo / "scripts" / "minecraft"
    scripts.mkdir(parents=True)
    shutil.copy2(ROOT / "scripts/minecraft/InitializeMigrationPostgres.ps1", scripts)
    shutil.copy2(ROOT / "scripts/minecraft/MigrationDatabaseEnvironment.psm1", scripts)

    outside_runtime_root = tmp_path / "outside-runtime"
    outside_runtime = outside_runtime_root / "end-rift-server-26.3"
    outside_runtime.mkdir(parents=True)
    junction = fake_repo / "local-runtime"
    link = subprocess.run(
        ["cmd.exe", "/c", "mklink", "/J", str(junction), str(outside_runtime_root)],
        check=False,
        capture_output=True,
        text=True,
        timeout=15,
    )
    if link.returncode != 0:
        pytest.skip(f"could not create a Windows junction fixture: {link.stderr or link.stdout}")

    initializer = scripts / "InitializeMigrationPostgres.ps1"
    runtime_argument = junction / "end-rift-server-26.3"
    escaped_initializer = str(initializer).replace("'", "''")
    escaped_runtime = str(runtime_argument).replace("'", "''")
    command = f"try {{ & '{escaped_initializer}' -RuntimeDirectory '{escaped_runtime}' -PostgresBin 'missing-postgres'; exit 0 }} catch {{ Write-Error $_; exit 73 }}"
    try:
        result = subprocess.run(
            [powershell, "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", command],
            check=False,
            capture_output=True,
            text=True,
            timeout=30,
        )
        output = result.stdout + result.stderr
        assert result.returncode == 73
        assert "reparse" in output.casefold()
        assert not (outside_runtime / "postgres-data").exists()
        assert not (outside_runtime / "logs").exists()
        assert not (outside_runtime / "migration-postgres-admin.env").exists()
        assert not (outside_runtime / "migration-isolated-postgres.env").exists()
        assert not (outside_runtime / "postgres-initdb-password.tmp").exists()
    finally:
        junction.rmdir()


def test_migration_postgres_password_cleanup_only_removes_a_file_created_by_this_call(tmp_path):
    module_path = ROOT / "scripts/minecraft/MigrationDatabaseEnvironment.psm1"
    initializer = _read("scripts/minecraft/InitializeMigrationPostgres.ps1")
    powershell = shutil.which("powershell.exe") or shutil.which("pwsh")
    if powershell is None:
        pytest.skip("PowerShell is required to exercise migration PostgreSQL temporary-file cleanup.")

    existing = tmp_path / "postgres-initdb-password.tmp"
    created = tmp_path / "owned-postgres-initdb-password.tmp"
    escaped_module = str(module_path).replace("'", "''")
    escaped_existing = str(existing).replace("'", "''")
    escaped_created = str(created).replace("'", "''")
    command = f"""
$ErrorActionPreference = 'Stop'
Import-Module -Name '{escaped_module}' -Force
[IO.File]::WriteAllText('{escaped_existing}', 'preserve-existing')
Remove-MigrationPrivateFileIfOwned -Path '{escaped_existing}' -CreatedByThisCall:$false
[IO.File]::WriteAllText('{escaped_created}', 'owned-secret')
Remove-MigrationPrivateFileIfOwned -Path '{escaped_created}' -CreatedByThisCall:$true
[PSCustomObject]@{{ existingPreserved = ([IO.File]::ReadAllText('{escaped_existing}') -ceq 'preserve-existing'); ownedRemoved = -not (Test-Path -LiteralPath '{escaped_created}') }} | ConvertTo-Json -Compress
"""
    result = subprocess.run(
        [powershell, "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", command],
        check=False,
        capture_output=True,
        text=True,
        timeout=30,
    )

    assert result.returncode == 0, result.stderr
    assert json.loads(result.stdout) == {"existingPreserved": True, "ownedRemoved": True}
    assert "$passwordFileCreatedByThisCall = $true" in initializer
    assert "Remove-MigrationPrivateFileIfOwned -Path $passwordFile -CreatedByThisCall:$passwordFileCreatedByThisCall" in initializer


def test_native_acceptance_guide_uses_the_dedicated_candidate_database():
    guide = _read("docs/minecraft-26.3/NATIVE_ACCEPTANCE_SIGNING.md")

    assert "migration-isolated-postgres.env" in guide
    assert "55434" in guide
    assert "migration-test.env" not in guide


def test_native_acceptance_guide_switches_plugin_inventory_around_local_client_check():
    guide = _read("docs/minecraft-26.3/NATIVE_ACCEPTANCE_SIGNING.md")

    unauthenticated_install = guide.index("install_migration_server_plugins.py --unauthenticated-test-runtime")
    local_test_start = guide.index("StartMigrationTestServer.ps1", unauthenticated_install)
    full_install = guide.index("install_migration_server_plugins.py", local_test_start)

    assert unauthenticated_install < local_test_start < full_install
