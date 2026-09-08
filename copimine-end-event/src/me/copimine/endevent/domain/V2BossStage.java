package me.copimine.endevent.domain;

/** One-way health bands used by the official V2 boss fight. */
public enum V2BossStage {
    AWAKENING(1.00D, 0.80D, "Хранитель Разлома — Пробуждение", "PURPLE"),
    HUNT(0.80D, 0.60D, "Хранитель Разлома — Охота", "BLUE"),
    RIFT(0.60D, 0.45D, "Хранитель Разлома — Разлом", "PINK"),
    OVERLOAD(0.45D, 0.30D, "Хранитель Разлома — Перегрузка", "YELLOW"),
    RAGE(0.30D, 0.20D, "Хранитель Разлома — Ярость", "RED"),
    LAST_SEAL(0.20D, 0.00D, "Хранитель Разлома — Последняя Печать", "WHITE");

    private final double upperExclusive;
    private final double lowerInclusive;
    private final String title;
    private final String color;

    V2BossStage(double upperExclusive, double lowerInclusive, String title, String color) {
        this.upperExclusive = upperExclusive;
        this.lowerInclusive = lowerInclusive;
        this.title = title;
        this.color = color;
    }

    public double upperExclusive() {
        return upperExclusive;
    }

    public double lowerInclusive() {
        return lowerInclusive;
    }

    public String title() {
        return title;
    }

    public String color() {
        return color;
    }

    public int rank() {
        return ordinal();
    }

    public static V2BossStage forHealth(double health, double maxHealth) {
        double safeMax = finite(maxHealth) && maxHealth > 0.0D ? maxHealth : 1.0D;
        double fraction = Math.max(0.0D, Math.min(1.0D,
                (finite(health) ? health : 0.0D) / safeMax));
        for (V2BossStage stage : values()) {
            if (fraction > stage.lowerInclusive && fraction <= stage.upperExclusive
                    || stage == LAST_SEAL && fraction <= stage.upperExclusive) {
                return stage;
            }
        }
        return AWAKENING;
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
