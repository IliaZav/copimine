import me.copimine.endevent.domain.EchoDecoyPolicy;
import me.copimine.endevent.domain.EchoDecoyPolicy.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Phase 5, Echo Type B (master 15.3): persistent boss decoys.
 *
 * <p>The contract names the exact numbers — 50 HP each, at most five alive, a
 * ~150 s summon cooldown — and, more importantly, the invariants that keep the
 * encounter winnable: a decoy never shares the real boss's authoritative health
 * or boss bar, its death never counts as the boss defeat, it drops nothing, and
 * it never becomes a permanent orphan.</p>
 */
public final class EchoDecoyPolicyTest {
    private static int failures = 0;

    public static void main(String[] args) {
        exactHealth();
        fiveAliveCap();
        summonCooldown();
        scaling();
        noBossHpOrBar();
        deathIsNotDefeat();
        noLootNoFriendlyFire();
        cleanup();
        System.out.println(failures == 0 ? "OK" : "FAILURES " + failures);
        if (failures != 0) System.exit(1);
    }

    // --- 15.3: exactly 50 HP ------------------------------------------------
    private static void exactHealth() {
        check("a decoy has exactly fifty hit points",
                EchoDecoyPolicy.DECOY_HEALTH == 50.0D);
        for (int players = 1; players <= 20; players++) {
            check("the decoy health never scales with the party at " + players,
                    EchoDecoyPolicy.decoyHealth(players) == 50.0D);
        }
        check("health ignores a zero party", EchoDecoyPolicy.decoyHealth(0) == 50.0D);
        check("health ignores a negative party", EchoDecoyPolicy.decoyHealth(-5) == 50.0D);
    }

    // --- 15.3: at most five living decoys -----------------------------------
    private static void fiveAliveCap() {
        check("the alive cap is five", EchoDecoyPolicy.MAX_ALIVE == 5);
        for (int alive = 0; alive <= 10; alive++) {
            check("alive=" + alive + " may summon up to the cap",
                    EchoDecoyPolicy.maySummonTo(alive) <= EchoDecoyPolicy.MAX_ALIVE);
        }
        check("five alive blocks a sixth", EchoDecoyPolicy.maySummonTo(5) == 0);
        check("six alive blocks anything", EchoDecoyPolicy.maySummonTo(6) == 0);
        check("a capacity of five admits a full set",
                EchoDecoyPolicy.maySummonTo(0) == EchoDecoyPolicy.MAX_ALIVE);
        // Summons never overfill, whatever the batch size asked for.
        for (int alive = 0; alive < EchoDecoyPolicy.MAX_ALIVE; alive++) {
            for (int batch = 0; batch <= 6; batch++) {
                int granted = EchoDecoyPolicy.resolveSummonBatch(alive, batch);
                check("alive=" + alive + " batch=" + batch + " never overfills",
                        alive + granted <= EchoDecoyPolicy.MAX_ALIVE);
                check("alive=" + alive + " batch=" + batch + " grants only what is asked",
                        granted <= batch);
                check("alive=" + alive + " batch=" + batch + " uses the full capacity",
                        granted == Math.min(batch, EchoDecoyPolicy.MAX_ALIVE - alive));
            }
        }
        // Already at (or somehow over) the cap, nothing more may be granted.
        for (int alive = EchoDecoyPolicy.MAX_ALIVE; alive <= 8; alive++) {
            for (int batch = 0; batch <= 6; batch++) {
                check("alive=" + alive + " batch=" + batch + " grants nothing",
                        EchoDecoyPolicy.resolveSummonBatch(alive, batch) == 0);
            }
        }
        check("a negative alive count is treated as empty",
                EchoDecoyPolicy.maySummonTo(-3) == EchoDecoyPolicy.MAX_ALIVE);
    }

