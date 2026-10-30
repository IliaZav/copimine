import java.util.HashSet;
import java.util.List;
import java.util.Set;
import me.copimine.endevent.domain.WaveTwoPhantomLayoutPolicy;
import me.copimine.endevent.domain.WaveTwoPhantomLayoutPolicy.Phantom;
import me.copimine.endevent.domain.WaveTwoPhantomLayoutPolicy.Position;

public final class WaveTwoPhantomLayoutPolicyTest {
    public static void main(String[] args) {
        Position anchor = new Position(12.25D, 64.0D, -3.75D);
        List<Phantom> first = WaveTwoPhantomLayoutPolicy.selectSafe(anchor, 741L, 17.0D,
                candidate -> new Position(Math.floor(candidate.x()) + 0.5D,
                        anchor.y(), Math.floor(candidate.z()) + 0.5D));
        List<Phantom> repeated = WaveTwoPhantomLayoutPolicy.selectSafe(anchor, 741L, 17.0D,
                candidate -> new Position(Math.floor(candidate.x()) + 0.5D,
                        anchor.y(), Math.floor(candidate.z()) + 0.5D));

        check(first.size() == 35 && first.equals(repeated),
                "the server layout selects 35 stable safe positions for one mark seed");
        check(first.get(0).id().equals("phantom-00") && first.get(34).id().equals("phantom-34"),
                "server IDs are canonical, bounded, and stable in layout order");
        Set<Position> positions = new HashSet<>();
        for (Phantom phantom : first) {
            check(positions.add(phantom.position()), "safe resolution must not produce duplicate positions");
            double dx = phantom.position().x() - anchor.x();
            double dz = phantom.position().z() - anchor.z();
            check(dx * dx + dz * dz <= 17.0D * 17.0D,
                    "randomized phantom positions stay within the validated arena radius");
        }
        double furthestSquared = first.stream().mapToDouble(phantom -> {
            double dx = phantom.position().x() - anchor.x();
            double dz = phantom.position().z() - anchor.z();
            return dx * dx + dz * dz;
        }).max().orElse(0.0D);
        List<Phantom> nextMark = WaveTwoPhantomLayoutPolicy.selectSafe(anchor, 742L, 17.0D,
                candidate -> new Position(Math.floor(candidate.x()) + 0.5D,
                        anchor.y(), Math.floor(candidate.z()) + 0.5D));
        check(furthestSquared > 14.0D * 14.0D && !first.equals(nextMark),
                "phantoms are scattered across the arena and rerandomized for the next mark");

        check(WaveTwoPhantomLayoutPolicy.selectSafe(new Position(Double.NaN, 64, 0), 1L, 17.0D,
                candidate -> candidate).isEmpty(), "non-finite anchors are rejected");
        check(WaveTwoPhantomLayoutPolicy.selectSafe(anchor, 741L, 17.0D,
                candidate -> null).isEmpty(), "no unsafe candidate is returned without adapter validation");
        System.out.println("WaveTwoPhantomLayoutPolicyTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
