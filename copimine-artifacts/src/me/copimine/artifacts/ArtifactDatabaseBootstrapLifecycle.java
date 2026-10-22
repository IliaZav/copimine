package me.copimine.artifacts;

/** Serializes the asynchronous database bootstrap with plugin enable/disable. */
final class ArtifactDatabaseBootstrapLifecycle {
    private enum State {
        NEW,
        WAITING,
        INITIALIZING,
        COMPLETE,
        CANCELLED,
        FAILED
    }

    private State state = State.NEW;

    synchronized boolean beginWaiting() {
        if (this.state != State.NEW) {
            return false;
        }
        this.state = State.WAITING;
        return true;
    }

    synchronized boolean isCancelled() {
        return this.state == State.CANCELLED;
    }

    synchronized boolean isFailed() {
        return this.state == State.FAILED;
    }

    synchronized boolean beginInitialization() {
        if (this.state != State.WAITING) {
            return false;
        }
        this.state = State.INITIALIZING;
        return true;
    }

    synchronized boolean complete() {
        if (this.state != State.INITIALIZING) {
            return false;
        }
        this.state = State.COMPLETE;
        return true;
    }

    synchronized boolean fail() {
        if (this.state != State.WAITING && this.state != State.INITIALIZING) {
            return false;
        }
        this.state = State.FAILED;
        return true;
    }

    synchronized boolean cancel() {
        if (this.state != State.NEW && this.state != State.WAITING) {
            return false;
        }
        this.state = State.CANCELLED;
        return true;
    }
}
