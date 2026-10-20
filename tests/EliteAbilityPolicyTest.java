import java.util.List;
import me.copimine.endevent.domain.EliteAbilityPolicy;

public final class EliteAbilityPolicyTest {
    public static void main(String[] args) {
        check(EliteAbilityPolicy.profileForWave(1, EliteAbilityPolicy.Role.SKELETON).isEmpty(),
                "Wave 1 must remain free of elite projectile pressure");
        check(EliteAbilityPolicy.profileForWave(2, EliteAbilityPolicy.Role.SKELETON)
                        .equals(List.of(EliteAbilityPolicy.Ability.TRIPLE_ARROWS,
                                EliteAbilityPolicy.Ability.ARROW_RAIN)),
                "the Wave 2 Bone Rift Archer keeps its triple-arrow volley and gains Arrow Rain");
        check(EliteAbilityPolicy.profileForWave(2, EliteAbilityPolicy.Role.ENDERMAN).isEmpty()
                        && EliteAbilityPolicy.profileForWave(2, EliteAbilityPolicy.Role.SPIDER).isEmpty()
                        && EliteAbilityPolicy.activeCastForWave(2, EliteAbilityPolicy.Role.SKELETON)
                        == EliteAbilityPolicy.Ability.ARROW_RAIN,
                "Wave 2 Arrow Rain is the active cast while other roles gain no spell pool");
        check(EliteAbilityPolicy.profileForWave(3, EliteAbilityPolicy.Role.SKELETON)
                        .equals(List.of(EliteAbilityPolicy.Ability.TRIPLE_ARROWS,
                                EliteAbilityPolicy.Ability.ARROW_RAIN)),
                "Wave 3 elite skeletons need their triple base and arrow rain");
        check(EliteAbilityPolicy.profileForWave(3, EliteAbilityPolicy.Role.ENDERMAN)
                        .equals(List.of(EliteAbilityPolicy.Ability.RIFT_BOLT)),
                "Wave 3 elite Endermen need only Rift Bolt");
        check(EliteAbilityPolicy.profileForWave(3, EliteAbilityPolicy.Role.SPIDER)
                        .equals(List.of(EliteAbilityPolicy.Ability.WEB_SHOT)),
                "Wave 3 elite spiders need only Web Shot");
        check(EliteAbilityPolicy.profileForWave(4, EliteAbilityPolicy.Role.ENDERMAN).isEmpty()
                        && EliteAbilityPolicy.profileForWave(4, EliteAbilityPolicy.Role.SPIDER).isEmpty(),
                "Wave 4 objective specialists must not gain roaming generic elite spells");
        check(EliteAbilityPolicy.profileForWave(4, EliteAbilityPolicy.Role.SKELETON)
                        .equals(List.of(EliteAbilityPolicy.Ability.TRIPLE_ARROWS)),
                "Wave 4 Channeler retains the triple base only when it fires a bow");
        check(EliteAbilityPolicy.profileForWave(5, EliteAbilityPolicy.Role.SKELETON)
                        .equals(List.of(EliteAbilityPolicy.Ability.TRIPLE_ARROWS,
                                EliteAbilityPolicy.Ability.EXPLOSIVE_ARROWS)),
                "Wave 5 elite skeletons need explosive arrows as their sole special attack");
        check(EliteAbilityPolicy.profileForWave(5, EliteAbilityPolicy.Role.ENDERMAN)
                        .equals(List.of(EliteAbilityPolicy.Ability.DRAGON_BREATH)),
                "Wave 5 elite Endermen need Dragon Breath only");
        check(EliteAbilityPolicy.profileForWave(5, EliteAbilityPolicy.Role.SPIDER)
                        .equals(List.of(EliteAbilityPolicy.Ability.WEB_SHOT)),
                "Wave 5 elite spiders need Web Shot only");
        check(EliteAbilityPolicy.profileForWave(6, EliteAbilityPolicy.Role.SKELETON)
                        .equals(List.of(EliteAbilityPolicy.Ability.TRIPLE_ARROWS,
                                EliteAbilityPolicy.Ability.ARROW_RAIN)),
                "Wave 6 pressure elite skeletons need Arrow Rain");
        check(EliteAbilityPolicy.profileForWave(6, EliteAbilityPolicy.Role.ENDERMAN)
                        .equals(List.of(EliteAbilityPolicy.Ability.VOID_BURST)),
                "Wave 6 pressure elite Endermen need Void Burst only");
        check(EliteAbilityPolicy.profileForWave(6, EliteAbilityPolicy.Role.SPIDER)
                        .equals(List.of(EliteAbilityPolicy.Ability.WEB_SHOT)),
                "Wave 6 pressure elite spiders need Web Shot only");
        for (EliteAbilityPolicy.Role role : EliteAbilityPolicy.Role.values()) {
            check(EliteAbilityPolicy.profileForWave(7, role).isEmpty(),
                    "Wave 7 must have no generic elite loadout for " + role);
        }
        check(EliteAbilityPolicy.profileForWave(0, EliteAbilityPolicy.Role.SKELETON).isEmpty(),
                "invalid wave numbers must fail closed");
        check(EliteAbilityPolicy.activeCastForWave(5, EliteAbilityPolicy.Role.SKELETON) == null,
                "Wave 5 explosive arrows remain on the physical bow path");
        check(EliteAbilityPolicy.activeCastForWave(4, EliteAbilityPolicy.Role.SKELETON) == null,
                "Wave 4 Channelers keep only their three-arrow bow attack");
        check(EliteAbilityPolicy.spiderConversions(3) == 1
                        && EliteAbilityPolicy.spiderConversions(5) == 2
                        && EliteAbilityPolicy.spiderConversions(4) == 0
                        && EliteAbilityPolicy.spiderConversions(6) == 1
                        && EliteAbilityPolicy.spiderConversions(7) == 0,
                "Waves 3, 5 and 6 promote only their specified number of support spiders");
        check(EliteAbilityPolicy.prefersRangedSupport(3, EliteAbilityPolicy.Role.SPIDER)
                        && EliteAbilityPolicy.prefersRangedSupport(5, EliteAbilityPolicy.Role.SPIDER)
                        && EliteAbilityPolicy.prefersRangedSupport(6, EliteAbilityPolicy.Role.SPIDER)
                        && !EliteAbilityPolicy.prefersRangedSupport(2, EliteAbilityPolicy.Role.SPIDER)
                        && !EliteAbilityPolicy.prefersRangedSupport(4, EliteAbilityPolicy.Role.SPIDER)
                        && !EliteAbilityPolicy.prefersRangedSupport(7, EliteAbilityPolicy.Role.SPIDER)
                        && !EliteAbilityPolicy.prefersRangedSupport(3, EliteAbilityPolicy.Role.SKELETON),
                "only WEB_SHOT elite spiders use the ranged support posture in their approved waves");
        check(EliteAbilityPolicy.SPIDER_SUPPORT_MIN_RANGE_BLOCKS >= 5.0D
                        && EliteAbilityPolicy.SPIDER_SUPPORT_MAX_RANGE_BLOCKS <= 8.0D
                        && EliteAbilityPolicy.SPIDER_SUPPORT_MAX_RANGE_BLOCKS
                        > EliteAbilityPolicy.SPIDER_SUPPORT_MIN_RANGE_BLOCKS,
                "elite spiders hold a useful but bounded ranged support distance");
        check(EliteAbilityPolicy.SPIDER_SUPPORT_ARRIVAL_TOLERANCE_BLOCKS > 0.0D
                        && EliteAbilityPolicy.SPIDER_SUPPORT_ARRIVAL_TOLERANCE_BLOCKS <= 0.5D,
                "elite spiders continue to the ranged waypoint instead of stopping in melee approach range");
        check(EliteAbilityPolicy.SPIDER_ROOT_TICKS == 30
                        && EliteAbilityPolicy.SPIDER_NAUSEA_TICKS == 120
                        && EliteAbilityPolicy.SPIDER_NAUSEA_AMPLIFIER == 2,
                "web control uses a 1.5 second root and Nausea III for six seconds");
        check(EliteAbilityPolicy.directDamage(EliteAbilityPolicy.Ability.WEB_SHOT) == 0.0D
                        && EliteAbilityPolicy.directDamage(EliteAbilityPolicy.Ability.DRAGON_BREATH) == 0.0D
                        && EliteAbilityPolicy.directDamage(EliteAbilityPolicy.Ability.VOID_BURST) <= 4.0D,
                "support web and Dragon Breath use their single area-impact transaction");
        check(EliteAbilityPolicy.DRAGON_CLOUD_AREA_DAMAGE == 1.0D
                        && EliteAbilityPolicy.DRAGON_CLOUD_AREA_DAMAGE <= 4.0D,
                "Dragon Breath cloud adds bounded area damage");
        System.out.println("EliteAbilityPolicyTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
