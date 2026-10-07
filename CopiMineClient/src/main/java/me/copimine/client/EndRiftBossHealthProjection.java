package me.copimine.client;

import java.util.Objects;

/**
 * Resolves the client-side health maximum for the one server-bound End Rift
 * guardian.  Minecraft's native LivingEntity attribute is intentionally left
 * untouched for every other entity; the projection exists only because the
 * client attribute implementation clamps unusually large boss values.
 */
public final class EndRiftBossHealthProjection {
    private EndRiftBossHealthProjection() {
    }

    public static double resolveMaxHealth(
            String entityUuid,
            double nativeMaxHealth,
            String boundBossUuid,
            EndEventClientState.BossBarState snapshot) {
        if (!isEligible(entityUuid, boundBossUuid, snapshot)) {
            return nativeMaxHealth;
        }
        return snapshot.maxHealth();
    }

    public static boolean isProjected(
            String entityUuid,
            String boundBossUuid,
            EndEventClientState.BossBarState snapshot) {
        return isEligible(entityUuid, boundBossUuid, snapshot);
    }

    private static boolean isEligible(
            String entityUuid,
            String boundBossUuid,
            EndEventClientState.BossBarState snapshot) {
        return entityUuid != null
                && !entityUuid.isBlank()
                && boundBossUuid != null
                && !boundBossUuid.isBlank()
                && Objects.equals(entityUuid, boundBossUuid)
                && snapshot != null
                && snapshot.visible()
                && Objects.equals(entityUuid, snapshot.bossUuid())
                && snapshot.instanceId() != null
                && !snapshot.instanceId().isBlank()
                && snapshot.maxHealth() > 0
                && Float.isFinite(snapshot.progress());
    }
}
