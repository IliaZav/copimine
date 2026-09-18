from __future__ import annotations

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CONTROLLER = ROOT / "copimine-end-event" / "src" / "me" / "copimine" / "endevent" / "runtime" / "BossHitboxController.java"
POLICY = ROOT / "copimine-end-event" / "src" / "me" / "copimine" / "endevent" / "domain" / "BossHitboxProxyReconciliationPolicy.java"
RUNNER = ROOT / "tests" / "RunEndRiftEventChecks.ps1"


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def test_policy_owns_expected_live_missing_and_stale_math() -> None:
    source = read(POLICY)
    assert "missing.removeAll(liveKeys)" in source
    assert "stale.removeAll(expectedKeys)" in source
    assert "duplicates" in source
    assert "duplicate" in source.lower()
    assert "expectedKeys(BossHitboxProfile profile)" in source
    assert "record Key(BossHitboxProfile.PartId partId, int segmentIndex)" in source
    assert "requiresRebuild()" in source


def test_controller_repairs_before_update_and_each_damage_route() -> None:
    source = read(CONTROLLER)
    assert "BossHitboxProxyReconciliationPolicy" in source
    assert "BossHitboxProxyMetadataPolicy" in source
    assert "if (!ensureHealthy(boss))" in source
    assert source.count("ensureHealthy(boss)") >= 4
    assert "boolean rebuilt = begin(boss, repairEventId, repairGeneration);" in source
    assert "BOSS_HITBOX_PROXY_RECREATED event=" in source
    assert "part=" in source and "segment=" in source and "generation=" in source
    assert "liveReconciliation()" in source
    assert "slotMetadataMatches" in source
    assert "isCurrentEvent" in source
    assert "!proxy.isValid()" in source
    assert "getPersistentDataContainer" in source
    assert "world.getEntities()" in source
    assert "duplicates()" in source
    assert "malformed" in source
    accept_start = source.index("public boolean acceptHit(")
    accept_end = source.index("public String attackIdentity", accept_start)
    assert "ensureHealthy" in source[accept_start:accept_end]


def test_controller_does_not_reuse_a_same_tick_world_scan() -> None:
    source = read(CONTROLLER)
    assert "cachedReconciliation" not in source
    assert "lastReconciliationServerTick" not in source
    assert "indexedSlotsHealthy" not in source
    live_start = source.index("private BossHitboxProxyReconciliationPolicy.Result liveReconciliation()")
    live_end = source.index("private boolean slotMetadataMatches", live_start)
    live_source = source[live_start:live_end]
    assert "world.getEntities()" in live_source
    assert "Bukkit.getCurrentTick()" not in live_source


def test_reconciliation_is_registered_in_the_current_gate() -> None:
    runner = read(RUNNER)
    assert "BossHitboxProxyReconciliationPolicyTest" in runner
    assert "test_end_event_boss_hitbox_reconciliation_contract.py" in runner
