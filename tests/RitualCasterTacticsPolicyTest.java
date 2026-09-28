import me.copimine.endevent.domain.RitualCasterTacticsPolicy;

public final class RitualCasterTacticsPolicyTest {
    public static void main(String[] args) {
        check("SOUL_BRAND_CASTER".equals(
                        RitualCasterTacticsPolicy.roleForSlot(2).name()),
                "caster slot 2 must own Soul Brand instead of reverse movement");
        check("RIFT_CHAINS_CASTER".equals(
                        RitualCasterTacticsPolicy.roleForSlot(3).name()),
                "caster slot 3 must own Rift Chains instead of control swap");
        check("FINAL_SEAL".equals(RitualCasterTacticsPolicy.roleForSlot(4).name()),
                "caster slot 4 must be the final prison seal");
        check(RitualCasterTacticsPolicy.state(true, false)
                        == RitualCasterTacticsPolicy.State.GUARDED_CASTING,
                "a living guard must keep the caster in guarded casting");
        check(RitualCasterTacticsPolicy.state(true, true)
                        == RitualCasterTacticsPolicy.State.GUARDED_CASTING,
                "a shielded hit must not wake a caster before its guards fall");
        check(RitualCasterTacticsPolicy.state(false, false)
                        == RitualCasterTacticsPolicy.State.EXPOSED_CASTING,
                "a guardless caster must keep casting until its first accepted hit");
        check(RitualCasterTacticsPolicy.state(false, true)
                        == RitualCasterTacticsPolicy.State.AWAKENED_ATTACKING,
                "the first accepted hit must wake a guardless caster permanently");
        check(!RitualCasterTacticsPolicy.canTargetPlayers(
                        RitualCasterTacticsPolicy.State.EXPOSED_CASTING),
                "exposed casters must not target players yet");
        check(RitualCasterTacticsPolicy.castsSphere(
                        RitualCasterTacticsPolicy.State.GUARDED_CASTING),
                "guarded casters must cast the sphere");
        check(RitualCasterTacticsPolicy.castsSphere(
                        RitualCasterTacticsPolicy.State.EXPOSED_CASTING),
                "exposed casters must cast the sphere");
        check(RitualCasterTacticsPolicy.canTargetPlayers(
                        RitualCasterTacticsPolicy.State.AWAKENED_ATTACKING),
                "awakened casters must be allowed to attack");
        check(RitualCasterTacticsPolicy.ownsRitualAbility(
                        RitualCasterTacticsPolicy.State.GUARDED_CASTING),
                "guarded casters must own their Ritual Sphere ability");
        check(RitualCasterTacticsPolicy.ownsRitualAbility(
                        RitualCasterTacticsPolicy.State.EXPOSED_CASTING),
                "exposed casters must keep their Ritual Sphere ability until first accepted hit");
        check(!RitualCasterTacticsPolicy.ownsRitualAbility(
                        RitualCasterTacticsPolicy.State.AWAKENED_ATTACKING),
                "awakened casters must leave Ritual Sphere ability ownership");
        check(!RitualCasterTacticsPolicy.contributesAmplification(
                        RitualCasterTacticsPolicy.State.GUARDED_CASTING,
                        RitualCasterTacticsPolicy.Role.FINAL_SEAL),
                "final seal must not amplify spell strength");
        check(!RitualCasterTacticsPolicy.contributesAmplification(
                        RitualCasterTacticsPolicy.State.AWAKENED_ATTACKING,
                        RitualCasterTacticsPolicy.Role.FINAL_SEAL),
                "final seal must never introduce an amplifier role");

        check(RitualCasterTacticsPolicy.roleForSlot(0)
                        == RitualCasterTacticsPolicy.Role.RIFT_BARRAGE_CASTER,
                "slot 0 owns Rift Barrage");
        check(RitualCasterTacticsPolicy.roleForSlot(1)
                        == RitualCasterTacticsPolicy.Role.GRAVITY_WELL_CASTER,
                "slot 1 owns Gravity Well");
        check(RitualCasterTacticsPolicy.roleForSlot(2)
                        == RitualCasterTacticsPolicy.Role.SOUL_BRAND_CASTER,
                "slot 2 owns Soul Brand");
        check(RitualCasterTacticsPolicy.roleForSlot(3)
                        == RitualCasterTacticsPolicy.Role.RIFT_CHAINS_CASTER,
                "slot 3 owns Rift Chains");
        check(RitualCasterTacticsPolicy.roleForSlot(4)
                        == RitualCasterTacticsPolicy.Role.FINAL_SEAL,
                "slot 4 must hold the final prison seal");
        check(RitualCasterTacticsPolicy.roleForSlot(5)
                        == RitualCasterTacticsPolicy.Role.FINAL_SEAL,
                "out-of-range slots must not add another spell role");
        System.out.println("RitualCasterTacticsPolicyTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
