import me.copimine.endevent.domain.ObeliskScalingPolicy;

public final class RiftObeliskScalingPolicyTest {
    public static void main(String[] args) {
        check(ObeliskScalingPolicy.profileForPlayers(1).obeliskCount() == 0,
                "one player is not a valid assault party");
        check(ObeliskScalingPolicy.profileForPlayers(2).obeliskCount() == 4,
                "two players use the bounded four-obelisk profile");
        check(ObeliskScalingPolicy.profileForPlayers(10).obeliskCount() == 5,
                "ten players use five obelisks");
        check(ObeliskScalingPolicy.profileForPlayers(20).obeliskCount() == 6,
                "twenty players use six obelisks");
        check(ObeliskScalingPolicy.MAX_OBELISKS == 6,
                "Wave 4 hard cap is six physical obelisks");
        System.out.println("RiftObeliskScalingPolicyTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
