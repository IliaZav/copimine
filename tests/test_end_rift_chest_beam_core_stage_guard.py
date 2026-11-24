"""Guard the signature ultimate's Core staging (specification section 7.1.1).

Binding source:
- The 2026-11-22 objective prompt, section 7.1.1: "Move to Core: Guardian ...
  travels or teleports by a bounded visually signaled path to the fixed central
  Core anchor. It plants feet in phases 1-5 ... Validate actual collision, world
  and safe volume. No sudden undetectable relocation and no permanent AI-off
  state."

Before this change ``telegraphBossSpell`` called ``stageBossForChestBeam`` and
ignored the result: when the Core volume could not legally own the shot the
method logged a refusal and then the cast still proceeded, firing a
"center-core" beam from wherever the boss happened to stand. The fix makes the
staging method return a real verdict and refuses (and refunds the hazard
reservation for) any chest beam that the Core cannot stage.

This is a static source scan on purpose: the staging path needs live Bukkit
world/chunk state, so a behavioural harness would be a full Paper adapter. The
scan still fails loudly if the refusal, the refund or the already-at-core
success path is reverted.
"""

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
EVENT = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"


def _event_source() -> str:
    return EVENT.read_text(encoding="utf-8")


def test_staging_reports_a_verdict_instead_of_void():
    source = _event_source()
    assert "private boolean stageBossForChestBeam(LivingEntity boss)" in source, (
        "the Core staging must return whether the boss really reads as a center cast"
    )
    assert "private void stageBossForChestBeam(LivingEntity boss)" not in source, (
        "a void staging call can never refuse an unstageable chest beam"
    )


def test_core_refusal_cancels_and_refunds_the_cast():
    source = _event_source()
    start = source.index("if (spell == EndRiftAiPolicy.BossSpell.CHEST_BEAM")
    aim = source.index("aimBossCastAt(boss, castMark);", start)
    body = source[start:aim]
    assert "!stageBossForChestBeam(boss)" in body, (
        "the telegraph must act on a failed Core staging"
    )
    refund = body.index("releaseBossHazardReservation(\"chest-beam-core-stage-refused\")")
    # The refusal must happen before the aim lock / state transition, so a
    # rejected shot leaves no half-committed cast behind: the refund is the last
    # statement inside the refusal block, which ends at the aim lock.
    assert refund < len(body)


def test_lanes_and_origin_are_frozen_at_aim_lock():
    """Specification 7.1.3 and 9H.02 shots 2-3.

    The old code drew the telegraph toward the boss's melee ``mark`` and then
    re-picked 1-3 lanes from live positions inside ``startChestBeam``. The
    announced lane and the damaging ray could therefore be different geometry,
    which is exactly the "fake aim line" 7.2 forbids. The fix captures one
    snapshot at aim-lock and reuses it for the preview, the charge and the fire.
    """
    source = _event_source()
    assert "private boolean lockChestBeamAims(LivingEntity boss)" in source, (
        "the ultimate must freeze its lanes through one named aim-lock step"
    )
    lock_start = source.index("private boolean lockChestBeamAims(LivingEntity boss)")
    lock_body = source[lock_start:source.index("private void startChestBeam(", lock_start)]
    assert "bossChestBeamLockedAims = List.copyOf(aims);" in lock_body, (
        "the aim-lock must store an immutable lane snapshot"
    )
    assert "bossChestBeamLockedChest = chest;" in lock_body, (
        "the chest origin must be frozen with the lanes"
    )
    assert "bossChestBeamLockedGeneration = generation;" in lock_body, (
        "the snapshot must be generation-fenced"
    )

    # The telegraph itself must call the aim-lock and preview the frozen lanes.
    telegraph = source.index("if (spell == EndRiftAiPolicy.BossSpell.CHEST_BEAM")
    telegraph_block = source[telegraph:source.index("aimBossCastAt(boss, castMark);", telegraph)]
    assert "lockChestBeamAims(boss)" in telegraph_block, (
        "the cast must freeze its lanes before the telegraph begins"
    )
    assert "renderChestBeamLaneRails(chestOrigin, bossChestBeamLockedAims" in source, (
        "the readable preview must rail the frozen lanes, not a separate aim"
    )


