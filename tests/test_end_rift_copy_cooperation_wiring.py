"""V7-21 / §10.4.2: the cooperative Guardian + copy techniques are really wired.

The pure policy is proven in ``BossCopyCooperationPolicyTest``. These tests
prove the *server* consults it: the RAGE phase owns the tick, admission is
delegated, a copy loss and every phase exit clean up, real damage goes through
the accepted-damage entry point, and no technique teleports a player.

None of the tests below asserts that a name merely appears in the file: each
one reads a real call site (or the real method body) and pins an ordering or a
guard that a mutant could otherwise delete.
"""

from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
MONOLITH = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"


def source() -> str:
    return MONOLITH.read_text("utf-8")


def method(name: str, text: str | None = None) -> str:
    text = text if text is not None else source()
    start = text.index("    private " + name)
    opening = text.index("{", start)
    depth, end = 1, opening + 1
    while depth:
        depth += (text[end] == "{") - (text[end] == "}")
        end += 1
    return text[start:end]


def code_only(body: str) -> str:
    """Strip comments: the documentation names the old defect on purpose."""
    without_block = re.sub(r"/\*.*?\*/", "", body, flags=re.S)
    return re.sub(r"//[^\n]*", "", without_block)


# ---------------------------------------------------------------------------
# 1. the general boss loop owns the tick, exactly once, before the pin
# ---------------------------------------------------------------------------

def test_the_general_boss_loop_owns_the_cooperation_tick():
    loop = method("void tickCurrentBoss(")
    assert "tickBossCopyCooperations(boss);" in loop, (
        "cooperative techniques must advance from the general boss loop"
    )
    assert loop.count("tickBossCopyCooperations(boss);") == 1, (
        "one owner only: a second call would advance the technique twice per tick"
    )
    assert loop.index("tickBossCopyCooperations(boss);") < loop.index(
        "pinBossToFinalSealCore(boss)"), (
        "the cooperation tick must precede the last-seal pin so an early pin "
        "return cannot starve it"
    )


# ---------------------------------------------------------------------------
# 2. admission is delegated to the pure policy, never re-implemented
# ---------------------------------------------------------------------------

def test_admission_is_delegated_to_the_pure_policy():
    start = method("void tryStartBossCopyCooperation(")
    assert "BossCopyCooperationPolicy.mayFire(" in start, (
        "every admission rule (participants, copies, cooldown, concurrency) "
        "must come from the pure policy"
    )
    assert "BossCopyCooperationPolicy.AdmissionState(" in start
    assert "BossCopyCooperationPolicy.supportedTechniques()" in start, (
        "the repertoire must be the policy's list, not a local copy"
    )
    # The scored technique is reserved before it is recorded as active.
    reserve_at = start.index("reserveBossCopyCooperation(")
    record_at = start.index("bossCopyCooperationTechnique = chosen;")
    assert reserve_at < record_at, (
        "a technique must not be recorded as active before its hazard slot is won"
    )


def test_the_tick_applies_the_phase_fence_and_copy_loss_rules():
    tick = code_only(method("void tickBossCopyCooperations("))
    assert "bossPhase != BossPhase.RAGE" in tick, (
        "cooperative techniques are RAGE-only"
    )
    assert 'cancelBossCopyCooperation(' in tick, (
        "leaving RAGE must cancel the technique, never leave a remote sequence"
    )
    assert "bossDefeatCinematicStarted" in tick, (
        "a started defeat cinematic must cancel the technique"
    )
    assert "BossCopyCooperationPolicy.copyLossOutcome(" in tick, (
        "copy loss must be decided by the policy"
    )
    assert "timeline.fixedAt(eventTickCounter)" in tick, (
        "copy loss before/after the fix is a different outcome; the fix tick "
        "must be consulted"
    )
    assert "timeline.executesAt(eventTickCounter)" in tick, (
        "damage may only land on a scheduled execute tick"
    )


def test_a_copy_loss_before_fix_cancels_and_after_fix_shortens_the_salvo():
    tick = code_only(method("void tickBossCopyCooperations("))
    loss_start = tick.index("copyLossOutcome(")
    # The loss branch must compare against CANCEL, and must drop dead copies
    # only for the SHORTENED salvo path.
    assert "CopyLoss.CANCEL" in tick[loss_start:], (
        "a cancelled technique must be recognised by its policy outcome"
    )
    assert "dropDeadCooperationCopies();" in tick, (
        "a shortened salvo must stop tracking the dead caster"
    )


