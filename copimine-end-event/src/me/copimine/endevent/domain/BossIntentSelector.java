package me.copimine.endevent.domain;

/** Deterministic group-geometry intent selection. */
public final class BossIntentSelector {
    private BossIntentSelector() {
    }

    public static BossIntent choose(BossPerceptionSnapshot snapshot) {
        if (snapshot == null || snapshot.livingPlayers().isEmpty()) {
            return BossIntent.RECOVER;
        }
        if (snapshot.movementStuck() || snapshot.bossSurrounded()) {
            return BossIntent.CREATE_SPACE;
        }
        if (snapshot.partyStacked()) {
            return BossIntent.PUNISH_STACK;
        }
        if (snapshot.partySpread()) {
            return BossIntent.PUNISH_SPREAD;
        }
        return switch (snapshot.phase()) {
            case AWAKENING -> BossIntent.PRESSURE;
            case HUNT -> BossIntent.FLANK;
            case RIFT -> BossIntent.CONTROL;
            case OVERLOAD -> snapshot.hazards().arenaLocked()
                    ? BossIntent.RECOVER : BossIntent.RANGED_PRESSURE;
            case RAGE -> BossIntent.RANGED_PRESSURE;
            case LAST_SEAL -> BossIntent.CHANNEL;
        };
    }
}
