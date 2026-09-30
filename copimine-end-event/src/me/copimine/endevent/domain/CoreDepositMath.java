package me.copimine.endevent.domain;

import java.util.Set;

public final class CoreDepositMath {
    private CoreDepositMath() {
    }

    public static int acceptedAmount(int held, int required, int progress) {
        int safeHeld = Math.max(0, held);
        int remaining = Math.max(0, Math.max(0, required) - Math.max(0, progress));
        return Math.min(safeHeld, remaining);
    }

    public static boolean canAccept(boolean mainHand, String material,
                                    Set<String> requirements,
                                    boolean officialArtifact, boolean customProtectedItem) {
        return mainHand
                && material != null
                && requirements != null
                && requirements.contains(material)
                && !officialArtifact
                && !customProtectedItem;
    }
}
