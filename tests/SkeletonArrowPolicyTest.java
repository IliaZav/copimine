import me.copimine.endevent.domain.SkeletonArrowPolicy;

public final class SkeletonArrowPolicyTest {
    public static void main(String[] args) {
        check(SkeletonArrowPolicy.forShot(3, true, 99L) == SkeletonArrowPolicy.ArrowKind.COMMON,
                "Wave 3 elite arrows remain ordinary physical arrows");
        check(SkeletonArrowPolicy.forShot(5, true, 99L) == SkeletonArrowPolicy.ArrowKind.EXPLOSIVE,
                "only Wave 5 elite bow shots use explosive arrows");
        check(SkeletonArrowPolicy.forShot(6, true, 99L) == SkeletonArrowPolicy.ArrowKind.COMMON,
                "Wave 6 pressure elites use Arrow Rain instead of explosive bow shots");
        check(SkeletonArrowPolicy.forShot(false, 0L) == SkeletonArrowPolicy.ArrowKind.POISON_NAUSEA,
                "the first 20 percent bucket must carry poison and nausea");
        check(SkeletonArrowPolicy.forShot(false, 19L) == SkeletonArrowPolicy.ArrowKind.POISON_NAUSEA,
                "the status bucket must include 19");
        check(SkeletonArrowPolicy.forShot(false, 20L) == SkeletonArrowPolicy.ArrowKind.COMMON,
                "the status bucket must stop at 20 percent");
        check(SkeletonArrowPolicy.STATUS_DURATION_TICKS == 140,
                "poison and nausea must last seven seconds");
        check(SkeletonArrowPolicy.STATUS_AMPLIFIER == 2,
                "level III must use Bukkit amplifier II");
        check(!SkeletonArrowPolicy.breaksBlocks(),
                "explosive arrows must never break blocks");
        check(SkeletonArrowPolicy.EXPLOSIVE_POWER > 0.0F,
                "explosive arrows need a visible damage pulse");
        check(SkeletonArrowPolicy.EXPLOSIVE_DAMAGE > 0.0D
                        && SkeletonArrowPolicy.EXPLOSIVE_DAMAGE < 10.0D,
                "explosive arrows need bounded player-only impact damage");
        System.out.println("SkeletonArrowPolicyTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
