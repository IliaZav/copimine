import me.copimine.endevent.domain.BossPhase;
import me.copimine.endevent.domain.InfernoMobWindowPolicy;
import me.copimine.endevent.domain.InfernoMobWindowPolicy.Role;

import java.util.List;

/**
 * N23-P1-09: the Phase 4 (OVERLOAD) infernal End mob window.
 *
 * <p>V5.1 requires a small, mixed, bounded squad of custom-textured Magma
 * Cubes, Blazes and Wither Skeletons that supplement the Guardian and then
 * leave. These checks pin the pure contract the runtime depends on: the window
 * is exactly OVERLOAD, the roster is bounded and party-scaled at 2/5/10/11/20,
 * no body spawns in a protected cell or attacks a safe-zone-protected player
 * without line-of-sight, a Magma Cube cannot recurse without budget, and every
 * body is retired on a phase change, death, wipe or restart.</p>
 */
public final class InfernoMobWindowPolicyTest {

    public static void main(String[] args) {
        windowIsOnlyOverload();
        capIsBoundedAndMonotonic();
        rosterAtPartyThresholdsIsBoundedAndMixed();
        roleBudgetMatchesTheRoster();
        spawnIsFailClosed();
        attackNeedsWindowProtectionAndSight();
        daughtersAreBudgetedNotRecursive();
        everyBodyIsRetiredOutsideTheWindow();
        blazeSalvoIsBounded();
        System.out.println("InfernoMobWindowPolicyTest OK");
    }

    private static void windowIsOnlyOverload() {
        for (BossPhase phase : BossPhase.values()) {
            boolean expected = phase == BossPhase.OVERLOAD;
            check(InfernoMobWindowPolicy.windowActive(phase) == expected,
                    "the infernal window is exactly OVERLOAD, not " + phase);
        }
        check(!InfernoMobWindowPolicy.windowActive(null), "a null phase is never in the window");
    }

    private static void capIsBoundedAndMonotonic() {
        int previous = 0;
        for (int players = 1; players <= 40; players++) {
            int cap = InfernoMobWindowPolicy.capFor(players);
            check(cap >= 2, "even a solo party gets a real pair");
            check(cap <= InfernoMobWindowPolicy.ROSTER_CAP, "the roster cap is never exceeded");
            check(cap >= previous, "the cap never shrinks as the party grows");
            previous = cap;
        }
        check(InfernoMobWindowPolicy.capFor(20) == InfernoMobWindowPolicy.ROSTER_CAP,
                "the 20-player budget reaches exactly the documented cap");
        check(InfernoMobWindowPolicy.ROSTER_CAP <= 9, "the roster stays small next to the Guardian");
    }

    private static void rosterAtPartyThresholdsIsBoundedAndMixed() {
        for (int players : new int[]{2, 5, 10, 11, 20}) {
            List<Role> roster = InfernoMobWindowPolicy.rosterFor(players);
            check(roster.size() == InfernoMobWindowPolicy.capFor(players),
                    "the squad size equals the cap at " + players + " players");
            check(roster.size() <= InfernoMobWindowPolicy.ROSTER_CAP, "the squad is bounded");
            check(roster.contains(Role.MAGMA_CUBE), "a ground Magma Cube is always present");
            check(roster.contains(Role.BLAZE), "a ranged Blaze is always present");
        }
        check(!InfernoMobWindowPolicy.rosterFor(2).contains(Role.WITHER_SKELETON),
                "a stalker appears only from five players on");
        check(InfernoMobWindowPolicy.rosterFor(5).contains(Role.WITHER_SKELETON),
                "a five-player party gets its stalker");
        check(InfernoMobWindowPolicy.rosterFor(20).stream().distinct().count() == 3,
                "the large squad is genuinely mixed, not one role cloned");
    }

    private static void roleBudgetMatchesTheRoster() {
        for (int players : new int[]{1, 2, 4, 5, 8, 10, 11, 15, 20, 40}) {
            int sum = 0;
            for (Role role : Role.values()) {
                int budget = InfernoMobWindowPolicy.roleBudget(players, role);
                check(budget >= 0, "a role budget is never negative");
                sum += budget;
            }
            check(sum == InfernoMobWindowPolicy.rosterFor(players).size(),
                    "the per-role budgets sum to the squad size");
            check(InfernoMobWindowPolicy.roleBudget(players, null) == 0,
                    "a null role has no budget");
        }
    }

