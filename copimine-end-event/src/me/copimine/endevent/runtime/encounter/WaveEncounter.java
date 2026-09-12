package me.copimine.endevent.runtime.encounter;

import me.copimine.endevent.domain.EndRiftObjective;
import me.copimine.endevent.runtime.EncounterContext;

/** Pure lifecycle contract implemented by every canonical numbered wave. */
public interface WaveEncounter {
    EndRiftObjective.Objective objective();
    int wave();
    boolean started();
    boolean completed();
    long generation();
    Result start(EncounterContext context);
    Result tick(EncounterContext context);
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
