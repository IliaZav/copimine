from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def test_web_support_spider_keeps_ranged_path_when_players_are_close() -> None:
    source = (ROOT / "copimine-end-event" / "src" / "me" / "copimine" / "endevent" / "CopiMineEndEvent.java").read_text(encoding="utf-8")
    path_start = source.index("private void maintainWaveMobPath(Mob mob, Player target, String kind, long now)")
    path_end = source.index("private boolean requestBoundedCombatMovement(", path_start)
    path_logic = source[path_start:path_end]

    assert "!isEliteSpiderWebSupport(mob, kind)" in path_logic, (
        "the generic four-block native melee approach must not pull a WEB_SUPPORT spider into melee range"
    )


def test_web_support_spider_uses_a_stable_bounded_ring_around_its_role_anchor() -> None:
    source = (ROOT / "copimine-end-event" / "src" / "me" / "copimine" / "endevent" / "CopiMineEndEvent.java").read_text(encoding="utf-8")
    destination_start = source.index("private Location waveTacticalDestination(Mob mob, Player target, String kind, long now)")
    destination_end = source.index("private Location waveMobObjectiveAnchor(", destination_start)
    destination_logic = source[destination_start:destination_end]

    assert "if (isEliteSpiderWebSupport(mob, kind))" in destination_logic
    assert "stableCombatRingLocation(supportAnchor, mob" in destination_logic
    assert "EliteAbilityPolicy.SPIDER_SUPPORT_MIN_RANGE_BLOCKS" in destination_logic
    assert "EliteAbilityPolicy.SPIDER_SUPPORT_MAX_RANGE_BLOCKS" in destination_logic


def test_wave3_gate_assignment_and_wave5_shelter_keep_spider_support_intent() -> None:
    source = (ROOT / "copimine-end-event" / "src" / "me" / "copimine" / "endevent" / "CopiMineEndEvent.java").read_text(encoding="utf-8")
    destination_start = source.index("private Location waveTacticalDestination(Mob mob, Player target, String kind, long now)")
    destination_end = source.index("private Location waveMobObjectiveAnchor(", destination_start)
    destination_logic = source[destination_start:destination_end]

    assert "wave3GateMobTargets.containsKey(mob.getUniqueId())" in destination_logic
    assert "? objectiveAnchor : target.getLocation()" in destination_logic
    shelter_start = destination_logic.index("if (readInt(mob, keyWave, 0) == 5 && blackFogPhase == BlackFogPhase.SAFE")
    shelter_end = destination_logic.index("preferred = realitySplitCombatPreferred", shelter_start)
    shelter_logic = destination_logic[shelter_start:shelter_end]
    assert "boolean spiderSupport = isEliteSpiderWebSupport(mob, kind)" in shelter_logic
    assert "spiderSupport ? EliteAbilityPolicy.SPIDER_SUPPORT_MIN_RANGE_BLOCKS : 3.75D" in shelter_logic
    assert "spiderSupport ? EliteAbilityPolicy.SPIDER_SUPPORT_MAX_RANGE_BLOCKS : 5.0D" in shelter_logic


def test_web_support_spiders_do_not_activate_vanilla_player_pursuit() -> None:
    source = (ROOT / "copimine-end-event" / "src" / "me" / "copimine" / "endevent" / "CopiMineEndEvent.java").read_text(encoding="utf-8")
    ai_start = source.index("private void tickWaveMobAi()")
    ai_end = source.index("private Map<UUID, UUID> planWaveMobSquads(", ai_start)
    ai_logic = source[ai_start:ai_end]
    spell_start = source.index("private void tickMiniBosses()")
    spell_end = source.index("private void telegraphMiniBossSpell(", spell_start)
    spell_logic = source[spell_start:spell_end]

    assert "if (supportSpider || targetChanged) mob.setTarget(supportSpider ? null : target)" in ai_logic
    assert "usesWaveCombatCoordination(entity) && !supportSpider" in ai_logic
    assert "? mostRecentWaveTarget(entity.getUniqueId()) : previousTargetId" in ai_logic
    assert "!isEliteSpiderWebSupport(mob, readString(mob, keyKind))" in spell_logic


def test_web_support_spider_does_not_reacquire_player_target_during_spell_recovery() -> None:
    source = (ROOT / "copimine-end-event" / "src" / "me" / "copimine" / "endevent" / "CopiMineEndEvent.java").read_text(encoding="utf-8")
    ai_start = source.index("private void tickWaveMobAi()")
    ai_end = source.index("private Map<UUID, UUID> planWaveMobSquads(", ai_start)
    ai_logic = source[ai_start:ai_end]
    recovery_start = ai_logic.index("waveCombatCoordinator.recovering(entity.getUniqueId(), generation, eventTickCounter)")
    recovery_end = ai_logic.index("List<Player> entityCandidates", recovery_start)
    recovery_logic = ai_logic[recovery_start:recovery_end]

    assert "boolean supportSpider = isEliteSpiderWebSupport(mob, kind)" in recovery_logic
    assert "if (supportSpider) mob.setTarget(null)" in recovery_logic
    assert "if (!supportSpider && (!(mob.getTarget() instanceof Player current)" in recovery_logic


def test_support_spider_web_shot_uses_its_current_squad_assignment() -> None:
    source = (ROOT / "copimine-end-event" / "src" / "me" / "copimine" / "endevent" / "CopiMineEndEvent.java").read_text(encoding="utf-8")
    spell_start = source.index("private void tickMiniBosses()")
    spell_end = source.index("private void telegraphMiniBossSpell(", spell_start)
    spell_logic = source[spell_start:spell_end]

    assert "mostRecentWaveTarget(entity.getUniqueId())" in spell_logic
    assert "Player assignedSupportTarget" in spell_logic
    assert "isMiniBossTargetAllowed(entity, assignedSupportTarget)" in spell_logic
    assert "usesWaveCombatCoordination(entity) && current != null" in spell_logic


def test_web_support_spider_uses_a_tight_waypoint_arrival_tolerance() -> None:
    source = (ROOT / "copimine-end-event" / "src" / "me" / "copimine" / "endevent" / "CopiMineEndEvent.java").read_text(encoding="utf-8")
    path_start = source.index("private void maintainWaveMobPath(Mob mob, Player target, String kind, long now)")
    path_end = source.index("private boolean requestBoundedCombatMovement(", path_start)
    path_logic = source[path_start:path_end]

    assert "double arrivalToleranceBlocks = isEliteSpiderWebSupport(mob, kind)" in path_logic
    assert "EliteAbilityPolicy.SPIDER_SUPPORT_ARRIVAL_TOLERANCE_BLOCKS" in path_logic
    assert "arrivalToleranceBlocks * arrivalToleranceBlocks" in path_logic
