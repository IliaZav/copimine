import me.copimine.endevent.domain.RiftFracturePolicy;

public final class RiftFracturePolicyTest {
    public static void main(String[] args) {
        check(RiftFracturePolicy.countForPlayers(0) == 0, "empty roster creates no fractures");
        check(RiftFracturePolicy.countForPlayers(2) == 3, "duo uses three bounded fractures");
        check(RiftFracturePolicy.countForPlayers(5) == 4, "five players use four fractures");
        check(RiftFracturePolicy.countForPlayers(8) == 5, "eight players use five fractures");
        check(RiftFracturePolicy.countForPlayers(20) == 6, "twenty players cap at six fractures");
        check(RiftFracturePolicy.countForPlayers(100) == RiftFracturePolicy.MAX_ACTIVE,
                "fractures never exceed the hard cap");
        check(RiftFracturePolicy.canDamage(30, 30, 90, false), "active fracture can damage once");
        check(!RiftFracturePolicy.canDamage(90, 30, 90, false), "expired fracture cannot damage");
        check(!RiftFracturePolicy.canDamage(40, 30, 90, true), "fracture damage is once per player");
        RiftFracturePolicy.Profile profile = RiftFracturePolicy.profileForPlayers(20);
        check(profile.count() == 6 && profile.radius() <= 5.0D && profile.damage() <= 20.0D,
                "profile remains bounded");
        System.out.println("RiftFracturePolicyTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