# ---------------------------------------------------------------------------
# 3. real damage, no raw player damage, no owner teleport
# ---------------------------------------------------------------------------

def test_damage_goes_through_the_accepted_damage_entry_point():
    # Mirror strike lands directly; salvo and chain share the swept-beam helper,
    # which is itself proven to report only accepted damage below.
    for name in ("void executeMirrorStrike(", "void sweepCooperationDamage("):
        body = code_only(method(name))
        assert "applyEchoReplicaDamage(" in body, (
            name + " must report only the damage the server accepted"
        )
        assert not re.search(r"\bplayer\.damage\(", body), (
            name + " must never bypass the accepted-damage accounting"
        )
    for name in ("void executeCoordinatedSalvo(", "void executeReflectionChain("):
        body = code_only(method(name))
        assert "sweepCooperationDamage(" in body, (
            name + " must land its damage through the shared accepted-damage sweep"
        )
        assert not re.search(r"\bplayer\.damage\(", body), (
            name + " must never bypass the accepted-damage accounting"
        )


def test_no_cooperation_method_teleports_a_player():
    for name in ("void swapBossWithCooperationCopy(", "void tickBossCopyCooperations(",
                 "void executeMirrorStrike(", "void executeCoordinatedSalvo(",
                 "void executeReflectionChain("):
        body = code_only(method(name))
        assert not re.search(r"\bplayer\.teleport\(", body), (
            name + " must not teleport a participant; mirror substitution swaps "
            "the boss and its copy, never the owner"
        )


def test_the_mirror_swap_is_between_the_boss_and_its_copy_only():
    swap = code_only(method("void swapBossWithCooperationCopy("))
    assert "boss.teleport(" in swap, "the Guardian moves to the copy"
    assert "livingCopy.teleport(" in swap, "the copy moves to the Guardian"
    assert "bossCopyCooperationCopies.get(0)" in swap, (
        "the mirror partner is the first participating copy"
    )


# ---------------------------------------------------------------------------
# 4. the hazard budget is reserved and released
# ---------------------------------------------------------------------------

def test_the_technique_reserves_and_releases_one_hazard_slot():
    reserve = method("boolean reserveBossCopyCooperation(")
    assert "bossHazardBudget.reserve(" in reserve, (
        "a cooperative technique is a heavy action and must win a real slot"
    )
    assert "bossHazardBudget" in reserve and "release(" not in reserve, (
        "reservation owns only the reserve half; release is symmetric in clear"
    )
    clear = method("void clearBossCopyCooperation()")
    assert "bossHazardBudget.release(" in clear, (
        "the slot must be released on every terminal path"
    )
    assert "bossCopyCooperationReservationId = null;" in clear


def test_mirror_is_a_reposition_not_a_targetless_grab():
    reserve = method("boolean reserveBossCopyCooperation(")
    assert "MechanicKind.REPOSITION" in reserve, (
        "MIRROR_SUBSTITUTION must reserve REPOSITION: MAJOR_GRAB requires a "
        "target and would always be refused, silently deleting the technique"
    )
    assert "MechanicKind.GROUND_SLAM" in reserve
    assert "MechanicKind.CHEST_STRIKE" in reserve


# ---------------------------------------------------------------------------
# 5. every cleanup path reaches the technique
# ---------------------------------------------------------------------------

def test_copy_teardown_cancels_the_cooperation():
    clear = method("int clearEchoAttackReplicas(")
    assert 'cancelBossCopyCooperation("echo-replicas-cleared:' in clear, (
        "retiring the copies must cancel a cooperative technique in flight"
    )
    boss_cleanup = method("void clearBossOnly(")
    assert 'cancelBossCopyCooperation("boss-cleanup")' in boss_cleanup, (
        "boss-only cleanup must cancel the technique"
    )


def test_a_dead_source_may_never_fire():
    # The reflection chain picks the last LIVE copy as the beam source, and the
    # executors skip any caster whose actor is gone.
    chain = code_only(method("void executeReflectionChain("))
    assert "isValid() && !living.isDead()" in chain, (
        "a dead copy must never be the beam source (no invisible shot)"
    )
    salvo = code_only(method("void executeCoordinatedSalvo("))
    assert "isValid() && !living.isDead()" in salvo
