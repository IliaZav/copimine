import java.util.List;
import java.util.UUID;
import me.copimine.endevent.domain.ChamberIsolationPolicy;

public final class ChamberIsolationPolicyTest {
    public static void main(String[] args) {
        check(ChamberIsolationPolicy.chamberCount(2) == 2, "two players need two chambers");
        check(ChamberIsolationPolicy.chamberCount(3) == 3, "three players need three chambers");
        check(ChamberIsolationPolicy.chamberCount(20) == 4, "twenty players are capped at four chambers");
        UUID a = UUID.nameUUIDFromBytes("a".getBytes());
        UUID b = UUID.nameUUIDFromBytes("b".getBytes());
        UUID c = UUID.nameUUIDFromBytes("c".getBytes());
        var assignment = ChamberIsolationPolicy.assign(List.of(a, b, c));
        check(assignment.chamberCount() == 3, "assignment uses three chambers");
        check(!ChamberIsolationPolicy.sameRoom(a, b, assignment), "different rooms stay isolated");
        check(!ChamberIsolationPolicy.allowsMobTarget(0, b, assignment, false),
                "closed room mob cannot target another room");
        check(ChamberIsolationPolicy.allowsMobTarget(0, a, assignment, false),
                "closed room mob can target its assigned player");
        check(ChamberIsolationPolicy.allowsInteraction(a, b, assignment, true), "open passage removes isolation");
        check(assignment.playersIn(0).size() == 1, "each three-player chamber starts with one player");
        check(ChamberIsolationPolicy.containsPoint(0, 0.0D, -8.0D, 3),
                "room sector contains its anchor");
        check(!ChamberIsolationPolicy.containsPoint(1, 0.0D, -8.0D, 3),
                "room sector rejects a neighbouring anchor");
        check(!ChamberIsolationPolicy.containsPoint(0, 0.0D, 0.0D, 3),
                "Core column is not a room destination");
        check(ChamberIsolationPolicy.sectorRadius(4, 12.0D) == 12.0D,
                "sector radius stays deterministic");
        System.out.println("ChamberIsolationPolicyTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
