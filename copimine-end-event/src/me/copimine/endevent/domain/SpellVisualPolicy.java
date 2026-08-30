package me.copimine.endevent.domain;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Player-facing visual contract for every End Rift spell.  The Bukkit adapter
 * turns the profile into vanilla particles; keeping the names, layers and
 * budgets here makes it possible to test that a newly added spell cannot
 * silently fall back to one generic particle.
 */
public final class SpellVisualPolicy {
    private static final Map<String, VisualProfile> PROFILES = buildProfiles();

    private SpellVisualPolicy() {
    }

    public static VisualProfile profile(String spellId) {
        if (spellId == null) {
            return null;
        }
        return PROFILES.get(spellId.trim().toLowerCase(Locale.ROOT));
    }

    public static Map<String, VisualProfile> profiles() {
        return PROFILES;
    }

    private static Map<String, VisualProfile> buildProfiles() {
        LinkedHashMap<String, VisualProfile> profiles = new LinkedHashMap<>();
        add(profiles, "void_blast", "кольцо взрыва", "DRAGON_BREATH", "DUST_MAGENTA", 3, 44);
        add(profiles, "rift_projectile", "спираль разлома", "REVERSE_PORTAL", "DUST_CYAN", 3, 29);
        add(profiles, "rift_arrows", "кровавый веер", "SOUL_FIRE_FLAME", "CRIT_RED", 3, 26);
        add(profiles, "arrow_salvo", "тройное копьё", "END_ROD", "DUST_MAGENTA", 3, 24);
        add(profiles, "void_mark", "четырёхугольное клеймо", "REVERSE_PORTAL", "DUST_PURPLE", 4, 48);
        add(profiles, "summon_servants", "спираль призыва", "SOUL_FIRE_FLAME", "WITCH", 3, 24);
        add(profiles, "will_distortion", "ломаная нить воли", "ELECTRIC_SPARK", "WITCH", 3, 24);
        add(profiles, "arena_inferno", "венец пламени", "SOUL_FIRE_FLAME", "END_ROD", 3, 40);
        add(profiles, "rift_step", "двойной след", "PORTAL", "END_ROD", 3, 24);
        add(profiles, "void_snare", "схлопывающиеся цепи", "REVERSE_PORTAL", "SMOKE", 3, 18);
        add(profiles, "echo_pulse", "удар эхом", "SCULK_SOUL", "SONIC_BOOM", 3, 22);
        add(profiles, "rift_euphoria", "Эйфория Пустоты", "WITCH", "REVERSE_PORTAL", 4, 36);
        return Map.copyOf(profiles);
    }

    private static void add(Map<String, VisualProfile> profiles, String id, String name,
                            String primaryParticle, String accentParticle,
                            int layers, int estimatedParticles) {
        profiles.put(id, new VisualProfile(id, name, primaryParticle, accentParticle,
                layers, estimatedParticles));
    }

    public record VisualProfile(String id, String displayName, String primaryParticle,
                                String accentParticle, int layers, int estimatedParticles) {
        public VisualProfile {
            id = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
            displayName = displayName == null ? "эффект Разлома" : displayName.trim();
            primaryParticle = primaryParticle == null ? "END_ROD" : primaryParticle.trim();
            accentParticle = accentParticle == null ? "END_ROD" : accentParticle.trim();
            layers = Math.max(2, layers);
            estimatedParticles = Math.max(1, estimatedParticles);
            if (id.isBlank() || displayName.isBlank()) {
                throw new IllegalArgumentException("invalid spell visual profile");
            }
        }
    }
}
