import java.util.HashSet;
import java.util.List;
import java.util.Set;
import me.copimine.endevent.domain.WaveTwoPhantomLayoutPolicy;
import me.copimine.endevent.domain.WaveTwoPhantomLayoutPolicy.Phantom;
import me.copimine.endevent.domain.WaveTwoPhantomLayoutPolicy.Position;

public final class WaveTwoPhantomLayoutPolicyTest {
    public static void main(String[] args) {
        Position anchor = new Position(12.25D, 64.0D, -3.75D);
        List<Phantom> first = WaveTwoPhantomLayoutPolicy.selectSafe(anchor, 741L,
                candidate -> new Position(Math.floor(candidate.x()) + 0.5D,
                        anchor.y(), Math.floor(candidate.z()) + 0.5D));
        List<Phantom> repeated = WaveTwoPhantomLayoutPolicy.selectSafe(anchor, 741L,
                candidate -> new Position(Math.floor(candidate.x()) + 0.5D,
                        anchor.y(), Math.floor(candidate.z()) + 0.5D));

        check(first.size() == 32 && first.equals(repeated),
                "the server layout selects exactly 32 stable safe positions for one mark seed");
        check(first.get(0).id().equals("phantom-00") && first.get(31).id().equals("phantom-31"),
                "server IDs are canonical, bounded, and stable in layout order");
        Set<Position> positions = new HashSet<>();
        for (Phantom phantom : first) {
            check(positions.add(phantom.position()), "safe resolution must not produce duplicate positions");
        }

        check(WaveTwoPhantomLayoutPolicy.selectSafe(new Position(Double.NaN, 64, 0), 1L,
                candidate -> candidate).isEmpty(), "non-finite anchors are rejected");
        check(WaveTwoPhantomLayoutPolicy.selectSafe(anchor, 741L,
                candidate -> null).isEmpty(), "no unsafe candidate is returned without adapter validation");
        System.out.println("WaveTwoPhantomLayoutPolicyTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
