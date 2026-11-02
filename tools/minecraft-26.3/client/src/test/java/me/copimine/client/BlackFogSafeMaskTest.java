package me.copimine.client;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wave 5 safe-volume occlusion (contract W5-017).
 *
 * <p>The previous rule was a uniform 4-block view radius: from safety the
 * player could read the whole arena up to 4 blocks away in every direction,
 * including terrain outside the safe footprint and other safe islands across
 * the fog. W5-017 rejects that shortcut. The readable region must instead be
 * the safe footprint itself expanded by a bounded margin, so a ray leaving the
 * footprint cannot reach geometry beyond the fog wall.
 */
class BlackFogSafeMaskTest {
    // A single 3x3 zone centred on (0, 0), floor block Y 64, as the server
    // publishes it (BlackFogSafeZonePolicy.cells with side 3).
    private static final List<BlackFogSafeVolumePolicy.SafeCell> ZONE = List.of(
            new BlackFogSafeVolumePolicy.SafeCell(-1, -1),
            new BlackFogSafeVolumePolicy.SafeCell(-1, 0),
            new BlackFogSafeVolumePolicy.SafeCell(-1, 1),
            new BlackFogSafeVolumePolicy.SafeCell(0, -1),
            new BlackFogSafeVolumePolicy.SafeCell(0, 0),
            new BlackFogSafeVolumePolicy.SafeCell(0, 1),
            new BlackFogSafeVolumePolicy.SafeCell(1, -1),
            new BlackFogSafeVolumePolicy.SafeCell(1, 0),
            new BlackFogSafeVolumePolicy.SafeCell(1, 1));

    private static final int FLOOR = 64;
    /**
     * Camera height of a player standing on the floor block top (65) with the
     * vanilla eye height. {@code readable} takes camera Y, not feet Y.
     */
    private static final double STANDING_CAMERA_Y =
            65.0D + BlackFogSafeVolumePolicy.EYE_HEIGHT_BLOCKS;

    @Test
    void theSafeFootprintItselfStaysFullyReadable() {
        BlackFogSafeVolumePolicy.SafeMask mask = BlackFogSafeVolumePolicy.maskFor(ZONE, FLOOR);
        assertTrue(mask.readable(0.5D, STANDING_CAMERA_Y, 0.5D, FLOOR),
                "the centre of the safe footprint is always readable");
        assertTrue(mask.readable(1.99D, STANDING_CAMERA_Y + 1.5D, 1.99D, FLOOR),
                "an occupant standing at the far edge of the footprint is readable");
        assertTrue(mask.readable(-0.99D, STANDING_CAMERA_Y - 1.5D, -0.99D, FLOOR));
    }

    @Test
    void theOldUniformFourBlockRadiusNoLongerLeaksTheArena() {
        BlackFogSafeVolumePolicy.SafeMask mask = BlackFogSafeVolumePolicy.maskFor(ZONE, FLOOR);
        // Three blocks east of the footprint edge: well inside the old 4-block
        // radius, but outside the footprint. W5-017 requires this to be black.
        assertFalse(mask.readable(3.0D, STANDING_CAMERA_Y, 0.5D, FLOOR),
                "a view ray leaving the safe footprint must not expose distant terrain");
        assertFalse(mask.readable(0.5D, STANDING_CAMERA_Y + 6.0D, 0.5D, FLOOR),
                "the old radius let a player read straight up over the fog wall");
    }

