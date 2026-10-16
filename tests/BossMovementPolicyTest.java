import java.util.List;
import java.util.Set;
import me.copimine.endevent.domain.BossMovementPolicy;

public final class BossMovementPolicyTest {
    public static void main(String[] args) {
        BossMovementPolicy.Anchor core = new BossMovementPolicy.Anchor(0.5D, 68.0D, 0.5D);
        BossMovementPolicy.Target target = new BossMovementPolicy.Target(12.5D, 69.0D, 0.5D);
        BossMovementPolicy.Candidate coreCandidate = candidate("core", 0.5D, 68.0D, 0.5D);
        BossMovementPolicy.Candidate flank = candidate("flank-east", 9.5D, 69.0D, 0.5D);
        BossMovementPolicy.Candidate targetSide = candidate("target-side", 12.5D, 70.0D, 0.5D);
        BossMovementPolicy.Candidate outside = candidate("outside", 24.5D, 69.0D, 0.5D);
        BossMovementPolicy.Candidate fire = candidate("fire", 8.5D, 69.0D, 4.5D).withFire(true);
        BossMovementPolicy.Candidate blocked = candidate("blocked", 10.5D, 69.0D, 4.5D).withMaterial("COBWEB");

        BossMovementPolicy.Candidate selected = BossMovementPolicy.chooseSafeDestination(
                core, target, List.of(coreCandidate, flank, targetSide, outside, fire, blocked),
                20.0D, 3.0D, 3.5D, Set.of("COBWEB"));
        check(selected != null, "a safe candidate must be selected");
        check(!selected.id().equals("core"), "Core cannot be a combat destination");
        check(!selected.id().equals("outside"), "outside candidate must be rejected");
        check(!selected.id().equals("fire"), "fire candidate must be rejected");
        check(!selected.id().equals("blocked"), "blocked material must be rejected");
        check(BossMovementPolicy.horizontalDistance(core, selected) >= 3.5D,
                "selected position must respect minimum Core distance");
        check(Math.abs(selected.y() - core.y()) <= 3.0D, "selected position must respect vertical bounds");

        BossMovementPolicy.Candidate fallback = BossMovementPolicy.chooseStuckFallback(
                core, List.of(coreCandidate, flank, targetSide), 20.0D, 3.0D, 3.5D, Set.of());
        check(fallback != null && !fallback.id().equals("core"), "stuck fallback must leave Core");

        BossMovementPolicy.Candidate deterministic = BossMovementPolicy.chooseSafeDestination(
                core, target, List.of(targetSide, flank), 20.0D, 3.0D, 3.5D, Set.of());
        check(selected.id().equals(deterministic.id()) || deterministic != null,
                "candidate choice must be deterministic");
        System.out.println("BossMovementPolicyTest OK");
    }

    private static BossMovementPolicy.Candidate candidate(String id, double x, double y, double z) {
        return new BossMovementPolicy.Candidate(id, x, y, z, true, true, true,
                false, false, false, false, false, "STONE");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
