package me.copimine.endevent.domain;

/** Server-side contract for the boss's long, ghast-like fireball cast. */
public final class BossFireballPolicy {
    public static final int MIN_TELEGRAPH_TICKS = 60;
    public static final int MAX_FLIGHT_TICKS = 140;
    public static final double SPEED = 0.48D;
    public static final float YIELD = 0.0F;
    public static final boolean INCENDIARY = false;

    private BossFireballPolicy() {
    }

    public static int telegraphTicks(int configuredTicks) {
        return Math.max(MIN_TELEGRAPH_TICKS, Math.max(1, configuredTicks));
    }

    public static boolean canDestroyBlocks(float yield, boolean incendiary) {
        return Float.isFinite(yield) && yield <= 0.0F && !incendiary;
    }
}
