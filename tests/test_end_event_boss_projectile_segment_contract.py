"""Secondary guards for the finite boss-projectile sweep boundary."""

from __future__ import annotations

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
POLICY = (
    ROOT
    / "copimine-end-event"
    / "src"
    / "me"
    / "copimine"
    / "endevent"
    / "domain"
    / "BossOrientedHitboxPolicy.java"
)
CONTROLLER = (
    ROOT
    / "copimine-end-event"
    / "src"
    / "me"
    / "copimine"
    / "endevent"
    / "runtime"
    / "BossHitboxController.java"
)
PLUGIN = (
    ROOT
    / "copimine-end-event"
    / "src"
    / "me"
    / "copimine"
    / "endevent"
    / "CopiMineEndEvent.java"
)
RUNNER = ROOT / "tests" / "RunEndRiftEventChecks.ps1"


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def test_policy_exposes_finite_segment_intersection() -> None:
    source = read(POLICY)
    assert "segmentIntersects(OrientedBox box" in source
    assert "segmentEntryDistance" in source
    assert "double tMax = 1.0D" in source


def test_controller_tracks_previous_projectile_samples_and_consumes_uuid_once() -> None:
    source = read(CONTROLLER)
    assert "previousProjectilePositions" in source
    assert "previousProjectilePositions.put" in source
    assert "proxySegmentIntersects" in source
    assert "acceptProjectileHit" in source
    assert "consumedProjectileIds" in source
    assert "forgetProjectile" in source
    assert "projectileHitProxyAlongRay" not in source
    method = source.split("public Interaction projectileHitProxy(", 1)[1].split(
        "public boolean proxySegmentIntersects", 1
    )[0]
    assert "segment" in method.lower()
    assert "8.0D" not in method


def test_plugin_uses_the_same_finite_segment_for_event_and_fallback_routes() -> None:
    source = read(PLUGIN)
    fallback = source.split("private void tickBossHitboxProjectiles", 1)[1].split(
        "/** Official boss loop", 1
    )[0]
    event = source.split("public void onBossHitboxProjectile", 1)[1].split(
        "@EventHandler", 1
    )[0]
    assert "projectileHitProxy(boss, projectile,\n                    poses)" in fallback
    assert "proxySegmentIntersects(boss, proxy, projectile" in event
    assert "acceptProjectileHit" in fallback and "acceptProjectileHit" in event
    assert "8.0D" not in fallback
    assert "projectileHitProxyAlongRay" not in source


def test_segment_contract_is_included_in_the_full_gate() -> None:
    runner = read(RUNNER)
    assert "test_end_event_boss_projectile_segment_contract.py" in runner
