import me.copimine.endevent.domain.RitualSpellVisualPolicy;

public final class RitualSpellVisualPolicyTest {
    public static void main(String[] args) {
        var ring = RitualSpellVisualPolicy.ring(4, 0.12, 0);
        check(ring.size() == 8, "an eight-segment rune stays within the world-beam budget");
        for (int i = 0; i < ring.size(); i++) {
            var segment = ring.get(i);
            check(Math.abs(Math.hypot(segment.from().x(), segment.from().z()) - 4) < 1e-8,
                    "warning rune exactly marks the gameplay radius");
            check(segment.to().equals(ring.get((i + 1) % 8).from()), "rune segments form a closed boundary");
        }
        var gravity = RitualSpellVisualPolicy.gravity(1.25);
        check(gravity.size() == 20, "two bounded runes and four moving inward streams");
        for (var segment : gravity) {
            check(Math.hypot(segment.from().x(), segment.from().z()) <= 4.001,
                    "gravity visual lies within its real four-block zone");
            check(segment.from().y() >= 0 && segment.from().y() < 0.5,
                    "ground visual never becomes an eye-level particle wall");
        }
        var start = new RitualSpellVisualPolicy.Point(0, 4, 0);
        var end = new RitualSpellVisualPolicy.Point(8, 1, 6);
        var chain = RitualSpellVisualPolicy.chain(start, end, 2);
        check(chain.size() == 6 && chain.get(0).from().equals(start)
                && chain.get(5).to().equals(end), "chain reaches sphere surface and actual target");
        for (int i = 0; i < 5; i++) check(chain.get(i).to().equals(chain.get(i + 1).from()), "no chain gaps");
        try { RitualSpellVisualPolicy.ring(Double.NaN, 0, 0); throw new AssertionError("invalid radius"); }
        catch (IllegalArgumentException expected) { }
        System.out.println("RitualSpellVisualPolicyTest OK");
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
