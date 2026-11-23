import me.copimine.endevent.domain.BossPhase;
import me.copimine.endevent.domain.CrushingGravityPolicy;

import java.util.List;

/**
 * V5.1 Crushing Gravity: arena-wide, no direct damage, real arrow physics,
 * never during the cocoon and never stacked on an unavoidable hazard.
 */
public final class CrushingGravityPolicyTest {
    public static void main(String[] args) {
        timingsStayInsideTheRatifiedBands();
        eligibilityIsRareAndNeverDuringTheCocoon();
        conflictingHazardsVetoTheCast();
        theFieldNeverDealsDirectDamage();
        realPhysicsIsAppliedNotFaked();
        everyBeatHasItsOwnAuthoredPhase();
        arenaBoundsAreScopedAndValidated();
        System.out.println("CrushingGravityPolicyTest OK");
    }

    private static void timingsStayInsideTheRatifiedBands() {
        check(CrushingGravityPolicy.WARNING_TICKS * 50L >= 1_600L
                        && CrushingGravityPolicy.WARNING_TICKS * 50L <= 2_200L,
                "the windup is 1.6..2.2 s");
        check(CrushingGravityPolicy.ACTIVE_TICKS * 50L >= 5_000L
                        && CrushingGravityPolicy.ACTIVE_TICKS * 50L <= 7_000L,
                "the live field is 5..7 s");
        check(CrushingGravityPolicy.COOLDOWN_TICKS * 50L >= 60_000L
                        && CrushingGravityPolicy.COOLDOWN_TICKS * 50L <= 90_000L,
                "the effective cooldown is 60..90 s");
        check(CrushingGravityPolicy.COLLAPSE_TICKS * 50L >= 1_000L
                        && CrushingGravityPolicy.COLLAPSE_TICKS * 50L <= 2_000L,
                "the visible collapse is 1..2 s");
    }

    private static void eligibilityIsRareAndNeverDuringTheCocoon() {
        check(!CrushingGravityPolicy.phaseEligible(BossPhase.AWAKENING, false),
                "Gravity is never a beginner-phase spell");
        check(!CrushingGravityPolicy.phaseEligible(BossPhase.HUNT, false),
                "Hunt stays physical");
        check(CrushingGravityPolicy.phaseEligible(BossPhase.RIFT, false),
                "Rift is eligible");
        check(CrushingGravityPolicy.phaseEligible(BossPhase.OVERLOAD, false),
                "Overload is eligible");
        check(CrushingGravityPolicy.phaseEligible(BossPhase.RAGE, false),
                "Rage is eligible");
        check(CrushingGravityPolicy.phaseEligible(BossPhase.LAST_SEAL, false),
                "a grounded final stand is eligible");
        for (BossPhase phase : BossPhase.values()) {
            check(!CrushingGravityPolicy.phaseEligible(phase, true),
                    "an intact cocoon blocks Gravity in " + phase);
        }
        check(CrushingGravityPolicy.cocoonBlocks(true),
                "the cocoon veto is explicit");
        check(!CrushingGravityPolicy.mayCast(BossPhase.RAGE, true, 1_000_000L, 0L, List.of()),
                "mayCast refuses during the cocoon even when everything else is ready");
        check(!CrushingGravityPolicy.mayCast(BossPhase.RAGE, false, 500L, 1_000L, List.of()),
                "mayCast respects the cooldown deadline");
    }

    private static void conflictingHazardsVetoTheCast() {
        check(CrushingGravityPolicy.mayCast(BossPhase.OVERLOAD, false, 10_000L, 0L, List.of()),
                "an empty hazard set permits the field");
        check(!CrushingGravityPolicy.mayCast(BossPhase.OVERLOAD, false, 10_000L, 0L,
                        List.of(CrushingGravityPolicy.Hazard.CHEST_BEAM)),
                "the Core beam lane vetoes Gravity");
        check(!CrushingGravityPolicy.mayCast(BossPhase.RAGE, false, 10_000L, 0L,
                        List.of(CrushingGravityPolicy.Hazard.ARENA_INFERNO)),
                "a wall-to-wall inferno front vetoes Gravity");
        check(!CrushingGravityPolicy.mayCast(BossPhase.RIFT, false, 10_000L, 0L,
                        List.of(CrushingGravityPolicy.Hazard.END_GEYSER)),
                "a geyser chain vetoes Gravity");
        check(!CrushingGravityPolicy.mayCast(BossPhase.LAST_SEAL, false, 10_000L, 0L,
                        List.of(CrushingGravityPolicy.Hazard.KAGUNE_VOID_GAP)),
                "a sealed Kagune void gap vetoes Gravity");
        check(!CrushingGravityPolicy.escapesRemain(
                        List.of(CrushingGravityPolicy.Hazard.CHEST_BEAM), 3),
                "no escape survives a beam lane under the field");
        check(!CrushingGravityPolicy.escapesRemain(List.of(), 0),
                "a field with no legal escape route is rejected");
        check(CrushingGravityPolicy.escapesRemain(
                        List.of(CrushingGravityPolicy.Hazard.VOID_MARK_CHAIN), 1),
                "a bounded mark chain is escapable and does not veto the field");
    }

