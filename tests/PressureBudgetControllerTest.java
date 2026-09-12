import me.copimine.endevent.domain.PressureBudgetController;

public final class PressureBudgetControllerTest {
    public static void main(String[] args) {
        expect(PressureBudgetController.profileForPlayers(1).activePressure() == 0,
                "one player has no official pressure profile");
        checkProfile(2, 6, 4, 1, 1, 2);
        checkProfile(3, 6, 4, 1, 1, 2);
        checkProfile(4, 9, 6, 1, 2, 2);
        checkProfile(5, 9, 6, 1, 2, 2);
        checkProfile(6, 16, 10, 2, 3, 2);
        checkProfile(10, 16, 10, 2, 3, 2);
        checkProfile(11, 22, 14, 3, 3, 3);
        checkProfile(15, 22, 14, 3, 3, 3);
        checkProfile(16, 30, 12, 3, 3, 3);
        checkProfile(20, 30, 12, 3, 3, 3);
        checkProfile(99, 30, 12, 3, 3, 3);

        var allowance = PressureBudgetController.allowanceFor(2, 0, 0, 0,
                99, 99, 99);
        expect(allowance.common() == 4 && allowance.elite() == 1 && allowance.special() == 1,
                "two-player profile must cap each role and active pressure");
        expect(allowance.resultingActive() == 6 && allowance.resultingActive() <= 56,
                "allowance must stay inside active pressure and hard cap");

        var full = PressureBudgetController.allowanceFor(20, 12, 2, 2, 99, 99, 99);
        expect(full.total() == 2 && full.resultingActive() == 18,
                "full profile must replenish only uncapped role slots");
        expect(PressureBudgetController.clampTotal(1000, 20) == 30,
                "requested spawn count must be bounded by the active pressure");
        System.out.println("PressureBudgetControllerTest OK");
    }

    private static void checkProfile(int players, int active, int common,
                                     int elite, int special, int maxFocused) {
        var profile = PressureBudgetController.profileForPlayers(players);
        expect(profile.activePressure() == active, players + " active pressure");
        expect(profile.commonCap() == common, players + " common cap");
        expect(profile.eliteCap() == elite, players + " elite cap");
        expect(profile.specialCap() == special, players + " special cap");
        expect(profile.maxFocusedAttackers() == maxFocused, players + " focus cap");
        expect(profile.activePressure() <= PressureBudgetController.HARD_CAP,
                players + " hard cap");
    }

    private static void expect(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
