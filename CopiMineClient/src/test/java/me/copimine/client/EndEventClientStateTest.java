package me.copimine.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EndEventClientStateTest {
    @Test
    void acceptsBossBindingAndOneControlEffect() {
        EndEventClientState state = new EndEventClientState();

        assertTrue(state.apply(packet("END_BOSS_BIND", "event-1", 1L, "boss-bind", 0L, "boss-uuid", "boss-id", "control-id"), 100L));
        assertTrue(state.isBossBound("boss-uuid"));
        assertTrue(state.apply(packet("END_CONTROL_START", "event-1", 1L, "control-1", 10_000L, "", "boss-id", "control-id"), 100L));
        assertTrue(state.isReverseActive(101L));
        assertTrue(state.controlInstanceId().equals("control-1"));
    }

    @Test
    void acceptsOnlyTheBoundBossBarAndKeepsHealthPhaseAndCastState() {
        EndEventClientState state = new EndEventClientState();

        assertTrue(state.apply(packet("END_BOSS_BIND", "event-1", 1L,
                "boss-bind", 0L, "boss-uuid", "boss-id", "control-id"), 100L));
        assertTrue(state.applyBossBar(packet("END_BOSS_BAR", "event-1", 1L,
                "boss-bind", 1_000L, "boss-uuid", "DISTORTION|JUDGMENT_CAST", "control-id"),
                0.736F, 1_840, 2_500, 200L));

        EndEventClientState.BossBarState bar = state.bossBar();
        assertTrue(state.hasActiveBossBar());
        assertEquals("DISTORTION", bar.phaseId());
        assertEquals("JUDGMENT_CAST", bar.castState());
        assertEquals(1_840, bar.health());
        assertEquals(2_500, bar.maxHealth());
        assertEquals(0.736F, bar.progress(), 0.0001F);

        assertFalse(state.applyBossBar(packet("END_BOSS_BAR", "event-1", 1L,
                "other-binding", 1_000L, "other-boss", "CATASTROPHE|NONE", "control-id"),
                1.0F, 2_500, 2_500, 300L));
        assertTrue(state.hasActiveBossBar());
    }

    @Test
    void keepsPhaseAndAnimationSeparateAndLetsAnExplicitIdleCueWin() {
        EndEventClientState state = new EndEventClientState();
        assertTrue(state.apply(packet("END_BOSS_BIND", "event-1", 1L,
                "boss-bind", 0L, "boss-uuid", "boss-id", "control-id"), 100L));

        assertTrue(state.apply(packet("END_BOSS_PHASE", "event-1", 1L,
                "boss-bind", 1_200L, "boss-uuid", "CATASTROPHE|SPELL_VOID_BLAST", "control-id"), 110L));
        assertEquals("CATASTROPHE", state.bossPhaseForEntity("boss-uuid"));
        assertEquals("SPELL_VOID_BLAST", state.bossAnimationForEntity("boss-uuid"));

        assertTrue(state.applyBossBar(packet("END_BOSS_BAR", "event-1", 1L,
                "boss-bind", 1_000L, "boss-uuid", "CATASTROPHE|JUDGMENT_CAST", "control-id"),
                0.5F, 1_250, 2_500, 120L));
        assertEquals("SPELL_VOID_BLAST", state.bossAnimationForEntity("boss-uuid"),
                "a spell cue must not be replaced by a periodic bar snapshot");

        assertTrue(state.apply(packet("END_BOSS_PHASE", "event-1", 1L,
                "boss-bind", 1_200L, "boss-uuid", "CATASTROPHE|IDLE", "control-id"), 130L));
        assertEquals("IDLE", state.bossAnimationForEntity("boss-uuid"));
        assertEquals("JUDGMENT_CAST", state.bossCastStateForEntity("boss-uuid"));
    }

    @Test
    void staleBossBarCannotReplaceANewerGenerationSnapshot() {
        EndEventClientState state = new EndEventClientState();
        state.apply(packet("END_BOSS_BIND", "event-1", 2L,
                "boss-bind", 0L, "boss-uuid", "boss-id", "control-id"), 100L);
        assertTrue(state.applyBossBar(packet("END_BOSS_BAR", "event-1", 2L,
                "boss-bind", 1_000L, "boss-uuid", "AWAKENING|NONE", "control-id"),
                1.0F, 2_500, 2_500, 110L));
        assertFalse(state.applyBossBar(packet("END_BOSS_BAR", "event-1", 1L,
                "boss-bind", 1_000L, "boss-uuid", "CATASTROPHE|NONE", "control-id"),
                0.1F, 250, 2_500, 120L));
        assertEquals("AWAKENING", state.bossBar().phaseId());
    }

    @Test
    void staleStopCannotCancelNewerControlInstance() {
        EndEventClientState state = new EndEventClientState();
        state.apply(packet("END_CONTROL_START", "event-1", 1L, "old", 100L, "", "", ""), 100L);
        state.apply(packet("END_CONTROL_START", "event-1", 1L, "new", 10_000L, "", "", ""), 250L);

        assertFalse(state.apply(packet("END_CONTROL_STOP", "event-1", 1L, "old", 0L, "", "", ""), 300L));
        assertTrue(state.isReverseActive(301L));
        assertTrue(state.controlInstanceId().equals("new"));
    }

    @Test
    void duplicateStartIsIdempotentAndDoesNotExtendTheOriginalDeadline() {
        EndEventClientState state = new EndEventClientState();

        assertTrue(state.apply(packet("END_CONTROL_START", "event-1", 1L,
                "same", 10_000L, "", "", ""), 100L));
        assertTrue(state.apply(packet("END_CONTROL_START", "event-1", 1L,
                "same", 10_000L, "", "", ""), 9_900L));

        assertTrue(state.isReverseActive(10_099L));
        assertFalse(state.isReverseActive(10_100L));
    }

    @Test
    void secondConcurrentControlInstanceIsRejectedUntilTheFirstExpires() {
        EndEventClientState state = new EndEventClientState();

        assertTrue(state.apply(packet("END_CONTROL_START", "event-1", 1L,
                "first", 10_000L, "", "", ""), 100L));
        assertFalse(state.apply(packet("END_CONTROL_START", "event-1", 1L,
                "second", 10_000L, "", "", ""), 200L));
        assertTrue(state.controlInstanceId().equals("first"));
    }

    @Test
    void generationAndEventChangesClearOldState() {
        EndEventClientState state = new EndEventClientState();
        state.apply(packet("END_BOSS_BIND", "event-1", 1L, "boss-bind", 0L, "boss-1", "", ""), 100L);
        state.apply(packet("END_CONTROL_START", "event-1", 1L, "control-1", 100L, "", "", ""), 100L);

        assertTrue(state.apply(packet("END_BOSS_BIND", "event-2", 1L, "boss-bind-2", 0L, "boss-2", "", ""), 200L));
        assertFalse(state.isBossBound("boss-1"));
        assertFalse(state.isReverseActive(201L));
        assertTrue(state.isBossBound("boss-2"));
    }

    @Test
    void expiryAndExplicitClearRemoveEffects() {
        EndEventClientState state = new EndEventClientState();
        state.apply(packet("END_CONTROL_START", "event-1", 1L, "control-1", 100L, "", "", ""), 100L);

        assertTrue(state.isReverseActive(199L));
        assertFalse(state.isReverseActive(200L));
        state.clear();
        assertFalse(state.isReverseActive(201L));
        assertFalse(state.hasBossBinding());
    }

    @Test
    void bindsEventMobVisualsByUuidAndIgnoresStaleUnbind() {
        EndEventClientState state = new EndEventClientState();
        assertTrue(state.apply(packet("END_ENTITY_BIND", "event-1", 1L, "mob-1", 0L,
                "mob-uuid", "END_RIFT_SPIDER_V1", "control-id"), 100L));
        assertTrue(state.visualForEntity("mob-uuid").equals("END_RIFT_SPIDER_V1"));

        assertTrue(state.apply(packet("END_ENTITY_BIND", "event-1", 1L, "mob-2", 0L,
                "mob-uuid", "END_RIFT_SHULKER_V1", "control-id"), 200L));
        assertFalse(state.apply(packet("END_ENTITY_UNBIND", "event-1", 1L, "mob-1", 0L,
                "mob-uuid", "", "control-id"), 300L));
        assertTrue(state.visualForEntity("mob-uuid").equals("END_RIFT_SHULKER_V1"));
        assertTrue(state.apply(packet("END_ENTITY_UNBIND", "event-1", 1L, "mob-2", 0L,
                "mob-uuid", "", "control-id"), 400L));
        assertTrue(state.visualForEntity("mob-uuid").isBlank());
    }

    private static EndEventPacket packet(String type, String eventId, long generation, String instance,
                                         long duration, String subject, String bossId, String controlId) {
        return new EndEventPacket(type, eventId, generation, instance, duration, subject, bossId, controlId);
    }
}
