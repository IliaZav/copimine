package me.copimine.artifacts;

/** Pure shot admission policy used to keep Multishot within one cooldown window. */
public final class CombatArtifactShotPolicy {
    private static final long MULTISHOT_WINDOW_TICKS = 2L;
    private static final int MULTISHOT_PROJECTILE_LIMIT = 3;

    private CombatArtifactShotPolicy() {
    }

    public static Decision decide(
            long nowSeconds,
            long currentTick,
            long cooldownUntil,
            boolean explosiveMultishot,
            ShotWindow previousWindow
    ) {
        boolean continuation = explosiveMultishot
                && previousWindow != null
                && currentTick >= previousWindow.tick()
                && currentTick - previousWindow.tick() <= MULTISHOT_WINDOW_TICKS
                && previousWindow.projectileCount() < MULTISHOT_PROJECTILE_LIMIT;
        if (cooldownUntil > nowSeconds && !continuation) {
            return new Decision(false, false, previousWindow);
        }

        if (!explosiveMultishot) {
            return new Decision(true, true, null);
        }

        ShotWindow nextWindow = continuation
                ? new ShotWindow(previousWindow.tick(), previousWindow.projectileCount() + 1)
                : new ShotWindow(currentTick, 1);
        return new Decision(true, !continuation, nextWindow);
    }

    public record Decision(boolean allowed, boolean startsShot, ShotWindow window) {
    }

    public record ShotWindow(long tick, int projectileCount) {
    }
}
