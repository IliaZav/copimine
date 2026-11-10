package me.copimine.endevent.domain;

/**
 * W4-02: the towers must scale their pressure by the party they face.
 *
 * <p>Player report: "too many projectiles for a tiny group". Two separate
 * levers caused it — every tower fired simultaneously, and every tower kept the
 * full-raid cadence — so both are pinned here.</p>
 */
public final class ObeliskFireCadencePolicyTest {
    private ObeliskFireCadencePolicyTest() {
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    public static void main(String[] args) {
        int base = ObeliskFireDirectorPolicy.DEFAULT_FIRE_INTERVAL_TICKS;

        // A two-player squad must see roughly half the barrage it saw before.
        int duo = ObeliskFireDirectorPolicy.fireInterval(base, false,
                ObeliskFireDirectorPolicy.MIN_TELEGRAPH_TICKS, 2);
        int raid = ObeliskFireDirectorPolicy.fireInterval(base, false,
                ObeliskFireDirectorPolicy.MIN_TELEGRAPH_TICKS, 20);
        check(duo > base, "a duo must not receive full-raid cadence: " + duo);
        check(raid <= base, "a full raid must not be starved: " + raid);
        // A duo cadence is bounded by the projectile-admission ceiling, so it
        // lands at the slowest legal tower rather than a literal 2x base.
        check(duo == ObeliskFireDirectorPolicy.MAX_BASE_FIRE_INTERVAL_TICKS,
                "a duo must sit at the slowest legal cadence: " + duo);
        check(ObeliskFireDirectorPolicy.fireInterval(base, false,
                ObeliskFireDirectorPolicy.MIN_TELEGRAPH_TICKS, 4) > base,
                "four players still get a gentler cadence than a full raid");
        check(ObeliskFireDirectorPolicy.fireInterval(base, false,
                ObeliskFireDirectorPolicy.MIN_TELEGRAPH_TICKS, 10) == base,
                "the tuned ten-player cadence is unchanged");

        // Monotonic: more players never means a slower tower.
        int previous = Integer.MAX_VALUE;
        for (int players = 1; players <= 20; players++) {
            int interval = ObeliskFireDirectorPolicy.fireInterval(base, false,
                    ObeliskFireDirectorPolicy.MIN_TELEGRAPH_TICKS, players);
            check(interval <= previous, "cadence must not slow down as the party grows");
            previous = interval;
        }

        // The telegraph floor and projectile-admission ceiling are never crossed.
        for (int players = 1; players <= 20; players++) {
            for (int candidate : new int[] {20, 26, 40}) {
                int interval = ObeliskFireDirectorPolicy.fireInterval(candidate, false,
                        ObeliskFireDirectorPolicy.MIN_TELEGRAPH_TICKS, players);
                check(interval >= ObeliskFireDirectorPolicy.MIN_TELEGRAPH_TICKS,
                        "a tower must stay readable: " + interval);
                check(interval <= ObeliskFireDirectorPolicy.MAX_BASE_FIRE_INTERVAL_TICKS,
                        "a tower must stay inside the admission bound: " + interval);
            }
        }

        // The legacy three-argument overload keeps its previous contract.
        check(ObeliskFireDirectorPolicy.fireInterval(base, false,
                ObeliskFireDirectorPolicy.MIN_TELEGRAPH_TICKS) == base,
                "the unscaled overload must stay identical");
        check(ObeliskFireDirectorPolicy.fireInterval(base, true,
                ObeliskFireDirectorPolicy.MIN_TELEGRAPH_TICKS) == (int) Math.round(base * 0.75D),
                "the accelerated overload must stay identical");

        // Simultaneous sequences: the loudest part of the complaint.
        check(ObeliskFireDirectorPolicy.simultaneousSequenceLimit(2, 4) == 1,
                "a duo must not face four overlapping warnings");
        check(ObeliskFireDirectorPolicy.simultaneousSequenceLimit(4, 4) == 2, "small squad");
        check(ObeliskFireDirectorPolicy.simultaneousSequenceLimit(7, 4) == 3, "mid squad");
        check(ObeliskFireDirectorPolicy.simultaneousSequenceLimit(10, 5) == 4, "large squad");
        check(ObeliskFireDirectorPolicy.simultaneousSequenceLimit(20, 6) == 6, "full raid uses every tower");
        check(ObeliskFireDirectorPolicy.simultaneousSequenceLimit(2, 2) == 1,
                "the limit never exceeds the towers that exist");
        check(ObeliskFireDirectorPolicy.simultaneousSequenceLimit(20, 1) == 1,
                "one tower still fires");
        check(ObeliskFireDirectorPolicy.simultaneousSequenceLimit(1, 4) >= 1,
                "an odd roster never silences the arena");

        // Sequencing stays bounded for every party size.
        for (int players = 1; players <= 20; players++) {
            int limit = ObeliskFireDirectorPolicy.simultaneousSequenceLimit(players, 6);
            check(limit >= 1 && limit <= ObeliskScalingPolicy.MAX_OBELISKS,
                    "sequence limit out of range: " + limit);
        }
    }
}
