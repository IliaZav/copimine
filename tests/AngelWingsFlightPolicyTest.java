import me.copimine.artifacts.AngelWingsFlightPolicy;

public final class AngelWingsFlightPolicyTest {
    public static void main(String[] args) {
        check(AngelWingsFlightPolicy.FLIGHT_SECONDS == 15, "flight duration");
        check(AngelWingsFlightPolicy.COOLDOWN_SECONDS == 300, "cooldown duration");
        check(AngelWingsFlightPolicy.COUNTDOWN_SECONDS == 3, "countdown duration");
        check(AngelWingsFlightPolicy.countdownSecondsAtElapsed(11L) == -1, "countdown is not early");
        check(AngelWingsFlightPolicy.countdownSecondsAtElapsed(12L) == 3, "countdown starts at three");
        check(AngelWingsFlightPolicy.countdownSecondsAtElapsed(13L) == 2, "countdown two");
        check(AngelWingsFlightPolicy.countdownSecondsAtElapsed(14L) == 1, "countdown one");
        check(AngelWingsFlightPolicy.countdownSecondsAtElapsed(15L) == 0, "countdown reaches zero");
        check(AngelWingsFlightPolicy.countdownSecondsAtElapsed(16L) == -1, "countdown stops after flight");
        check(AngelWingsFlightPolicy.canActivate(100L, 100L), "activation at expiry");
        check(!AngelWingsFlightPolicy.canActivate(100L, 101L), "activation during cooldown");
        check(AngelWingsFlightPolicy.nextCooldownUntil(100L) == 400L, "next cooldown");
        check(AngelWingsFlightPolicy.shouldRevokeFlight(false, false, false), "temporary flight revoke");
        check(!AngelWingsFlightPolicy.shouldRevokeFlight(true, false, false), "preserve pre-existing flight");
        check(!AngelWingsFlightPolicy.shouldRevokeFlight(false, true, false), "preserve creative flight");
        check(!AngelWingsFlightPolicy.shouldRevokeFlight(false, false, true), "preserve spectator flight");
        System.out.println("AngelWingsFlightPolicyTest OK");
    }

    private static void check(boolean condition, String label) {
        if (!condition) {
            throw new AssertionError(label);
        }
    }
}
