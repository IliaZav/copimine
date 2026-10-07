import me.copimine.endevent.domain.ShardPassivePolicy;

public final class ShardPassivePolicyTest {
    public static void main(String[] args) {
        check(ShardPassivePolicy.endPassivesActive(true, true),
                "authentic shard must activate End passives in the End");
        check(!ShardPassivePolicy.endPassivesActive(false, true),
                "spoofed shard must not activate End passives");
        check(!ShardPassivePolicy.endPassivesActive(true, false),
                "End passives must not leak into other worlds");
        check(ShardPassivePolicy.END_EFFECT_AMPLIFIER == 1,
                "Strength and Speed must be level II");
        check(ShardPassivePolicy.END_EFFECT_REFRESH_TICKS == 40,
                "owned effects must refresh on a bounded 40-tick window");
        check(ShardPassivePolicy.cancelEnderPearlSelfDamage(true),
                "authentic shard must cancel pearl self-damage");
        check(!ShardPassivePolicy.cancelEnderPearlSelfDamage(false),
                "spoofed shard must not cancel pearl self-damage");
        check(ShardPassivePolicy.endermanDamage(10.0D, true) == 5.0D,
                "authentic shard must halve Enderman damage");
        check(ShardPassivePolicy.endermanDamage(10.0D, false) == 10.0D,
                "other players must keep normal Enderman damage");
        check(ShardPassivePolicy.endermanDamage(Double.NaN, true) == 0.0D,
                "NaN damage must fail closed");
        check(ShardPassivePolicy.endermanDamage(Double.POSITIVE_INFINITY, true) == 0.0D,
                "positive infinity damage must fail closed");
        check(ShardPassivePolicy.endermanDamage(Double.NEGATIVE_INFINITY, false) == 0.0D,
                "negative infinity damage must fail closed");
        check(ShardPassivePolicy.endermanDamage(-1.0D, false) == 0.0D,
                "negative damage must fail closed");
        System.out.println("ShardPassivePolicyTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
