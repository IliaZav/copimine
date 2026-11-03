package me.copimine.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The boss bar must show that the party's damage landed.
 *
 * <p>A bar that only ever renders the current health gives no feedback for a
 * burst: the number simply is smaller next frame. The chip is the gap between
 * the trailing value and the current one, and it has to close on its own so a
 * stalled bar is not left looking like permanent damage.</p>
 */
public final class EndRiftBossBarChipTest {
    @Test
    void aDropCreatesATrailingChip() {
        EndRiftBossBarHud.resetTrackingForTest();
        EndRiftBossBarHud.trackProgressForTest(1.0F);
        EndRiftBossBarHud.trackProgressForTest(0.7F);
        assertEquals(1.0F, EndRiftBossBarHud.trailingProgressForTest(), 1.0e-6F,
                "the chip must hold the pre-hit value so the lost slice is visible");
    }

    @Test
    void healingDoesNotCreateAChip() {
        EndRiftBossBarHud.resetTrackingForTest();
        EndRiftBossBarHud.trackProgressForTest(0.4F);
        EndRiftBossBarHud.trackProgressForTest(0.8F);
        assertEquals(0.4F, EndRiftBossBarHud.trailingProgressForTest(), 1.0e-6F,
                "a rising bar must not draw a red chip");
    }

    @Test
    void repeatedHitsKeepTheHighestTrailingValue() {
        EndRiftBossBarHud.resetTrackingForTest();
        EndRiftBossBarHud.trackProgressForTest(1.0F);
        EndRiftBossBarHud.trackProgressForTest(0.9F);
        EndRiftBossBarHud.trackProgressForTest(0.6F);
        assertEquals(1.0F, EndRiftBossBarHud.trailingProgressForTest(), 1.0e-6F,
                "the chip must span the whole burst, not just the last hit");
    }

    @Test
    void progressIsClampedForRendering() {
        assertEquals(0.0F, EndRiftBossBarHud.clampProgressForTest(-5.0D), 1.0e-6F);
        assertEquals(1.0F, EndRiftBossBarHud.clampProgressForTest(5.0D), 1.0e-6F);
        assertEquals(0.0F, EndRiftBossBarHud.clampProgressForTest(Double.NaN), 1.0e-6F);
        assertTrue(EndRiftBossBarHud.clampProgressForTest(0.5D) > 0.49F);
    }

    @Test
    void phaseMarkersStayOrdered() {
        float[] markers = EndRiftBossBarHud.phaseMarkersForTest();
        assertEquals(5, markers.length, "six phases means five thresholds");
        for (int i = 1; i < markers.length; i++) {
            assertTrue(markers[i] > markers[i - 1], "phase markers must ascend");
        }
    }
}
