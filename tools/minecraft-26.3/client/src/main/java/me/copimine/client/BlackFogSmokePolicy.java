package me.copimine.client;

/** Client-local smoke budget and safe-floor clearance, independent of rendering. */
public final class BlackFogSmokePolicy {
    public static final int MAX_PARTICLES = 256;
    public static final int INITIAL_PARTICLES = 96;
    public static final int PER_TICK = 8;
    public static final double RADIUS = 1.25;
    private BlackFogSmokePolicy() {}

    public static int budget(int alive, boolean firstFrame) {
        return Math.max(0, Math.min(MAX_PARTICLES - alive, firstFrame ? INITIAL_PARTICLES : PER_TICK));
    }

    public static boolean clearOfSafeFloor(double x, double z, BlackFogVolumePolicy.Floor floor) {
        for (int bx = (int) Math.floor(x - RADIUS); bx <= (int) Math.floor(x + RADIUS); bx++) {
            for (int bz = (int) Math.floor(z - RADIUS); bz <= (int) Math.floor(z + RADIUS); bz++) {
                if (floor.safe(bx, bz)) return false;
            }
        }
        return true;
    }
}
