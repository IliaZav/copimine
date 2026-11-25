import me.copimine.endevent.domain.WardenV5CombatPolicy;
import me.copimine.endevent.domain.WardenV5CombatPolicy.Action;
import me.copimine.endevent.domain.WardenV5CombatPolicy.Step;

import java.util.ArrayList;
import java.util.List;

/**
 * V5.1 Warden contract: exact 5x4 sweep volume, wall-piercing radius-6 sphere,
 * quiet-before-lock footstep counterplay and fair scheduling between them.
 */
public final class WardenV5CombatPolicyTest {
    public static void main(String[] args) {
        legacyIdsMapOntoFinalActions();
        sweepIsExactlyFiveByFour();
        sweepEffectsAreBounded();
        echoIsASelfCentredWallPiercingSphere();
        sculkResonanceIsACounterableTrace();
        schedulingNeverFeedsAStunIntoTheBurst();
        System.out.println("WardenV5CombatPolicyTest OK");
    }

    private static void legacyIdsMapOntoFinalActions() {
        check(WardenV5CombatPolicy.action("WARDEN_WIDE_STRIKE") == Action.FRONTAL_SWEEP,
                "the wide strike becomes the committed 5x4 sweep");
        check(WardenV5CombatPolicy.action("WARDEN_SLAM") == Action.ECHO_SPACE_EXPLOSION,
                "the old radial slam is reconciled with the final self burst");
        check(WardenV5CombatPolicy.action("WAVE7_WARDEN_SCULK_RESONANCE") == Action.SCULK_RESONANCE,
                "the prefixed Sculk id maps onto the trace");
        check(WardenV5CombatPolicy.action("WARDEN_NARROW_STRIKE") == Action.NATIVE,
                "native strikes stay native");
        check(WardenV5CombatPolicy.action("WARDEN_SONIC_LUNGE") == Action.NATIVE,
                "the existing lunge is not duplicated as a custom major");
    }

    private static void sweepIsExactlyFiveByFour() {
        check(WardenV5CombatPolicy.SWEEP_WIDTH_BLOCKS == 5.0D,
                "the sweep is exactly five blocks across");
        check(WardenV5CombatPolicy.SWEEP_FORWARD_BLOCKS == 4.0D,
                "the sweep reaches exactly four blocks forward");
        check(WardenV5CombatPolicy.sweepHits(0.5D, 0.0D, 0.0D),
                "a player at the Warden's feet in front is hit");
        check(WardenV5CombatPolicy.sweepHits(4.0D, 2.5D, 1.4D),
                "the true forward and lateral edges still hit");
        check(!WardenV5CombatPolicy.sweepHits(4.05D, 0.0D, 0.0D),
                "one step beyond four blocks forward is a miss");
        check(!WardenV5CombatPolicy.sweepHits(2.0D, 2.55D, 0.0D),
                "one step outside the five-block width is a miss");
        check(!WardenV5CombatPolicy.sweepHits(-0.5D, 0.0D, 0.0D),
                "a player behind the Warden is a miss");
        check(!WardenV5CombatPolicy.sweepHits(2.0D, 0.0D, 2.6D),
                "a player on an unreachable ledge is a miss");
        check(WardenV5CombatPolicy.SWEEP_PREP_TICKS >= 14
                        && WardenV5CombatPolicy.SWEEP_PREP_TICKS <= 20,
                "the sweep preparation is 0.7..1.0 s");
    }

    private static void sweepEffectsAreBounded() {
        check(WardenV5CombatPolicy.SWEEP_STUN_TICKS == 20,
                "an accepted hit yields exactly twenty ticks of stun");
        check(WardenV5CombatPolicy.SWEEP_SLOW_TICKS == 60,
                "an accepted hit yields exactly sixty ticks of Slowness");
        check(WardenV5CombatPolicy.SWEEP_STUN_SLOW_AMPLIFIER
                        > WardenV5CombatPolicy.SWEEP_SLOW_AMPLIFIER,
                "the stun lock is stronger than the later slow");
        check(WardenV5CombatPolicy.SWEEP_HIT_WINDOW_TICKS <= 4,
                "the one-hit window is short enough to deduplicate a tick");
        check(WardenV5CombatPolicy.SWEEP_COOLDOWN_TICKS > WardenV5CombatPolicy.SWEEP_PREP_TICKS,
                "the sweep owns a real cooldown");
        check(WardenV5CombatPolicy.sweepVolume().startsWith("5x4"),
                "the diagnostic volume states the exact footprint");
    }

