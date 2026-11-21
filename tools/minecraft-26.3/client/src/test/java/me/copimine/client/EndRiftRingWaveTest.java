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
    void degenerateCountsDoNotThrow() {
        assertEquals(0.5D, EndRiftTentacleAnimator.ringInstanceRise(
                3, 0, KaguneRingFrame.TRAVEL, 0.5D, 0.5D), 1.0E-9D);
        assertEquals(0.5D, EndRiftTentacleAnimator.ringInstanceRise(
                -1, 0, KaguneRingFrame.EMERGE, 0.5D, 0.5D), 1.0E-9D);
    }
}