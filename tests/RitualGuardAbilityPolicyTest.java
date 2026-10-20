import me.copimine.endevent.domain.RitualGuardAbilityPolicy;

public final class RitualGuardAbilityPolicyTest {
    public static void main(String[] args) {
        var warden = RitualGuardAbilityPolicy.forGuardSlot(0);
        var archer = RitualGuardAbilityPolicy.forGuardSlot(1);
        var spider = RitualGuardAbilityPolicy.forGuardSlot(2);
        check(warden.role() == RitualGuardAbilityPolicy.Role.WARDEN_SLAM,
                "enderman guard slot owns the marked slam");
        check(archer.role() == RitualGuardAbilityPolicy.Role.RIFT_BOLT,
                "skeleton guard slot owns the narrow marked bolt");
        check(spider.role() == RitualGuardAbilityPolicy.Role.WEB_SNARE,
                "spider guard slot owns the bounded snare");
        check(RitualGuardAbilityPolicy.forGuardSlot(3) == null,
                "unknown slots must not gain a guard ability");
        check(RitualGuardAbilityPolicy.forEntityType("ENDERMan") == warden,
                "spawned endermen must retain their role profile");
        check(RitualGuardAbilityPolicy.forEntityType("SKELETON") == archer,
                "spawned skeletons must retain their role profile");
        check(RitualGuardAbilityPolicy.forEntityType("SPIDER") == spider,
                "spawned spiders must retain their role profile");
        boolean[] soloRoles = new boolean[3];
        for (int caster = 0; caster < 5; caster++) {
            soloRoles[RitualGuardAbilityPolicy.roleSlot(caster, 0)] = true;
            boolean[] group = new boolean[3];
            for (int slot = 0; slot < 3; slot++) {
                int roleSlot = RitualGuardAbilityPolicy.roleSlot(caster, slot);
                check(!group[roleSlot], "three-guard group has distinct roles");
                group[roleSlot] = true;
            }
        }
        check(soloRoles[0] && soloRoles[1] && soloRoles[2],
                "five solo guards include endermen, skeletons and actual web-casting spiders");

        check(RitualGuardAbilityPolicy.mayCommit(warden, true, true, true,
                        false, false, false, 8.0D),
                "role action should commit at its inclusive range boundary");
        check(!RitualGuardAbilityPolicy.mayCommit(warden, true, true, true,
                        true, false, false, 2.0D),
                "guard telegraph must not overlap a major Sphere spell");
        check(!RitualGuardAbilityPolicy.mayCommit(warden, true, true, true,
                        false, true, false, 2.0D),
                "shared reservation permits only one guard telegraph");
        check(!RitualGuardAbilityPolicy.mayCommit(warden, true, false, true,
                        false, false, false, 2.0D),
                "dead guards cannot commit a later action");
        check(!RitualGuardAbilityPolicy.mayCommit(warden, true, true, false,
                        false, false, false, 2.0D),
                "invalid targets cannot be committed");
        check(!RitualGuardAbilityPolicy.mayCommit(warden, true, true, true,
                        false, false, true, 2.0D),
                "guard cooldown must prevent a repeat cast");
        check(!RitualGuardAbilityPolicy.mayCommit(warden, true, true, true,
                        false, false, false, Double.NaN),
                "invalid range must be rejected");

        check(RitualGuardAbilityPolicy.hits(warden,
                        warden.impactRadiusBlocks() * warden.impactRadiusBlocks()),
                "impact boundary is inclusive");
        check(!RitualGuardAbilityPolicy.hits(archer,
                        (archer.impactRadiusBlocks() + 0.05D)
                                * (archer.impactRadiusBlocks() + 0.05D)),
                "a player who dodges the narrow bolt must take no ability damage");
        check(!RitualGuardAbilityPolicy.hits(spider, Double.POSITIVE_INFINITY),
                "non-finite positions cannot hit");
        for (var profile : new RitualGuardAbilityPolicy.Profile[]{warden, archer, spider}) {
            check(profile.damage() <= 4.0D && profile.telegraphTicks() >= 20
                            && profile.cooldownTicks() >= 160,
                    "guard actions stay telegraphed, bounded and rate limited");
        }
        System.out.println("RitualGuardAbilityPolicyTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
