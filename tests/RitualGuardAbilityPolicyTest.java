import me.copimine.endevent.domain.RitualGuardAbilityPolicy;
import me.copimine.endevent.domain.RitualSphereScalingPolicy;

public final class RitualGuardAbilityPolicyTest {
    public static void main(String[] args) {
        var tank = RitualGuardAbilityPolicy.forGuardSlot(0);
        var archer = RitualGuardAbilityPolicy.forGuardSlot(1);
        var warden = RitualGuardAbilityPolicy.forGuardSlot(2);
        var spider = RitualGuardAbilityPolicy.forGuardSlot(3);
        check(tank == null, "zombie tank uses ordinary bounded melee instead of a cast driver");
        check(archer.role() == RitualGuardAbilityPolicy.Role.DEFENSIVE_VOLLEY,
                "skeleton guard slot owns a physical defensive volley");
        check(warden.role() == RitualGuardAbilityPolicy.Role.WARDEN_SLAM,
                "enderman defender owns a medium-range damaging spell");
        check(spider.role() == RitualGuardAbilityPolicy.Role.WEB_SNARE,
                "spider guard slot owns the bounded snare");
        check(RitualGuardAbilityPolicy.forGuardSlot(4) == null,
                "unknown slots must not gain a guard ability");
        check(RitualGuardAbilityPolicy.forEntityType("ZOMBIE") == null,
                "zombie tanks must not receive a ranged support cast");
        check(RitualGuardAbilityPolicy.forEntityType("ENDERMan") == warden,
                "spawned endermen must retain their role profile");
        check(RitualGuardAbilityPolicy.forEntityType("SKELETON") == archer,
                "spawned skeletons must retain their role profile");
        check(RitualGuardAbilityPolicy.forEntityType("SPIDER") == spider,
                "spawned spiders must retain their role profile");
        var endermanBrand = RitualGuardAbilityPolicy.forGuardAction(2, 1);
        check(endermanBrand.role() == RitualGuardAbilityPolicy.Role.SOUL_BRAND
                        && RitualGuardAbilityPolicy.forGuardAction(2, 2).role()
                        == RitualGuardAbilityPolicy.Role.WARDEN_SLAM,
                "enderman spell role alternates between its two actual attacks");
        check(RitualGuardAbilityPolicy.isCompatibleWithMob(endermanBrand, "ENDERMAN")
                        && !RitualGuardAbilityPolicy.isCompatibleWithMob(endermanBrand, "SPIDER"),
                "Soul Brand provenance is restricted to Enderman guards");
        String[] guardEntityTypes = {"ZOMBIE", "SKELETON", "ENDERMAN", "SPIDER"};
        for (int role = 0; role < guardEntityTypes.length; role++) {
            check(guardEntityTypes[role].equals(
                            RitualGuardAbilityPolicy.entityTypeForGuardRole(role)),
                    "guard role " + role + " has one deterministic mob type");
            check(RitualGuardAbilityPolicy.isGuardEntityTypeCompatible(
                            role, guardEntityTypes[role], false),
                    "ordinary mob type matches guard role " + role);
        }
        check(RitualGuardAbilityPolicy.isGuardEntityTypeCompatible(0, "ZOMBIE", true)
                        && !RitualGuardAbilityPolicy.isGuardEntityTypeCompatible(1, "ZOMBIE", true)
                        && !RitualGuardAbilityPolicy.isGuardEntityTypeCompatible(0, "SPIDER", false),
                "Rift Giants replace only zombie tank slots and recovery rejects role mismatches");
        for (int role = 1; role <= 3; role++) {
            var action = RitualGuardAbilityPolicy.forGuardAction(role, 0);
            check(RitualGuardAbilityPolicy.isCompatibleWithMob(action, guardEntityTypes[role]),
                    "role action profile agrees with its spawned mob type " + guardEntityTypes[role]);
        }
        check(RitualGuardAbilityPolicy.isCompatibleWithMob(
                        RitualGuardAbilityPolicy.forGuardAction(2, 1), "ENDERMAN"),
                "every Enderman spell action remains compatible with its roster species");
        check(RitualGuardAbilityPolicy.forGuardActionAtRange(2, 0, 8.0D).role()
                        == RitualGuardAbilityPolicy.Role.WARDEN_SLAM,
                "the Enderman may use its close spell at the inclusive range boundary");
        check(RitualGuardAbilityPolicy.forGuardActionAtRange(2, 0, 8.01D).role()
                        == RitualGuardAbilityPolicy.Role.SOUL_BRAND
                        && RitualGuardAbilityPolicy.forGuardActionAtRange(2, 0, 12.0D).role()
                        == RitualGuardAbilityPolicy.Role.SOUL_BRAND,
                "a far Enderman target falls back to the real medium-range projectile spell");
        check(RitualGuardAbilityPolicy.forGuardActionAtRange(2, 0, 12.01D) == null,
                "Enderman spells do not commit outside their maximum range");
        check(!RitualGuardAbilityPolicy.mayBeginSpiderWeb(2.0D)
                        && RitualGuardAbilityPolicy.mayBeginSpiderWeb(2.01D)
                        && RitualGuardAbilityPolicy.mayBeginSpiderWeb(10.0D)
                        && !RitualGuardAbilityPolicy.mayBeginSpiderWeb(10.01D),
                "Spider web casts use useful range and leave point-blank attacks to vanilla melee");
        boolean[] soloRoles = new boolean[4];
        for (int caster = 0; caster < 5; caster++) {
            soloRoles[RitualGuardAbilityPolicy.roleSlot(caster, 0)] = true;
            boolean[] group = new boolean[4];
            for (int slot = 0; slot < 3; slot++) {
                int roleSlot = RitualGuardAbilityPolicy.roleSlot(caster, slot);
                check(!group[roleSlot], "three-guard group has distinct roles");
                group[roleSlot] = true;
            }
        }
        check(soloRoles[0] && soloRoles[1] && soloRoles[2] && soloRoles[3],
                "five solo guards include zombie, skeleton, enderman and spider roles");
        check(RitualGuardAbilityPolicy.roleSlot(4, 0) == 0,
                "five one-guard caster groups include a deterministic zombie tank");

        for (int players : new int[]{2, 4, 5, 8, 9, 10, 11, 20}) {
            int expectedGiants = players <= 4 ? 1 : players <= 8 ? 2 : 3;
            int actualGiants = 0;
            var roster = RitualSphereScalingPolicy.forPlayers(players);
            int expectedGuards = players <= 4 ? 5 : players <= 8 ? 10 : 15;
            check(roster.casterCount() * roster.guardsPerCaster() == expectedGuards,
                    players + "-player roster retains the approved 5/10/15 guard count");
            for (int caster = 0; caster < roster.casterCount(); caster++) {
                for (int slot = 0; slot < roster.guardsPerCaster(); slot++) {
                    int role = RitualGuardAbilityPolicy.roleSlot(caster, slot);
                    boolean giant = RitualGuardAbilityPolicy.isGiantReplacement(players, caster, slot);
                    check(RitualGuardAbilityPolicy.isGuardEntityTypeCompatible(
                                    role, RitualGuardAbilityPolicy.entityTypeForGuardRole(role), giant),
                            players + "-player recovery keeps role, species and giant replacement aligned");
                    if (giant) {
                        actualGiants++;
                        check(role == 0,
                                "giants replace zombie tank slots only");
                    }
                }
            }
            check(actualGiants == expectedGiants, players + "-player roster replaces the approved tank slots with giants");
            check(roster.casterCount() == 5, "giants never add casters or guard slots");
        }
        check(RitualGuardAbilityPolicy.isGiantReplacement(5, 0, 0)
                        && RitualGuardAbilityPolicy.isGiantReplacement(5, 3, 1),
                "five-player roster replaces only the two zombie roles that exist in its two-guard groups");
        check(!RitualGuardAbilityPolicy.isGiantReplacement(5, 2, 2)
                        && !RitualGuardAbilityPolicy.isGiantReplacement(5, 4, 0),
                "five-player roster cannot assign giants to absent or surplus zombie slots");

        check(RitualGuardAbilityPolicy.mayCommit(warden, true, true, true,
                        false, false, false, 8.0D),
                "role action should commit at its inclusive range boundary");
        check(!RitualGuardAbilityPolicy.mayCommit(spider, true, true, true,
                        true, false, false, 2.0D),
                "Spider hard control must wait through an Anchor or Anvil hazard window");
        check(RitualGuardAbilityPolicy.mayCommit(warden, true, true, true,
                        true, false, false, 2.0D),
                "ordinary guard attacks continue during major Sphere spells");
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
        check(RitualGuardAbilityPolicy.mayHitWardenSlamVictim(
                        warden, warden.impactRadiusBlocks() * warden.impactRadiusBlocks(),
                        RitualGuardAbilityPolicy.WARDEN_SLAM_MAX_VERTICAL_BLOCKS, true),
                "Warden slam accepts its inclusive ground-level and line-of-sight boundary");
        check(!RitualGuardAbilityPolicy.mayHitWardenSlamVictim(
                        warden, 0.0D, RitualGuardAbilityPolicy.WARDEN_SLAM_MAX_VERTICAL_BLOCKS + 0.01D, true)
                        && !RitualGuardAbilityPolicy.mayHitWardenSlamVictim(
                        warden, 0.0D, 0.0D, false)
                        && !RitualGuardAbilityPolicy.mayHitWardenSlamVictim(
                        archer, 0.0D, 0.0D, true),
                "the ground slam rejects elevated, occluded, and non-slam victims");
        check(!RitualGuardAbilityPolicy.acceptedPlayerDamage(20.0D, 4.0D,
                        20.0D, 4.0D, false)
                        && RitualGuardAbilityPolicy.acceptedPlayerDamage(20.0D, 4.0D,
                        17.0D, 4.0D, false)
                        && RitualGuardAbilityPolicy.acceptedPlayerDamage(20.0D, 4.0D,
                        20.0D, 1.0D, false)
                        && RitualGuardAbilityPolicy.acceptedPlayerDamage(1.0D, 0.0D,
                        0.0D, 0.0D, true),
                "cancelled hits have no receipt; health, absorption, and lethal damage do");
        check(RitualGuardAbilityPolicy.isHardControl(spider)
                        && !RitualGuardAbilityPolicy.isHardControl(endermanBrand),
                "the scheduler distinguishes Spider control from ordinary attacks");
        check(!RitualGuardAbilityPolicy.suppressesHardControl(false, false)
                        && RitualGuardAbilityPolicy.suppressesHardControl(true, false)
                        && RitualGuardAbilityPolicy.suppressesHardControl(false, true),
                "only the Anchor and Anvil hazard windows suppress new hard control");
        check(archer.cooldownTicks() >= 240 && archer.cooldownTicks() <= 300,
                "three-arrow Skeleton volley is limited to one every 12 to 15 seconds");
        check(archer.slownessTicks() == 0,
                "defensive Skeleton volleys do not inherit the generic ritual-arrow slow");
        check(warden.damage() <= 4.0D && archer.damage() <= 4.0D
                        && spider.damage() <= 4.0D && endermanBrand.damage() <= 4.0D
                        && spider.slownessTicks() <= 30,
                "guard actions stay damage capped and Spider movement control stays brief");
        long endermanBurstStartTick = 100L;
        long endermanBurstEndsAtTick = endermanBurstStartTick
                + RitualGuardAbilityPolicy.ENDERMAN_MELEE_BURST_TICKS;
        check(RitualGuardAbilityPolicy.ENDERMAN_MELEE_COOLDOWN_TICKS >= 100L
                        && RitualGuardAbilityPolicy.mayContinueEndermanMelee(
                        2.0D, 139L, endermanBurstEndsAtTick),
                "Enderman remains in melee through two pinned 20-tick attack windows");
        check(RitualGuardAbilityPolicy.mayBeginEndermanMelee(2.25D, 100L, 100L)
                        && !RitualGuardAbilityPolicy.mayBeginEndermanMelee(2.26D, 100L, 100L)
                        && !RitualGuardAbilityPolicy.mayBeginEndermanMelee(1.0D, 99L, 100L),
                "Enderman melee starts only at close range when its cooldown is ready");
        check(RitualGuardAbilityPolicy.mayContinueEndermanMelee(
                        2.0D, 139L, endermanBurstEndsAtTick)
                        && !RitualGuardAbilityPolicy.mayContinueEndermanMelee(
                        2.0D, endermanBurstEndsAtTick, endermanBurstEndsAtTick)
                        && !RitualGuardAbilityPolicy.mayContinueEndermanMelee(
                        2.3D, 139L, endermanBurstEndsAtTick),
                "Enderman's two-window melee burst ends at its deadline or when the player leaves bite range");
        check(RitualGuardAbilityPolicy.endermanMeleeOwnsController(139L, endermanBurstEndsAtTick)
                        && !RitualGuardAbilityPolicy.endermanMeleeOwnsController(
                        endermanBurstEndsAtTick, endermanBurstEndsAtTick),
                "the spell selector yields controller ownership to active Enderman melee");
        System.out.println("RitualGuardAbilityPolicyTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
