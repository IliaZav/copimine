package me.copimine.endevent.domain;

/**
 * Admission rules for the disposable local visual run.
 *
 * The run may observe either idle setup state. It never owns or advances the
 * official encounter state machine, so the persisted READY_FOR_PLAYERS state
 * after a restart is just as safe as COLLECTING.
 */
public final class CreativeTestAdmissionPolicy {
    private CreativeTestAdmissionPolicy() {
    }

    public static boolean mayStart(EventPhase phase,
                                   boolean officialRewardRosterEmpty,
                                   boolean officialCombatActive) {
        return isIdlePhase(phase)
                && officialRewardRosterEmpty
                && !officialCombatActive;
    }

    public static boolean isIdlePhase(EventPhase phase) {
        return phase == EventPhase.COLLECTING || phase == EventPhase.READY_FOR_PLAYERS;
    }
}
