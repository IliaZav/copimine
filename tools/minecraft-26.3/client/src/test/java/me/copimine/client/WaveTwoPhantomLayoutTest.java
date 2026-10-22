package me.copimine.client;

import net.minecraft.client.renderer.entity.state.EndermanRenderState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaveTwoPhantomLayoutTest {
    private static final WaveTwoPhantomLayout.Position ANCHOR =
            new WaveTwoPhantomLayout.Position(100.5D, 64.0D, -20.5D);

    @Test
    void candidateLayoutIsDeterministicAndBoundedAroundTheFirstMarkFrame() {
        List<WaveTwoPhantomLayout.Position> first = WaveTwoPhantomLayout.candidatesAround(ANCHOR, 1234L);
        List<WaveTwoPhantomLayout.Position> repeated = WaveTwoPhantomLayout.candidatesAround(ANCHOR, 1234L);
        List<WaveTwoPhantomLayout.Position> nextMark = WaveTwoPhantomLayout.candidatesAround(ANCHOR, 1235L);

        assertEquals(64, first.size());
        assertEquals(first, repeated);
        assertNotEquals(first, nextMark);
        assertTrue(first.stream().allMatch(WaveTwoPhantomLayout::finite));
        assertTrue(first.stream().allMatch(position ->
                Math.hypot(position.x() - ANCHOR.x(), position.z() - ANCHOR.z()) <= 12.0D + 1.0E-9D));
    }

    @Test
    void safeSelectionSkipsRejectedColumnsAndNeverExceedsThirtyTwo() {
        List<WaveTwoPhantomLayout.Position> candidates = WaveTwoPhantomLayout.candidatesAround(ANCHOR, 7L);
        List<WaveTwoPhantomLayout.Position> selected = WaveTwoPhantomLayout.selectSafe(candidates,
                candidate -> new WaveTwoPhantomLayout.Position(candidate.x(), 65.0D, candidate.z()));

        assertEquals(32, selected.size());
        assertTrue(selected.stream().allMatch(position -> position.y() == 65.0D));
        AtomicInteger checked = new AtomicInteger();
        List<WaveTwoPhantomLayout.Position> sparse = WaveTwoPhantomLayout.selectSafe(candidates,
                candidate -> checked.getAndIncrement() < 8
                        ? new WaveTwoPhantomLayout.Position(candidate.x(), 65.0D, candidate.z())
                        : null);
        assertEquals(8, sparse.size());
        assertTrue(WaveTwoPhantomLayout.selectSafe(candidates, candidate -> null).isEmpty());
    }

    @Test
    void activeMarkLayoutStaysStableAndClearsOnExpiryOrPhaseChange() {
        WaveTwoPhantomSession session = new WaveTwoPhantomSession();
        WaveTwoPhantomSession.MarkKey mark = new WaveTwoPhantomSession.MarkKey(
                "end-event", 3L, UUID.fromString("00000000-0000-0000-0000-000000000001"), 10_000L);
        List<WaveTwoPhantomLayout.Position> layout = WaveTwoPhantomLayout.candidatesAround(ANCHOR, 51L);
        AtomicInteger creations = new AtomicInteger();

        List<WaveTwoPhantomLayout.Position> first = session.positionsFor(mark, () -> {
            creations.incrementAndGet();
            return layout;
        });
        List<WaveTwoPhantomLayout.Position> repeated = session.positionsFor(mark, () -> {
            creations.incrementAndGet();
            return List.of();
        });

        assertEquals(32, first.size());
        assertEquals(first, repeated);
        assertEquals(1, creations.get());
        assertTrue(session.hasActiveMark());

        assertTrue(session.positionsFor(null, List::of).isEmpty());
        assertFalse(session.hasActiveMark());

        List<WaveTwoPhantomLayout.Position> secondMark = session.positionsFor(
                new WaveTwoPhantomSession.MarkKey(mark.eventId(), mark.generation(),
                        mark.markedPlayer(), mark.startedAtMillis() + 45_000L),
                () -> {
                    creations.incrementAndGet();
                    return layout;
                });
        assertEquals(32, secondMark.size());
        assertEquals(2, creations.get());

        session.positionsFor(new WaveTwoPhantomSession.MarkKey(
                "end-event", 4L, mark.markedPlayer(), 55_000L), () -> List.of());
        assertTrue(session.hasActiveMark());
        assertTrue(session.positionsFor(null, () -> new ArrayList<>(layout)).isEmpty());
        assertFalse(session.hasActiveMark());
    }

    @Test
    void phantomsFaceTheMarkedPlayer() {
        WaveTwoPhantomLayout.Position phantom = new WaveTwoPhantomLayout.Position(0.0D, 64.0D, 0.0D);
        WaveTwoPhantomLayout.Position player = new WaveTwoPhantomLayout.Position(1.0D, 65.5D, 0.0D);

        assertEquals((float) (Math.PI / 2.0D), WaveTwoPhantomLayout.targetYaw(phantom, player), 1.0E-6F);
        assertEquals(0.0F, WaveTwoPhantomLayout.targetYaw(phantom, phantom));
    }

    @Test
    void phantomLookPoseKeepsTheBodyStillAndTurnsOnlyTheHead() {
        WaveTwoPhantomLayout.Position phantom = new WaveTwoPhantomLayout.Position(0.0D, 64.0D, 0.0D);
        WaveTwoPhantomLayout.LookPose eastward = WaveTwoPhantomLayout.lookAt(
                phantom, new WaveTwoPhantomLayout.Position(4.0D, 65.5D, 0.0D));
        WaveTwoPhantomLayout.LookPose northward = WaveTwoPhantomLayout.lookAt(
                phantom, new WaveTwoPhantomLayout.Position(0.0D, 65.5D, -4.0D));
        WaveTwoPhantomLayout.LookPose westward = WaveTwoPhantomLayout.lookAt(
                phantom, new WaveTwoPhantomLayout.Position(-4.0D, 65.5D, 0.0D));
        WaveTwoPhantomLayout.LookPose southward = WaveTwoPhantomLayout.lookAt(
                phantom, new WaveTwoPhantomLayout.Position(0.0D, 65.5D, 4.0D));

        assertEquals(0.0F, eastward.bodyYawDegrees());
        assertEquals(-90.0F, eastward.headYawDegrees(), 1.0E-5F);
        assertEquals(0.0F, northward.bodyYawDegrees());
        assertEquals(-180.0F, northward.headYawDegrees(), 1.0E-5F);
        assertEquals(0.0F, westward.bodyYawDegrees());
        assertEquals(90.0F, westward.headYawDegrees(), 1.0E-5F);
        assertEquals(0.0F, southward.bodyYawDegrees());
        assertEquals(0.0F, southward.headYawDegrees(), 1.0E-5F);
    }

    @Test
    void phantomRendererAppliesTheLookPoseAsHeadRotationOnly() {
        EndermanRenderState state = new EndermanRenderState();
        state.xRot = 12.0F;
        WaveTwoPhantomLayout.LookPose lookPose = new WaveTwoPhantomLayout.LookPose(0.0F, -90.0F);

        WaveTwoPhantomRenderer.applyHeadLook(state, lookPose);

        assertEquals(-90.0F, state.yRot, 1.0E-5F);
        assertEquals(0.0F, state.xRot);
    }

    @Test
    void activationBindsOnlyToAnExactPositionInTheCachedLayout() {
        List<WaveTwoPhantomLayout.Position> positions = List.of(
                new WaveTwoPhantomLayout.Position(12.5D, 64.0D, -3.25D),
                new WaveTwoPhantomLayout.Position(13.5D, 64.0D, -3.25D));

        assertEquals(0, WaveTwoPhantomLayout.indexOfExactPosition(positions,
                new WaveTwoPhantomLayout.Position(12.5D, 64.0D, -3.25D)));
        assertEquals(1, WaveTwoPhantomLayout.indexOfExactPosition(positions,
                new WaveTwoPhantomLayout.Position(13.5D, 64.0D, -3.25D)));
        assertEquals(-1, WaveTwoPhantomLayout.indexOfExactPosition(positions,
                new WaveTwoPhantomLayout.Position(12.501D, 64.0D, -3.25D)));
        assertEquals(-1, WaveTwoPhantomLayout.indexOfExactPosition(positions,
                new WaveTwoPhantomLayout.Position(Double.NaN, 64.0D, -3.25D)));
    }
}
