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
    assert branch == "riftRam != null && riftRam.complete();", branch
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


RUNTIME = ROOT / "copimine-end-event/src/me/copimine/endevent/runtime/RiftRamEncounter.java"


def runtime_body(start, end):
    text = RUNTIME.read_text(encoding="utf-8")
    return text[text.index(start):text.index(end, text.index(start))]


def test_the_impacts_are_banked_before_any_effect_runs():
    impact = runtime_body("private void impact()", "private void stomp(")
    assert impact.index("banked=true; impacts++;") < impact.index("clearPillar();")


def test_one_ram_is_the_whole_spawn_guard():
    spawn = runtime_body("public void start(", "private Location findSite(")
    assert "if (ram != null || closed)" in spawn
    assert spawn.count("spawn(site, Ravager.class") == 1


def test_the_wave_schedule_is_empty_so_nothing_else_spawns():
    text = SOURCE.read_text(encoding="utf-8")
    start = text.index("} else if (wave == 6) {")
    block = text[start:text.index("} else if (wave == 7) {", start)]
    assert "waveSpawnSchedule = List.of();" in block, (
        "a scheduled roster would re-introduce the uncontrolled spawns the ram replaces")
    assert "startRiftRam(world, core);" in block


def test_scale_drives_the_hitbox_not_only_the_modelsheet():
    spawn = runtime_body("public void start(", "private Location findSite(")
    assert "Attribute.GENERIC_SCALE" in spawn
    assert "RiftRamPolicy.SCALE" in spawn


def test_the_charge_locks_its_direction_and_never_homes():
    charge = runtime_body("private void charge(", "private void impact()")
    assert "direction.clone()" in charge and "getTarget()" not in charge


def test_the_charge_uses_swept_pillar_collision():
    charge = runtime_body("private void charge(", "private void impact()")
    assert "RiftRamPolicy.segmentHitsPillar(" in charge
    assert "host.core()" not in charge and "segmentHitsCore" not in charge


def test_armour_fragments_are_temporary_and_lootless():
    text = RUNTIME.read_text(encoding="utf-8")
    assert "entity.setPersistent(false);" in text
    assert "dropItem" not in text and "FallingBlock" not in text
    assert "if(age>=30) { host.remove(d.display());" in text


def test_the_core_is_untouched_when_the_ram_finishes():
    complete = runtime_body("public void died(", "private void clearPillar()")
    assert "completed=true;" in complete and "cleanupDisplays();" in complete
    assert "getBlock().set" not in RUNTIME.read_text(encoding="utf-8")
