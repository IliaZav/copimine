"""Wave 6 runtime: the Rift Ram replaces the completed-by-triangle ritual.

The wave never completed because currentObjectiveProgressComplete() asked for
wave6Complete AND RitualCasterProgressionPolicy.mayComplete(mage deaths,
prisoner, prison broken, sphere clean) at the same instant. These checks read the
monolith and assert the new contract: a single ram, a banked charge guard before
any effect, and completion that depends on nothing from the deleted encounter.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"


def body(start: str, end: str) -> str:
    text = SOURCE.read_text(encoding="utf-8")
    return text[text.index(start):text.index(end, text.index(start))]


def test_wave_six_completion_no_longer_consults_the_deleted_encounter():
    text = SOURCE.read_text(encoding="utf-8")
    start = text.index("private boolean currentObjectiveProgressComplete(")
    block = text[start:text.index("private String carrierDiagnosticText(", start)]
    wave6 = block[block.index("case RIFT_RAM"):block.index("case REALITY_SPLIT")]
    # The branch must be EXACTLY the ram condition. A substring check passes even
    # when another term is ANDed in later, which is how the legacy flags could
    # creep back -- so normalise whitespace and compare the whole expression.
    branch = " ".join(wave6[len("case RIFT_RAM -> "):].split())
    assert branch.startswith("riftRamComplete && RiftRamPolicy.isComplete("), branch
    assert branch.endswith(");"), branch
    assert "RiftRamPolicy.isComplete(riftRamImpacts, isRiftRamAlive())" in wave6, (
        "wave 6 must complete on its own three-impacts-then-kill condition")
    for legacy in ("ritualCasterDeathCount", "wave6PrisonBroken",
                   "isRitualSphereRuntimeClean", "RitualCasterProgressionPolicy"):
        assert legacy not in wave6, (
            f"{legacy} belongs to the deleted encounter and must not gate completion")


def test_the_ritual_objective_can_never_report_completion():
    text = SOURCE.read_text(encoding="utf-8")
    start = text.index("private boolean currentObjectiveProgressComplete(")
    block = text[start:text.index("private String carrierDiagnosticText(", start)]
    assert "case COLLAPSE_RINGS, RITUAL_SPHERE -> false;" in block, (
        "a legacy objective must stay inert, so an old snapshot cannot look complete")


def test_the_impacts_are_banked_before_any_effect_runs():
    impact = body("private void bankRiftRamImpact(", "private void spawnRiftRamFragments(")
    guard = impact.index("mayAcceptImpact(")
    banked = impact.index("riftRamChargeBanked = true;")
    increment = impact.index("riftRamImpacts++;")
    first_effect = impact.index("world.playSound(")
    assert guard < banked < increment < first_effect, (
        "the guard must run before the bank flag, and the flag before any effect, "
        "so a duplicate collision callback in the same tick cannot crack the Core twice")


def test_one_ram_is_the_whole_spawn_guard():
    spawn = body("private void startRiftRam(", "private Location riftRamSpawnLocation(")
    assert "riftRamId = ram.getUniqueId();" in spawn
    assert spawn.count("world.spawn(") == 1, (
        "exactly one entity is created; the legacy wave spawned casters and guards "
        "on independent schedules, which is what allowed a second encounter")
    # A retried start must not adopt the previous ram.
    assert spawn.index("riftRamId = null;") < spawn.index("world.spawn(")


def test_the_wave_schedule_is_empty_so_nothing_else_spawns():
    text = SOURCE.read_text(encoding="utf-8")
    start = text.index("} else if (wave == 6) {")
    block = text[start:text.index("} else if (wave == 7) {", start)]
    assert "waveSpawnSchedule = List.of();" in block, (
        "a scheduled roster would re-introduce the uncontrolled spawns the ram replaces")
    assert "startRiftRam(world, core);" in block


def test_scale_drives_the_hitbox_not_only_the_modelsheet():
    spawn = body("private void startRiftRam(", "private Location riftRamSpawnLocation(")
    assert "Attribute.GENERIC_SCALE" in spawn, (
        "Paper's scale attribute is what resizes the collision box; setting only a "
        "visual would leave a vanilla-sized body around a 1.5x model")
    assert "RiftRamPolicy.SCALE" in spawn


def test_the_charge_locks_its_direction_and_never_homes():
    commit = body("private void commitRiftRamCharge(", "private void advanceRiftRamCharge(")
    assert "RiftRamPolicy.lockChargeDirection(" in commit
    assert "riftRamTargetId = null;" in commit, (
        "the target is forgotten at commit time; a homing charge makes the dodge unwinnable")
    advance = body("private void advanceRiftRamCharge(", "private static float endpointYaw(")
    assert "riftRamChargeDirX" in advance and "getTarget()" not in advance


def test_the_charge_uses_swept_core_collision():
    advance = body("private void advanceRiftRamCharge(", "private static float endpointYaw(")
    assert "RiftRamPolicy.segmentHitsCore(" in advance, (
        "a 1.1-block step per tick can cross the Core entirely, so a point test tunnels")


def test_armour_fragments_are_temporary_and_lootless():
    fragments = body("private void spawnRiftRamFragments(", "private void protectRiftRam(")
    assert "entity.setPersistent(false);" in fragments, (
        "a fragment that persists would leak into wave 7")
    assert "dropItem" not in fragments and "setDropChance" not in fragments
    assert "display.remove()" in fragments, "fragments must clean themselves up"


def test_the_core_is_restored_when_the_ram_finishes():
    complete = body("private void restoreRiftCore(", "}")
    assert "refreshClientBindingsForPlayer(" in complete
    text = SOURCE.read_text(encoding="utf-8")
    assert "riftRamComplete = true;" in text
    assert "restoreRiftCore();" in text