    private static void theFieldNeverDealsDirectDamage() {
        check(CrushingGravityPolicy.DIRECT_DAMAGE == 0.0D,
                "Gravity is a control field with zero direct damage");
        check(CrushingGravityPolicy.HORIZONTAL_STEER_SCALE == 1.0D,
                "the player keeps full horizontal control");
        check(CrushingGravityPolicy.JUMP_IMPULSE_SCALE > 0.0D,
                "jumping is reduced, not deleted");
        check(CrushingGravityPolicy.JUMP_IMPULSE_SCALE < 1.0D,
                "the reduction is real");
    }

    private static void realPhysicsIsAppliedNotFaked() {
        double next = CrushingGravityPolicy.nextVerticalVelocity(0.2D);
        check(next < 0.2D,
                "an airborne player is genuinely accelerated downward");
        check(next >= CrushingGravityPolicy.MAX_DOWNWARD_SPEED,
                "the extra pull is capped so nobody is slammed into the floor");
        double terminal = 0.0D;
        for (int tick = 0; tick < 100; tick++) {
            terminal = CrushingGravityPolicy.nextVerticalVelocity(terminal);
        }
        check(Math.abs(terminal - CrushingGravityPolicy.MAX_DOWNWARD_SPEED) < 1.0E-9D,
                "the pull converges to its bounded terminal speed");
        check(CrushingGravityPolicy.ARROW_HORIZONTAL_RETENTION > 0.0D
                        && CrushingGravityPolicy.ARROW_HORIZONTAL_RETENTION < 1.0D,
                "arrows are slowed, not silently destroyed");
        check(Math.abs(CrushingGravityPolicy.arrowHorizontalSpeed(2.0D) - 1.1D) < 1.0E-9D,
                "a newly fired arrow keeps a real fractional speed");
        check(CrushingGravityPolicy.ARROW_GRAVITY_MULTIPLIER > 1.0D,
                "in-flight arrows fall faster under real physics");
    }

    private static void everyBeatHasItsOwnAuthoredPhase() {
        check(CrushingGravityPolicy.beat(0)
                        == CrushingGravityPolicy.Beat.WARNING,
                "the cast opens with the warning");
        check(CrushingGravityPolicy.beat(CrushingGravityPolicy.WARNING_TICKS)
                        == CrushingGravityPolicy.Beat.LIVE,
                "the field goes live after the warning");
        check(CrushingGravityPolicy.beat(CrushingGravityPolicy.WARNING_TICKS
                                + CrushingGravityPolicy.ACTIVE_TICKS)
                        == CrushingGravityPolicy.Beat.COLLAPSE,
                "the field ends with a visible collapse");
        check(CrushingGravityPolicy.beat(CrushingGravityPolicy.totalTicks())
                        == CrushingGravityPolicy.Beat.DONE,
                "the cast terminates and ownership is released");
        check(CrushingGravityPolicy.pullActive(0) == false,
                "no pull during the warning");
        check(CrushingGravityPolicy.pullActive(CrushingGravityPolicy.WARNING_TICKS),
                "the pull is live only in the live beat");
        check(CrushingGravityPolicy.pullActive(CrushingGravityPolicy.totalTicks()) == false,
                "no pull after the collapse");
        check(CrushingGravityPolicy.progress(CrushingGravityPolicy.totalTicks()) == 1.0D,
                "progress is bounded");
        check(CrushingGravityPolicy.readyAt(1_000L)
                        == 1_000L + (CrushingGravityPolicy.ACTIVE_TICKS
                        + CrushingGravityPolicy.COOLDOWN_TICKS) * 50L,
                "the next legal start follows the whole field plus the cooldown");
    }

    private static void arenaBoundsAreScopedAndValidated() {
        CrushingGravityPolicy.Bounds bounds =
                new CrushingGravityPolicy.Bounds(-20.0D, 60.0D, -20.0D, 20.0D, 90.0D, 20.0D);
        check(bounds.contains(0.0D, 70.0D, 0.0D),
                "the arena centre is inside the field");
        check(!bounds.contains(30.0D, 70.0D, 0.0D),
                "a spectator outside the arena is not in the field");
        check(!bounds.contains(0.0D, 100.0D, 0.0D),
                "the field is vertically bounded too");
        check(bounds.radius() == 20.0D, "the footprint radius is measured from the bounds");
        boolean rejected = false;
        try {
            new CrushingGravityPolicy.Bounds(1.0D, 1.0D, 1.0D, 0.0D, 2.0D, 2.0D);
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        check(rejected, "an inverted footprint is rejected instead of trusted");
    }

    private static void check(boolean value, String reason) {
        if (!value) throw new AssertionError(reason);
    }
}
