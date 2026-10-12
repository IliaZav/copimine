package me.copimine.client;

final class IrisShaderpackApplyTransaction {
    @FunctionalInterface
    interface Action {
        void run() throws Exception;
    }

    @FunctionalInterface
    interface Rollback<T> {
        void restore(T snapshot) throws Exception;
    }

    private IrisShaderpackApplyTransaction() { }

    static <T> void execute(boolean createdSnapshot, T snapshot, Action apply,
                            Rollback<T> rollback, Runnable clearCreatedSnapshot) throws Exception {
        try {
            apply.run();
        } catch (Exception applyFailure) {
            try {
                rollback.restore(snapshot);
            } catch (Exception rollbackFailure) {
                if (rollbackFailure != applyFailure) {
                    applyFailure.addSuppressed(rollbackFailure);
                }
                throw applyFailure;
            }
            if (createdSnapshot) {
                try {
                    clearCreatedSnapshot.run();
                } catch (Exception cleanupFailure) {
                    if (cleanupFailure != applyFailure) {
                        applyFailure.addSuppressed(cleanupFailure);
                    }
                }
            }
            throw applyFailure;
        }
    }
}
