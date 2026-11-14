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
    public static final String DUST_VIOLET = "DUST_VIOLET";

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

    /**
     * The radial ground slam.
     *
     * <p>Built to be unmissable from inside the pile it punishes: a hard cyan
     * shockwave ring on the floor, a second violet ring half a block up, a
     * rising crown of sculk so the Warden reads as inhaling, and a soul burst at
     * chest height. The old Warden signatures were two thin passes each, which
     * at arena distance read as nothing.</p>
     */
    private static final List<Pass> WARDEN_SLAM = List.of(
            new Pass(Shape.GROUND_RING, 42, 0.0D, 6.0D, 0.0D, 0.0D,
                    "SONIC_BOOM", DUST_CYAN, true),
            new Pass(Shape.GROUND_RING, 34, 0.0D, 3.7D, 0.0D, 0.0D,
                    "SOUL_FIRE_FLAME", DUST_PURPLE, false),
            new Pass(Shape.HELIX, 26, 2.6D, 1.1D, 2.6D, 0.0D,
                    "SCULK_SOUL", DUST_VIOLET, false),
            new Pass(Shape.SELF_AURA, 16, 0.0D, 1.5D, 1.9D, 0.06D,
                    "SOUL", DUST_CYAN, false),
            new Pass(Shape.IMPACT_BURST, 20, 0.0D, 4.4D, 1.2D, 0.04D,
                    "SCULK_SOUL", DUST_CYAN, false));

    /**
     * Archmage fang cast, now that it paints a line under every victim.
     *
     * <p>The signature has to tell the group HOW MANY lines are coming: a single
     * ground ring would read as a single-target cast and invite the party to
     * stand still. The wide crimson ring plus the violet crown says "the floor
     * is unsafe" before the fangs arrive.</p>
     */
    private static final List<Pass> ARCHMAGE_FANGS_GROUP = List.of(
            new Pass(Shape.TARGET_OUTLINE, 24, 2.2D, 2.4D, 0.05D, 0.0D,
                    "SOUL_FIRE_FLAME", DUST_RED, false),
            new Pass(Shape.GROUND_RING, 34, 0.0D, 5.2D, 0.0D, 0.0D,
                    "SOUL_FIRE_FLAME", DUST_MAGENTA, false),
            new Pass(Shape.HELIX, 22, 1.6D, 0.65D, 2.0D, 0.0D,
                    "SOUL", DUST_VIOLET, false),
            new Pass(Shape.IMPACT_BURST, 18, 0.0D, 2.0D, 0.9D, 0.05D,
                    "SOUL_FIRE_FLAME", DUST_PURPLE, false));

    /**
     * The Marksman's one-shot defender summon.
     *
     * <p>W7-03: this identity was dispatched to the effect runtime but had no
     * authored passes at all, so the single most important beat of the trial —
     * a Giant arriving because the party pushed the archer below half health —
     * happened in silence and with no visual. It gets a rising green pillar so
     * the arrival is read from across the room.</p>
     */
    private static final List<Pass> MARKSMAN_DEFENDER = List.of(
            new Pass(Shape.GROUND_RING, 30, 0.0D, 3.2D, 0.0D, 0.0D,
                    "HAPPY_VILLAGER", DUST_GREEN, false),
            new Pass(Shape.HELIX, 30, 2.0D, 0.8D, 3.0D, 0.0D,
                    "END_ROD", DUST_GREEN, true),
            new Pass(Shape.SELF_AURA, 14, 0.0D, 1.2D, 1.8D, 0.06D,
                    "CRIT", DUST_GREEN, true));

    /**
     * The pair's joint crossfire.
     *
     * <p>W7-03: this is the strongest move the Marksman trial has, and it had no
     * authored signature at all — it was dispatched under its own id and fell
     * through to an empty pass list, so the one moment the two archers fire
     * together looked exactly like two ordinary shots. It gets two crossing
     * lances so the X they form is visible from the target's own position.</p>
     */
    private static final List<Pass> MARKSMAN_PAIRED_CROSSFIRE = List.of(
            new Pass(Shape.GROUND_RING, 20, 0.0D, 2.2D, 0.0D, 0.0D,
                    "END_ROD", DUST_CYAN, true),
            new Pass(Shape.AIM_LANCE, 18, 15.0D, 0.85D, 0.0D, 0.0D,
                    "ELECTRIC_SPARK", DUST_CYAN, true),
            new Pass(Shape.IMPACT_BURST, 16, 0.0D, 1.6D, 0.9D, 0.05D,
                    "CRIT", DUST_WHITE, true),
            new Pass(Shape.SELF_AURA, 10, 0.0D, 0.6D, 1.5D, 0.05D,
                    "CRIT", DUST_CYAN, true));

    /**
     * The authored 3D body for one Wave 7 attack identity.
     *
     * <p>Every pass list above is vanilla particles only, and the objective is
     * explicit that "the visual work is NOT complete merely because a particle
     * trail exists". Each ability therefore also names a real mesh body from the
     * spell kit, chosen for its form rather than invented fresh: an arrow rack
     * for the Marksman volleys, a lance for the aimed pierces, a rune for the
     * support buff, an arch for the summoned defender, a crown of fire for the
     * fang lines, a gate for the blink, an iris for the beam, talons for the
     * narrow strike and a fault for the wide one, a sonic bloom for the lunge and
     * a cage for the group fangs.
     *
     * <p>A null result means "no body", which the caller must treat as a bug for
     * any dispatched ability rather than as a quiet fallback: that silent
     * fallback is how the miniboss spells ended up particle-only.
     */
    public static BossSpellGeometryPolicy.Shape bodyShape(String abilityId) {
        if (abilityId == null || abilityId.isBlank()) {
            return null;
        }
        return switch (normalize(abilityId)) {
            case "MARKSMAN_QUICK_SHOT" -> BossSpellGeometryPolicy.Shape.ARROW_RACK;
            case "MARKSMAN_HEAVY_SHOT" -> BossSpellGeometryPolicy.Shape.RIFT_LANCE;
            case "MARKSMAN_SPREAD_SHOT" -> BossSpellGeometryPolicy.Shape.ARROW_RACK;
            case "MARKSMAN_SUPPORT" -> BossSpellGeometryPolicy.Shape.VOID_RUNE;
            case "MARKSMAN_PAIRED_CROSSFIRE" -> BossSpellGeometryPolicy.Shape.RIFT_LANCE;
            case "MARKSMAN_DEFENDER" -> BossSpellGeometryPolicy.Shape.SUMMON_ARCH;
            case "ARCHMAGE_FANGS" -> BossSpellGeometryPolicy.Shape.INFERNO_CROWN;
            case "ARCHMAGE_FANGS_GROUP" -> BossSpellGeometryPolicy.Shape.FIREBALL_CAGE;
            case "ARCHMAGE_SPEAR" -> BossSpellGeometryPolicy.Shape.RIFT_LANCE;
            case "ARCHMAGE_STEP" -> BossSpellGeometryPolicy.Shape.RIFT_GATE;
            case "ARCHMAGE_BEAM" -> BossSpellGeometryPolicy.Shape.CHEST_IRIS;
            case "WARDEN_NARROW_STRIKE" -> BossSpellGeometryPolicy.Shape.AMBUSH_TALONS;
            case "WARDEN_WIDE_STRIKE" -> BossSpellGeometryPolicy.Shape.SLAM_FAULT;
            case "WARDEN_SONIC_LUNGE" -> BossSpellGeometryPolicy.Shape.ECHO_BLOOM;
            case "WARDEN_SLAM" -> BossSpellGeometryPolicy.Shape.SLAM_FAULT;
            default -> null;
        };
    }

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
            case "MARKSMAN_PAIRED_CROSSFIRE" -> MARKSMAN_PAIRED_CROSSFIRE;
            case "MARKSMAN_DEFENDER" -> MARKSMAN_DEFENDER;
            case "ARCHMAGE_FANGS" -> ARCHMAGE_FANGS;
            case "ARCHMAGE_SPEAR" -> ARCHMAGE_SPEAR;
            case "ARCHMAGE_STEP" -> ARCHMAGE_STEP;
            case "ARCHMAGE_BEAM" -> ARCHMAGE_BEAM;
            case "WARDEN_NARROW_STRIKE" -> WARDEN_NARROW;
            case "WARDEN_WIDE_STRIKE" -> WARDEN_WIDE;
            case "WARDEN_SONIC_LUNGE" -> WARDEN_LUNGE;
            case "WARDEN_SLAM" -> WARDEN_SLAM;
            case "ARCHMAGE_FANGS_GROUP" -> ARCHMAGE_FANGS_GROUP;
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
            case "MARKSMAN_PAIRED_CROSSFIRE" -> "ENTITY_ARROW_SHOOT";
            case "MARKSMAN_DEFENDER" -> "ENTITY_RAVAGER_ROAR";
            case "ARCHMAGE_FANGS" -> "ENTITY_EVOKER_CAST_SPELL";
            case "ARCHMAGE_SPEAR" -> "ITEM_TRIDENT_THROW";
            case "ARCHMAGE_STEP" -> "ENTITY_ENDERMAN_TELEPORT";
            case "ARCHMAGE_BEAM" -> "ENTITY_EVOKER_CAST_SPELL";
            case "WARDEN_NARROW_STRIKE" -> "ENTITY_WARDEN_ATTACK_IMPACT";
            case "WARDEN_WIDE_STRIKE" -> "ENTITY_WARDEN_ATTACK_IMPACT";
            case "WARDEN_SONIC_LUNGE" -> "ENTITY_WARDEN_SONIC_BOOM";
            case "WARDEN_SLAM" -> "ENTITY_WARDEN_SONIC_BOOM";
            case "ARCHMAGE_FANGS_GROUP" -> "ENTITY_EVOKER_CAST_SPELL";
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
            case "WARDEN_SLAM" -> "ENTITY_WARDEN_ROAR";
            case "ARCHMAGE_FANGS_GROUP" -> "BLOCK_RESPAWN_ANCHOR_CHARGE";
            case "WARDEN_NARROW_STRIKE", "WARDEN_WIDE_STRIKE" -> "ENTITY_WARDEN_HEARTBEAT";
            case "WARDEN_SONIC_LUNGE" -> "ENTITY_WARDEN_ROAR";
            default -> "";
        };
    }

    /**
     * Canonical form of an ability identity.
     *
     * <p>The runtime hands the policy the bare attack name, while the dispatcher
     * and the logs carry the {@code WAVE7_} prefix. Both mean the same ability,
     * so the prefix is stripped here instead of at every call site: a policy
     * that answers an empty pass list for one spelling is a silent, invisible
     * ability, and that failure mode has already shipped once.</p>
     */
    private static String normalize(String abilityId) {
        String canonical = abilityId.trim().toUpperCase(java.util.Locale.ROOT);
        return canonical.startsWith("WAVE7_") ? canonical.substring("WAVE7_".length()) : canonical;
    }
}
