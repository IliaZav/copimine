package me.copimine.dungeon.domain;

import java.util.List;
import java.util.Objects;

/**
 * A room wave: an ordered group of enemy templates plus an optional spawn delay
 * in ticks (spec OV4.4: one or several waves, finite live cap, bounded retry).
 */
public record WaveSpec(List<EnemyGroup> group, int delayTicks) {

    public WaveSpec {
        Objects.requireNonNull(group, "group");
        group = List.copyOf(group);
        if (delayTicks < 0) {
            throw new IllegalArgumentException("Задержка волны не может быть отрицательной.");
        }
    }

    public static WaveSpec of(int delayTicks, EnemyGroup... groups) {
        return new WaveSpec(List.of(groups), delayTicks);
    }

    public int totalCount() {
        int total = 0;
        for (EnemyGroup member : group) {
            total += member.count();
        }
        return total;
    }

    public boolean isEmpty() {
        return group.isEmpty();
    }
}
