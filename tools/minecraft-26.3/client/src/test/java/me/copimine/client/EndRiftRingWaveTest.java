package me.copimine.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import me.copimine.protocol.KaguneRingFrame;
import org.junit.jupiter.api.Test;

/**
 * The owner's ring brief: each tentacle rises while the one before it descends,
 * so the circle reads as a wave travelling around the arena rather than a
 * rigid wall of identical spikes lifting as one plate.
 */
class EndRiftRingWaveTest {
    private static final int COUNT = 112;

    @Test
    void wideArenaHasContinuousContactExceptForItsAuthoredPassages() {
        var frame = new KaguneRingFrame(0, KaguneRingFrame.TRAVEL, 60D, 59D, 1D, .5D,
                java.util.List.of(0D, Math.PI));
        double betweenStems = frame.direction(30) + Math.PI / frame.instances();
        double x = 59.5D * Math.cos(betweenStems);
        double z = 59.5D * Math.sin(betweenStems);
        assertTrue(frame.contacts(x, 3.6D, z, x, 3.6D, z, .3D, 1.8D));
        org.junit.jupiter.api.Assertions.assertFalse(
                frame.contacts(59.5D, 0D, 0D, 59.5D, 0D, 0D, .3D, 1.8D));
    }

    @Test
    void travellingRowHasNeighboursOutOfPhase() {
        // The renderer hands the row a full-height base, so the wave shows as a
        // moving dip: one stem descends while its neighbour is still up.
        double base = 1.0D;
        double strike = 0.25D;
        double lowest = 2.0D;
        double highest = -1.0D;
        for (int index = 0; index < COUNT; index++) {
            double rise = EndRiftTentacleAnimator.ringInstanceRise(
                    index, COUNT, KaguneRingFrame.TRAVEL, base, strike);
            lowest = Math.min(lowest, rise);
            highest = Math.max(highest, rise);
        }
        assertTrue(lowest < EndRiftTentacleAnimator.RING_WAVE_FLOOR + 0.05D,
                "some stems must actually dip for the wave to read");
        assertTrue(highest > lowest + 0.15D,
                "the row must not be one rigid height");
        // A ramp exists somewhere: adjacent stems differ materially.
        boolean foundRamp = false;
        for (int index = 0; index < COUNT && !foundRamp; index++) {
            double here = EndRiftTentacleAnimator.ringInstanceRise(
                    index, COUNT, KaguneRingFrame.TRAVEL, base, strike);
            double next = EndRiftTentacleAnimator.ringInstanceRise(
                    (index + 1) % COUNT, COUNT, KaguneRingFrame.TRAVEL, base, strike);
            foundRamp = Math.abs(here - next) > 0.02D;
        }
        assertTrue(foundRamp, "neighbours must be out of phase");
    }

    @Test
    void theWholeRingStillCoversTheContactBand() {
        for (int stage : new int[] {KaguneRingFrame.EMERGE, KaguneRingFrame.TRAVEL,
                KaguneRingFrame.RETRACT}) {
            for (int index = 0; index < COUNT; index++) {
                double rise = EndRiftTentacleAnimator.ringInstanceRise(
                        index, COUNT, stage, 1.0D, 0.5D);
                assertTrue(rise >= EndRiftTentacleAnimator.RING_WAVE_FLOOR - 1.0E-9D,
                        "stage " + stage + " stem " + index + " fell below the contact floor");
                assertTrue(rise <= 1.0D + 1.0E-9D,
                        "stage " + stage + " stem " + index + " rose above full height");
            }
        }
    }

    @Test
    void emergeTrailsTheStemBeforeIt() {
        // During emergence the crest runs around the ring; a stem later in the
        // row cannot be ahead of the one leading it.
        double first = EndRiftTentacleAnimator.ringInstanceRise(
                0, COUNT, KaguneRingFrame.EMERGE, 0.5D, 0.0D);
        double later = EndRiftTentacleAnimator.ringInstanceRise(
                COUNT / 3, COUNT, KaguneRingFrame.EMERGE, 0.5D, 0.0D);
        assertTrue(later < first,
                "the ring must emerge as a wave, not as one rigid plate");
    }

