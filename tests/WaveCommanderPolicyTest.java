import me.copimine.endevent.domain.WaveCommanderPolicy;

public final class WaveCommanderPolicyTest {
    public static void main(String[] args) {
        check(!WaveCommanderPolicy.isDifficultWave(1), "wave one must not spawn a commander");
        check(!WaveCommanderPolicy.isDifficultWave(2), "wave two must not spawn a commander");
        for (int wave = 3; wave <= 6; wave++) {
            check(WaveCommanderPolicy.isDifficultWave(wave), "difficult wave range must include " + wave);
            check(WaveCommanderPolicy.shouldAssign(wave, true, false),
                    "one elite must be eligible as commander on wave " + wave);
            check(!WaveCommanderPolicy.shouldAssign(wave, true, true),
                    "a wave may have only one commander");
            check(!WaveCommanderPolicy.shouldAssign(wave, false, false),
                    "a common mob cannot become commander");
        }
        check(WaveCommanderPolicy.AURA_RADIUS_BLOCKS == 10.0D,
                "commander aura must stay local");
        check(WaveCommanderPolicy.AURA_DURATION_TICKS == 40,
                "commander aura refresh must be two seconds");
        check(WaveCommanderPolicy.MAX_LIVE_MOBS == 56,
                "commander policy must share the hard live-mob cap");
        check(WaveCommanderPolicy.displayName("Элитный эндермен").startsWith("Командир волны"),
                "commander name must be visible to players");
        System.out.println("WaveCommanderPolicyTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
