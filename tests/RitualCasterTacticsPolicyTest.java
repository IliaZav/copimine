import me.copimine.endevent.domain.RitualCasterTacticsPolicy;

public final class RitualCasterTacticsPolicyTest {
    public static void main(String[] args) {
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
        check(RitualCasterTacticsPolicy.contributesAmplification(
                        RitualCasterTacticsPolicy.State.GUARDED_CASTING,
                        RitualCasterTacticsPolicy.Role.AMPLIFIER),
                "guarded amplifiers must contribute to the ritual");
        check(!RitualCasterTacticsPolicy.contributesAmplification(
                        RitualCasterTacticsPolicy.State.AWAKENED_ATTACKING,
                        RitualCasterTacticsPolicy.Role.AMPLIFIER),
                "awakened amplifiers must stop amplifying after leaving the channel");

        check(RitualCasterTacticsPolicy.roleForSlot(0)
                        == RitualCasterTacticsPolicy.Role.PROJECTILE_CASTER,
                "slot 0 owns sphere projectiles");
        check(RitualCasterTacticsPolicy.roleForSlot(1)
                        == RitualCasterTacticsPolicy.Role.ZONE_CASTER,
                "slot 1 owns corrupted zones");
        check(RitualCasterTacticsPolicy.roleForSlot(2)
                        == RitualCasterTacticsPolicy.Role.REVERSE_CASTER,
                "slot 2 owns reverse movement");
        check(RitualCasterTacticsPolicy.roleForSlot(3)
                        == RitualCasterTacticsPolicy.Role.CONTROL_SWAP_CASTER,
                "slot 3 owns control swap");
        check(RitualCasterTacticsPolicy.roleForSlot(4)
                        == RitualCasterTacticsPolicy.Role.AMPLIFIER,
                "slot 4 must amplify the ritual rather than introduce a fifth spell");
        check(RitualCasterTacticsPolicy.roleForSlot(5)
                        == RitualCasterTacticsPolicy.Role.AMPLIFIER,
                "slot 5 must amplify the ritual rather than introduce a sixth spell");
        System.out.println("RitualCasterTacticsPolicyTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
