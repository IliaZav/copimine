package me.copimine.endevent.domain;

/** Transient cast state used by the bounded damage gate. */
public enum BossCastState {
    NONE,
    ABSORPTION_CHANNEL,
    JUDGMENT_CAST,
    EXHAUSTED
}
