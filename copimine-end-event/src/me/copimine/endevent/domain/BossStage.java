package me.copimine.endevent.domain;

/**
 * Authoritative boss health stages.  The thresholds are expressed in absolute
 * HP because the event has a fixed 2500 HP default and must not drift when a
 * large hit crosses more than one boundary.
 */
public enum BossStage {
    AWAKENING(2500.0D, 2001.0D, "Страж Разлома — Пробуждение", "PURPLE"),
    HUNTER(2000.0D, 1501.0D, "Страж Разлома — Охотник", "BLUE"),
    DISTORTION(1500.0D, 1001.0D, "Страж Разлома — Искажение", "PINK"),
    ABSORPTION(1000.0D, 501.0D, "Страж Разлома — Поглощение", "YELLOW"),
    CATASTROPHE(500.0D, 0.0D, "Страж Разлома — Катастрофа", "RED");

    private final double upperInclusive;
    private final double lowerInclusive;
    private final String bossBarTitle;
    private final String bossBarColor;

    BossStage(double upperInclusive, double lowerInclusive, String bossBarTitle, String bossBarColor) {
        this.upperInclusive = upperInclusive;
        this.lowerInclusive = lowerInclusive;
        this.bossBarTitle = bossBarTitle;
        this.bossBarColor = bossBarColor;
    }

    public double upperInclusive() {
        return upperInclusive;
    }

    public double lowerInclusive() {
        return lowerInclusive;
    }

    public String bossBarTitle() {
        return bossBarTitle;
    }

    public String bossBarColor() {
        return bossBarColor;
    }

    public static BossStage forHealth(double health) {
        double safe = Double.isFinite(health) ? Math.max(0.0D, health) : 0.0D;
        for (BossStage stage : values()) {
            if (safe <= stage.upperInclusive && safe >= stage.lowerInclusive) {
                return stage;
            }
        }
        return safe > AWAKENING.upperInclusive ? AWAKENING : CATASTROPHE;
    }
}
