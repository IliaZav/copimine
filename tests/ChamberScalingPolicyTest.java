import me.copimine.endevent.domain.ChamberScalingPolicy;

public final class ChamberScalingPolicyTest {
    public static void main(String[] args) {
        check(ChamberScalingPolicy.forPlayers(1).healthMultiplier() == 0.75D, "one player uses reduced health");
        check(ChamberScalingPolicy.forPlayers(2).healthMultiplier() == 1.0D, "two players are baseline");
        check(ChamberScalingPolicy.forPlayers(3).cadenceMultiplier() == 1.10D, "three players increase cadence");
        check(ChamberScalingPolicy.forPlayers(4).healthMultiplier() == 1.45D, "four players use bounded health");
        check(ChamberScalingPolicy.forPlayers(20).healthMultiplier() == 1.60D, "larger rooms are capped");
        System.out.println("ChamberScalingPolicyTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
