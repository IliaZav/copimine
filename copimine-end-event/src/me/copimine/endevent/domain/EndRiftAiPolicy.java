package me.copimine.endevent.domain;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

/**
 * Small deterministic policy layer for the End Rift combat controllers.
 * Bukkit entities stay outside this class so the fairness and spell contracts
 * can be tested without a running server.
 */
public final class EndRiftAiPolicy {
    private EndRiftAiPolicy() {
    }

    public enum BossPhase {
        NORMAL,
        HALF,
        FINAL
    }

    public enum BossSpell {
        VOID_BLAST("void_blast", "Взрыв Бездны"),
        RIFT_PROJECTILE("rift_projectile", "Снаряд Разлома"),
        VOID_MARK("void_mark", "Клеймо Пустоты"),
        RIFT_ARROWS("rift_arrows", "Шквал Стрел Разлома"),
        SUMMON_SERVANTS("summon_servants", "Призыв слуг Разлома"),
        WILL_DISTORTION("will_distortion", "Искажение воли"),
        ARENA_INFERNO("arena_inferno", "Пламя Разлома");

        private final String id;
        private final String displayName;

        BossSpell(String id, String displayName) {
            this.id = id;
            this.displayName = displayName;
        }

        public String id() {
            return id;
        }

        public String displayName() {
            return displayName;
        }
    }

    public enum MiniBossSpell {
        RIFT_STEP("rift_step", "Рывок Разлома"),
        VOID_SNARE("void_snare", "Кандалы Пустоты"),
        ECHO_PULSE("echo_pulse", "Импульс Эха"),
        ARROW_SALVO("arrow_salvo", "Залп Разлома"),
        RIFT_EUPHORIA("rift_euphoria", "Эйфория Пустоты");

        private final String id;
        private final String displayName;

        MiniBossSpell(String id, String displayName) {
            this.id = id;
            this.displayName = displayName;
        }

        public String id() {
            return id;
        }

        public String displayName() {
            return displayName;
        }
    }

    /**
     * The mini-boss spell selects one bounded visual/effect profile.  These
     * identifiers mirror the already shipped narcotics visual catalog, while
     * the event remains safe if the optional client bridge is unavailable.
     */
    public enum NarcoticEffect {
        DESATURATE("DESATURATE", "Пелена", "DARKNESS"),
        COLOR_CONVOLVE("COLOR_CONVOLVE", "Цветной срыв", "NAUSEA"),
        SCAN_PINCUSHION("SCAN_PINCUSHION", "Иглы сканера", "GLOWING"),
        GREEN_NOISE("GREEN_NOISE", "Зелёный шум", "POISON"),
        INVERT("INVERT", "Обратный свет", "BLINDNESS"),
        WOBBLE("WOBBLE", "Качание пустоты", "NAUSEA"),
        BLOBS("BLOBS", "Пятна разлома", "SLOWNESS"),
        PENCIL("PENCIL", "Линии на сетчатке", "MINING_FATIGUE"),
        CHAOS("CHAOS", "Хаос в крови", "WEAKNESS");

        private final String id;
        private final String displayName;
        private final String potionEffectId;

        NarcoticEffect(String id, String displayName, String potionEffectId) {
            this.id = id;
            this.displayName = displayName;
            this.potionEffectId = potionEffectId;
        }

        public String id() {
            return id;
        }

        public String displayName() {
            return displayName;
        }

        public String potionEffectId() {
            return potionEffectId;
        }
    }

    public static BossPhase bossPhase(
            double health,
            double maxHealth,
            double halfThreshold,
            double finalThreshold,
            boolean halfTriggered,
            boolean finalTriggered) {
        double safeHealth = Math.max(0.0D, Math.min(finite(maxHealth) && maxHealth > 0.0D ? maxHealth : 1.0D,
                finite(health) ? health : 0.0D));
        if (finalTriggered || safeHealth <= Math.max(0.0D, finalThreshold)) {
            return BossPhase.FINAL;
        }
        if (halfTriggered || safeHealth <= Math.max(finalThreshold, halfThreshold)) {
            return BossPhase.HALF;
        }
        return BossPhase.NORMAL;
    }

    public static TargetChoice chooseFairTarget(
            List<UUID> candidates,
            UUID current,
            List<UUID> recent,
            int cursor) {
        List<UUID> unique = new ArrayList<>(new LinkedHashSet<>(candidates == null ? List.of() : candidates));
        unique.removeIf(uuid -> uuid == null);
        if (unique.isEmpty()) {
            return new TargetChoice(null, Math.max(0, cursor));
        }
        LinkedHashSet<UUID> recentSet = new LinkedHashSet<>(recent == null ? List.of() : recent);
        List<UUID> preferred = unique.stream()
                .filter(uuid -> !uuid.equals(current) && !recentSet.contains(uuid))
                .toList();
        List<UUID> fallback = unique.stream()
                .filter(uuid -> !uuid.equals(current))
                .toList();
        List<UUID> pool = preferred.isEmpty() ? (fallback.isEmpty() ? unique : fallback) : preferred;
        int safeCursor = Math.floorMod(cursor, pool.size());
        return new TargetChoice(pool.get(safeCursor), safeCursor + 1);
    }

    /**
     * Keep a bounded, newest-first memory for a mob's recent player targets.
     * The controller uses this to spread pressure across a five-player party
     * without making target selection random or retaining disconnected UUIDs
     * forever.
     */
    public static List<UUID> rememberTarget(List<UUID> recent, UUID target, int limit) {
        int safeLimit = Math.max(0, limit);
        if (target == null || safeLimit == 0) {
            return List.of();
        }
        LinkedHashSet<UUID> ordered = new LinkedHashSet<>();
        ordered.add(target);
        if (recent != null) {
            for (UUID uuid : recent) {
                if (uuid != null) {
                    ordered.add(uuid);
                }
                if (ordered.size() >= safeLimit) {
                    break;
                }
            }
        }
        return List.copyOf(ordered).subList(0, Math.min(safeLimit, ordered.size()));
    }

    public static BossSpell chooseBossSpell(List<BossSpell> available, BossSpell previous, int cursor) {
        List<BossSpell> unique = new ArrayList<>(new LinkedHashSet<>(available == null ? List.of() : available));
        unique.removeIf(spell -> spell == null);
        if (unique.isEmpty()) {
            return null;
        }
        List<BossSpell> alternatives = unique.stream().filter(spell -> spell != previous).toList();
        List<BossSpell> pool = alternatives.isEmpty() ? unique : alternatives;
        return pool.get(Math.floorMod(cursor, pool.size()));
    }

    /** Stable assignment: an elite owns one ability for its entire lifetime. */
    public static MiniBossSpell miniBossSpell(int wave, int eliteSlot) {
        MiniBossSpell[] spells = MiniBossSpell.values();
        int offset = Math.max(0, wave - 3);
        return spells[Math.floorMod(offset + eliteSlot, spells.length)];
    }

    /** Stable for a cast seed, but still distributes the profiles uniformly. */
    public static NarcoticEffect randomNarcoticEffect(long seed) {
        NarcoticEffect[] effects = NarcoticEffect.values();
        return effects[Math.floorMod(seed, effects.length)];
    }

    public record TargetChoice(UUID target, int nextCursor) {
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
