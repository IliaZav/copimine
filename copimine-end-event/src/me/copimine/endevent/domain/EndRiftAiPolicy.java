package me.copimine.endevent.domain;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

/** Pure target, ability and client-facing combat selections. */
public final class EndRiftAiPolicy {
    private EndRiftAiPolicy() {
    }

    public enum BossSpell {
        VOID_BLAST("void_blast", "Взрыв Бездны"),
        RIFT_PROJECTILE("rift_projectile", "Снаряд Разлома"),
        VOID_MARK("void_mark", "Клеймо Пустоты"),
        RIFT_ARROWS("rift_arrows", "Шквал Стрел Разлома"),
        SUMMON_SERVANTS("summon_servants", "Призыв слуг Разлома"),
        ARENA_INFERNO("arena_inferno", "Пламя Разлома"),
        FINAL_STRIKE("final_strike", "Приговор Разлома");

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

    public static TargetChoice chooseFairTarget(List<UUID> candidates, UUID current,
                                                List<UUID> recent, int cursor) {
        List<UUID> unique = new ArrayList<>(new LinkedHashSet<>(
                candidates == null ? List.of() : candidates));
        unique.removeIf(uuid -> uuid == null);
        if (unique.isEmpty()) {
            return new TargetChoice(null, Math.max(0, cursor));
        }
        LinkedHashSet<UUID> recentSet = new LinkedHashSet<>(recent == null ? List.of() : recent);
        List<UUID> preferred = unique.stream()
                .filter(uuid -> !uuid.equals(current) && !recentSet.contains(uuid)).toList();
        List<UUID> fallback = unique.stream()
                .filter(uuid -> !uuid.equals(current)).toList();
        List<UUID> pool = preferred.isEmpty() ? (fallback.isEmpty() ? unique : fallback) : preferred;
        int safeCursor = Math.floorMod(cursor, pool.size());
        return new TargetChoice(pool.get(safeCursor), safeCursor + 1);
    }

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

    public static BossSpell chooseBossSpell(List<BossSpell> available,
                                             BossSpell previous, int cursor) {
        List<BossSpell> unique = new ArrayList<>(new LinkedHashSet<>(
                available == null ? List.of() : available));
        unique.removeIf(spell -> spell == null);
        if (unique.isEmpty()) {
            return null;
        }
        List<BossSpell> alternatives = unique.stream().filter(spell -> spell != previous).toList();
        List<BossSpell> pool = alternatives.isEmpty() ? unique : alternatives;
        return pool.get(Math.floorMod(cursor, pool.size()));
    }

    /** Stable one-spell assignment for an elite's lifetime and objective. */
    public static MiniBossSpell miniBossSpell(EndRiftObjective.Objective objective,
                                              int eliteSlot) {
        MiniBossSpell[] spells = MiniBossSpell.values();
        int safeObjective = objective == null ? 0 : objective.ordinal();
        return spells[Math.floorMod(safeObjective + eliteSlot, spells.length)];
    }

    public static NarcoticEffect randomNarcoticEffect(long seed) {
        NarcoticEffect[] effects = NarcoticEffect.values();
        return effects[Math.floorMod(seed, effects.length)];
    }

    public record TargetChoice(UUID target, int nextCursor) {
    }
}
