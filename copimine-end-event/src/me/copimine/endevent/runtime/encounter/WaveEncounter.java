package me.copimine.endevent.runtime.encounter;

import me.copimine.endevent.domain.EndRiftObjective;
import me.copimine.endevent.runtime.EncounterContext;

/** Pure lifecycle contract implemented by every canonical numbered wave. */
public interface WaveEncounter {
    EndRiftObjective.Objective objective();
    int wave();
    int progress();
    int required();
    boolean started();
    boolean completed();
    long generation();
    Result start(EncounterContext context);
    Result tick(EncounterContext context);
    /** Accept the terminal result from the live Bukkit objective adapter. */
    Result reportRuntimeResult(EncounterContext context, boolean objectiveComplete);

    /**
     * Accept the live objective's progress AND its completion hint.
     *
     * <p>Wave objectives that are counters must publish their real count every
     * tick; a wave that only reports a completion boolean stays at zero until
     * the moment it finishes, which makes its progress unreadable.</p>
     */
    Result reportRuntimeResult(EncounterContext context, boolean objectiveComplete,
                               int reportedProgress);
    Result complete(EncounterContext context);
    void reset();

    enum Status {
        STARTED,
        ALREADY_STARTED,
        IN_PROGRESS,
        COMPLETE,
        REJECTED
    }

    record Result(Status status, int progress, int required, String reason) {
        public boolean accepted() {
            return status != Status.REJECTED;
        }

        public boolean complete() {
            return status == Status.COMPLETE || progress >= required && required > 0;
        }
    }
}
