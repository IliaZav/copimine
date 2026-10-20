import me.copimine.artifacts.VeinMinerPolicy;

public final class VeinMinerPolicyTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        check(VeinMinerPolicy.MAX_BLOCKS == 32, "hard cap");
        check(VeinMinerPolicy.isWhitelisted("DIAMOND_ORE"), "diamond ore");
        check(VeinMinerPolicy.isWhitelisted("DEEPSLATE_DIAMOND_ORE"), "deepslate diamond ore");
        check(VeinMinerPolicy.isWhitelisted("ANCIENT_DEBRIS"), "ancient debris");
        check(!VeinMinerPolicy.isWhitelisted("STONE"), "stone excluded");
        check(!VeinMinerPolicy.isWhitelisted("DIAMOND_BLOCK"), "diamond block excluded");
        check(VeinMinerPolicy.sameFamily("DIAMOND_ORE", "DEEPSLATE_DIAMOND_ORE"), "deepslate family");
        check(!VeinMinerPolicy.sameFamily("DIAMOND_ORE", "IRON_ORE"), "mixed family");
        check(!VeinMinerPolicy.sameFamily("DIAMOND_ORE", "STONE"), "non-ore family");
        System.out.println("VeinMinerPolicyTest OK");
    }
}
