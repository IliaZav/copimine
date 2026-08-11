package me.copimine.artifacts;

import java.util.Objects;

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
        return decide(nowSeconds, currentTick, cooldownUntil, explosiveMultishot, "", previousWindow);
    }

    public static Decision decide(
            long nowSeconds,
            long currentTick,
            long cooldownUntil,
            boolean explosiveMultishot,
            String weaponUniqueId,
            ShotWindow previousWindow
    ) {
        String physicalWeaponId = weaponUniqueId == null ? "" : weaponUniqueId;
        boolean continuation = explosiveMultishot
                && previousWindow != null
                && Objects.equals(previousWindow.weaponUniqueId(), physicalWeaponId)
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
                ? new ShotWindow(previousWindow.tick(), previousWindow.projectileCount() + 1, physicalWeaponId)
                : new ShotWindow(currentTick, 1, physicalWeaponId);
        return new Decision(true, !continuation, nextWindow);
    }

    public record Decision(boolean allowed, boolean startsShot, ShotWindow window) {
    }

    public record ShotWindow(long tick, int projectileCount, String weaponUniqueId) {
        public ShotWindow(long tick, int projectileCount) {
            this(tick, projectileCount, "");
        }
    }
}
