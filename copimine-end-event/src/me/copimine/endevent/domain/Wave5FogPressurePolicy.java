package me.copimine.endevent.domain;

/** Bounded pressure decisions; the server owns real death/spawn receipts. */
public final class Wave5FogPressurePolicy {
    private Wave5FogPressurePolicy() { }

    public static int resurrectionBudget(int players, int completedCycles, int dead) {
        return Math.min(Math.max(0, dead), Math.min(6,
                2 + Math.max(0, Math.min(20, players)) / 5 + Math.max(0, completedCycles) / 4));
    }

    public static int packSize(int players, boolean cleared) {
        return 10 + Math.max(0, Math.min(18, players - 2)) * 5 / 18 + (cleared ? 3 : 0);
    }

    /** -1 = no elite, 0/1/2 = Enderman/skeleton/spider. No mutable reroll state. */
    public static int eliteKind(long attemptSeed, int cycle, boolean cleared) {
        java.util.Random random = new java.util.Random(attemptSeed ^ (0x9E3779B97F4A7C15L * (cycle + 1L)));
        int chance = cleared ? 30 : Math.min(18, 8 + Math.max(0, cycle));
        return random.nextInt(100) < chance ? random.nextInt(3) : -1;
    }
}
