package me.copimine.dungeon.domain;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Pure capability and AI-limit registry (spec OV4.10
 * {@code DungeonMobTemplateRegistry}; verification R05).
 *
 * <p>Exactly three vanilla-derived dungeon species may exist, and every template
 * carries a stable capability set plus a bounded aggro limit. This is a data
 * table only: it introduces no new behavioural scripting runtime and no new
 * species. The runtime that turns a template into a real Paper spawn stays
 * outside the pure core.</p>
 */
public final class DungeonMobRegistry {

    /** Bounded abilities a template may advertise. */
    public enum Ability {
        /** Burrows into and out of blocks. */
        BURROW,
        /** Guided leap toward a target. */
        LEAP,
        /** A ranged wind projectile with predictable knockback. */
        WIND_PROJECTILE,
        /** A charged poison bolt. */
        POISON_BOLT,
        /** Aggro-group linking. */
        SWARM,
        /** Infrequent, bounded strategic teleport. */
        STRATEGIC_TELEPORT,
        /** Plain melee pressure. */
        MELEE
    }

    /** Hard ceiling on a template's configured aggro range. */
    public static final int MAX_AGGRO_RANGE = 48;

    /** A template's stable capability profile. */
    public record Profile(MobTemplate template, Set<Ability> abilities, int aggroRange) {

        public Profile {
            Objects.requireNonNull(template, "template");
            abilities = Set.copyOf(abilities);
            if (aggroRange < 1 || aggroRange > MAX_AGGRO_RANGE) {
                throw new IllegalArgumentException("Дистанция агрессии вне допустимого диапазона: " + aggroRange);
            }
        }
    }

    private static final Map<MobTemplate, Set<Ability>> ABILITIES = new EnumMap<>(MobTemplate.class);
    private static final Map<MobTemplate, Integer> AGGRO = new EnumMap<>(MobTemplate.class);

    static {
        ABILITIES.put(MobTemplate.RIFT_SILVERFISH, EnumSet.of(Ability.BURROW, Ability.LEAP, Ability.MELEE));
        ABILITIES.put(MobTemplate.RIFT_BREEZE, EnumSet.of(Ability.LEAP, Ability.WIND_PROJECTILE, Ability.MELEE));
        ABILITIES.put(MobTemplate.RIFT_BOGGED, EnumSet.of(Ability.POISON_BOLT, Ability.MELEE));
        ABILITIES.put(MobTemplate.ELITE_ENDERMAN, EnumSet.of(Ability.STRATEGIC_TELEPORT, Ability.MELEE));
        ABILITIES.put(MobTemplate.ORDINARY_SPIDER, EnumSet.of(Ability.SWARM, Ability.MELEE));
        ABILITIES.put(MobTemplate.ORDINARY_ENDERMAN, EnumSet.of(Ability.STRATEGIC_TELEPORT, Ability.MELEE));

        AGGRO.put(MobTemplate.RIFT_SILVERFISH, 12);
        AGGRO.put(MobTemplate.RIFT_BREEZE, 16);
        AGGRO.put(MobTemplate.RIFT_BOGGED, 20);
        AGGRO.put(MobTemplate.RIFT_GIANT, 32);
        AGGRO.put(MobTemplate.RIFT_RAM, 40);
    }

    private DungeonMobRegistry() {
    }

    public static Profile profile(MobTemplate template) {
        Objects.requireNonNull(template, "template");
        Set<Ability> abilities = ABILITIES.getOrDefault(template, Set.of(Ability.MELEE));
        int aggro = AGGRO.getOrDefault(template, template.isDungeonExtension() ? 16 : 24);
        return new Profile(template, abilities, aggro);
    }

    public static boolean has(MobTemplate template, Ability ability) {
        return profile(template).abilities().contains(ability);
    }

    /** The number of new (dungeon-extension) species: exactly three. */
    public static int newSpeciesCount() {
        int count = 0;
        for (MobTemplate template : MobTemplate.values()) {
            if (template.isDungeonExtension()) {
                count++;
            }
        }
        return count;
    }

    /** Every template must be a vanilla-derived actor, never a brand new runtime. */
    public static boolean isDerivedFromVanilla(MobTemplate template) {
        return template.baseType() != null && !template.baseType().isBlank();
    }

    public static String describe(MobTemplate template) {
        Profile profile = profile(template);
        return template.id() + " [" + template.baseType() + "] aggro=" + profile.aggroRange()
                + " abilities=" + profile.abilities();
    }
}
