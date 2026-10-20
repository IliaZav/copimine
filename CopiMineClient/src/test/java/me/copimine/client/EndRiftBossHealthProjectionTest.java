package me.copimine.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EndRiftBossHealthProjectionTest {
    private static final String BOSS_UUID = "68b56dc8-a3e0-4158-acf9-d2944248f5d2";
    private static final String INSTANCE_ID = "boss-bind";

    @Test
    void projectsTheAuthoritativeBossMaximumForTheBoundBossOnly() {
        EndEventClientState.BossBarState snapshot = snapshot(3_750, 5_000, true);

        assertEquals(5_000.0D, EndRiftBossHealthProjection.resolveMaxHealth(
                BOSS_UUID, 1_024.0D, BOSS_UUID, snapshot));
    }

    @Test
    void leavesOrdinaryEntitiesOnTheirNativeMaximum() {
        EndEventClientState.BossBarState snapshot = snapshot(3_750, 5_000, true);

        assertEquals(40.0D, EndRiftBossHealthProjection.resolveMaxHealth(
                "123e4567-e89b-12d3-a456-426614174000", 40.0D, BOSS_UUID, snapshot));
    }

    @Test
    void fallsBackToNativeMaximumWhenTheSnapshotIsMissingOrStale() {
        assertEquals(1_024.0D, EndRiftBossHealthProjection.resolveMaxHealth(
                BOSS_UUID, 1_024.0D, BOSS_UUID, null));
        assertEquals(1_024.0D, EndRiftBossHealthProjection.resolveMaxHealth(
                BOSS_UUID, 1_024.0D, BOSS_UUID, snapshot(3_750, 5_000, false)));
    }

    @Test
    void rejectsAHealthSnapshotForAnotherBossUuid() {
        EndEventClientState.BossBarState snapshot = new EndEventClientState.BossBarState(
                INSTANCE_ID, "other-boss", "AWAKENING", "NONE",
                5_000, 5_000, 1.0F, true, 100L);

        assertEquals(1_024.0D, EndRiftBossHealthProjection.resolveMaxHealth(
                BOSS_UUID, 1_024.0D, BOSS_UUID, snapshot));
    }

    private static EndEventClientState.BossBarState snapshot(int health, int maxHealth,
                                                               boolean visible) {
        return new EndEventClientState.BossBarState(
                INSTANCE_ID, BOSS_UUID, "AWAKENING", "NONE",
                health, maxHealth, health / (float) maxHealth, visible, 100L);
    }
}
