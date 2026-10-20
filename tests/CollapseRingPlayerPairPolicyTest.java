import me.copimine.endevent.domain.CollapseRingDefinition;
import me.copimine.endevent.domain.CollapseRingPlayerPairPolicy;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class CollapseRingPlayerPairPolicyTest {
    public static void main(String[] args) {
        List<UUID> players = List.of(
                UUID.fromString("00000000-0000-0000-0000-000000000041"),
                UUID.fromString("00000000-0000-0000-0000-000000000042"),
                UUID.fromString("00000000-0000-0000-0000-000000000043"),
                UUID.fromString("00000000-0000-0000-0000-000000000044"),
                UUID.fromString("00000000-0000-0000-0000-000000000045"));
        List<CollapseRingPlayerPairPolicy.PlayerPair> pairs =
                CollapseRingPlayerPairPolicy.assign(players, 3);
        check(pairs.size() == 3, "five players must form three deterministic pair lanes");
        check(pairs.get(0).players().size() == 2 && pairs.get(1).players().size() == 2
                        && pairs.get(2).players().size() == 1,
                "an odd roster keeps the final participant in an explicit solo lane");
        check(CollapseRingPlayerPairPolicy.playersForRing(2, players, 3)
                        .equals(pairs.get(2).players()),
                "each ring must resolve one stable participant group");
        check(CollapseRingPlayerPairPolicy.relativeAngle(1_000L, 1_100L)
                        > 0.0D,
                "ring rotation must be anchored to the local pair start tick");

        CollapseRingDefinition.RingDefinition definition = CollapseRingDefinition.forRing(
                1, 10.5D, 70.0D, -3.5D, pairs.get(1).players(),
                Set.of(UUID.fromString("00000000-0000-0000-0000-000000000099")));
        check(definition.radius() == 14.0D,
                "RingDefinition must own the canonical second ring radius");
        check(definition.containsPlayer(10.5D + 14.0D, 70.0D, -3.5D),
                "RingDefinition player containment must use its own spatial data");
        check(definition.containsMob(10.5D + 14.0D, 70.0D, -3.5D),
                "RingDefinition mob containment must use the same radius");
        check(definition.visual().pointCount() == 80,
                "visual settings must be derived from the same ring definition");
        System.out.println("CollapseRingPlayerPairPolicyTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
