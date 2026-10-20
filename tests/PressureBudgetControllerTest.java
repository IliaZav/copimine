import me.copimine.endevent.domain.PressureBudgetController;

public final class PressureBudgetControllerTest {
    public static void main(String[] args) {
        expect(PressureBudgetController.profileForPlayers(1).activePressure() == 0,
                "one player has no official pressure profile");
        checkProfile(2, 6, 4, 1, 1);
        checkProfile(3, 6, 4, 1, 1);
        checkProfile(4, 9, 6, 1, 2);
        checkProfile(5, 9, 6, 1, 2);
        checkProfile(6, 16, 10, 2, 3);
        checkProfile(10, 16, 10, 2, 3);
        checkProfile(11, 22, 14, 3, 3);
        checkProfile(15, 22, 14, 3, 3);
        checkProfile(16, 30, 12, 3, 3);
        checkProfile(20, 30, 12, 3, 3);
        checkProfile(99, 30, 12, 3, 3);

        expect(PressureBudgetController.effectiveProjectileCap(2) == 9,
                "two-player profile includes its ordinary allowance and one reserved five-arrow rain");
        expect(PressureBudgetController.configuredProjectileCap(2) == 4,
                "two-player ordinary projectile admission retains its configured four-child cap");
        expect(PressureBudgetController.configuredProjectileCap(3) == 5,
                "three-player ordinary cap follows four plus players divided by three");
        expect(PressureBudgetController.effectiveProjectileCap(3) == 10,
                "three-player profile includes its ordinary allowance and one reserved rain");
        expect(PressureBudgetController.configuredProjectileCap(4) == 5,
                "four-player ordinary cap retains the existing five-child allowance");
        expect(PressureBudgetController.effectiveProjectileCap(4) == 10,
                "four-player profile adds one named rain lane to its ordinary cap");
        expect(PressureBudgetController.configuredProjectileCap(6) == 6,
                "six-player ordinary cap retains the existing six-child allowance");
        expect(PressureBudgetController.effectiveProjectileCap(6) == 11,
                "larger profiles add only the one named rain lane");
        expect(PressureBudgetController.effectiveProjectileCap(1) == 0,
                "players below the official minimum receive no projectile allowance");

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
        var projectileDiagnostics = PressureBudgetController.diagnostics(2,
                0, 0, 0, 9, 0, null, 0, "wave5", 1L);
        expect(!projectileDiagnostics.hardCapExceeded(),
                "diagnostics include the isolated named arrow-rain reservation");
        expect(PressureBudgetController.diagnostics(2,
                0, 0, 0, 10, 0, null, 0, "wave5", 1L).hardCapExceeded(),
                "diagnostics flag physical projectile counts above the effective limit");
        System.out.println("PressureBudgetControllerTest OK");
    }

    private static void checkProfile(int players, int active, int common,
                                     int elite, int special) {
        var profile = PressureBudgetController.profileForPlayers(players);
        expect(profile.activePressure() == active, players + " active pressure");
        expect(profile.commonCap() == common, players + " common cap");
        expect(profile.eliteCap() == elite, players + " elite cap");
        expect(profile.specialCap() == special, players + " special cap");
        expect(profile.projectileCap() == Math.max(4, Math.min(16, 4 + profile.players() / 3)),
                players + " configured projectile cap stays unchanged");
        expect(profile.activePressure() <= PressureBudgetController.HARD_CAP,
                players + " hard cap");
    }

    private static void expect(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
