import java.util.List;
import me.copimine.endevent.domain.EliteProjectileImpactPolicy;

public final class EliteProjectileImpactPolicyTest {
    public static void main(String[] args) {
        FakeTarget eligible = new FakeTarget("eligible", true, true, true, 1.0D);
        FakeTarget duplicate = new FakeTarget("eligible", true, true, true, 1.0D);
        FakeTarget ineligible = new FakeTarget("ineligible", true, false, true, 1.0D);
        FakeTarget spectator = new FakeTarget("spectator", true, false, true, 1.0D);
        FakeTarget mob = new FakeTarget("mob", false, true, true, 1.0D);
        FakeTarget behindWall = new FakeTarget("behind-wall", true, true, false, 1.0D);
        FakeTarget outsideRadius = new FakeTarget("outside-radius", true, true, true, 16.0D);
        List<FakeTarget> candidates = List.of(eligible, duplicate, ineligible, spectator,
                mob, behindWall, outsideRadius);

        var playerCollision = new EliteProjectileImpactPolicy.Impact(
                true, true, false, false, true);
        int affected = EliteProjectileImpactPolicy.applyVoidBurst(
                playerCollision, candidates, 2.5D, 4.0D);
        check(affected == 1, "only one distinct eligible visible player in radius is damaged");
        check(eligible.hits == 1 && duplicate.hits == 0,
                "the direct-hit player is included once in the area transaction");
        check(ineligible.hits == 0 && spectator.hits == 0 && mob.hits == 0
                        && behindWall.hits == 0 && outsideRadius.hits == 0,
                "ineligible players, spectators, mobs, screened and distant targets are excluded");
        check(eligible.lastDamage == 4.0D, "impact damage remains capped to the direct policy value");

        FakeTarget overRadius = new FakeTarget("over-radius", true, true, true, 1.0D);
        check(EliteProjectileImpactPolicy.applyVoidBurst(playerCollision,
                        List.of(overRadius), 3.01D, 4.0D) == 0
                        && overRadius.hits == 0,
                "the adapter cannot widen Void Burst beyond its hard radius cap");
        FakeTarget overDamage = new FakeTarget("over-damage", true, true, true, 1.0D);
        check(EliteProjectileImpactPolicy.applyVoidBurst(playerCollision,
                        List.of(overDamage), 2.5D, 4.01D) == 0
                        && overDamage.hits == 0,
                "the adapter cannot raise Void Burst above its direct damage cap");

        FakeTarget blockTarget = new FakeTarget("block-target", true, true, true, 2.0D);
        var blockCollision = new EliteProjectileImpactPolicy.Impact(
                false, false, true, false, true);
        check(EliteProjectileImpactPolicy.applyVoidBurst(blockCollision,
                        List.of(blockTarget), 2.5D, 4.0D) == 1,
                "a real block collision can apply the bounded area");

        assertRejected(new EliteProjectileImpactPolicy.Impact(true, true, false, true, true),
                "cancelled projectile collision must fail closed");
        assertRejected(new EliteProjectileImpactPolicy.Impact(true, true, false, false, false),
                "stale event generation must fail closed");
        assertRejected(new EliteProjectileImpactPolicy.Impact(false, false, false, false, true),
                "a timeout without player or block collision must not damage");
        assertRejected(new EliteProjectileImpactPolicy.Impact(true, false, false, false, true),
                "an ineligible player collision must not damage");

        System.out.println("EliteProjectileImpactPolicyTest OK");
    }

    private static void assertRejected(EliteProjectileImpactPolicy.Impact impact, String message) {
        FakeTarget target = new FakeTarget("rejected", true, true, true, 0.0D);
        check(EliteProjectileImpactPolicy.applyVoidBurst(impact, List.of(target), 2.5D, 4.0D) == 0,
                message);
        check(target.hits == 0, message);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class FakeTarget implements EliteProjectileImpactPolicy.Target {
        private final String id;
        private final boolean player;
        private final boolean eligible;
        private final boolean visible;
        private final double distanceSquared;
        private int hits;
        private double lastDamage;

        private FakeTarget(String id, boolean player, boolean eligible,
                           boolean visible, double distanceSquared) {
            this.id = id;
            this.player = player;
            this.eligible = eligible;
            this.visible = visible;
            this.distanceSquared = distanceSquared;
        }

        @Override public String id() { return id; }
        @Override public boolean isPlayer() { return player; }
        @Override public boolean isEligible() { return eligible; }
        @Override public boolean isVisible() { return visible; }
        @Override public double distanceSquared() { return distanceSquared; }
        @Override public void damage(double amount) { hits++; lastDamage = amount; }
    }
}
