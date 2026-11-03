package me.copimine.endevent.domain;

import java.util.List;

/**
 * Authored particle and sound signature for every Wave 7 miniboss ability.
 *
 * <p>The trial actors used to announce their attacks with a single generic
 * particle each. This policy gives every ability a readable visual identity
 * built from layered vanilla particle passes — a ground ring, an ascending
 * spiral, an aimed lance, a radial burst — so a player can tell a Marksman
 * heavy shot from a quick shot and an Archmage fang line from its beam before
 * the damage lands. The runtime only executes the passes; every number lives
 * here so the shapes can be asserted in tests without a server.</p>
 */
public final class Wave7AbilityEffectPolicy {
    /** One particle pass: shape, count, extents, colour and spread. */
    public record Pass(Shape shape, int count, double extent, double radius, double height,
                       double spread, String particle, String accentParticle, boolean glow) {
        public Pass {
            if (shape == null || count < 1 || count > 512
                    || !Double.isFinite(extent) || extent < 0.0D || extent > 64.0D
                    || !Double.isFinite(radius) || radius < 0.0D || radius > 16.0D
                    || !Double.isFinite(height) || height < 0.0D || height > 16.0D
                    || !Double.isFinite(spread) || spread < 0.0D || spread > 1.0D
                    || particle == null || particle.isBlank()
                    || accentParticle == null) {
                throw new IllegalArgumentException("invalid Wave 7 effect pass");
            }
        }
    }

    /** The primitive shapes the runtime knows how to draw. */
    public enum Shape {
        /** A flat ring on the ground under the actor. */
        GROUND_RING,
        /** Points along the aimed direction from the actor's eyes. */
        AIM_LANCE,
        /** A vertical helix rising around the actor. */
        HELIX,
        /** An expanding sphere centred on the impact point. */
        IMPACT_BURST,
        /** A tight glow on the actor's silhouette. */
        SELF_AURA,
        /** A trail left behind a moving projectile. */
        PROJECTILE_TRAIL,
        /** An outline drawn on the ground around a marked player. */
        TARGET_OUTLINE
    }

    public static final String DUST_PURPLE = "DUST_PURPLE";
    public static final String DUST_CYAN = "DUST_CYAN";
    public static final String DUST_RED = "DUST_RED";
    public static final String DUST_WHITE = "DUST_WHITE";
    public static final String DUST_GREEN = "DUST_GREEN";
    public static final String DUST_MAGENTA = "DUST_MAGENTA";

    private static final List<Pass> MARKSMAN_QUICK = List.of(
            new Pass(Shape.AIM_LANCE, 6, 9.0D, 0.22D, 0.0D, 0.0D,
                    "END_ROD", DUST_WHITE, true),
            new Pass(Shape.SELF_AURA, 4, 0.0D, 0.35D, 1.1D, 0.05D,
                    "CRIT", DUST_WHITE, true));
    private static final List<Pass> MARKSMAN_HEAVY = List.of(
            new Pass(Shape.AIM_LANCE, 14, 14.0D, 0.55D, 0.0D, 0.0D,
                    "ELECTRIC_SPARK", DUST_CYAN, true),
            new Pass(Shape.GROUND_RING, 12, 0.0D, 1.1D, 0.0D, 0.0D,
                    "END_ROD", DUST_CYAN, true),
            new Pass(Shape.SELF_AURA, 8, 0.0D, 0.55D, 1.4D, 0.08D,
                    "CRIT", DUST_CYAN, true));
    private static final List<Pass> MARKSMAN_SPREAD = List.of(
            new Pass(Shape.GROUND_RING, 24, 0.0D, 2.6D, 0.0D, 0.0D,
                    "END_ROD", DUST_GREEN, true),
            new Pass(Shape.IMPACT_BURST, 18, 0.0D, 2.2D, 0.6D, 0.06D,
                    "CRIT", DUST_GREEN, true),
            new Pass(Shape.AIM_LANCE, 10, 16.0D, 1.1D, 0.0D, 0.0D,
                    "END_ROD", DUST_GREEN, true));
    private static final List<Pass> MARKSMAN_SUPPORT = List.of(
            new Pass(Shape.HELIX, 20, 1.4D, 0.55D, 1.9D, 0.02D,
                    "HAPPY_VILLAGER", DUST_GREEN, false),
            new Pass(Shape.SELF_AURA, 8, 0.0D, 0.45D, 1.2D, 0.04D,
                    "END_ROD", DUST_GREEN, true));