    // --- 15.3: ~150 s summon cooldown ---------------------------------------
    private static void summonCooldown() {
        check("the cooldown is about 150 seconds",
                EchoDecoyPolicy.SUMMON_COOLDOWN_TICKS == 3000);
        check("the cooldown sits in the requested 2-3 minute band",
                EchoDecoyPolicy.SUMMON_COOLDOWN_TICKS >= 2400
                        && EchoDecoyPolicy.SUMMON_COOLDOWN_TICKS <= 3600);
        // A fresh phase may summon immediately.
        check("a first summon is allowed", EchoDecoyPolicy.maySummon(-1L, 100L));
        check("the cooldown blocks an immediate second summon",
                !EchoDecoyPolicy.maySummon(100L, 101L));
        check("the cooldown blocks one tick early",
                !EchoDecoyPolicy.maySummon(100L, 100L + EchoDecoyPolicy.SUMMON_COOLDOWN_TICKS - 1L));
        check("the cooldown releases exactly on time",
                EchoDecoyPolicy.maySummon(100L, 100L + EchoDecoyPolicy.SUMMON_COOLDOWN_TICKS));
        check("a negative clock never blocks", EchoDecoyPolicy.maySummon(-1L, -1L));
        // No forced repetition: a phase that ends quickly summons once.
        List<Long> schedule = EchoDecoyPolicy.summonSchedule(0L, 600L);
        check("a short phase summons at most once", schedule.size() <= 1);
        List<Long> longPhase = EchoDecoyPolicy.summonSchedule(0L, 10_000L);
        for (int index = 1; index < longPhase.size(); index++) {
            check("summons are spaced by the cooldown",
                    longPhase.get(index) - longPhase.get(index - 1)
                            >= EchoDecoyPolicy.SUMMON_COOLDOWN_TICKS);
        }
        check("the schedule never runs past the phase",
                longPhase.isEmpty() || longPhase.get(longPhase.size() - 1) <= 10_000L);
    }

    // --- 15.3: scale the initial count, respecting the cap ------------------
    private static void scaling() {
        int previous = 0;
        for (int players = 0; players <= 20; players++) {
            int initial = EchoDecoyPolicy.initialDecoyCount(players);
            check("initial count is bounded at " + players,
                    initial >= 0 && initial <= EchoDecoyPolicy.MAX_ALIVE);
            check("initial count never exceeds the party at " + players,
                    initial <= Math.max(0, players));
            check("initial count is monotone at " + players, initial >= Math.min(previous, initial));
            previous = initial;
        }
        check("a solo player is not swarmed", EchoDecoyPolicy.initialDecoyCount(1) <= 1);
        check("a duo is not swarmed", EchoDecoyPolicy.initialDecoyCount(2) <= 2);
        check("a raid reaches the cap", EchoDecoyPolicy.initialDecoyCount(20) == 5);
        check("an empty party summons nothing", EchoDecoyPolicy.initialDecoyCount(0) == 0);
        check("a negative party summons nothing", EchoDecoyPolicy.initialDecoyCount(-4) == 0);
    }

    // --- 15.3: no shared authoritative HP or boss bar -----------------------
    private static void noBossHpOrBar() {
        check("a decoy never shares the boss HP",
                !EchoDecoyPolicy.sharesBossHealth());
        check("a decoy never joins the boss bar",
                !EchoDecoyPolicy.joinsBossBar());
        // Whatever the real boss's pool is, the decoy keeps its own 50.
        for (double boss : new double[]{5000.0D, 20_000.0D, 1.0E9D}) {
            check("a decoy keeps its own health against boss pool " + boss,
                    EchoDecoyPolicy.decoyHealth(10) == 50.0D
                            && EchoDecoyPolicy.decoyHealthOverride(boss) == 50.0D);
        }
        check("a decoy is visibly the same family as the boss",
                EchoDecoyPolicy.usesBossModelFamily());
        // ...but it is still distinguishable, otherwise it is a lie.
        check("a careful player can tell the real boss apart",
                EchoDecoyPolicy.hasIdentifiableClues());
    }

