package me.copimine.endevent.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;

/** Executes a Wave 7 block mutation as one rollback-capable operation. */
public final class RealitySplitBarrierMutationPolicy {
    private RealitySplitBarrierMutationPolicy() {
    }

    public static <T> Result<T> applyAtomically(List<T> cells,
                                                 Function<T, String> capture,
                                                 Predicate<T> apply,
                                                 BiPredicate<T, String> restore) {
        return applyAtomically(cells, capture, apply, restore, () -> true);
    }

    /**
     * Capture every pre-mutation state before writing any cell.  The optional
     * commit callback runs only after all writes succeed; if it refuses, every
     * attempted cell is restored in reverse order.
     */
    public static <T> Result<T> applyAtomically(List<T> cells,
                                                 Function<T, String> capture,
                                                 Predicate<T> apply,
                                                 BiPredicate<T, String> restore,
                                                 BooleanSupplier commit) {
        Objects.requireNonNull(cells, "cells");
        Objects.requireNonNull(capture, "capture");
        Objects.requireNonNull(apply, "apply");
        Objects.requireNonNull(restore, "restore");
        Objects.requireNonNull(commit, "commit");

        List<T> orderedCells = List.copyOf(cells);
        Map<T, String> before = new LinkedHashMap<>();
        List<T> failures = new ArrayList<>();
        for (T cell : orderedCells) {
            try {
                String state = capture.apply(cell);
                if (state == null || state.isBlank()) {
                    failures.add(cell);
                    return new Result<>(false, true, 0, failures);
                }
                before.put(cell, state);
            } catch (RuntimeException error) {
                failures.add(cell);
                return new Result<>(false, true, 0, failures);
            }
        }

        List<T> attempted = new ArrayList<>();
        int applied = 0;
        boolean operationSucceeded = true;
        for (T cell : orderedCells) {
            attempted.add(cell);
            try {
                if (!apply.test(cell)) {
                    failures.add(cell);
                    operationSucceeded = false;
                    break;
                }
                applied++;
            } catch (RuntimeException error) {
                failures.add(cell);
                operationSucceeded = false;
                break;
            }
        }

        if (operationSucceeded) {
            try {
                operationSucceeded = commit.getAsBoolean();
            } catch (RuntimeException error) {
                operationSucceeded = false;
            }
        }
        if (operationSucceeded) {
            return new Result<>(true, true, applied, List.of());
        }

        boolean rollbackComplete = true;
        for (int index = attempted.size() - 1; index >= 0; index--) {
            T cell = attempted.get(index);
            try {
                if (!restore.test(cell, before.get(cell))) {
                    failures.add(cell);
                    rollbackComplete = false;
                }
            } catch (RuntimeException error) {
                failures.add(cell);
                rollbackComplete = false;
            }
        }
        return new Result<>(false, rollbackComplete, applied, failures);
    }

    public record Result<T>(boolean committed,
                            boolean rollbackComplete,
                            int applied,
                            List<T> failedCells) {
        public Result {
            failedCells = List.copyOf(failedCells);
        }
    }
}
