package me.copimine.client;

/**
 * Camera test for the Wave 5 safe volumes.
 *
 * <p>The server publishes its safe areas as emerald floors before the blackout
 * starts, so the client reads the same blocks instead of duplicating the
 * layout over the bridge.
 *
 * <p>The vertical bounds deliberately mirror the authoritative damage
 * exemption in {@code CopiMineEndEvent.isCurrentSafeZonePlayer}, which accepts
 * any player within 2.0 blocks of the combat floor. A wider client volume
 * would let a player read the arena while the server is already damaging them.
 */
public final class BlackFogSafeVolumePolicy {
    /** Camera height above the block the player stands on. */
    public static final double EYE_HEIGHT_BLOCKS = 1.62D;
    /** Server damage exemption: {@code abs(playerY - combatFloorY) <= 2.0}. */
    public static final double SAFE_VERTICAL_SLACK_BLOCKS = 2.0D;
    /**
     * Invisible wall a safe volume still sees through. Vanilla fog cannot mask
     * per pixel, so the safe column is honoured as a short readable radius.
     *
     * <p>shortcut: safe-zone visibility is a radius, not an exact volume mask;
     * upgrade to a masked post-process pass when uniform-capable post effects
     * land.
     */
    public static final float SAFE_VIEW_RADIUS_BLOCKS = 4.0F;

    private BlackFogSafeVolumePolicy() {
    }

    /**
     * Fog distance for the current frame. Being inside a safe volume neither
     * clears the blackout nor restores vanilla fog: it only shortens the view,
     * so the safe floor and its occupants are readable while the rest of the
     * arena stays black.
     */
    public static float fogEndBlocks(boolean insideSafeVolume, float blackoutEnd) {
        if (!Float.isFinite(blackoutEnd) || blackoutEnd < 0.0F) {
            throw new IllegalArgumentException("blackout fog end must be finite and non-negative");
        }
        return insideSafeVolume ? SAFE_VIEW_RADIUS_BLOCKS : blackoutEnd;
    }

    /** Feet height implied by a camera at {@code cameraY}. */
    public static double feetY(double cameraY) {
        if (!Double.isFinite(cameraY)) {
            throw new IllegalArgumentException("camera height must be finite");
        }
        return cameraY - EYE_HEIGHT_BLOCKS;
    }

    /**
     * First safe-floor block worth testing. The block top sits one above the
     * block Y, so the floor under the feet can be a few blocks below.
     */
    public static int firstCandidateFloorY(double cameraY) {
        return (int) Math.floor(feetY(cameraY)) - 3;
    }

    public static int lastCandidateFloorY(double cameraY) {
        return (int) Math.floor(feetY(cameraY)) + 1;
    }

    /**
     * Whether a camera at {@code cameraY} is inside the volume above the safe
     * floor block at {@code floorBlockY}, using the server's own slack.
     */
    public static boolean insideSafeColumn(double cameraY, int floorBlockY) {
        if (!Double.isFinite(cameraY)) return false;
        double floorSurfaceY = floorBlockY + 1.0D;
        return Math.abs(feetY(cameraY) - floorSurfaceY) <= SAFE_VERTICAL_SLACK_BLOCKS;
    }

    /** Block column a camera occupies; the safe floor must share it exactly. */
    public static int blockX(double cameraX) {
        if (!Double.isFinite(cameraX)) {
            throw new IllegalArgumentException("camera x must be finite");
        }
        return (int) Math.floor(cameraX);
    }

    public static int blockZ(double cameraZ) {
        if (!Double.isFinite(cameraZ)) {
            throw new IllegalArgumentException("camera z must be finite");
        }
        return (int) Math.floor(cameraZ);
    }
}