    private static final List<Pass> ARCHMAGE_FANGS = List.of(
            new Pass(Shape.TARGET_OUTLINE, 28, 1.9D, 1.9D, 0.05D, 0.0D,
                    "SOUL_FIRE_FLAME", DUST_RED, false),
            new Pass(Shape.HELIX, 16, 1.2D, 0.5D, 1.6D, 0.0D,
                    "SOUL", DUST_PURPLE, false));
    private static final List<Pass> ARCHMAGE_SPEAR = List.of(
            new Pass(Shape.AIM_LANCE, 16, 15.0D, 0.6D, 0.0D, 0.0D,
                    "SOUL_FIRE_FLAME", DUST_MAGENTA, true),
            new Pass(Shape.SELF_AURA, 10, 0.0D, 0.5D, 1.5D, 0.06D,
                    "SOUL", DUST_MAGENTA, false));
    private static final List<Pass> ARCHMAGE_STEP = List.of(
            new Pass(Shape.IMPACT_BURST, 22, 0.0D, 1.3D, 1.0D, 0.05D,
                    "REVERSE_PORTAL", DUST_PURPLE, false),
            new Pass(Shape.HELIX, 14, 1.6D, 0.45D, 1.8D, 0.0D,
                    "PORTAL", DUST_PURPLE, false));
    private static final List<Pass> ARCHMAGE_BEAM = List.of(
            new Pass(Shape.AIM_LANCE, 22, 14.0D, 0.7D, 0.0D, 0.0D,
                    "DRAGON_BREATH", DUST_MAGENTA, false),
            new Pass(Shape.IMPACT_BURST, 16, 0.0D, 1.4D, 1.1D, 0.04D,
                    "DRAGON_BREATH", DUST_PURPLE, false));

    private static final List<Pass> WARDEN_NARROW = List.of(
            new Pass(Shape.GROUND_RING, 14, 0.0D, 1.6D, 0.0D, 0.0D,
                    "SOUL", DUST_PURPLE, false),
            new Pass(Shape.AIM_LANCE, 12, 7.0D, 0.5D, 0.0D, 0.0D,
                    "SONIC_BOOM", DUST_CYAN, true));
    private static final List<Pass> WARDEN_WIDE = List.of(
            new Pass(Shape.GROUND_RING, 26, 0.0D, 2.8D, 0.0D, 0.0D,
                    "SOUL", DUST_PURPLE, false),
            new Pass(Shape.AIM_LANCE, 18, 6.0D, 1.0D, 0.0D, 0.0D,
                    "SONIC_BOOM", DUST_CYAN, true),
            new Pass(Shape.IMPACT_BURST, 12, 0.0D, 1.8D, 0.4D, 0.05D,
                    "SCULK_SOUL", DUST_CYAN, false));
    private static final List<Pass> WARDEN_LUNGE = List.of(
            new Pass(Shape.HELIX, 24, 1.8D, 0.7D, 2.2D, 0.0D,
                    "SCULK_SOUL", DUST_CYAN, false),
            new Pass(Shape.AIM_LANCE, 20, 9.0D, 0.9D, 0.0D, 0.0D,
                    "SONIC_BOOM", DUST_CYAN, true));

    private Wave7AbilityEffectPolicy() {
    }

