package me.copimine.client;

import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wave 5 blackout is total outside safe volumes.
 *
 * <p>Superseded expectation: the fog used to keep a positive 4-6 block view
 * distance derived from {@code 8 - 4 * intensity}, which read as a vignette
 * over a still-visible arena. The hazard contract requires the entire arena to
 * be opaque black except the published safe volumes, so the fog end is now a
 * hard blackout and safe columns are exempted by position.
 */
class EndEventBlackFogDensityTest {
    @Test
    void activeWaveFiveFogIsOpaqueRegardlessOfIntensity() {
        EndEventBlackFogManager manager = new EndEventBlackFogManager();

        assertTrue(manager.apply(packet(1L, 1_000L, 0.5F), 1_000L));
        assertEquals(0.02F, manager.fogEndBlocks("the_end", 1_100L), 0.001F,
                "the least intense supported fog still has to black the arena out");

        assertTrue(manager.apply(packet(2L, 2_000L, 0.88F), 2_000L));
        assertEquals(0.02F, manager.fogEndBlocks("the_end", 2_100L), 0.001F,
                "the normal server intensity is a full blackout");

        assertTrue(manager.apply(packet(3L, 3_000L, 1.0F), 3_000L));
        assertEquals(0.02F, manager.fogEndBlocks("the_end", 3_100L), 0.001F,
                "maximum intensity does not extend visibility");
    }

    @Test
    void zeroVisibilityMeansInactiveNotBlackout() {
        EndEventBlackFogManager manager = new EndEventBlackFogManager();

        assertEquals(0.0F, manager.fogEndBlocks("the_end", 1_000L), 0.001F,
                "no active fog must never be interpreted as a blackout");
        assertTrue(manager.apply(packet(1L, 1_000L, 1.0F), 1_000L));
        assertEquals(0.0F, manager.fogEndBlocks("overworld", 1_100L), 0.001F,
                "another dimension keeps its normal fog");
        assertEquals(0.0F, manager.fogEndBlocks("the_end", 20_000L), 0.001F,
                "the fog expires on its own lifetime");
    }

    @Test
    void safeVolumesAreExemptFromTheBlackoutButNothingElseIs() {
        // Standing on an emerald floor at block Y 64: feet at surface 65.
        double onFloor = 65.0D + BlackFogSafeVolumePolicy.EYE_HEIGHT_BLOCKS;
        assertTrue(BlackFogSafeVolumePolicy.insideSafeColumn(onFloor, 64));
        assertTrue(BlackFogSafeVolumePolicy.insideSafeColumn(onFloor + 2.0D, 64),
                "the server still exempts two blocks of vertical slack");
        assertTrue(BlackFogSafeVolumePolicy.insideSafeColumn(onFloor - 2.0D, 64));

        // The server's exemption ends at 2.0 blocks, so the client must too.
        assertFalse(BlackFogSafeVolumePolicy.insideSafeColumn(onFloor + 2.1D, 64),
                "a player above the server's damage exemption must not see out of the blackout");
        assertFalse(BlackFogSafeVolumePolicy.insideSafeColumn(onFloor - 2.1D, 64));
        assertFalse(BlackFogSafeVolumePolicy.insideSafeColumn(64.0D + 1.62D, 70),
                "a ceiling above the camera is not a safe floor");
    }

    @Test
    void standingOnASafeFloorFindsItInTheCandidateRange() {
        double onFloor = 65.0D + BlackFogSafeVolumePolicy.EYE_HEIGHT_BLOCKS;
        assertTrue(BlackFogSafeVolumePolicy.firstCandidateFloorY(onFloor) <= 64);
        assertTrue(BlackFogSafeVolumePolicy.lastCandidateFloorY(onFloor) >= 64,
                "the block the player stands on has to be scanned");
    }

    @Test
    void safeVolumeNeverRestoresVanillaFog() {
        assertEquals(0.02F, BlackFogSafeVolumePolicy.fogEndBlocks(false, 0.02F), 0.0001F,
                "outside a safe volume the arena is opaque black");
        assertEquals(BlackFogSafeVolumePolicy.SAFE_READ_DISTANCE_BLOCKS,
                BlackFogSafeVolumePolicy.fogEndBlocks(true, 0.02F), 0.0001F,
                "inside a safe volume the view is shortened, not cleared");
        assertTrue(BlackFogSafeVolumePolicy.SAFE_READ_DISTANCE_BLOCKS > 0.02F,
                "the safe volume has to stay readable");
        assertThrows(IllegalArgumentException.class,
                () -> BlackFogSafeVolumePolicy.fogEndBlocks(true, Float.NaN));
    }

    /**
     * W5-017 supersedes the uniform four-block radius. The readable distance is
     * now derived from the widest published footprint instead of being an
     * independent view radius, so it can never swallow the arena.
     */
    @Test
    void theReadableDistanceIsBoundedByTheWidestPublishedFootprint() {
        double halfDiagonal = Math.sqrt(2.0D) * (BlackFogSafeVolumePolicy.MAX_FOOTPRINT_SIDE / 2.0D
                + BlackFogSafeVolumePolicy.SAFE_MARGIN_BLOCKS);
        assertEquals(halfDiagonal, BlackFogSafeVolumePolicy.SAFE_READ_DISTANCE_BLOCKS, 1.0E-4D,
                "the read distance is exactly the far corner of the widest footprint plus margin");
        assertTrue(BlackFogSafeVolumePolicy.SAFE_READ_DISTANCE_BLOCKS < halfDiagonal + 2.0D,
                "the wall has to close immediately past the footprint, not 4 blocks out");
        assertTrue(BlackFogSafeVolumePolicy.SAFE_READ_DISTANCE_BLOCKS < 4.0F,
                "the superseded uniform 4-block radius is gone");
    }

    @Test
    void safeVolumeGeometryRejectsNonFiniteCamera() {
        assertThrows(IllegalArgumentException.class,
                () -> BlackFogSafeVolumePolicy.firstCandidateFloorY(Double.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> BlackFogSafeVolumePolicy.blockX(Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class,
                () -> BlackFogSafeVolumePolicy.blockZ(Double.NaN));
    }

    private static BridgePayload packet(long sequence, long timestamp, float intensity) {
        return new BridgePayload("END_EVENT:END_FOG_STATE", ClientBridgeProtocol.PROTOCOL_VERSION,
                sequence, timestamp, "event-1", "wave5-black-fog", false, false, false, false,
                Set.of(), "", "WAVE5_FOG_V1", 8_000, intensity, 0, 0,
                "the_end|wave5|1", "", "", "", "ACTIVE");
    }
}