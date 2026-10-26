package me.copimine.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RiftGuardianModelRendererTest {
    @Test
    void knownActionsWithoutTheirOwnClipPlayAuthoredMotion() {
        assertEquals("CHEST_STRIKE", RiftGuardianModelRenderer.normalizeAnimationId("CAST_CHARGE"));
        assertEquals("CHEST_STRIKE", RiftGuardianModelRenderer.normalizeAnimationId("TELEGRAPHING"));
        assertEquals("CHEST_STRIKE", RiftGuardianModelRenderer.normalizeAnimationId("SPELL_VOID_BLAST"));
        assertEquals("GROUND_SLAM", RiftGuardianModelRenderer.normalizeAnimationId("SPELL_SUMMON_SERVANTS"));
        assertEquals("GROUND_SLAM", RiftGuardianModelRenderer.normalizeAnimationId("SPELL_ARENA_INFERNO"));
        assertEquals("TELEPORT_RIP", RiftGuardianModelRenderer.normalizeAnimationId("TELEPORT"));
        assertEquals("UNKNOWN", RiftGuardianModelRenderer.normalizeAnimationId("NOT_A_REAL_ACTION"));
    }
}