    /** Every authored pass for one Wave 7 attack identity. */
    public static List<Pass> passes(String abilityId) {
        if (abilityId == null || abilityId.isBlank()) {
            return List.of();
        }
        return switch (normalize(abilityId)) {
            case "MARKSMAN_QUICK_SHOT" -> MARKSMAN_QUICK;
            case "MARKSMAN_HEAVY_SHOT" -> MARKSMAN_HEAVY;
            case "MARKSMAN_SPREAD_SHOT" -> MARKSMAN_SPREAD;
            case "MARKSMAN_SUPPORT" -> MARKSMAN_SUPPORT;
            case "ARCHMAGE_FANGS" -> ARCHMAGE_FANGS;
            case "ARCHMAGE_SPEAR" -> ARCHMAGE_SPEAR;
            case "ARCHMAGE_STEP" -> ARCHMAGE_STEP;
            case "ARCHMAGE_BEAM" -> ARCHMAGE_BEAM;
            case "WARDEN_NARROW_STRIKE" -> WARDEN_NARROW;
            case "WARDEN_WIDE_STRIKE" -> WARDEN_WIDE;
            case "WARDEN_SONIC_LUNGE" -> WARDEN_LUNGE;
            default -> List.of();
        };
    }

    /**
     * Sound an ability plays when it fires.
     *
     * <p>The keys are vanilla {@code org.bukkit.Sound} constant names so the
     * runtime can resolve them without a second table, and a missing key is a
     * silent ability rather than a crash.</p>
     */
    public static String releaseSound(String abilityId) {
        return switch (normalize(abilityId)) {
            case "MARKSMAN_QUICK_SHOT" -> "ENTITY_ARROW_SHOOT";
            case "MARKSMAN_HEAVY_SHOT" -> "ENTITY_ARROW_HIT_PLAYER";
            case "MARKSMAN_SPREAD_SHOT" -> "ENTITY_ARROW_SHOOT";
            case "MARKSMAN_SUPPORT" -> "BLOCK_AMETHYST_BLOCK_CHIME";
            case "ARCHMAGE_FANGS" -> "ENTITY_EVOKER_CAST_SPELL";
            case "ARCHMAGE_SPEAR" -> "ITEM_TRIDENT_THROW";
            case "ARCHMAGE_STEP" -> "ENTITY_ENDERMAN_TELEPORT";
            case "ARCHMAGE_BEAM" -> "ENTITY_EVOKER_CAST_SPELL";
            case "WARDEN_NARROW_STRIKE" -> "ENTITY_WARDEN_ATTACK_IMPACT";
            case "WARDEN_WIDE_STRIKE" -> "ENTITY_WARDEN_ATTACK_IMPACT";
            case "WARDEN_SONIC_LUNGE" -> "ENTITY_WARDEN_SONIC_BOOM";
            default -> "";
        };
    }

    /** Short cue played while an ability is still telegraphing. */
    public static String telegraphSound(String abilityId) {
        return switch (normalize(abilityId)) {
            case "MARKSMAN_QUICK_SHOT", "MARKSMAN_SPREAD_SHOT" -> "BLOCK_AMETHYST_BLOCK_CHIME";
            case "MARKSMAN_HEAVY_SHOT" -> "BLOCK_AMETHYST_BLOCK_RESONATE";
            case "ARCHMAGE_FANGS" -> "BLOCK_RESPAWN_ANCHOR_CHARGE";
            case "ARCHMAGE_SPEAR" -> "ENTITY_EVOKER_PREPARE_ATTACK";
            case "ARCHMAGE_STEP" -> "ENTITY_ILLUSIONER_PREPARE_MIRROR";
            case "ARCHMAGE_BEAM" -> "BLOCK_BEACON_ACTIVATE";
            case "WARDEN_NARROW_STRIKE", "WARDEN_WIDE_STRIKE" -> "ENTITY_WARDEN_HEARTBEAT";
            case "WARDEN_SONIC_LUNGE" -> "ENTITY_WARDEN_ROAR";
            default -> "";
        };
    }

    private static String normalize(String abilityId) {
        return abilityId.trim().toUpperCase(java.util.Locale.ROOT);
    }
}
