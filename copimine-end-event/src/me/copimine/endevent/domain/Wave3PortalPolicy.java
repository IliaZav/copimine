package me.copimine.endevent.domain;

/** Bounded roles for the defending pack in Wave 3. */
public final class Wave3PortalPolicy {
    public static final int PORTAL_COUNT = 3;
    public static final int PUSHERS_PER_PACK = 2;
    public static final int PUSHER_KNOCKBACK_AMPLIFIER = 1;

    private Wave3PortalPolicy() {
    }

    public static boolean isPusher(int packSlot) {
        return packSlot >= 0 && packSlot < PUSHERS_PER_PACK;
    }

    public static int pusherCount(int packSize) {
        return Math.min(PUSHERS_PER_PACK, Math.max(0, packSize));
    }
}
