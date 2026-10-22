package me.copimine.artifacts;

/** Focused regression coverage for one-time Artifacts database bootstrap lifecycle. */
public final class ArtifactDatabaseBootstrapLifecycleTest {
    private ArtifactDatabaseBootstrapLifecycleTest() {}

    public static void main(String[] args) {
        startsAndInitializesOnce();
        cancellationBeforeReadinessPreventsInitialization();
        failureIsTerminal();
        System.out.println("ArtifactDatabaseBootstrapLifecycleTest OK (three transitions)");
    }

    private static void startsAndInitializesOnce() {
        ArtifactDatabaseBootstrapLifecycle lifecycle = new ArtifactDatabaseBootstrapLifecycle();
        if (!lifecycle.beginWaiting() || lifecycle.beginWaiting()) {
            throw new AssertionError("database bootstrap must have one waiting owner");
        }
        if (!lifecycle.beginInitialization() || lifecycle.beginInitialization()) {
            throw new AssertionError("database bootstrap initialization must begin once");
        }
        if (!lifecycle.complete() || lifecycle.fail() || lifecycle.cancel()) {
            throw new AssertionError("completed bootstrap must remain complete");
        }
    }

    private static void cancellationBeforeReadinessPreventsInitialization() {
        ArtifactDatabaseBootstrapLifecycle lifecycle = new ArtifactDatabaseBootstrapLifecycle();
        if (!lifecycle.beginWaiting() || !lifecycle.cancel() || !lifecycle.isCancelled()) {
            throw new AssertionError("plugin disable must cancel a waiting bootstrap");
        }
        if (lifecycle.beginInitialization() || lifecycle.complete() || lifecycle.fail()) {
            throw new AssertionError("cancelled bootstrap must not initialize, complete, or fail later");
        }
    }

    private static void failureIsTerminal() {
        ArtifactDatabaseBootstrapLifecycle lifecycle = new ArtifactDatabaseBootstrapLifecycle();
        if (!lifecycle.beginWaiting() || !lifecycle.fail() || !lifecycle.isFailed()) {
            throw new AssertionError("bootstrap failure must be recorded for one main-thread disable");
        }
        if (lifecycle.fail() || lifecycle.beginInitialization() || lifecycle.cancel()) {
            throw new AssertionError("failed bootstrap must not retry initialization or change lifecycle");
        }
    }
}
