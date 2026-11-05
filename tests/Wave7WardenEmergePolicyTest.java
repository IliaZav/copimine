import me.copimine.endevent.domain.Wave7WardenEmergePolicy;

public final class Wave7WardenEmergePolicyTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        check(Wave7WardenEmergePolicy.emerging(0), "the first tick is part of the emergence");
        check(Wave7WardenEmergePolicy.emerging(Wave7WardenEmergePolicy.EMERGE_TICKS - 1),
                "the last tick before the deadline still emerges");
        check(!Wave7WardenEmergePolicy.emerging(Wave7WardenEmergePolicy.EMERGE_TICKS),
                "the sequence must end so the body can be handed back to combat");

        check(Math.abs(Wave7WardenEmergePolicy.verticalOffset(0)
                        + Wave7WardenEmergePolicy.SINK_DEPTH_BLOCKS) < 1.0E-9D,
                "the body starts fully under the floor");
        check(Wave7WardenEmergePolicy.verticalOffset(Wave7WardenEmergePolicy.EMERGE_TICKS) == 0.0D,
                "the body ends exactly at its final position");

        double previous = Wave7WardenEmergePolicy.verticalOffset(0);
        for (int tick = 1; tick <= Wave7WardenEmergePolicy.EMERGE_TICKS; tick++) {
            double current = Wave7WardenEmergePolicy.verticalOffset(tick);
            check(current >= previous - 1.0E-9D, "the body never sinks back at tick " + tick);
            previous = current;
        }

        // Deceleration: the second half of the travel is slower than the first.
        double firstHalf = Wave7WardenEmergePolicy.verticalOffset(
                Wave7WardenEmergePolicy.SINK_TICKS + Wave7WardenEmergePolicy.RISE_TICKS / 2)
                - Wave7WardenEmergePolicy.verticalOffset(Wave7WardenEmergePolicy.SINK_TICKS);
        double secondHalf = Wave7WardenEmergePolicy.verticalOffset(
                Wave7WardenEmergePolicy.SINK_TICKS + Wave7WardenEmergePolicy.RISE_TICKS)
                - Wave7WardenEmergePolicy.verticalOffset(
                Wave7WardenEmergePolicy.SINK_TICKS + Wave7WardenEmergePolicy.RISE_TICKS / 2);
        check(firstHalf > secondHalf, "the emergence must decelerate");

        int roars = 0;
        for (int tick = 0; tick < Wave7WardenEmergePolicy.EMERGE_TICKS; tick++) {
            if (Wave7WardenEmergePolicy.roarsAt(tick)) roars++;
        }
        check(roars == 1, "the roar plays exactly once per emergence, got " + roars);
        check(Wave7WardenEmergePolicy.rumbleVolume(Wave7WardenEmergePolicy.EMERGE_TICKS) == 0.0F,
                "the rumble is silent once the body is out");
        check(Wave7WardenEmergePolicy.easeOut(-5.0D) == 0.0D
                && Wave7WardenEmergePolicy.easeOut(5.0D) == 1.0D, "easing clamps its input");
        System.out.println("Wave7WardenEmergePolicyTest OK");
    }
}
