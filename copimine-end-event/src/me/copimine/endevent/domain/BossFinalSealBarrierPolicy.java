package me.copimine.endevent.domain;

/**
 * Defines the lifetime of the Wave 7 containment wall after the objective is
 * complete. The final-seal room is still the Wave 7 arena, so its walls must
 * survive the cooldown, cinematic, boss fight and defeat cinematic.
 */
public final class BossFinalSealBarrierPolicy {
    private BossFinalSealBarrierPolicy() {
    }

    public static boolean keepFor(EventPhase phase, int activeWave) {
        if (activeWave != 7 || phase == null) {
            return false;
        }
        return switch (phase) {
            case PRE_BOSS_COOLDOWN, BOSS_CINEMATIC, BOSS_ACTIVE, BOSS_FINISH -> true;
            default -> false;
        };
    }
}
