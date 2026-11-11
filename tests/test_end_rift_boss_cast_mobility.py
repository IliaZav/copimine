"""M5: a boss cast must never leave the Guardian immobile forever.

The 11-November master contract (sections 10.2 and 10.3) supersedes the old
"stop pathfinding for the whole cast" behaviour.  A mobile cast keeps legal
locomotion and keeps evaluating targets; only the authored heavy animation
segment of a stationary cast may lock the body.  Every release must restore
navigation and gravity, and a stale cast flag must never be able to hold the
boss in place.

These probes compile the real domain policy and assert behaviour, not source
strings.
"""

from pathlib import Path
import os
import shutil
import subprocess

import pytest


ROOT = Path(__file__).resolve().parents[1]
DOMAIN = ROOT / "copimine-end-event" / "src" / "me" / "copimine" / "endevent" / "domain"
EVENT = ROOT / "copimine-end-event" / "src" / "me" / "copimine" / "endevent" / "CopiMineEndEvent.java"


def _tool(name):
    java_home = os.environ.get("JAVA_HOME")
    executable = name + (".exe" if os.name == "nt" else "")
    if java_home:
        candidate = Path(java_home) / "bin" / executable
        if candidate.is_file():
            return str(candidate)
    found = shutil.which(name)
    assert found, f"{name} is required to compile the boss cast-mobility probe"
    return found


def _run_probe(tmp_path, name, probe_source, domain_types=()):
    probe = tmp_path / f"{name}.java"
    probe.write_text(probe_source, encoding="utf-8")
    compiled = subprocess.run(
        [_tool("javac"), "-encoding", "UTF-8", "-d", str(tmp_path),
         *(str(DOMAIN / (type_name + ".java")) for type_name in domain_types),
         str(probe)],
        capture_output=True, text=True)
    assert compiled.returncode == 0, compiled.stdout + compiled.stderr
    executed = subprocess.run(
        [_tool("java"), "-cp", str(tmp_path), name],
        capture_output=True, text=True)
    assert executed.returncode == 0, executed.stdout + executed.stderr
    return executed.stdout


