import me.copimine.endevent.domain.Wave7MinibossSteadinessPolicy;

/** A miniboss must arc into a new heading, never vibrate between two. */
public final class Wave7MinibossSteadinessPolicyTest {
    private static int checks;

    private Wave7MinibossSteadinessPolicyTest() {
    }

    public static void main(String[] args) {
        headingIsHeldBeforeItMayChange();
        turningIsBoundedPerTick();
        oppositeInputsDoNotFlipInstantly();
        intentFollowsDistanceAndHealth();
        aHeldHeadingOnlyChangesAfterTheWindow();
        System.out.println("Wave7MinibossSteadinessPolicyTest: PASS (" + checks + " checks)");
    }

    private static void headingIsHeldBeforeItMayChange() {
        check("a fresh actor may pick a heading", Wave7MinibossSteadinessPolicy.mayRethink(
                100L, Long.MIN_VALUE / 2, Long.MIN_VALUE / 2, false));
        check("a just-committed heading is held", !Wave7MinibossSteadinessPolicy.mayRethink(
                100L, 100L, 100L, false));
        check("a heading is still held halfway through the commit window",
                !Wave7MinibossSteadinessPolicy.mayRethink(100L + 3L, 100L, 103L, false));
        check("a held heading may be reconsidered after the window",
                Wave7MinibossSteadinessPolicy.mayRethink(
                        100L + Wave7MinibossSteadinessPolicy.COMMIT_TICKS, 100L,
                        100L + Wave7MinibossSteadinessPolicy.COMMIT_TICKS, false));
        check("a blocked actor may rethink immediately",
                Wave7MinibossSteadinessPolicy.mayRethink(101L, 100L, 101L, true));
        check("the commit window is long enough to be visible",
                Wave7MinibossSteadinessPolicy.COMMIT_TICKS >= 10);
    }

    private static void turningIsBoundedPerTick() {
        double[] straight = Wave7MinibossSteadinessPolicy.steadyHeading(1.0D, 0.0D, 1.0D, 0.0D);
        check("same direction stays the same direction",
                Math.abs(straight[0] - 1.0D) < 1.0E-6D && Math.abs(straight[1]) < 1.0E-6D);
        // A ninety-degree request must be spread over several ticks.
        double[] turn = Wave7MinibossSteadinessPolicy.steadyHeading(1.0D, 0.0D, 0.0D, 1.0D);
        double angle = Math.toDegrees(Math.atan2(turn[1], turn[0]));
        check("a hard turn is limited to the per-tick cap",
                Math.abs(angle - Wave7MinibossSteadinessPolicy.MAX_TURN_DEGREES_PER_TICK) < 1.0E-6D);
        check("the clamped heading stays normalised",
                Math.abs(Math.hypot(turn[0], turn[1]) - 1.0D) < 1.0E-6D);
        double[] small = Wave7MinibossSteadinessPolicy.steadyHeading(1.0D, 0.0D,
                Math.cos(Math.toRadians(4.0D)), Math.sin(Math.toRadians(4.0D)));
        check("a turn inside the cap is taken exactly",
                Math.abs(Math.toDegrees(Math.atan2(small[1], small[0])) - 4.0D) < 1.0E-6D);
        double[] idle = Wave7MinibossSteadinessPolicy.steadyHeading(1.0D, 0.0D, 0.0D, 0.0D);
        check("no desired direction means no movement",
                idle[0] == 0.0D && idle[1] == 0.0D);
    }

    private static void oppositeInputsDoNotFlipInstantly() {
        // The defect: left then right on consecutive ticks. Steering through a
        // reversal must walk around the compass, not jump 180 degrees.
        double x = 1.0D;
        double z = 0.0D;
        for (int tick = 0; tick < 30; tick++) {
            double[] next = Wave7MinibossSteadinessPolicy.steadyHeading(x, z, -1.0D, 0.0D);
            double step = Math.toDegrees(Math.acos(Math.max(-1.0D, Math.min(1.0D,
                    x * next[0] + z * next[1]))));
            check("every tick turns by no more than the cap (tick " + tick + ")",
                    step <= Wave7MinibossSteadinessPolicy.MAX_TURN_DEGREES_PER_TICK + 1.0E-6D);
            x = next[0];
            z = next[1];
        }
        check("a sustained opposite input does eventually reverse direction", x < -0.5D);
        check("a reversal is recognised",
                Wave7MinibossSteadinessPolicy.isReversal(1.0D, 0.0D, -1.0D, 0.0D));
        check("a small correction is not a reversal",
                !Wave7MinibossSteadinessPolicy.isReversal(1.0D, 0.0D, 0.9D, 0.2D));
    }

    private static void intentFollowsDistanceAndHealth() {
        check("far away means approach", Wave7MinibossSteadinessPolicy.intentFor(30.0D, 1.0D, false, false)
                == Wave7MinibossSteadinessPolicy.Intent.APPROACH);
        check("too close means retreat", Wave7MinibossSteadinessPolicy.intentFor(1.0D, 1.0D, false, false)
                == Wave7MinibossSteadinessPolicy.Intent.RETREAT);
        check("a healthy actor at range holds its line",
                Wave7MinibossSteadinessPolicy.intentFor(6.0D, 1.0D, false, false)
                        == Wave7MinibossSteadinessPolicy.Intent.APPROACH);
        check("a hurt actor at range strafes",
                Wave7MinibossSteadinessPolicy.intentFor(6.0D, 0.6D, false, false)
                        == Wave7MinibossSteadinessPolicy.Intent.STRAFE);
        check("a vanished actor breaks contact",
                Wave7MinibossSteadinessPolicy.intentFor(6.0D, 1.0D, false, true)
                        == Wave7MinibossSteadinessPolicy.Intent.RETREAT);
        check("a hurt actor with cover breaks contact",
                Wave7MinibossSteadinessPolicy.intentFor(6.0D, 0.4D, true, false)
                        == Wave7MinibossSteadinessPolicy.Intent.RETREAT);
        check("every intent has a description",
                !Wave7MinibossSteadinessPolicy.describe(Wave7MinibossSteadinessPolicy.Intent.APPROACH).isBlank());
    }

private static void aHeldHeadingOnlyChangesAfterTheWindow() {
        // End-to-end simulation of the old defect: alternate the requested
        // direction every tick, exactly as the old routine did, and prove the
        // committed heading does not follow it tick for tick.
        double x = 1.0D, z = 0.0D;
        double previousX = x, previousZ = z;
        int flips = 0;
        for (int tick = 0; tick < 60; tick++) {
            double wantX = (tick % 2 == 0) ? -1.0D : 1.0D;
            double[] next = Wave7MinibossSteadinessPolicy.steadyHeading(x, z, wantX, 0.0D);
            double step = Math.toDegrees(Math.acos(Math.max(-1.0D, Math.min(1.0D,
                    x * next[0] + z * next[1]))));
            check("alternating input never exceeds the turn cap (tick " + tick + ")",
                    step <= Wave7MinibossSteadinessPolicy.MAX_TURN_DEGREES_PER_TICK + 1.0E-6D);
            if (Math.signum(next[0]) != Math.signum(previousX)) flips++;
            previousX = next[0];
            previousZ = next[1];
            x = next[0];
            z = next[1];
        }
        // A vibrating actor would flip every tick; a steady one flips rarely.
        check("alternating input does not produce a per-tick vibration", flips <= 6);
    }

    private static void check(String name, boolean condition) {
        checks++;
        if (!condition) throw new AssertionError("FAILED: " + name);
    }
}
