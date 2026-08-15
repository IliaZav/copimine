package me.copimine.client;

public final class ClientReadyRetryState {
    public static final int MAX_ATTEMPTS = 6;
    public static final long DEADLINE_MILLIS = 15_000L;

    private boolean started;
    private boolean terminal;
    private boolean accepted;
    private int attempts;
    private long deadlineAt;
    private long nextAttemptAt;
    private String lastReason = "";

    public void start(long nowMillis) {
        started = true;
        terminal = false;
        accepted = false;
        attempts = 0;
        deadlineAt = nowMillis + DEADLINE_MILLIS;
        nextAttemptAt = nowMillis;
        lastReason = "";
    }

    public void reset() {
        started = false;
        terminal = false;
        accepted = false;
        attempts = 0;
        deadlineAt = 0L;
        nextAttemptAt = 0L;
        lastReason = "";
    }

    public boolean shouldSend(long nowMillis) {
        return started
                && !terminal
                && attempts < MAX_ATTEMPTS
                && nowMillis <= deadlineAt
                && nowMillis >= nextAttemptAt;
    }

    public void markSent(long nowMillis) {
        if (!shouldSend(nowMillis)) {
            return;
        }
        attempts++;
        long delay = switch (attempts) {
            case 1 -> 1_000L;
            case 2 -> 2_000L;
            default -> 4_000L;
        };
        nextAttemptAt = nowMillis + delay;
    }

    public void acknowledge(boolean accepted, String reason) {
        this.accepted = accepted;
        this.terminal = true;
        this.lastReason = reason == null ? "" : reason;
    }

    public boolean active() {
        return started && !terminal && attempts < MAX_ATTEMPTS;
    }

    public boolean expired(long nowMillis) {
        return started && !terminal && (nowMillis > deadlineAt || (attempts >= MAX_ATTEMPTS && nowMillis >= nextAttemptAt));
    }

    public boolean accepted() {
        return accepted;
    }

    public int attempts() {
        return attempts;
    }

    public String lastReason() {
        return lastReason;
    }
}
