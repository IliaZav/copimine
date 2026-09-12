import me.copimine.endevent.domain.EndRiftObjective;
import me.copimine.endevent.domain.WaveCommanderPolicy;

public final class WaveCommanderPolicyTest {
    public static void main(String[] args) {
        check(!WaveCommanderPolicy.supportsCommander(
                        EndRiftObjective.Objective.RIFT_CARRIERS),
                "wave one must not spawn a commander");
        check(!WaveCommanderPolicy.supportsCommander(
                        EndRiftObjective.Objective.RIFT_HUNT),
                "wave two must not spawn a commander");
        for (EndRiftObjective.Objective objective : EndRiftObjective.Objective.values()) {
            boolean supported = WaveCommanderPolicy.supportsCommander(objective);
            check(WaveCommanderPolicy.shouldAssign(objective, true, false) == supported,
                    "commander assignment must follow objective capability: " + objective);
            check(!WaveCommanderPolicy.shouldAssign(objective, true, true),
                    "an objective may have only one commander");
            check(!WaveCommanderPolicy.shouldAssign(objective, false, false),
                    "a common mob cannot become commander");
        }
        check(WaveCommanderPolicy.supportsCommander(
                        EndRiftObjective.Objective.REALITY_SPLIT),
                "Reality Split explicitly supports one chamber commander");
        check(WaveCommanderPolicy.AURA_RADIUS_BLOCKS == 10.0D,
                "commander aura must stay local");
        check(WaveCommanderPolicy.AURA_DURATION_TICKS == 40,
                "commander aura refresh must be two seconds");
        check(WaveCommanderPolicy.displayName("Элитный эндермен")
                        .startsWith("Командир волны"),
                "commander name must be visible to players");
        System.out.println("WaveCommanderPolicyTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
