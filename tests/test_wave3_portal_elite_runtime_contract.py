"""Regression contract for the scheduled second-portal Rift Giant encounter."""
from pathlib import Path
import re


ROOT = Path(__file__).resolve().parents[1]
EVENT = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"
OFFICIAL_RUNNER = ROOT / "tests/RunEndRiftOfficialTwoPlayerLive.ps1"


def _method(source: str, name: str) -> str:
    declaration = re.search(
        rf"\b(?:private|public)\s+(?:static\s+)?[\w.<>, ?\[\]]+\s+{re.escape(name)}\s*\([^;]*?\)\s*\{{",
        source,
        re.DOTALL,
    )
    assert declaration, f"missing required production method: {name}"
    opening = source.index("{", declaration.start())
    depth = 0
    for index in range(opening, len(source)):
        if source[index] == "{":
            depth += 1
        elif source[index] == "}":
            depth -= 1
            if depth == 0:
                return source[declaration.start() : index + 1]
    raise AssertionError(f"unterminated production method: {name}")


def test_real_wave3_tick_schedules_the_elite_even_after_portal_two_closes():
    source = EVENT.read_text(encoding="utf-8")
    scheduler = _method(source, "tickWave3PortalSpawns")
    elite_tick = _method(source, "tickWave3PortalElite")

    call = scheduler.index("tickWave3PortalElite(now)")
    closed_portal_return = scheduler.index("if (active < 0)")
    assert call < closed_portal_return, (
        "the due/fallback decision must still run while portals are collapsing or closed"
    )
    assert "Wave3PortalElitePolicy.decide(" in elite_tick
    assert "PortalProgress.NOT_REACHED" in elite_tick
    assert "PortalProgress.OPEN" in elite_tick
    assert "PortalProgress.CAPTURED" in elite_tick
    assert "WAVE3_PORTAL_ELITE_DELAY_MILLIS" in elite_tick
    assert "wave3PortalEliteSpawned" in elite_tick
    assert "spawnWaveThreePortalElite" in elite_tick


def test_rift_giant_is_owned_scaled_and_provenanced_at_portal_two():
    source = EVENT.read_text(encoding="utf-8")
    spawn = _method(source, "spawnWaveThreePortalElite")
    giant_spawn = _method(source, "spawnRiftGiantEntity")

    assert "safePortalSpawnLocation(portal, portalIndex" in spawn
    assert "keyWaveThreePortalIndex" in spawn
    assert "keyWaveThreePortalElite" in spawn
    assert "EntityType.ZOMBIE" in giant_spawn, "Rift Giant must use the supported zombie base"
    assert "EVENT_KIND_ELITE" in giant_spawn
    assert "keyRiftGiant" in giant_spawn
    assert "Attribute.GENERIC_SCALE" in giant_spawn
    assert "registerOwnedEntity(giant)" in giant_spawn
    assert "assignCombatTactic(giant" in giant_spawn

    elite_tick = _method(source, "tickWave3PortalElite")
    assert "Wave3PortalElitePolicy.SECOND_PORTAL_INDEX" in elite_tick
    assert "portals.get(decision.portalIndex())" in elite_tick
    assert "PressureBudgetController.profileForPlayers(currentWaveScalePlayers).activePressure()" in elite_tick


def test_new_wave3_attempt_resets_its_single_elite_schedule():
    source = EVENT.read_text(encoding="utf-8")
    prepare = _method(source, "currentPrepareSequentialPortals")
    assert "wave3PortalEliteDueAtMillis = 0L" in prepare
    assert "wave3PortalEliteSpawned = false" in prepare


def test_wave3_cannot_complete_before_the_required_giant_is_admitted():
    source = EVENT.read_text(encoding="utf-8")
    tick = _method(source, "tickCurrentObjective")
    assert re.search(
        r"objectiveWave\s*!=\s*3\s*\|\|\s*wave3PortalEliteSpawned",
        tick,
    ), "the portal objective must retain its completion fence until the elite has spawned"


def test_giant_ai_uses_bounded_strike_summon_and_aura_policies():
    source = EVENT.read_text(encoding="utf-8")
    mob_tick = _method(source, "tickWaveMobAi")
    assert "tickRiftGiantAura(giant, now)" in mob_tick
    assert "tickRiftGiantOffense(giant, target, now)" in mob_tick

    spawn = _method(source, "spawnRiftGiantEntity")
    assert "Attribute.GENERIC_SCALE" in spawn
    assert "Attribute.GENERIC_KNOCKBACK_RESISTANCE" in spawn
    assert "Attribute.GENERIC_ATTACK_KNOCKBACK" in spawn
    assert "movement.setBaseValue(0.18D)" in spawn
    assert "giant.setAI(true)" in spawn

    offense = _method(source, "tickRiftGiantOffense")
    summons = _method(source, "tickRiftGiantSummon")
    aura = _method(source, "tickRiftGiantAura")
    assert "RiftGiantPolicy.shouldBeginHeavyStrike" in offense
    assert "RiftGiantPolicy.shouldHitAfterTelegraph" in offense
    assert "RiftGiantPolicy.admittedSummons" in summons
    assert "RiftGiantPolicy.isAuraRecipient" in aura
    assert "PotionEffectType.REGENERATION" in aura
    assert "PotionEffectType.RESISTANCE" in aura


def test_giant_death_clears_its_aura_leases_summons_and_pending_casts():
    source = EVENT.read_text(encoding="utf-8")
    removal = _method(source, "onOwnedEntityRemove")
    cleanup = _method(source, "cleanupRiftGiantRuntime")
    assert "cleanupRiftGiantRuntime(entity" in removal
    assert "riftGiantStrikeCharges.remove(giantId)" in cleanup
    assert "riftGiantSummonCharges.remove(giantId)" in cleanup
    assert "PotionEffectType.REGENERATION" in cleanup
    assert "PotionEffectType.RESISTANCE" in cleanup
    assert "unregisterOwnedEntity(summonId" in cleanup


def test_official_two_player_run_requires_the_second_portal_elite_receipt():
    runner = OFFICIAL_RUNNER.read_text(encoding="utf-8")
    assert re.search(
        r"Wait-Log\s+-AfterOffset\s+\$portalOffset[\s\S]*?-Pattern\s+'WAVE3_PORTAL_ELITE_SPAWNED.*portal=1.*'",
        runner,
    ), "the normal bot route must require the second-portal elite spawn receipt"
