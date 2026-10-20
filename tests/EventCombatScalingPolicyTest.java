import me.copimine.endevent.domain.EventCombatScalingPolicy;

public final class EventCombatScalingPolicyTest {
    public static void main(String[] args) {
        checkProfile(2, 4, 1, 1, 1);
        checkProfile(3, 5, 1, 1, 1);
        checkProfile(5, 7, 1, 1, 1);
        checkProfile(10, 10, 2, 2, 2);
        checkProfile(20, 12, 3, 3, 3);
        EventCombatScalingPolicy.Profile capped = EventCombatScalingPolicy.forPlayers(99);
        check(capped.players() == 20 && capped.maxConcurrentHostiles() <= 56,
                "party sizes above twenty must stay at the bounded twenty-player profile");
        for (int players = 2; players <= 20; players++) {
            EventCombatScalingPolicy.Profile profile = EventCombatScalingPolicy.forPlayers(players);
            check(profile.commonMobs() >= 4 && profile.commonMobs() <= 12,
                    "common pressure must stay within the documented range");
            check(profile.maxConcurrentHostiles() <= 56,
                    "the pressure profile must never exceed the global hostile cap");
        }
        System.out.println("EventCombatScalingPolicyTest OK");
    }

    private static void checkProfile(int players, int common, int elite, int heavy, int mechanics) {
        EventCombatScalingPolicy.Profile profile = EventCombatScalingPolicy.forPlayers(players);
        check(profile.players() == players, "profile must retain the clamped party size");
        check(profile.commonMobs() == common, players + " players common pressure mismatch");
        check(profile.elites() == elite, players + " players elite pressure mismatch");
        check(profile.heavyMechanics() == heavy, players + " players heavy pressure mismatch");
        check(profile.mechanicSlots() == mechanics, players + " players mechanic slots mismatch");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
