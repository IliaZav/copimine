"""Source contracts for the shared End Rift hive AI call sites."""

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
EVENT = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"


def _declaration(source: str, signature: str) -> str:
    start = source.index(signature)
    opening = source.index("{", start)
    depth = 0
    for index in range(opening, len(source)):
        if source[index] == "{":
            depth += 1
        elif source[index] == "}":
            depth -= 1
            if depth == 0:
                return source[start:index + 1]
    raise AssertionError(f"unterminated method: {signature}")


def test_wave6_elites_are_coordinated_but_ritual_casters_are_not() -> None:
    source = EVENT.read_text(encoding="utf-8")
    uses_coordination = _declaration(source, "private boolean usesWaveCombatCoordination(Entity entity)")
    hive_eligibility = _declaration(source, "private boolean isWaveAiCombatEntity(Entity entity)")

    assert "wave <= 6" in uses_coordination
    assert "EVENT_KIND_RITUAL_CASTER" in uses_coordination
    assert "EVENT_KIND_RITUAL_CASTER" in hive_eligibility


def test_active_spell_lease_keeps_its_target_and_owns_navigation() -> None:
    source = EVENT.read_text(encoding="utf-8")
    tick = _declaration(source, "private void tickWaveMobAi()")

    lease_check = tick.index("waveCombatCoordinator.currentLease(")
    planned_target = tick.index("UUID plannedTargetId =")
    assert lease_check < planned_target
    assert "lease.owner().equals(entity.getUniqueId())" in tick
    assert "lease.target()" in tick
    assert "mob.getPathfinder().stopPathfinding()" in tick[lease_check:planned_target]


def test_retarget_invalidates_the_old_navigation_intention() -> None:
    source = EVENT.read_text(encoding="utf-8")
    tick = _declaration(source, "private void tickWaveMobAi()")

    assert "waveLastPathDestinations.remove(entity.getUniqueId())" in tick
    assert "nextWavePathRequestMillis.remove(entity.getUniqueId())" in tick


def test_wave2_flank_destination_follows_the_assigned_squad_target() -> None:
    source = EVENT.read_text(encoding="utf-8")
    destination = _declaration(source, "private Location waveTacticalDestination(")

    assert "Math.floorMod(mob.getUniqueId().hashCode(), 3) == 2" not in destination


def test_live_cohort_set_is_reconciled_after_collection() -> None:
    source = EVENT.read_text(encoding="utf-8")
    plan = _declaration(source, "private Map<UUID, UUID> planWaveMobSquads(List<Player> candidates)")

    reconcile = plan.index("waveCombatCoordinator.retainCohorts(generation, cohorts.keySet())")
    plans = plan.index("for (Map.Entry<String, List<Mob>> entry : cohorts.entrySet())")
    assert reconcile < plans
