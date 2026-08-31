package me.copimine.endevent.domain;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Immutable visual palette for floor-anchored zone states.
 */
public final class ZoneVisualPolicy {
    private static final Map<ZoneState, Profile> PROFILES = buildProfiles();

    private ZoneVisualPolicy() {
    }

    public static Profile profile(ZoneState state) {
        return state == null ? null : PROFILES.get(state);
    }

    public static Map<ZoneState, Profile> profiles() {
        return PROFILES;
    }

    private static Map<ZoneState, Profile> buildProfiles() {
        LinkedHashMap<ZoneState, Profile> profiles = new LinkedHashMap<>();
        profiles.put(ZoneState.FREE, new Profile("free", "minecraft:end_rod", "minecraft:portal", 0x63D5FF, 0.04D, 72, 22));
        profiles.put(ZoneState.OCCUPIED, new Profile("occupied", "minecraft:golden_apple", "minecraft:note", 0xF6C45E, 0.05D, 64, 26));
        profiles.put(ZoneState.DANGER, new Profile("danger", "minecraft:smoke", "minecraft:flame", 0xE85B3D, 0.07D, 60, 30));
        profiles.put(ZoneState.SAFE, new Profile("safe", "minecraft:happy_villager", "minecraft:dust_color_transition", 0x4ACCA7, 0.08D, 68, 18));
        profiles.put(ZoneState.COMPLETED, new Profile("completed", "minecraft:spore_blossom_air", "minecraft:effect", 0x7BEA67, 0.12D, 56, 16));
        return Map.copyOf(profiles);
    }

    public enum ZoneState {
        FREE,
        OCCUPIED,
        DANGER,
        SAFE,
        COMPLETED
    }

    public record Profile(String id, String primaryParticle, String accentParticle, int colorRgb,
                          double floorY, int ringPoints, int particleBudget) {
        public Profile {
            id = normalize(id);
            primaryParticle = normalize(primaryParticle);
            accentParticle = normalize(accentParticle);
            if (id.isBlank() || primaryParticle.isBlank() || accentParticle.isBlank()) {
                throw new IllegalArgumentException("invalid zone profile");
            }
            if (primaryParticle.equals(accentParticle)) {
                throw new IllegalArgumentException("zone profile particles must be distinct");
            }
            if (colorRgb < 0 || colorRgb > 0xFFFFFF) {
                throw new IllegalArgumentException("invalid zone profile color");
            }
            if (floorY < 0.04D || floorY > 0.12D) {
                throw new IllegalArgumentException("invalid zone profile floor height");
            }
            if (ringPoints < 1 || ringPoints > 96) {
                throw new IllegalArgumentException("invalid zone profile ring points");
            }
            if (particleBudget < 1 || particleBudget > 96) {
                throw new IllegalArgumentException("invalid zone profile particle budget");
            }
        }

        private static String normalize(String value) {
            return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        }
    }
}
