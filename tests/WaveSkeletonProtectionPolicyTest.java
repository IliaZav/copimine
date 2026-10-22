import me.copimine.endevent.domain.WaveSkeletonProtectionPolicy;

public final class WaveSkeletonProtectionPolicyTest {
    public static void main(String[] args) {
        check(WaveSkeletonProtectionPolicy.RESPONSE_DURATION_MILLIS == 7_000L,
                "skeleton protection holds its target for a bounded seven seconds");
        check(WaveSkeletonProtectionPolicy.mayReact(true, true, true, true,
                        true, true, 0.0D),
                "an accepted hit on a same-wave skeleton can alert a nearby normal zombie");
        check(WaveSkeletonProtectionPolicy.mayReact(true, true, true, true,
                        true, true,
                        WaveSkeletonProtectionPolicy.RESPONSE_RADIUS_BLOCKS
                                * WaveSkeletonProtectionPolicy.RESPONSE_RADIUS_BLOCKS),
                "the protection radius includes its exact boundary");
        check(!WaveSkeletonProtectionPolicy.mayReact(false, true, true, true,
                        true, true, 1.0D),
                "cancelled or zero-damage attacks cannot provoke protection");
        check(!WaveSkeletonProtectionPolicy.mayReact(true, false, true, true,
                        true, true, 1.0D),
                "ineligible attackers cannot provoke protection");
        check(!WaveSkeletonProtectionPolicy.mayReact(true, true, false, true,
                        true, true, 1.0D),
                "entities from another attempt cannot share protection");
        check(!WaveSkeletonProtectionPolicy.mayReact(true, true, true, false,
                        true, true, 1.0D),
                "entities from another wave cannot share protection");
        check(!WaveSkeletonProtectionPolicy.mayReact(true, true, true, true,
                        false, true, 1.0D),
                "ritual and elite skeleton roles are not treated as normal skeletons");
        check(!WaveSkeletonProtectionPolicy.mayReact(true, true, true, true,
                        true, false, 1.0D),
                "giants and non-normal zombie roles do not use the normal protection response");
        check(!WaveSkeletonProtectionPolicy.mayReact(true, true, true, true,
                        true, true, Double.NaN),
                "non-finite positions cannot trigger a response");
        check(!WaveSkeletonProtectionPolicy.mayReact(true, true, true, true,
                        true, true,
                        WaveSkeletonProtectionPolicy.RESPONSE_RADIUS_BLOCKS
                                * WaveSkeletonProtectionPolicy.RESPONSE_RADIUS_BLOCKS + 0.01D),
                "players outside the protection radius do not trigger a response");

        int[] selected = WaveSkeletonProtectionPolicy.nearestResponders(
                new double[]{25.0D, 4.0D, 9.0D, 1.0D},
                new boolean[]{true, true, false, true}, 8);
        check(selected.length == 2 && selected[0] == 3 && selected[1] == 1,
                "only the two nearest eligible zombies respond");
        int[] stableTie = WaveSkeletonProtectionPolicy.nearestResponders(
                new double[]{4.0D, 4.0D, 2.0D}, new boolean[]{true, true, true}, 2);
        check(stableTie.length == 2 && stableTie[0] == 2
                        && stableTie[1] == 0,
                "equal-distance candidates retain their stable caller order");
        check(WaveSkeletonProtectionPolicy.nearestResponders(
                        new double[]{Double.NaN, -1.0D}, new boolean[]{true, true}, 2).length == 0,
                "invalid distances are never selected");
        check(WaveSkeletonProtectionPolicy.nearestResponders(
                        new double[]{1.0D}, new boolean[]{true}, 0).length == 0,
                "a skeleton cannot exceed the currently available responder slots");

        check(WaveSkeletonProtectionPolicy.responseIsActive(1_000L, 1_001L,
                        true, true, true),
                "response remains active before its deadline for a valid target and attempt");
        check(!WaveSkeletonProtectionPolicy.responseIsActive(1_001L, 1_001L,
                        true, true, true),
                "normal assignment resumes exactly at the response deadline");
        check(!WaveSkeletonProtectionPolicy.responseIsActive(1_000L, 1_001L,
                        false, true, true),
                "response ends immediately when its target becomes ineligible");
        check(!WaveSkeletonProtectionPolicy.responseIsActive(1_000L, 1_001L,
                        true, false, true),
                "response cannot survive a wave transition");
        check(!WaveSkeletonProtectionPolicy.responseIsActive(1_000L, 1_001L,
                        true, true, false),
                "response cannot survive an attempt generation change");

        System.out.println("WaveSkeletonProtectionPolicyTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
