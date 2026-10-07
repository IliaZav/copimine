package me.copimine.client;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EndEventWorldVfxManagerTest {
    @Test
    void casterHandLinksInterpolateButCleanupRejectsQueuedPackets() {
        var manager = new EndEventWorldVfxManager();
        String key = "wave6-ritual-caster-hand-0";
        String id = "event-1:2:world:" + key;
        var first = at(beam("event-1", 2, id, "overworld|" + key + "|0,64,0",
                "4,69,6|BE75FF", .14F, 650), 100);
        var moved = at(beam("event-1", 2, id, "overworld|" + key + "|2,64,0",
                "4,69,6|BE75FF", .14F, 650), 350);
        assertTrue(manager.applyBeam(first, 100));
        assertTrue(manager.applyBeam(moved, 350));
        assertEquals(0, manager.snapshots(350).getFirst().start().x, .001);
        assertEquals(1, manager.snapshots(425).getFirst().start().x, .001);
        assertTrue(manager.applyClear(at(clear("event-1", 2, id), 450), 450));
        assertTrue(manager.snapshots(500).isEmpty());
        assertFalse(manager.applyBeam(moved, 500));
        assertTrue(manager.applyBeam(at(beam("event-1", 3, "event-1:3:world:" + key,
                "overworld|" + key + "|3,64,0", "4,69,6|BE75FF", .14F, 650), 600), 600));
        assertEquals(3, manager.snapshots(600).getFirst().start().x, .001);
    }
    @Test
    void carrierBeamInterpolatesSmallMovesAndSnapsOnGenerationChangeOrTeleport() {
        var manager=new EndEventWorldVfxManager();
        String id="event-1:2:world:wave1-carrier-objective";
        var first=at(beam("event-1",2,id,"overworld|wave1-carrier-objective|0,64,0",
                "4,65,6|4EE6FF",.1F,650),100);
        assertTrue(manager.applyBeam(first,100));
        var moved=at(beam("event-1",2,id,"overworld|wave1-carrier-objective|2,64,0",
                "4,65,6|4EE6FF",.1F,650),350);
        assertTrue(manager.applyBeam(moved,350));
        assertEquals(0,manager.snapshots(350).getFirst().start().x,0.001);
        assertEquals(1,manager.snapshots(425).getFirst().start().x,0.001);
        assertEquals(2,manager.snapshots(500).getFirst().start().x,0.001);
        assertTrue(manager.applyBeam(at(beam("event-1",2,id,"overworld|wave1-carrier-objective|20,64,0",
                "4,65,6|4EE6FF",.1F,650),600),600));
        assertEquals(20,manager.snapshots(600).getFirst().start().x,0.001);
        assertTrue(manager.applyBeam(at(beam("event-1",3,"event-1:3:world:wave1-carrier-objective",
                "overworld|wave1-carrier-objective|1,64,0","4,65,6|4EE6FF",.1F,650),700),700));
        assertEquals(1,manager.snapshots(700).getFirst().start().x,0.001);
        manager.clear();
        assertTrue(manager.snapshots(800).isEmpty());
    }
    @Test
    void spellTargetFeedbackFollowsServerStageClearExpiryAndRespawnFences() {
        EndEventWorldVfxManager manager = new EndEventWorldVfxManager();
        String warning = "event-1:2:world:wave6-spell-screen-GRAVITY_WELL-warning";
        String active = "event-1:2:world:wave6-spell-screen-GRAVITY_WELL-active";
        BridgePayload warningPacket = at(beam("event-1", 2, warning,
                "overworld|wave6-spell-screen-GRAVITY_WELL-warning|1,64,2", "1,64.1,2|4EE6FF", .1F, 650), 100);
        BridgePayload activePacket = at(beam("event-1", 2, active,
                "overworld|wave6-spell-screen-GRAVITY_WELL-active|1,64,2", "1,64.1,2|4EE6FF", .1F, 650), 300);
        assertTrue(manager.applyBeam(warningPacket, 100));
        assertEquals(warning, RitualSpellPresentationPolicy.screenCue(manager.snapshots(), "overworld", 200).instanceId());
        assertTrue(manager.applyClear(at(clear("event-1", 2, warning), 300), 300));
        assertTrue(manager.applyBeam(activePacket, 300));
        assertEquals(active, RitualSpellPresentationPolicy.screenCue(manager.snapshots(), "overworld", 400).instanceId());
        assertFalse(manager.applyBeam(warningPacket, 400));
        manager.tick(1300);
        assertTrue(manager.snapshots().isEmpty());
        assertNull(RitualSpellPresentationPolicy.screenCue(manager.snapshots(), "overworld", 1300));

        assertTrue(manager.applyBeam(at(activePacket, 1400), 1400));
        manager.clear();
        assertNull(RitualSpellPresentationPolicy.screenCue(manager.snapshots(), "overworld", 1500));
        assertFalse(manager.applyBeam(at(activePacket, 1500), 1500));
        assertTrue(manager.resumeAfterLocalExit("event-1", 2, 1600));
        assertFalse(manager.applyBeam(at(activePacket, 1500), 1700));
        assertTrue(manager.applyBeam(at(activePacket, 1700), 1700));
        assertNotNull(RitualSpellPresentationPolicy.screenCue(manager.snapshots(), "overworld", 1800));
        assertTrue(manager.applyClear(at(clear("event-1", 2, active), 1900), 1900));
        assertNull(RitualSpellPresentationPolicy.screenCue(manager.snapshots(), "overworld", 1950));
        assertFalse(manager.applyBeam(at(activePacket, 1700), 2000));
        manager.clearEvent("event-1", 2);
        assertFalse(manager.applyBeam(at(activePacket, 2100), 2100));
    }

    @Test
    void serverResumeRestoresSameGenerationAfterRespawnWithoutAdmittingQueuedPackets() {
        EndEventWorldVfxManager manager = new EndEventWorldVfxManager();
        BridgePayload original = at(beam("event-1", 2L, "channel", "overworld|caster|1,64,2",
                "4,65,6|4EE6FF", 0.1F, 650), 100L);
        assertTrue(manager.applyBeam(original, 100L));
        manager.clear();
        assertFalse(manager.resumeAfterLocalExit("event-1", 3L, 300L));
        assertFalse(manager.resumeAfterLocalExit("other", 2L, 300L));
        assertFalse(manager.resumeAfterLocalExit("event-1", 2L, 90L));
        assertTrue(manager.resumeAfterLocalExit("event-1", 2L, 300L));
        assertFalse(manager.applyBeam(at(original, 200L), 400L));
        assertTrue(manager.applyBeam(at(original, 400L), 400L));
        manager.clearEvent("event-1", 2L);
        assertFalse(manager.resumeAfterLocalExit("event-1", 2L, 500L));
    }
    @Test
    void reorderedInstanceClearCannotReviveAnOldBeamOrEraseANewerCast() {
        EndEventWorldVfxManager manager = new EndEventWorldVfxManager();
        BridgePayload old = at(beam("event-1", 1L, "channel", "overworld|caster|1,64,2",
                "4,65,6|4EE6FF", 0.1F, 650), 100L);
        assertTrue(manager.applyBeam(old, 100L));
        assertTrue(manager.applyClear(at(clear("event-1", 1L, "channel"), 200L), 200L));
        assertFalse(manager.applyBeam(old, 300L));
        assertEquals(0, manager.activeBeamCount());
        BridgePayload newer = at(old, 400L);
        assertTrue(manager.applyBeam(newer, 400L));
        assertFalse(manager.applyClear(at(clear("event-1", 1L, "channel"), 200L), 500L));
        assertEquals(1, manager.activeBeamCount());
        assertFalse(manager.applyBeam(old, 600L));
    }

    private static BridgePayload at(BridgePayload p, long timestamp) {
        return new BridgePayload(p.type(), p.protocol(), p.seq(), timestamp, p.sessionId(), p.clientVersion(),
                p.clientVisuals(), p.clientOverlay(), p.clientShaderLike(), p.trueIrisShader(), p.supportedEffects(),
                p.effectId(), p.shaderpack(), p.durationMillis(), p.intensity(), p.fadeInMillis(), p.fadeOutMillis(),
                p.mode(), p.clearPolicy(), p.source(), p.reason(), p.status());
    }
    @Test
    void acceptsActualServerCasterInstanceIncludingEventAndGenerationPrefix() {
        String event = "502d1dbc-9d7b-4420-9f93-b2ec2d226d96";
        String key = "wave6-ritual-" + "a48a9934-2524-4e43-8010-268985749333";
        EndEventWorldVfxManager manager = new EndEventWorldVfxManager();
        for (long generation : new long[]{1245L, Long.MAX_VALUE}) {
            String instance = event + ":" + generation + ":world:" + key;
            assertTrue(manager.applyBeam(beam(event, generation, instance,
                    "overworld|" + key + "|8.500,70.900,-32.500",
                    "8.500,71.200,-38.500|D04BFF", 0.10F, 650), 100L));
            assertEquals(instance, manager.snapshots().get(0).instanceId());
            assertTrue(manager.applyClear(clear(event, generation, instance), 200L));
        }
        assertFalse(manager.applyBeam(beam(event, Long.MAX_VALUE, "x".repeat(129),
                "overworld|" + key + "|8,70,-32", "8,71,-38|D04BFF", 0.10F, 650), 300L));
    }

    @Test
    void localClearKeepsGenerationFenceUntilConnectionReset() {
        EndEventWorldVfxManager manager = new EndEventWorldVfxManager();
        BridgePayload current = beam("event-1", 2L, "current", "overworld|caster|1,64,2",
                "4,65,6|4EE6FF", 0.1F, 650);
        assertTrue(manager.applyBeam(current, 100L));
        manager.clear();
        assertFalse(manager.applyBeam(current, 200L));
        assertTrue(manager.applyBeam(beam("event-1", 3L, "new", "overworld|caster|1,64,2",
                "4,65,6|4EE6FF", 0.1F, 650), 300L));
        assertTrue(manager.applyBeam(beam("event-2", 1L, "next", "overworld|caster|1,64,2",
                "4,65,6|4EE6FF", 0.1F, 650), 400L));
        assertFalse(manager.applyBeam(current, 500L));
        assertEquals("next", manager.snapshots().get(0).instanceId());
        manager.reset();
        assertTrue(manager.applyBeam(current, 600L));
    }
    @Test
    void acceptsBoundedBeamAndExpiresIt() {
        EndEventWorldVfxManager manager = new EndEventWorldVfxManager();
        BridgePayload beam = beam("event-1", 1L, "instance-1", "overworld|absorption|1,64,2",
                "4,65,6|4EE6FF", 0.12F, 500);

        assertTrue(manager.applyBeam(beam, 1_000L));
        assertEquals(1, manager.activeBeamCount());
        assertEquals(0x4EE6FF, manager.snapshots().get(0).color());
        assertEquals(0.12F, manager.snapshots().get(0).width(), 0.0001F);
        manager.tick(2_000L);
        assertEquals(0, manager.activeBeamCount());
    }

    @Test
    void refreshingAContinuousChannelDoesNotRestartItsFadeAndFlow() {
        EndEventWorldVfxManager manager = new EndEventWorldVfxManager();
        BridgePayload channel = beam("event-1", 1L, "channel", "overworld|caster|1,64,2",
                "4,65,6|4EE6FF", 0.1F, 650);
        assertTrue(manager.applyBeam(channel, 100L));
        assertTrue(manager.applyBeam(at(channel, 2L), 600L));
        assertEquals(100L, manager.snapshots().get(0).startedAtMillis());
        assertEquals(1_600L, manager.snapshots().get(0).expiresAtMillis());
        assertEquals(1, manager.activeBeamCount());
    }

    @Test
    void rejectsStaleGenerationAndInvalidCoordinates() {
        EndEventWorldVfxManager manager = new EndEventWorldVfxManager();
        assertTrue(manager.applyBeam(beam("event-1", 2L, "instance-1", "overworld|absorption|1,64,2",
                "4,65,6|4EE6FF", 0.12F, 500), 100L));
        assertFalse(manager.applyBeam(beam("event-1", 1L, "instance-old", "overworld|absorption|NaN,64,2",
                "4,65,6|4EE6FF", 0.12F, 500), 200L));
        assertEquals(1, manager.activeBeamCount());
    }

    @Test
    void enforcesActiveBeamCapButAllowsRefreshOfExistingInstance() {
        EndEventWorldVfxManager manager = new EndEventWorldVfxManager();
        for (int index = 0; index < EndEventWorldVfxManager.MAX_ACTIVE_BEAMS; index++) {
            assertTrue(manager.applyBeam(beam("event-1", 1L, "beam-" + index,
                    "overworld|key-" + index + "|1,64,2", "4,65,6|4EE6FF", 0.12F, 1_000), 100L));
        }
        assertFalse(manager.applyBeam(beam("event-1", 1L, "beam-over-cap",
                "overworld|overflow|1,64,2", "4,65,6|4EE6FF", 0.12F, 1_000), 100L));
        assertTrue(manager.applyBeam(at(beam("event-1", 1L, "beam-0",
                "overworld|key-0|2,64,2", "5,65,6|69DAFF", 0.20F, 1_000), 2L), 200L));
        assertEquals(EndEventWorldVfxManager.MAX_ACTIVE_BEAMS, manager.activeBeamCount());
    }

    @Test
    void clearsOnlyTheServerSelectedInstance() {
        EndEventWorldVfxManager manager = new EndEventWorldVfxManager();
        manager.applyBeam(beam("event-1", 1L, "beam-1", "overworld|key-1|1,64,2",
                "4,65,6|4EE6FF", 0.12F, 1_000), 100L);
        manager.applyBeam(beam("event-1", 1L, "beam-2", "overworld|key-2|1,64,2",
                "4,65,6|4EE6FF", 0.12F, 1_000), 100L);
        assertTrue(manager.applyClear(clear("event-1", 1L, "beam-1"), 200L));
        assertEquals(1, manager.activeBeamCount());
    }

    private static BridgePayload beam(String eventId, long generation, String instance,
                                      String mode, String end, float width, int duration) {
        return new BridgePayload("END_EVENT:END_WORLD_BEAM", 2, generation, 1L,
                eventId, instance, false, false, false, false, Set.of(), "", "RIFT_BEAM",
                duration, width, 0, 0, mode, end, "", "", "");
    }

    private static BridgePayload clear(String eventId, long generation, String instance) {
        return new BridgePayload("END_EVENT:END_WORLD_VFX_CLEAR", 2, generation, 1L,
                eventId, instance, false, false, false, false, Set.of(), "", "RIFT_BEAM",
                1_000, 0.0F, 0, 0, "", "", "", "", "");
    }
}
