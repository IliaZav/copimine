import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import me.copimine.endevent.domain.WaveRewardPolicy;

public final class WaveRewardPolicyTest {
    public static void main(String[] args) {
        Map<String, Integer> configured = new LinkedHashMap<>();
        configured.put("ENDER_PEARL", 24);
        configured.put("AMETHYST_SHARD", 20);
        configured.put("DIAMOND", 6);
        configured.put("ECHO_SHARD", 2);

        WaveRewardPolicy.RewardBundle first = WaveRewardPolicy.bundle(5, 0, 2, configured);
        WaveRewardPolicy.RewardBundle second = WaveRewardPolicy.bundle(5, 1, 2, configured);
        check(first.stacks().size() <= 8, "personal bundle must stay within the entity budget");
        check(first.stacks().equals(second.stacks()), "participants must receive the same fair bundle");
        check(first.stacks().stream().allMatch(stack -> stack.amount() > 0), "bundle amounts must be positive");
        check(first.stacks().stream().map(WaveRewardPolicy.RewardStack::material).distinct().count()
                == first.stacks().size(), "bundle must merge duplicate material entries");

        boolean rareA = WaveRewardPolicy.sharedRareRoll("event-123", 5);
        boolean rareB = WaveRewardPolicy.sharedRareRoll("event-123", 5);
        check(rareA == rareB, "shared rare decision must be deterministic per event and wave");
        check(!WaveRewardPolicy.sharedRareRoll("", 5), "blank event id must fail closed");

        boolean rejected = false;
        try {
            WaveRewardPolicy.bundle(6, 0, 2, configured);
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        check(rejected, "unknown waves must be rejected");
        System.out.println("WaveRewardPolicyTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