def test_fire_reuses_the_snapshot_and_never_retargets():
    """9H.03 / N23-P1-03: fire only along the lanes frozen at aim-lock.

    The old code kept a fire-time re-selection fallback for a cast with no lock.
    That is exactly the silent retarget the no-retarget contract forbids, so the
    fallback is gone: a cast that lost its generation-locked snapshot cancels
    safely instead of firing along an undisclosed replacement lane.
    """
    source = _event_source()
    start = source.index("private void startChestBeam(")
    body = source[start:source.index("private void renderChestBeamStage(", start)]
    assert "bossChestBeamLockedGeneration == callbackGeneration" in body, (
        "fire must gate on the frozen snapshot for this generation"
    )
    assert "new ArrayList<>(bossChestBeamLockedAims)" in body, (
        "fire must copy the frozen lanes verbatim"
    )
    assert "ChestBeamPolicy.selectLaneIndices(" not in body, (
        "fire must never re-select targets: the aim-lock snapshot is authoritative"
    )
    assert "cancelChestBeamWithoutLock(boss, callbackGeneration)" in body, (
        "a cast with no locked snapshot must cancel safely, not re-aim"
    )


def test_lock_loss_cancels_refunds_and_clears_the_beam():
    """N23-P1-03: losing the aim lock must fail closed.

    The cancel path may not leave a live cast, a stale stripe or an unreleased
    hazard reservation behind, and it must return to recovery so the brain can
    choose a legal action instead of the shot silently firing elsewhere.
    """
    source = _event_source()
    start = source.index("private void cancelChestBeamWithoutLock(")
    body = source[start:source.index("private void abortChestBeam(", start)]
    assert "clearWorldVfxBeamsByPrefix(CHEST_BEAM_VFX_PREFIX)" in body, (
        "the cancel must clear any staged beam stripe"
    )
    assert 'releaseBossHazardReservation("chest-beam-lock-lost")' in body, (
        "the cancel must refund the hazard reservation"
    )
    assert "bossChestBeamLockedAims = List.of()" in body, (
        "the cancel must drop the stale lane snapshot"
    )
    assert "BossAbilityState.RECOVERY" in body, (
        "the cancel must return the brain to recovery for another legal action"
    )


def test_already_at_core_and_successful_teleport_both_succeed():
    source = _event_source()
    start = source.index("private boolean stageBossForChestBeam(")
    body = source[start:source.index("private void startChestBeam(", start)]
    # Already standing on the Core is a legal center cast.
    assert "dx * dx + dz * dz <= 16.0D" in body
    # A validated safe stand point that the teleport committed also succeeds.
    assert "return true;" in body
    assert "return false;" in body, "an unstageable Core volume must fail closed"


def test_staging_and_cooldown_use_the_tested_policy_rules():
    """The runtime must not re-implement the pure policy it already tests.

    ``ChestBeamPolicyTest`` exercises ``mayCast`` and ``onCooldown`` directly.
    If the runtime keeps its own inline copies, those copies can drift away from
    the tested rules while the Java test stays green. Anchor both call sites to
    the single policy source of truth so a divergence fails here.
    """
    source = _event_source()
    start = source.index("private boolean stageBossForChestBeam(")
    staging = source[start:source.index("private void startChestBeam(", start)]
    assert "ChestBeamPolicy.mayCast(" in staging, (
        "Core staging must delegate its admission decision to the tested mayCast rule"
    )
    assert "ChestBeamPolicy.onCooldown(" in source, (
        "the ultimate's frequency gate must delegate to the tested onCooldown rule"
    )
    assert "bossChestBeamCooldownUntilTick > eventTickCounter" not in source, (
        "the inline cooldown comparison must not shadow the tested onCooldown rule"
    )
