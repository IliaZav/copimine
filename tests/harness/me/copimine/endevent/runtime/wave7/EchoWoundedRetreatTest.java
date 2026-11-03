package me.copimine.endevent.runtime.wave7;

import java.util.Objects;
import me.copimine.endevent.domain.wave7.EchoBehaviorStrategy;
import me.copimine.endevent.runtime.wave7.EchoDuelCombatRuntimeAdapter.MovementIntent;
import me.copimine.endevent.runtime.wave7.EchoDuelCombatRuntimeAdapter.Observation;

/**
 * A wounded copy must break contact instead of trading blows it cannot win.
 *
 * <p>Before this, healing only engaged when the copy still carried food, so a
 * melee copy with an empty inventory simply stood there and died. The retreat
 * is deliberately not applied to ranged copies: they already kite into their
 * hold band, and turning them around would pull them out of their own range.</p>
 */
public final class EchoWoundedRetreatTest {
    private static int failures;

    public static void main(String[] args) {
        brawlerRetreatsWhenWounded();
        brawlerHoldsWhenHealthy();
        rangedCopyStillKites();
        retreatDoesNotBreakTheBand();

        if (failures > 0) {
            System.out.println(failures + " failure(s)");
            System.exit(1);
        }
        System.out.println("EchoWoundedRetreatTest OK");
    }

    /** Melee-dominant, prefers close quarters. */
    private static EchoBehaviorStrategy brawler() {
        return new EchoBehaviorStrategy(800, 200, 400, 500, 300, 600, 0b111_1111, true,
                200, 100, 400, 500, 250);
    }

    /** Ranged-dominant, keeps distance. */
    private static EchoBehaviorStrategy archer() {
        return new EchoBehaviorStrategy(200, 800, 500, 300, 200, 700, 0b111_1111, true,
                250, 800, 400, 300, 250);
    }

    private static Observation inMelee() {
        return new Observation(100L, 4.0D, true, true, false);
    }

    private static void brawlerRetreatsWhenWounded() {
        // Standing right next to the owner, below the healing threshold.
        MovementIntent intent = EchoDuelCombatRuntimeAdapter.movementIntent(brawler(), inMelee(), true);
        check(intent == MovementIntent.KITE,
                "a wounded brawler must open the gap, got " + intent);
    }

    private static void brawlerHoldsWhenHealthy() {
        MovementIntent intent = EchoDuelCombatRuntimeAdapter.movementIntent(brawler(), inMelee(), false);
        check(intent != MovementIntent.KITE || isLateral(intent),
                "a healthy brawler in reach must not run away");
    }

    private static void rangedCopyStillKites() {
        // Close in on a ranged owner: it kites. That is unchanged, and it must
        // also be true when healthy, so the wounded flag is not the only cause.
        MovementIntent healthy = EchoDuelCombatRuntimeAdapter.movementIntent(archer(),
                new Observation(100L, 1.0D, true, true, false), false);
        check(healthy == MovementIntent.KITE, "a ranged copy keeps its distance, got " + healthy);
    }

    private static void retreatDoesNotBreakTheBand() {
        // Wounded is a movement decision only: it must never be expressed as an
        // impossible intent or throw.
        for (double distanceSquared : new double[]{0.0D, 1.0D, 16.0D, 400.0D, 100_000.0D}) {
            MovementIntent intent = EchoDuelCombatRuntimeAdapter.movementIntent(brawler(),
                    new Observation(7L, distanceSquared, true, true, false), true);
            check(intent != null, "distanceSquared=" + distanceSquared + " produced no intent");
        }
    }

    private static boolean isLateral(MovementIntent intent) {
        return intent == MovementIntent.STRAFE_LEFT || intent == MovementIntent.STRAFE_RIGHT;
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            failures++;
            System.out.println("FAIL: " + message);
        }
    }
}
