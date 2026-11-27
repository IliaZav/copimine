import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import me.copimine.endevent.domain.RageEchoSummonPolicy;
import me.copimine.endevent.domain.RageEchoSummonPolicy.IncomingDamageMode;
import me.copimine.endevent.domain.wave7.EchoBehaviorStrategy;
import me.copimine.endevent.domain.wave7.EchoLoadoutState;
import me.copimine.endevent.runtime.wave7.EchoDuelCombatRuntimeAdapter;
import me.copimine.endevent.runtime.wave7.EchoDuelCombatRuntimeAdapter.Action;
import me.copimine.endevent.runtime.wave7.EchoDuelCombatRuntimeAdapter.Decision;
import me.copimine.endevent.runtime.wave7.EchoDuelCombatRuntimeAdapter.Observation;
import me.copimine.endevent.runtime.wave7.RageEchoSession;
import me.copimine.endevent.runtime.RealitySplitEchoDuelState;
import me.copimine.endevent.runtime.RealitySplitEchoDuelState.ActorLifecycle;

/**
 * E-01: rare Personal Echo admission, session lifecycle, and common-engine
 * integration — tested as pure domain, no Bukkit dependency.
 */
public final class RageEchoSummonPolicyTest {
    private static int failures = 0;
    // Stable fixture identifiers, never confused with real UUIDs.
    private static final UUID EVENT = id(10);
    private static final long GENERATION = 100L;
    private static final long EPOCH = 101L;
    private static final UUID DUEL = id(20);
    private static final UUID OWNER = id(30);
    private static final UUID CARRIER = id(40);
    private static final UUID WORLD = id(50);

    public static void main(String[] args) {
        noBossBarOrReward();
        maySummitRespectsCooldownAndCap();
        summonHealthAndDamageScaleByOwner();
        lifetimeExpiry();
        incomingDamageModeDefaultsToOwnerOnly();
        sessionBuildsValidActiveDuel();
        sessionExpiryAndActorDeath();
        engineTickFromSessionReturnsMeleeDecision();
        describeIsStable();
        System.out.println(failures == 0 ? "RageEchoSummonPolicyTest OK" : "FAILURES " + failures);
        if (failures != 0) System.exit(1);
    }

    // --- §10.4.3 invariants --------------------------------------------------

    private static void noBossBarOrReward() {
        check("rare Echo has no boss bar", !RageEchoSummonPolicy.JOINS_BOSS_BAR);
        check("rare Echo drops no loot", !RageEchoSummonPolicy.DROPS_LOOT);
        check("rare Echo uses its own model", !RageEchoSummonPolicy.USES_BOSS_MODEL);
    }

    // --- admission -----------------------------------------------------------

    private static void maySummitRespectsCooldownAndCap() {
        // Fresh summon allowed when none alive and within cooldown.
        check("fresh summon is ready", RageEchoSummonPolicy.maySummon(-1L, 0L, 0, true, true));
        // Cap blocks simultaneous.
        check("cap blocks second summon",
                !RageEchoSummonPolicy.maySummon(-1L, 0L, 1, true, true));
        // Phase not active blocks.
        check("phase not active blocks",
                !RageEchoSummonPolicy.maySummon(-1L, 0L, 0, false, true));
        // Owner offline blocks.
        check("owner offline blocks",
                !RageEchoSummonPolicy.maySummon(-1L, 0L, 0, true, false));
        // Cooldown blocks early repeat.
        check("cooldown blocks repeat",
                !RageEchoSummonPolicy.maySummon(0L, RageEchoSummonPolicy.COOLDOWN_TICKS - 1L, 0, true, true));
        // After cooldown is ready again.
        check("after cooldown is ready again",
                RageEchoSummonPolicy.maySummon(0L, RageEchoSummonPolicy.COOLDOWN_TICKS, 0, true, true));
        // maySummonTo returns correct remaining slots.
        check("maySummonTo with 0 alive", RageEchoSummonPolicy.maySummonTo(0) == 1);
        check("maySummonTo with 1 alive", RageEchoSummonPolicy.maySummonTo(1) == 0);
        check("maySummonTo handles negative", RageEchoSummonPolicy.maySummonTo(-1) == 1);
    }

    // --- health / damage -----------------------------------------------------

    private static void summonHealthAndDamageScaleByOwner() {
        // Owner with 20 HP: 20 × 1.75 = 35
        double health = RageEchoSummonPolicy.summonHealth(20.0D);
        check("summon health = 35 for 20-HP owner",
                Double.compare(Math.round(health), 35.0D) == 0);
        // Owner with 0 or negative: uses fallback
        health = RageEchoSummonPolicy.summonHealth(0.0D);
        check("summon health floors at 1 for zero-input",
                health >= 1.0D);
        // Damage: profile attack × 0.9
        double damage = RageEchoSummonPolicy.summonDamage(10.0D);
        check("summon damage = 9 for 10-damage owner",
                Double.compare(Math.round(damage * 10.0D) / 10.0D, 9.0D) == 0);
        // Fallback
        damage = RageEchoSummonPolicy.summonDamage(-1.0D);
        check("summon damage uses fallback for invalid input",
                Double.compare(damage, RageEchoSummonPolicy.FALLBACK_ATTACK_DAMAGE * RageEchoSummonPolicy.DAMAGE_MULTIPLIER) == 0);
    }

    // --- lifetime ------------------------------------------------------------

    private static void lifetimeExpiry() {
        check("not expired at spawn", !RageEchoSummonPolicy.expired(100L, 100L));
        check("not expired mid-life",
                !RageEchoSummonPolicy.expired(100L, 100L + RageEchoSummonPolicy.lifetimeTicks() - 1L));
        check("expired after lifetime",
                RageEchoSummonPolicy.expired(100L, 100L + RageEchoSummonPolicy.lifetimeTicks()));
        // Zero spawn tick edge case.
        check("non-negative lifetime for zero spawn",
                !RageEchoSummonPolicy.expired(0L, 0L));
    }