    private static void echoIsASelfCentredWallPiercingSphere() {
        check(WardenV5CombatPolicy.ECHO_RADIUS_BLOCKS == 6.0D,
                "the detonation radius is exactly six blocks");
        check(WardenV5CombatPolicy.echoHits(0.0D, 0.0D, 0.0D),
                "the caster's own cell is inside");
        check(WardenV5CombatPolicy.echoHits(5.9D, 0.0D, 0.0D),
                "a target within six blocks is inside");
        check(!WardenV5CombatPolicy.echoHits(6.1D, 0.0D, 0.0D),
                "a target beyond six blocks is outside");
        // 3.4*sqrt(3) ~ 5.89 blocks, so this is a genuine three-dimensional test.
        check(WardenV5CombatPolicy.echoHits(3.4D, 3.4D, 3.4D),
                "the volume is a real sphere, not a flat cylinder");
        check(!WardenV5CombatPolicy.echoHits(0.0D, 0.0D, 6.5D),
                "the sphere is bounded vertically too");
        // Wall piercing is a property of the resolution, not of the geometry:
        // there is deliberately no cover check anywhere in this policy.
        check(WardenV5CombatPolicy.ECHO_WARNING_TICKS >= 30
                        && WardenV5CombatPolicy.ECHO_WARNING_TICKS <= 40,
                "the warning is 1.5..2.0 s");
        check(WardenV5CombatPolicy.ECHO_COOLDOWN_TICKS >= 440
                        && WardenV5CombatPolicy.ECHO_COOLDOWN_TICKS <= 700,
                "the initial cooldown is 22..35 s");
        check(WardenV5CombatPolicy.ECHO_KNOCKBACK > 0.0D,
                "a landed detonation still carries real knockback");
    }

    private static void sculkResonanceIsACounterableTrace() {
        List<Step> moving = new ArrayList<>();
        for (int index = 0; index < 10; index++) {
            moving.add(new Step(index * 0.6D, 64.0D, 0.0D, 10 - index, false, true));
        }
        check(WardenV5CombatPolicy.maySelect(moving),
                "a moving player is a legal trace target");
        List<Step> sneaking = new ArrayList<>(moving);
        sneaking.add(new Step(6.0D, 64.0D, 0.0D, 4, true, true));
        check(!WardenV5CombatPolicy.maySelect(sneaking),
                "sneaking before the lock suppresses selection entirely");
        check(!WardenV5CombatPolicy.maySelect(List.of()),
                "no history means no trace");
        List<Step> stale = new ArrayList<>();
        for (int index = 0; index < 5; index++) {
            stale.add(new Step(index, 64.0D, 0.0D,
                    WardenV5CombatPolicy.TRACE_HISTORY_TICKS + 5, false, true));
        }
        check(!WardenV5CombatPolicy.maySelect(stale),
                "an expired history buffer cannot target a player");
        List<double[]> trace = WardenV5CombatPolicy.tracePolyline(moving, 5);
        check(trace.size() >= 2 && trace.size() <= 6,
                "the trace is a short 3..5 block polyline, not a full path history");
        check(WardenV5CombatPolicy.TRACE_WARNING_TICKS >= 22
                        && WardenV5CombatPolicy.TRACE_WARNING_TICKS <= 28,
                "the locked warning is 1.1..1.4 s");
        check(WardenV5CombatPolicy.traceHits(3.0D, 0.0D, 0.0D, 0.0D, 5.0D, 0.0D, 0.9D),
                "a player still on the announced corridor is hit");
        check(!WardenV5CombatPolicy.traceHits(3.0D, 1.6D, 0.0D, 0.0D, 5.0D, 0.0D, 0.9D),
                "a perpendicular sidestep after the lock genuinely dodges");
        check(WardenV5CombatPolicy.TRACE_MAX_TRACKS == 2,
                "a larger party gets at most two separated tracks");
    }

    private static void schedulingNeverFeedsAStunIntoTheBurst() {
        check(!WardenV5CombatPolicy.mayFollowSweepWithEcho(0),
                "a burst may not start on the sweep's impact tick");
        check(!WardenV5CombatPolicy.mayFollowSweepWithEcho(
                        WardenV5CombatPolicy.SWEEP_TO_ECHO_GAP_TICKS - 1),
                "the stun plus a fair reaction second is mandatory");
        check(WardenV5CombatPolicy.mayFollowSweepWithEcho(
                        WardenV5CombatPolicy.SWEEP_TO_ECHO_GAP_TICKS),
                "after the fair gap the burst is legal");
        check(!WardenV5CombatPolicy.mayStartCustomCast(true, false),
                "one custom major at a time");
        check(WardenV5CombatPolicy.mayStartCustomCast(false, false),
                "native combat is allowed between custom casts");
        check(WardenV5CombatPolicy.warningTicks(Action.FRONTAL_SWEEP)
                        == WardenV5CombatPolicy.SWEEP_PREP_TICKS,
                "the sweep tell is its preparation");
        check(WardenV5CombatPolicy.clip(Action.FRONTAL_SWEEP).equals("WARDEN_SWEEP"),
                "the authored clip label is stable for the pose packet");
    }

    private static void check(boolean value, String reason) {
        if (!value) throw new AssertionError(reason);
    }
}
