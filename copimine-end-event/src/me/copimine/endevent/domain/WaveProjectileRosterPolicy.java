package me.copimine.endevent.domain;

/** Normalize a running encounter's roster at the adapter boundary, not at admission. */
public final class WaveProjectileRosterPolicy {
    private WaveProjectileRosterPolicy() {}

    public static int budgetPlayers(int livingPlayers, int admittedPlayers, boolean diagnostic) {
        int living = Math.max(0, Math.min(PressureBudgetController.MAX_PLAYERS, livingPlayers));
        if (living == 0) return 0;
        if (diagnostic) return Math.max(PressureBudgetController.MIN_PLAYERS, living);
        int admitted = Math.max(0, Math.min(PressureBudgetController.MAX_PLAYERS, admittedPlayers));
        if (admitted < PressureBudgetController.MIN_PLAYERS) return 0;
        // A legal attempt continues after a casualty, with the smallest bounded
        // profile. One survivor must not inherit the full twenty-player allowance.
        return Math.max(PressureBudgetController.MIN_PLAYERS, Math.min(living, admitted));
    }
}