CAST_MOBILITY_PROBE = r"""
import me.copimine.endevent.domain.*;

public class BossCastMobilityProbe {
    static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        // ---- Mobile casts keep locomotion -------------------------------
        for (EndRiftAiPolicy.BossSpell spell : new EndRiftAiPolicy.BossSpell[] {
                EndRiftAiPolicy.BossSpell.RIFT_PROJECTILE,
                EndRiftAiPolicy.BossSpell.RIFT_ARROWS,
                EndRiftAiPolicy.BossSpell.VOID_BLAST,
                EndRiftAiPolicy.BossSpell.VOID_MARK,
                EndRiftAiPolicy.BossSpell.BOSS_FIREBALL,
                EndRiftAiPolicy.BossSpell.SUMMON_SERVANTS,
                EndRiftAiPolicy.BossSpell.SMALL_KAGUNE }) {
            check(BossCastMobilityPolicy.allowsLocomotion(spell),
                    "mobile cast must allow legal locomotion: " + spell);
            check(BossCastMobilityPolicy.allowsTargetEvaluation(spell),
                    "mobile cast must keep target evaluation: " + spell);
            check(!BossCastMobilityPolicy.forbidsNativeAi(spell),
                    "mobile cast must not disable native AI: " + spell);
        }

        // ---- Stationary casts lock only the authored body segment -------
        for (EndRiftAiPolicy.BossSpell spell : new EndRiftAiPolicy.BossSpell[] {
                EndRiftAiPolicy.BossSpell.ARENA_INFERNO,
                EndRiftAiPolicy.BossSpell.FINAL_STRIKE,
                EndRiftAiPolicy.BossSpell.AMBUSH_GRAB,
                EndRiftAiPolicy.BossSpell.MELEE_SWIPE,
                EndRiftAiPolicy.BossSpell.CHEST_STRIKE,
                EndRiftAiPolicy.BossSpell.GROUND_SLAM }) {
            check(!BossCastMobilityPolicy.allowsLocomotion(spell),
                    "authored heavy cast must not drift while animating: " + spell);
            int lock = BossCastMobilityPolicy.lockedTicks(spell);
            check(lock > 0, "stationary cast needs a bounded lock window: " + spell);
            check(lock <= BossCastMobilityPolicy.MAX_STATIONARY_LOCK_TICKS,
                    "stationary lock must stay bounded: " + spell + " = " + lock);
        }

        // A heavy lock is a window of the animation, never the whole clip:
        // the authored recovery tail belongs to the movement controller.
        check(BossCastMobilityPolicy.lockedTicks(EndRiftAiPolicy.BossSpell.GROUND_SLAM)
                        < BossContactStrikePolicy.clipTicks(EndRiftAiPolicy.BossSpell.GROUND_SLAM),
                "ground slam lock must end inside its authored clip");
        check(BossCastMobilityPolicy.lockedTicks(EndRiftAiPolicy.BossSpell.MELEE_SWIPE)
                        < BossContactStrikePolicy.clipTicks(EndRiftAiPolicy.BossSpell.MELEE_SWIPE),
                "melee swipe lock must end inside its authored clip");

        // A stationary cast still keeps evaluating a target and (after its
        // authored segment) is free to move again.
        check(BossCastMobilityPolicy.allowsTargetEvaluation(EndRiftAiPolicy.BossSpell.GROUND_SLAM),
                "a grounded cast must still evaluate targets");
        check(BossCastMobilityPolicy.allowsLocomotionAfterLock(EndRiftAiPolicy.BossSpell.GROUND_SLAM),
                "a grounded cast must move again after its authored segment");

        // ---- Unknown input fails closed to "no lock" --------------------
        check(!BossCastMobilityPolicy.forbidsNativeAi(null),
                "a null ability must never disable native AI");
        check(BossCastMobilityPolicy.lockedTicks(null) == 0,
                "a null ability must lock nothing");
        check(BossCastMobilityPolicy.allowsLocomotion(null),
                "a null ability must leave locomotion untouched");

        // ---- Recovery always restores navigation and gravity ------------
        BossCastMobilityPolicy.Recovery recovery = BossCastMobilityPolicy.recovery();
        check(recovery.restoreAi(), "recovery must restore native AI");
        check(recovery.restoreAware(), "recovery must restore awareness");
        check(recovery.restoreGravity(), "recovery must restore gravity");
        check(recovery.clearPath(), "recovery must clear a stale cast path");
        check(recovery.restoreNavigation(), "recovery must restore navigation");
        check(recovery.resumeTargetEvaluation(), "recovery must resume target evaluation");

        // ---- A stale cast flag can never pin the boss forever -----------
        // The recovery decision is a pure function of the clock, so a lost
        // callback cannot hold the body: once the bounded budget is spent the
        // answer is "recover", not "keep holding".
        // The lock window is half-open [0, lockedTicks): the last locked tick
        // holds, and the very next tick is already the movement controller's.
        check(BossCastMobilityPolicy.shouldHold(EndRiftAiPolicy.BossSpell.GROUND_SLAM,
                        0L, BossCastMobilityPolicy.lockedTicks(
                                EndRiftAiPolicy.BossSpell.GROUND_SLAM) - 1L),
                "the lock must hold for every tick of its window");
        check(!BossCastMobilityPolicy.shouldHold(EndRiftAiPolicy.BossSpell.GROUND_SLAM,
                        0L, BossCastMobilityPolicy.lockedTicks(
                                EndRiftAiPolicy.BossSpell.GROUND_SLAM)),
                "the locked window must end on time, never extend");
        check(!BossCastMobilityPolicy.shouldHold(
                        EndRiftAiPolicy.BossSpell.GROUND_SLAM, 0L, -5L),
                "a negative elapsed time must not hold the boss");
        check(!BossCastMobilityPolicy.shouldHold((EndRiftAiPolicy.BossSpell) null, 0L, 1L),
                "a null ability can never hold the boss");
        check(!BossCastMobilityPolicy.shouldHold(EndRiftAiPolicy.BossSpell.RIFT_PROJECTILE, 0L, 1L),
                "a mobile cast must never hold the boss in place");

        // A stale flag older than the whole budget must self-heal.
        check(BossCastMobilityPolicy.stale(1000L, 100_000L),
                "an ancient cast timestamp is stale and must be released");
        check(!BossCastMobilityPolicy.stale(100_000L, 100_500L),
                "a fresh cast timestamp is not stale");

        System.out.println("BossCastMobilityProbe OK");
    }
}
"""


