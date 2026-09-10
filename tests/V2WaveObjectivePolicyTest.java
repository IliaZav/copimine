import me.copimine.endevent.domain.V2WaveObjectivePolicy;

public final class V2WaveObjectivePolicyTest {
    public static void main(String[] args) {
        V2WaveObjectivePolicy.Objective[] expected = {
                V2WaveObjectivePolicy.Objective.CARRIER,
                V2WaveObjectivePolicy.Objective.HUNT_MARK,
                V2WaveObjectivePolicy.Objective.PORTALS,
                V2WaveObjectivePolicy.Objective.BLACK_FOG,
                V2WaveObjectivePolicy.Objective.COLLAPSE_RINGS,
                V2WaveObjectivePolicy.Objective.CHAMBERS};
        for (int wave = 1; wave <= 6; wave++) {
            check(V2WaveObjectivePolicy.objective(wave) == expected[wave - 1],
                    "unexpected V2 objective for wave " + wave);
            check(!V2WaveObjectivePolicy.title(wave).isBlank(),
                    "V2 objective title must be present");
        }
        check(V2WaveObjectivePolicy.portalCount() == 3,
                "Wave 3 must always use exactly three portals");
        check(V2WaveObjectivePolicy.safeZoneCount(0, 0) == 0,
                "zero living players must not create a meaningless safe zone");
        check(V2WaveObjectivePolicy.safeZoneCount(2, 0) == 1,
                "duo Wave 4 first safe-zone set must contain one zone");
        check(V2WaveObjectivePolicy.safeZoneCount(10, 0) == 5,
                "ten-player Wave 4 first safe-zone set must contain five zones");
        check(V2WaveObjectivePolicy.safeZoneCount(10, 1) == 4,
                "ten-player Wave 4 second safe-zone set must contain four zones");
        check(V2WaveObjectivePolicy.safeZoneCount(10, 2) == 3,
                "ten-player Wave 4 third safe-zone set must contain three zones");
        check(V2WaveObjectivePolicy.safeZoneSide(0) == 3
                        && V2WaveObjectivePolicy.safeZoneSide(1) == 2
                        && V2WaveObjectivePolicy.safeZoneSide(2) == 1,
                "safe-zone sides must be 3x3, 2x2 and 1x1");
        check(V2WaveObjectivePolicy.chamberCount(2) == 2
                        && V2WaveObjectivePolicy.chamberCount(3) == 3
                        && V2WaveObjectivePolicy.chamberCount(10) == 4,
                "Wave 6 chamber scaling must be 2/3/4");
        check(V2WaveObjectivePolicy.ringCount() == 3,
                "Wave 5 must have exactly three collapse rings");
        check(V2WaveObjectivePolicy.isComplete(1,
                new V2WaveObjectivePolicy.Progress(3, 0, 0, 0, 0, false)),
                "three delivered charges complete Wave 1");
        check(!V2WaveObjectivePolicy.isComplete(1,
                new V2WaveObjectivePolicy.Progress(2, 0, 0, 0, 0, false)),
                "two delivered charges do not complete Wave 1");
        check(V2WaveObjectivePolicy.isComplete(3,
                new V2WaveObjectivePolicy.Progress(0, 0, 3, 0, 0, false)),
                "three captured portals complete Wave 3");
        check(!V2WaveObjectivePolicy.isComplete(4,
                new V2WaveObjectivePolicy.Progress(0, 0, 0, 2, 1, false)),
                "incomplete fog cycles must not complete Wave 4");
        check(V2WaveObjectivePolicy.isComplete(4,
                new V2WaveObjectivePolicy.Progress(0, 0, 0, 3, 0, false)),
                "three fog cycles complete Wave 4");
        check(V2WaveObjectivePolicy.isComplete(5,
                new V2WaveObjectivePolicy.Progress(0, 0, 0, 0, 3, true)),
                "three collapsed rings complete Wave 5");
        check(V2WaveObjectivePolicy.isComplete(6,
                new V2WaveObjectivePolicy.Progress(0, 0, 0, 0, 0, true)),
                "all chambers cleared complete Wave 6");
        check(!V2WaveObjectivePolicy.chambersComplete(false, 0),
                "Wave 6 must not open its passage between staggered groups");
        check(!V2WaveObjectivePolicy.chambersComplete(true, 1),
                "live chamber mobs must keep Wave 6 active");
        check(V2WaveObjectivePolicy.chambersComplete(true, 0),
                "Wave 6 completes after all groups and all mobs are clear");
        boolean rejected = false;
        try {
            V2WaveObjectivePolicy.objective(7);
        } catch (IllegalArgumentException expectedException) {
            rejected = true;
        }
        check(rejected, "unknown V2 waves must fail closed");
        System.out.println("V2WaveObjectivePolicyTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
