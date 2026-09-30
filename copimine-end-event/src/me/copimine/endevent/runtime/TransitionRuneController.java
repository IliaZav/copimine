package me.copimine.endevent.runtime;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import me.copimine.endevent.domain.TransitionRunePolicy;

/**
 * Small stateful adapter for the server tick loop. It owns only the current
 * hold state; Bukkit rendering and phase transitions stay in the plugin.
 */
public final class TransitionRuneController {
    public enum RenderState {
        EMPTY,
        OCCUPIED,
        CHARGING,
        COMPLETE
    }

    public record Observation(TransitionRunePolicy.Check check,
                              TransitionRunePolicy.HoldState hold,
                              RenderState renderState, boolean justCompleted) {
        public Observation {
            check = check == null
                    ? new TransitionRunePolicy.Check(false, Set.of(), TransitionRunePolicy.Reason.IDLE)
                    : check;
            hold = hold == null ? TransitionRunePolicy.HoldState.idle() : hold;
            renderState = renderState == null ? RenderState.EMPTY : renderState;
        }
    }

    private final long holdMillis;
    private TransitionRunePolicy.HoldState hold = TransitionRunePolicy.HoldState.idle();

    public TransitionRuneController(long holdMillis) {
        this.holdMillis = Math.max(1L, holdMillis);
    }

    public Observation observe(Set<UUID> roster,
                               Collection<TransitionRunePolicy.RuneOccupancy> occupancies,
                               long nowMillis) {
        TransitionRunePolicy.Check check = TransitionRunePolicy.evaluate(roster, occupancies);
        TransitionRunePolicy.HoldState previous = hold;
        hold = TransitionRunePolicy.advance(previous, check, nowMillis, holdMillis);
        boolean justCompleted = !previous.complete() && hold.complete();
        RenderState render = !check.complete()
                ? occupancies == null || occupancies.isEmpty() ? RenderState.EMPTY : RenderState.OCCUPIED
                : hold.complete() ? RenderState.COMPLETE : RenderState.CHARGING;
        return new Observation(check, hold, render, justCompleted);
    }

    public TransitionRunePolicy.HoldState holdState() {
        return hold;
    }

    public void reset() {
        hold = TransitionRunePolicy.HoldState.idle();
    }
}