    @Test
    void anotherSafeIslandAcrossTheFogStaysHidden() {
        // Two zones separated by open fog: the far zone must not be readable
        // simply because it is also emerald.
        List<BlackFogSafeVolumePolicy.SafeCell> both = List.of(
                new BlackFogSafeVolumePolicy.SafeCell(0, 0),
                new BlackFogSafeVolumePolicy.SafeCell(12, 0));
        BlackFogSafeVolumePolicy.SafeMask mask = BlackFogSafeVolumePolicy.maskFor(both, FLOOR);
        // Scoped to the island the camera stands on: the far island is
        // emerald too, and a membership test over every published cell would
        // clear it, which W5-017 forbids.
        BlackFogSafeVolumePolicy.SafeMask fromFirst =
                mask.aroundCamera(0.5D, STANDING_CAMERA_Y, 0.5D);
        assertTrue(fromFirst.readable(0.5D, STANDING_CAMERA_Y, 0.5D, FLOOR));
        assertFalse(fromFirst.readable(12.5D, STANDING_CAMERA_Y, 0.5D, FLOOR),
                "a second island reachable only through fog must not be visible");
    }

    @Test
    void theMaskFollowsTheCameraSoTheOccupiedIslandStaysReadable() {
        List<BlackFogSafeVolumePolicy.SafeCell> both = List.of(
                new BlackFogSafeVolumePolicy.SafeCell(0, 0),
                new BlackFogSafeVolumePolicy.SafeCell(12, 0));
        BlackFogSafeVolumePolicy.SafeMask mask = BlackFogSafeVolumePolicy.maskFor(both, FLOOR);
        // The readable region is resolved around the camera: whoever stands on
        // the second island reads that island, and the first island is still
        // hidden across the fog because no ray may stay clear between them.
        BlackFogSafeVolumePolicy.SafeMask fromSecond =
                mask.aroundCamera(12.5D, STANDING_CAMERA_Y, 0.5D);
        assertTrue(fromSecond.readable(12.5D, STANDING_CAMERA_Y, 0.5D, FLOOR),
                "the island the player stands on is readable");
        assertFalse(fromSecond.readable(0.5D, STANDING_CAMERA_Y, 0.5D, FLOOR),
                "the island across the fog stays hidden even though it is emerald");
        assertFalse(fromSecond.readable(6.0D, STANDING_CAMERA_Y, 0.5D, FLOOR),
                "the fog between two islands is never readable");
    }

    @Test
    void verticalEscapeOverTheWallIsRefused() {
        BlackFogSafeVolumePolicy.SafeMask mask = BlackFogSafeVolumePolicy.maskFor(ZONE, FLOOR);
        assertTrue(mask.readable(0.5D, STANDING_CAMERA_Y + 2.0D, 0.5D, FLOOR),
                "the server still exempts two blocks of vertical slack");
        assertFalse(mask.readable(0.5D, STANDING_CAMERA_Y + 2.1D, 0.5D, FLOOR),
                "jumping above the exemption must not reveal the arena");
    }

    @Test
    void aMaskBuiltForAnotherFloorReadsNothing() {
        BlackFogSafeVolumePolicy.SafeMask mask = BlackFogSafeVolumePolicy.maskFor(ZONE, FLOOR);
        assertFalse(mask.readable(0.5D, STANDING_CAMERA_Y, 0.5D, 70),
                "a stale floor revision must not grant readability");
    }

    @Test
    void anEmptyMaskReadsNothing() {
        BlackFogSafeVolumePolicy.SafeMask mask =
                BlackFogSafeVolumePolicy.maskFor(List.of(), FLOOR);
        assertFalse(mask.readable(0.5D, STANDING_CAMERA_Y, 0.5D, FLOOR));
        assertEquals(0, mask.cellCount());
    }

    @Test
    void maskRejectsNonFiniteCameraPositions() {
        BlackFogSafeVolumePolicy.SafeMask mask = BlackFogSafeVolumePolicy.maskFor(ZONE, FLOOR);
        assertFalse(mask.readable(Double.NaN, STANDING_CAMERA_Y, 0.5D, FLOOR));
        assertFalse(mask.readable(0.5D, Double.POSITIVE_INFINITY, 0.5D, FLOOR));
        assertFalse(mask.readable(0.5D, STANDING_CAMERA_Y, Double.NaN, FLOOR));
    }
}