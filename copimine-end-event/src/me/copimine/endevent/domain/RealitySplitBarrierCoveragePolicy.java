package me.copimine.endevent.domain;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Predicate;

/** Intended geometry is not evidence of collision when placement was declined. */
public final class RealitySplitBarrierCoveragePolicy {
    private RealitySplitBarrierCoveragePolicy() { }
    public static Set<RealitySplitBarrierPolicy.Cell> missing(
            Collection<RealitySplitBarrierPolicy.Cell> required,
            Predicate<RealitySplitBarrierPolicy.Cell> closesCell) {
        if (required == null || closesCell == null) throw new IllegalArgumentException("coverage context is required");
        Set<RealitySplitBarrierPolicy.Cell> missing = new LinkedHashSet<>();
        for (var cell : required) {
            if (cell == null) throw new IllegalArgumentException("required wall cell must be non-null");
            if (!closesCell.test(cell)) missing.add(cell);
        }
        return Set.copyOf(missing);
    }
}