    private static void spawnIsFailClosed() {
        int players = 10;
        int cap = InfernoMobWindowPolicy.capFor(players);
        check(InfernoMobWindowPolicy.maySpawn(BossPhase.OVERLOAD, Role.BLAZE, players, 0, false, true),
                "an open breach with room admits the first body");
        check(!InfernoMobWindowPolicy.maySpawn(BossPhase.OVERLOAD, Role.BLAZE, players, cap, false, true),
                "a full roster refuses another body");
        check(!InfernoMobWindowPolicy.maySpawn(BossPhase.OVERLOAD, Role.BLAZE, players, -1, false, true),
                "a negative alive count is refused");
        check(!InfernoMobWindowPolicy.maySpawn(BossPhase.OVERLOAD, null, players, 0, false, true),
                "a null role is refused");
        check(!InfernoMobWindowPolicy.maySpawn(BossPhase.RAGE, Role.BLAZE, players, 0, false, true),
                "a non-Overload phase is refused");
        check(!InfernoMobWindowPolicy.maySpawn(BossPhase.OVERLOAD, Role.BLAZE, players, 0, true, true),
                "a protected landing cell is refused");
        check(!InfernoMobWindowPolicy.maySpawn(BossPhase.OVERLOAD, Role.BLAZE, players, 0, false, false),
                "an unopened breach is refused");
        check(!InfernoMobWindowPolicy.maySpawn(BossPhase.OVERLOAD, Role.WITHER_SKELETON, 2, 0, false, true),
                "a role outside this party's squad is refused");
    }

    private static void attackNeedsWindowProtectionAndSight() {
        check(InfernoMobWindowPolicy.attackAllowed(BossPhase.OVERLOAD, false, true),
                "an in-window, unobstructed, unprotected attack is allowed");
        check(!InfernoMobWindowPolicy.attackAllowed(BossPhase.OVERLOAD, true, true),
                "a safe-zone-protected player is never attacked");
        check(!InfernoMobWindowPolicy.attackAllowed(BossPhase.OVERLOAD, false, false),
                "no line-of-sight means no attack");
        check(!InfernoMobWindowPolicy.attackAllowed(BossPhase.HUNT, false, true),
                "no attack is ever taken outside the window");
    }

    private static void daughtersAreBudgetedNotRecursive() {
        int players = 10;
        int cap = InfernoMobWindowPolicy.capFor(players);
        check(InfernoMobWindowPolicy.daughterAllowed(BossPhase.OVERLOAD, Role.MAGMA_CUBE, players, 0),
                "a Magma Cube may divide while the budget has room");
        check(!InfernoMobWindowPolicy.daughterAllowed(BossPhase.OVERLOAD, Role.MAGMA_CUBE, players, cap),
                "a full roster suppresses the split");
        check(!InfernoMobWindowPolicy.daughterAllowed(BossPhase.OVERLOAD, Role.BLAZE, players, 0),
                "a Blaze never divides");
        check(!InfernoMobWindowPolicy.daughterAllowed(BossPhase.OVERLOAD, Role.WITHER_SKELETON, players, 0),
                "a Wither Skeleton never divides");
        check(!InfernoMobWindowPolicy.daughterAllowed(BossPhase.RAGE, Role.MAGMA_CUBE, players, 0),
                "no split is admitted outside the window");
    }

    private static void everyBodyIsRetiredOutsideTheWindow() {
        check(!InfernoMobWindowPolicy.shouldRetire(BossPhase.OVERLOAD, true, true, false),
                "a healthy in-window body is kept");
        check(InfernoMobWindowPolicy.shouldRetire(BossPhase.RAGE, true, true, false),
                "a body is retired when the window closes");
        check(InfernoMobWindowPolicy.shouldRetire(BossPhase.OVERLOAD, false, true, false),
                "a body is retired when its owner is gone");
        check(InfernoMobWindowPolicy.shouldRetire(BossPhase.OVERLOAD, true, false, false),
                "a body is retired when the party is no longer active");
        check(InfernoMobWindowPolicy.shouldRetire(BossPhase.OVERLOAD, true, true, true),
                "a body is retired on a restart");
    }

    private static void blazeSalvoIsBounded() {
        int min = InfernoMobWindowPolicy.BLAZE_SALVO_MIN;
        int max = InfernoMobWindowPolicy.BLAZE_SALVO_MAX;
        check(min >= 2 && max <= 3, "the salvo band is the authored 2..3 shots");
        check(InfernoMobWindowPolicy.blazeSalvoSize(0) == min, "a tiny salvo is raised to the minimum");
        check(InfernoMobWindowPolicy.blazeSalvoSize(100) == max, "a huge salvo is clamped to the maximum");
        check(InfernoMobWindowPolicy.blazeSalvoSize(2) == 2
                        && InfernoMobWindowPolicy.blazeSalvoSize(3) == 3,
                "an in-band salvo is preserved");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("FAIL: " + message);
        }
    }
}