    @Test
    void theWaveTravelsWhenTheSweepMoves() {
        int index = COUNT / 8;
        double atStart = EndRiftTentacleAnimator.ringInstanceRise(
                index, COUNT, KaguneRingFrame.TRAVEL, 1.0D, 0.0D);
        double atQuarter = EndRiftTentacleAnimator.ringInstanceRise(
                index, COUNT, KaguneRingFrame.TRAVEL, 1.0D, 0.25D);
        assertNotEquals(atStart, atQuarter, 1.0E-6D,
                "the same stem must change height as the wave passes it");
    }

    @Test
    void retractingStemSinksFromUprightTowardTheRetractedPose() {
        // The retraction blend must follow retraction progress, not remaining
        // height: a stem that has just started to sink still reads upright, and
        // one that has almost sunk must read as retracted. The old blend weight
        // ran backwards and snapped the pose upright as the stem descended.
        EndRiftTentaclePose.TentaclePose upright =
                EndRiftTentacleAnimator.poseFor("EMERGE", 1F, 0L);
        double high = distance(EndRiftTentacleAnimator.ringPose(
                retractFrame(0.9D), 0, 0), upright);
        double middle = distance(EndRiftTentacleAnimator.ringPose(
                retractFrame(0.5D), 0, 0), upright);
        double low = distance(EndRiftTentacleAnimator.ringPose(
                retractFrame(0.1D), 0, 0), upright);
        assertTrue(high < middle && middle < low,
                "sinking must move the pose away from upright monotonically: "
                        + high + " < " + middle + " < " + low);
        assertTrue(low > high * 4D,
                "an almost-sunk stem must not snap back toward upright");
    }

    @Test
    void almostSunkStemReachesTheRetractedPose() {
        EndRiftTentaclePose.TentaclePose retracted =
                EndRiftTentacleAnimator.poseFor("RETRACT", 0.9F, 0L);
        EndRiftTentaclePose.TentaclePose low =
                EndRiftTentacleAnimator.ringPose(retractFrame(0.1D), 0, 0);
        EndRiftTentaclePose.TentaclePose high =
                EndRiftTentacleAnimator.ringPose(retractFrame(0.9D), 0, 0);
        assertTrue(distance(low, retracted) < distance(high, retracted),
                "the end of retraction must approach the retracted pose");
    }

    private static KaguneRingFrame retractFrame(double rise) {
        // count=0 keeps ringInstanceRise on the frame's own rise, so this tests
        // the blend itself rather than the per-stem phase offset.
        return new KaguneRingFrame(0, KaguneRingFrame.RETRACT, 8.0D, 8.0D,
                rise, 0.0D, java.util.List.of(0.0D, Math.PI));
    }

    private static final String[] BONES = {
            "root", "base", "seg_01", "seg_02", "seg_03", "seg_04", "seg_05",
            "seg_06", "tip", "tip_claw_1", "tip_claw_2", "tip_claw_3", "tip_claw_4"};

    private static double distance(EndRiftTentaclePose.TentaclePose left,
                                   EndRiftTentaclePose.TentaclePose right) {
        double total = 0.0D;
        for (String bone : BONES) {
            EndRiftTentaclePose.BoneTransform a = left.transform(bone);
            EndRiftTentaclePose.BoneTransform b = right.transform(bone);
            total += square(a.translationX() - b.translationX())
                    + square(a.translationY() - b.translationY())
                    + square(a.translationZ() - b.translationZ())
                    + square(a.pitch() - b.pitch())
                    + square(a.yaw() - b.yaw())
                    + square(a.roll() - b.roll())
                    + square(a.scaleX() - b.scaleX())
                    + square(a.scaleY() - b.scaleY())
                    + square(a.scaleZ() - b.scaleZ());
        }
        return total;
    }

    private static double square(double value) {
        return value * value;
    }

    @Test
    void degenerateCountsDoNotThrow() {
        assertEquals(0.5D, EndRiftTentacleAnimator.ringInstanceRise(
                3, 0, KaguneRingFrame.TRAVEL, 0.5D, 0.5D), 1.0E-9D);
        assertEquals(0.5D, EndRiftTentacleAnimator.ringInstanceRise(
                -1, 0, KaguneRingFrame.EMERGE, 0.5D, 0.5D), 1.0E-9D);
    }
}