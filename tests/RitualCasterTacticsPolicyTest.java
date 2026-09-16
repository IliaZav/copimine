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

        RitualCasterTacticsPolicy.Attack[] attacks = new RitualCasterTacticsPolicy.Attack[6];
        for (int slot = 0; slot < attacks.length; slot++) {
            attacks[slot] = RitualCasterTacticsPolicy.attackForSlot(slot);
            for (int previous = 0; previous < slot; previous++) {
                check(attacks[previous] != attacks[slot],
                        "caster slots 0-5 must have distinct attacks");
            }
        }
        check("sphere-barrage".equals(attacks[0].id()), "slot zero attack id");
        check("rift-spikes".equals(attacks[5].id()), "slot five attack id");
        System.out.println("RitualCasterTacticsPolicyTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
