package me.copimine.artifacts;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/** Runs one orphan-transfer reconciliation at a time and always releases its guard. */
public final class OrphanTransferReconciliationRunGate {
    private final AtomicBoolean active = new AtomicBoolean();

    public boolean runIfIdle(Runnable reconciliation) {
        Objects.requireNonNull(reconciliation, "reconciliation");
        if (!active.compareAndSet(false, true)) {
            return false;
        }
        try {
            reconciliation.run();
            return true;
        } finally {
            active.set(false);
        }
    }
}
