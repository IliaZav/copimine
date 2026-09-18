import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import me.copimine.endevent.domain.TargetPressurePolicy;

public final class TargetPressurePolicyTest {
    public static void main(String[] args) {
        List<UUID> players = new ArrayList<>();
        for (int index = 0; index < 10; index++) {
            players.add(new UUID(0L, index + 1L));
        }
        TargetPressurePolicy.Assignment assignment = TargetPressurePolicy.distribute(players, 30, 3, 0);
        Map<UUID, Integer> counts = new LinkedHashMap<>();
        for (UUID player : assignment.targets()) counts.merge(player, 1, Integer::sum);
        check(assignment.targets().size() == 30, "all bounded attackers must receive a target");
        check(counts.size() == 10, "ten living alternatives must all receive pressure");
        check(counts.values().stream().allMatch(value -> value <= 3),
                "no target may receive more than the configured focus cap");
        check(assignment.nextCursor() >= 0, "cursor must remain usable for the next pack");

        TargetPressurePolicy.Assignment saturated = TargetPressurePolicy.distribute(players, 40, 3, 4);
        check(saturated.targets().size() == 30,
                "attackers beyond the bounded target budget must be held for a later group");
        check(TargetPressurePolicy.distribute(List.of(), 4, 3, 0).targets().isEmpty(),
                "no living targets means no attack assignments");
        System.out.println("TargetPressurePolicyTest OK");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