    // --- incoming damage mode ------------------------------------------------

    private static void incomingDamageModeDefaultsToOwnerOnly() {
        UUID other = id(99);
        check("OWNER_ONLY: owner may damage",
                RageEchoSummonPolicy.mayBeDamagedBy(OWNER, OWNER, IncomingDamageMode.OWNER_ONLY));
        check("OWNER_ONLY: other may not damage",
                !RageEchoSummonPolicy.mayBeDamagedBy(other, OWNER, IncomingDamageMode.OWNER_ONLY));
        check("null attacker is rejected",
                !RageEchoSummonPolicy.mayBeDamagedBy(null, OWNER, IncomingDamageMode.OWNER_ONLY));
        check("null mode defaults to OWNER_ONLY",
                RageEchoSummonPolicy.mayBeDamagedBy(OWNER, OWNER, null));
        // ALLIED_HELP admits anyone.
        check("ALLIED_HELP admits anyone",
                RageEchoSummonPolicy.mayBeDamagedBy(other, OWNER, IncomingDamageMode.ALLIED_HELP));
    }

    // --- session lifecycle (pure, no Bukkit) ---------------------------------

    private static RageEchoSession buildSession() {
        EchoBehaviorStrategy strategy = new EchoBehaviorStrategy(750, 250, 500, 250, 100,
                900, 0b000_0001, true);
        List<EchoLoadoutState.Item> slots = new ArrayList<>(41);
        for (int i = 0; i < 41; i++) slots.add(EchoLoadoutState.Item.empty());
        slots.set(0, new EchoLoadoutState.Item("IRON_SWORD", 1, 250, 0, Map.of(), "", false));
        EchoLoadoutState replica = EchoLoadoutState.create(EVENT, GENERATION, DUEL, OWNER, slots, 0);
        return new RageEchoSession(EVENT, GENERATION, EPOCH, DUEL, OWNER, CARRIER,
                WORLD, 100L, replica, strategy, 20.0D, 20.0D);
    }

    private static void sessionBuildsValidActiveDuel() {
        RageEchoSession session = buildSession();
        check("session is active at construction", session.active());
        check("session has valid duel record", session.duel() != null);
        check("duel lifecycle is ACTIVE",
                session.duel().lifecycle() == me.copimine.endevent.runtime.RealitySplitEchoDuelState.Lifecycle.ACTIVE);
        check("actor is ALIVE",
                session.duel().actor().lifecycle() == ActorLifecycle.ALIVE);
        check("actor health is scaled",
                session.duel().actor().maximumHealth() > 20.0D);
        check("scope is arena-wide",
                session.scope().combatAdmitted() && session.scope().ownerInCell()
                        && session.scope().actorInCell() && session.scope().sameWorld());
        check("duel record passes identity validation",
                session.duel().duelId().equals(DUEL) && session.duel().ownerUuid().equals(OWNER));
    }

    private static void sessionExpiryAndActorDeath() {
        RageEchoSession session = buildSession();
        check("not expired at spawn", !session.expired(session.spawnTick()));
        check("expired past lifetime",
                session.expired(session.spawnTick() + RageEchoSummonPolicy.lifetimeTicks()));
        check("actor alive", session.actorAlive());
        check("canTick is true", session.canTick());
        session.deactivate();
        check("deactivated", !session.active());
        check("deactivated cannot tick", !session.canTick());
    }

    // --- common engine integration -------------------------------------------

    private static void engineTickFromSessionReturnsMeleeDecision() {
        RageEchoSession session = buildSession();
        // Observation at tick 200, within melee range, with line of sight.
        Observation obs = new Observation(200L, 3.0D, true, true, false, 30L, 4L);
        var decision = EchoDuelCombatRuntimeAdapter.tick(
                session.duel(), session.replica(), session.scope(), obs);
        check("engine returns a decision", decision.isPresent());
        if (decision.isPresent()) {
            // In melee range with an IRON_SWORD and a close observation the
            // adapter should choose MELEE or NONE (if cooldown gates it).
            Action action = decision.get().action();
            check("decision is MELEE or NONE in melee range",
                    action == Action.MELEE || action == Action.NONE);
            if (action == Action.MELEE) {
                check("melee decision uses correct scope",
                        decision.get().scope().duelId().equals(DUEL));
                check("melee decision has item slot",
                        decision.get().itemSlot() >= 0);
            }
        }
        double observedHealth = session.duel().actor().currentHealth() - 1.0D;
        session.synchronizeHealth(observedHealth);
        check("accepted native health is the next decision baseline",
                session.duel().actor().currentHealth() == observedHealth);
        session.deactivate();
        var deadDecision = EchoDuelCombatRuntimeAdapter.tick(
                session.duel(), session.replica(), session.scope(), obs);
        check("deactivated session does not produce decisions",
                deadDecision.isEmpty());
    }

    // --- diagnostic ----------------------------------------------------------

    private static void describeIsStable() {
        RageEchoSession session = buildSession();
        Map<String, String> d = session.describe();
        check("describe has duel key", d.containsKey("duel"));
        check("describe has carrier key", d.containsKey("carrier"));
        check("describe has active true", "true".equals(d.get("active")));
        check("describe has spawnTick", d.containsKey("spawnTick"));
    }

    // --- helpers -------------------------------------------------------------

    private static UUID id(int seed) {
        return new UUID(seed, seed);
    }

    private static void check(String label, boolean condition) {
        if (!condition) {
            System.out.println("FAIL: " + label);
            failures++;
        }
    }
}