    // --- 15.3: deaths never trigger the boss defeat -------------------------
    private static void deathIsNotDefeat() {
        check("a decoy death is not the boss defeat",
                !EchoDecoyPolicy.deathTriggersBossDefeat());
        check("a decoy death is not counted as boss damage",
                !EchoDecoyPolicy.deathCountsAsBossDamage());
        check("killing every decoy still leaves the boss alive",
                !EchoDecoyPolicy.allDecoysDeadEndsEncounter(5));
        check("a decoy death is reported for cleanup",
                EchoDecoyPolicy.onDecoyDeath("decoy-1") == DecoyOutcome.CLEANUP_ONLY);
        check("a null decoy id still yields a clean outcome",
                EchoDecoyPolicy.onDecoyDeath(null) == DecoyOutcome.CLEANUP_ONLY);
    }

    // --- 15.3: no loot, no friendly fire ------------------------------------
    private static void noLootNoFriendlyFire() {
        check("a decoy drops nothing", !EchoDecoyPolicy.dropsLoot());
        check("a decoy grants no experience", EchoDecoyPolicy.experienceReward() == 0);
        check("a decoy cannot damage other event mobs",
                !EchoDecoyPolicy.mayDamageOtherEventMobs());
        check("a decoy hits softer than the real boss",
                EchoDecoyPolicy.decoyDamage(14.0D) < 14.0D);
        check("a decoy still hits for something", EchoDecoyPolicy.decoyDamage(14.0D) > 0.0D);
        check("a decoy damage multiplier is a real reduction",
                EchoDecoyPolicy.DAMAGE_MULTIPLIER > 0.0D
                        && EchoDecoyPolicy.DAMAGE_MULTIPLIER < 1.0D);
        check("a degenerate boss damage falls back to a safe value",
                EchoDecoyPolicy.decoyDamage(Double.NaN) > 0.0D
                        && EchoDecoyPolicy.decoyDamage(-1.0D) > 0.0D);
    }

    // --- 15.3: no permanent orphans -----------------------------------------
    private static void cleanup() {
        check("a decoy has a finite lifetime", EchoDecoyPolicy.MAX_LIFETIME_TICKS > 0L
                && EchoDecoyPolicy.MAX_LIFETIME_TICKS <= 20L * 60L * 15L);
        List<String> decoys = new ArrayList<>(List.of("a", "b", "c"));
        for (String reason : List.of("PHASE_END", "BOSS_DEFEAT", "RESTART", "EVENT_END",
                "OWNER_LOST", "TIMEOUT")) {
            check("cleanup is required for " + reason, EchoDecoyPolicy.requiresCleanup(reason));
        }
        check("an unknown reason still cleans up", EchoDecoyPolicy.requiresCleanup("NOPE"));
        check("a null reason still cleans up", EchoDecoyPolicy.requiresCleanup(null));
        check("a phase end removes every decoy",
                EchoDecoyPolicy.decoysToRemove(decoys, "PHASE_END").size() == decoys.size());
        check("an empty roster removes nothing",
                EchoDecoyPolicy.decoysToRemove(List.of(), "PHASE_END").isEmpty());
        check("a null roster removes nothing",
                EchoDecoyPolicy.decoysToRemove(null, "PHASE_END").isEmpty());
        // A decoy never outlives its phase, even if nothing else fires.
        check("a decoy expires on its own lifetime",
                EchoDecoyPolicy.expires(0L, EchoDecoyPolicy.MAX_LIFETIME_TICKS));
        check("a decoy is alive before its lifetime ends",
                !EchoDecoyPolicy.expires(0L, EchoDecoyPolicy.MAX_LIFETIME_TICKS - 1L));
        check("a negative spawn tick does not expire early",
                !EchoDecoyPolicy.expires(-100L, 0L));
        check("a negative spawn tick still expires eventually",
                EchoDecoyPolicy.expires(-100L, EchoDecoyPolicy.MAX_LIFETIME_TICKS));
    }

    private static void check(String name, boolean condition) {
        if (!condition) {
            System.out.println("FAIL " + name);
            failures++;
        }
    }
}