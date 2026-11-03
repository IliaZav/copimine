import me.copimine.endevent.domain.Wave7ArchmageVexPolicy;

/**
 * The Archmage's swarm spell spends its own Vexes as ammunition.
 *
 * <p>The user asked for a new spell tied to the Vexes ("вредины"). These checks
 * pin the decision it creates: ignore the swarm and eat the burst plus the
 * Archmage's healing, or clear it and deny both. Every number is bounded, so
 * the spell can never one-shot a group or reset the encounter.</p>
 */
public final class Wave7ArchmageSwarmPolicyTest {
    private static int failures;

    public static void main(String[] args) {
        swarmNeedsRealMinions();
        swarmRespectsItsCooldown();
        damageIsBoundedPerCast();
        healingIsBoundedAndRefusedAtFullHealth();
        aWipedSwarmWeakensTheBurst();
        report();
    }

    private static void swarmNeedsRealMinions() {
        check("no Vex means no spell",
                !Wave7ArchmageVexPolicy.swarmReady(0, 10_000L, true, false));
        check("one Vex is not a swarm",
                !Wave7ArchmageVexPolicy.swarmReady(
                        Wave7ArchmageVexPolicy.SWARM_MIN_VEXES - 1, 10_000L, true, false));
        check("a real swarm casts",
                Wave7ArchmageVexPolicy.swarmReady(
                        Wave7ArchmageVexPolicy.SWARM_MIN_VEXES, 10_000L, true, false));
        check("a dead Archmage never casts",
                !Wave7ArchmageVexPolicy.swarmReady(6, 10_000L, false, false));
        check("a busy Archmage never casts",
                !Wave7ArchmageVexPolicy.swarmReady(6, 10_000L, true, true));
    }

    private static void swarmRespectsItsCooldown() {
        check("the spell cannot be spammed", !Wave7ArchmageVexPolicy.swarmReady(6,
                Wave7ArchmageVexPolicy.SWARM_COOLDOWN_TICKS - 1, true, false));
        check("the spell returns after its cooldown", Wave7ArchmageVexPolicy.swarmReady(6,
                Wave7ArchmageVexPolicy.SWARM_COOLDOWN_TICKS, true, false));
        check("the first cast is always allowed", Wave7ArchmageVexPolicy.swarmReady(6, -1L, true, false));
    }

    private static void damageIsBoundedPerCast() {
        check("no Vex means no damage", Wave7ArchmageVexPolicy.swarmDamage(0) == 0.0D);
        check("damage scales with the swarm",
                Wave7ArchmageVexPolicy.swarmDamage(3) > Wave7ArchmageVexPolicy.swarmDamage(1));
        check("damage is capped", Wave7ArchmageVexPolicy.swarmDamage(500)
                == Wave7ArchmageVexPolicy.SWARM_MAX_DAMAGE);
        check("a capped burst cannot one-shot a full-health player",
                Wave7ArchmageVexPolicy.SWARM_MAX_DAMAGE < 20.0D);
    }

    private static void healingIsBoundedAndRefusedAtFullHealth() {
        check("a wounded Archmage heals", Wave7ArchmageVexPolicy.swarmHeal(3, 100.0D, 1000.0D) > 0.0D);
        check("a full-health Archmage gains nothing",
                Wave7ArchmageVexPolicy.swarmHeal(6, 1000.0D, 1000.0D) == 0.0D);
        check("healing never overshoots the maximum",
                Wave7ArchmageVexPolicy.swarmHeal(6, 995.0D, 1000.0D) <= 5.0D);
        check("healing is capped", Wave7ArchmageVexPolicy.swarmHeal(500, 1.0D, 10_000.0D)
                == Wave7ArchmageVexPolicy.SWARM_MAX_HEAL);
    }

    private static void aWipedSwarmWeakensTheBurst() {
        int cap = Wave7ArchmageVexPolicy.cap(10);
        check("a cleared swarm spends nothing",
                Wave7ArchmageVexPolicy.swarmVexSpent(0, cap) == 0);
        check("the spell never spends more than is alive",
                Wave7ArchmageVexPolicy.swarmVexSpent(1, cap) == 1);
        check("the spell never spends more than the cap",
                Wave7ArchmageVexPolicy.swarmVexSpent(50, cap) == cap);
        check("a half-cleared swarm hits for less",
                Wave7ArchmageVexPolicy.swarmDamage(Wave7ArchmageVexPolicy.swarmVexSpent(3, cap))
                        < Wave7ArchmageVexPolicy.swarmDamage(Wave7ArchmageVexPolicy.swarmVexSpent(6, cap)));
    }

    private static void report() {
        System.out.println(failures == 0 ? "Wave7ArchmageSwarmPolicyTest: PASS"
                : "Wave7ArchmageSwarmPolicyTest: FAIL " + failures);
        if (failures > 0) System.exit(1);
    }

    private static void check(String label, boolean condition) {
        if (!condition) {
            failures++;
            System.out.println("FAIL: " + label);
        }
    }
}
