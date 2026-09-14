from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
PLUGIN = ROOT / "copimine-end-event"
SRC = PLUGIN / "src/me/copimine/endevent"
DOMAIN = SRC / "domain"


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def test_wave6_uses_one_geometry_for_visuals_and_guard_containment() -> None:
    policy = read(DOMAIN / "CollapseRingGeometryPolicy.java")
    root = read(SRC / "CopiMineEndEvent.java")
    assert "RING_RADII = {8.0D, 14.0D, 19.0D}" in policy
    assert "RING_BAND_HALF_WIDTH = 2.25D" in policy
    assert "CollapseRingGeometryPolicy.ringRadius(ring)" in root
    assert "CollapseRingGeometryPolicy.visualPointCount(ring)" in root
    assert "CollapseRingGeometryPolicy.inPlayerLane" in root
    assert "CollapseRingGeometryPolicy.clampToPlayerLaneRadius" in root
    assert "renderCurrentCollapseRings(core, now)" in root
    assert "enforceCollapseRingLanes(anchor)" in root
    assert "enforceCollapseRingPlayerContainment(anchor)" in root
    assert "PlayerMoveEvent" in root
    assert "WAVE6_RING_CONTAINMENT" in root
    assert "WAVE6_RING_LEASH" in root
    assert "combatFloorY() + 1.0D" in root
    assert "private int chamberWaveNumber()" in root
    assert "private int chamberWaveNumber() {\n        return 7;\n    }" in root


def test_wave7_has_journaled_physical_boundaries_and_opening() -> None:
    policy = read(DOMAIN / "RealitySplitBarrierPolicy.java")
    journal = read(SRC / "HazardMutationJournal.java")
    root = read(SRC / "CopiMineEndEvent.java")
    assert "HEIGHT = 5" in policy
    assert "MIN_RADIUS = 0.5D" in policy
    assert "MAX_RADIUS = 27.5D" in policy
    assert "MAX_CELLS = 1536" in policy
    assert "boundaryForPair" in policy
    assert "REALITY_SPLIT_BARRIER" in journal
    assert "spawnRealitySplitBarriers(world, core)" in root
    assert "restoreRealitySplitBarriersAfterBootstrap()" in root
    assert "clearRealitySplitBarriers(\"wave-objective-reset\")" in root
    assert "openRealitySplitBoundary(" in root
    assert "Material.BARRIER" in root
    assert "Material.AMETHYST_BLOCK" in root
    assert "REALITY_SPLIT_WALL_MATERIAL" in root
    assert "isRealitySplitBarrierBlock" in root
    assert ".setType(REALITY_SPLIT_WALL_MATERIAL, false)" in root
    assert "journaled=true" in root
    assert "localChamberRoster" in root
    live_script = ROOT / "tests" / "RunEndRiftWave6Wave7BoundariesLive.ps1"
    assert live_script.exists()
    live = read(live_script)
    assert "minecraft:amethyst_block" in live
    assert "wall_material=amethyst_block" in live
    assert "LIVE_WAVE6_BOUNDARIES_PASS" in live
    assert "LIVE_WAVE7_BARRIERS_PASS" in live
    assert "LIVE_WAVE7_BARRIER_CLEANUP_PASS" in live


def test_wave7_barrier_is_not_a_permanent_map_change() -> None:
    root = read(SRC / "CopiMineEndEvent.java")
    recovery = read(SRC / "CopiMineEndEvent.java")
    assert "hazardJournal.markRestored()" in root
    assert "restoreBlock(barrier, realitySplitBarrierOriginals.get(cell))" in root
    assert "entry.isRealitySplitBarrierMutation()" in recovery
