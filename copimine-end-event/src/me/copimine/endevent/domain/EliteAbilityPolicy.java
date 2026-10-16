package me.copimine.endevent.domain;

import java.util.List;

/** Wave-owned elite abilities. An empty profile means that an elite has no spell pool. */
public final class EliteAbilityPolicy {
    public static final int SPIDER_ROOT_TICKS = 30;
    public static final int SPIDER_WEAVING_TICKS = 120;
    public static final int SPIDER_NAUSEA_TICKS = 120;
    public static final int SPIDER_NAUSEA_AMPLIFIER = 2;
    public static final double SPIDER_SUPPORT_MIN_RANGE_BLOCKS = 5.5D;
    public static final double SPIDER_SUPPORT_MAX_RANGE_BLOCKS = 7.5D;
    public static final double SPIDER_SUPPORT_ARRIVAL_TOLERANCE_BLOCKS = 0.25D;
    public static final int DRAGON_BLINDNESS_TICKS = 60;
    public static final float DRAGON_CLOUD_RADIUS = 2.7F;
    public static final double DRAGON_CLOUD_AREA_DAMAGE = 1.0D;

    private EliteAbilityPolicy() { }

    public enum Role { SKELETON, ENDERMAN, SPIDER }

    public enum Ability {
        TRIPLE_ARROWS,
        ARROW_RAIN,
        EXPLOSIVE_ARROWS,
        RIFT_BOLT,
        DRAGON_BREATH,
        VOID_BURST,
        WEB_SHOT
    }

    public static List<Ability> profileForWave(int wave, Role role) {
        if (role == null) return List.of();
        return switch (wave) {
            case 2 -> role == Role.SKELETON ? List.of(Ability.TRIPLE_ARROWS) : List.of();
            case 3 -> switch (role) {
                case SKELETON -> List.of(Ability.TRIPLE_ARROWS, Ability.ARROW_RAIN);
                case ENDERMAN -> List.of(Ability.RIFT_BOLT);
                case SPIDER -> List.of(Ability.WEB_SHOT);
            };
            case 4 -> role == Role.SKELETON
                    ? List.of(Ability.TRIPLE_ARROWS) : List.of();
            case 5 -> switch (role) {
                case SKELETON -> List.of(Ability.TRIPLE_ARROWS, Ability.EXPLOSIVE_ARROWS);
                case ENDERMAN -> List.of(Ability.DRAGON_BREATH);
                case SPIDER -> List.of(Ability.WEB_SHOT);
            };
            case 6 -> switch (role) {
                case SKELETON -> List.of(Ability.TRIPLE_ARROWS, Ability.ARROW_RAIN);
                case ENDERMAN -> List.of(Ability.VOID_BURST);
                case SPIDER -> List.of(Ability.WEB_SHOT);
            };
            default -> List.of();
        };
    }

    public static boolean has(int wave, Role role, Ability ability) {
        return ability != null && profileForWave(wave, role).contains(ability);
    }

    /** Web-shot elites hold a useful support ring instead of pursuing melee contact. */
    public static boolean prefersRangedSupport(int wave, Role role) {
        return has(wave, role, Ability.WEB_SHOT);
    }

    /** Number of ordinary spiders promoted to the web-control role per wave. */
    public static int spiderConversions(int wave) {
        return switch (wave) {
            case 3 -> 1;
            case 5 -> 2;
            case 6 -> 1;
            default -> 0;
        };
    }

    public static double directDamage(Ability ability) {
        if (ability == null) return 0.0D;
        return switch (ability) {
            case ARROW_RAIN -> 3.0D;
            case RIFT_BOLT, VOID_BURST -> 4.0D;
            // The player collision only creates the cloud. The cloud is the
            // sole damage receipt, so its direct-hit player is not hit twice.
            case DRAGON_BREATH -> 0.0D;
            case TRIPLE_ARROWS, EXPLOSIVE_ARROWS, WEB_SHOT -> 0.0D;
        };
    }

    /** Ability cast by the coordinator; bow passives remain on the bow event path. */
    public static Ability activeCastForWave(int wave, Role role) {
        return profileForWave(wave, role).stream()
                .filter(ability -> ability != Ability.TRIPLE_ARROWS
                        && ability != Ability.EXPLOSIVE_ARROWS)
                .findFirst().orElse(null);
    }
}
