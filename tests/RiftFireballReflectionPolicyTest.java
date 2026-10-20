import me.copimine.endevent.domain.RiftFireballReflectionPolicy;

public final class RiftFireballReflectionPolicyTest {
    public static void main(String[] args) {
        check(!RiftFireballReflectionPolicy.canReflect(true, true, false, false),
                "a Wave 7 reflection projectile cannot be reflected by a player in another chamber");
        check(RiftFireballReflectionPolicy.canReflect(true, false, true, false),
                "the assigned chamber participant can reflect the Eye projectile");
        check(RiftFireballReflectionPolicy.canReflect(false, true, false, false),
                "ordinary encounter fireballs remain reflectable by an active participant");
        check(RiftFireballReflectionPolicy.canReflect(false, false, false, true),
                "local probe fireballs remain reflectable by the local probe participant");
        System.out.println("RiftFireballReflectionPolicyTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
