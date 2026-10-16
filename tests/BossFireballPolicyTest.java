import me.copimine.endevent.domain.BossFireballPolicy;

public final class BossFireballPolicyTest {
    public static void main(String[] args) {
        require(BossFireballPolicy.telegraphTicks(20)
                        >= BossFireballPolicy.MIN_TELEGRAPH_TICKS,
                "the long fireball cast must have a readable wind-up");
        require(BossFireballPolicy.SPEED > 0.0D
                        && BossFireballPolicy.SPEED <= 0.8D,
                "fireball speed must remain bounded");
        require(BossFireballPolicy.canDestroyBlocks(
                        BossFireballPolicy.YIELD, BossFireballPolicy.INCENDIARY),
                "boss fireball must never destroy blocks or leave fire");
        require(!BossFireballPolicy.canDestroyBlocks(1.0F, false),
                "a positive explosion yield must fail closed");
        System.out.println("BossFireballPolicyTest OK");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