def test_mobile_and_stationary_casts_are_distinct(tmp_path):
    """Contract 10.2: mobile casts allow locomotion; heavy casts lock a segment."""
    output = _run_probe(tmp_path, "BossCastMobilityProbe", CAST_MOBILITY_PROBE,
                        ("BossCastMobilityPolicy", "EndRiftAiPolicy", "BossContactStrikePolicy",
                         "BossPhase"))
    assert "BossCastMobilityProbe OK" in output


def _method_body(source, signature):
    """Extract one method body, skipping its javadoc.

    The javadoc is stripped deliberately: it narrates the superseded
    setAI(false) behaviour, and a prose mention must never be mistaken for
    the code the contract forbids.
    """
    start = source.index(signature)
    opening = source.index("{", start)
    depth = 0
    for cursor in range(opening, len(source)):
        depth += (source[cursor] == "{") - (source[cursor] == "}")
        if depth == 0:
            return source[opening : cursor + 1]
    raise AssertionError(f"unterminated method: {signature}")


def test_hold_boss_cast_position_no_longer_disables_native_ai():
    """Contract 10.2: the adapter must not setAI(false)/setAware(false) for a cast."""
    source = EVENT.read_text(encoding="utf-8")
    # The no-arg overload only delegates; the behaviour lives in the
    # spell-aware overload, which is the body the contract constrains.
    body = _method_body(source, 'private void holdBossCastPosition('
                       'LivingEntity boss, EndRiftAiPolicy.BossSpell spell)')

    # SUPERSEDED: the old body stopped pathfinding, disabled AI, disabled
    # awareness and zeroed horizontal velocity for the whole cast. The 11-Nov
    # contract keeps locomotion for mobile casts, so the adapter must now route
    # through the mobility policy instead of unilaterally stopping the body.
    assert "BossCastMobilityPolicy" in body, (
        "holdBossCastPosition must consult BossCastMobilityPolicy")
    # Native AI/awareness may only ever be suppressed behind the policy
    # guard, never unconditionally: forbidsNativeAi currently returns false
    # for every spell, so this branch is dead by construction.
    assert "forbidsNativeAi(spell)" in body, (
        "any AI suppression must be gated by the mobility policy")
    guarded = body[body.index("forbidsNativeAi(spell)"):]
    assert "setAI(" not in body[:body.index("forbidsNativeAi(spell)")], (
        "a cast must not disable native AI outside the policy guard")
    # The mobile branch must be released before any locomotion-suppressing call.
    assert body.index("allowsLocomotion") < body.index("stopPathfinding"), (
        "a mobile cast must be released before the path is ever stopped")


def test_cast_recovery_restores_navigation_and_gravity():
    """Contract 10.2: recovery restores navigation; no stale flag survives."""
    source = EVENT.read_text(encoding="utf-8")
    assert "BossCastMobilityPolicy.recovery()" in source, (
        "the adapter must apply the policy recovery contract, not ad-hoc flags")
    # The reconciled/expired cast path is the place where a lost callback is
    # repaired. Both terminal cast states must hand the body back.
    body = _method_body(source, "private void reconcileBossCastProjection(")
    assert "releaseBossCastMobility(" in body, (
        "an expired cast must restore navigation, awareness and gravity")
    # Expiry, not just clean recovery, must restore: a lost callback is exactly
    # the case that used to freeze the Guardian forever.
    expired = body[:body.index("if (before == BossAbilityState.RECOVERY")]
    assert "releaseBossCastMobility(" in expired, (
        "the expired-cast branch must also release the body")
    # The literal recovery contract is applied by the shared release helper,
    # so the reconcile path only has to call it from both terminal branches.
    helper = _method_body(source, "private void releaseBossCastMobility(")
    assert "BossCastMobilityPolicy.recovery()" in helper, (
        "the release helper must apply the policy recovery contract")
    assert "setGravity(" in helper, (
        "recovery must always restore gravity")
