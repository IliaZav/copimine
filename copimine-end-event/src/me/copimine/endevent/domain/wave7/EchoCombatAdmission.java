package me.copimine.endevent.domain.wave7;

import java.util.Objects;
import java.util.UUID;

/** Exact private pair permissions. Room geometry remains an adapter responsibility. */
public final class EchoCombatAdmission {
    public static final long MAX_SOURCE_TICKS = 1_200;

    private EchoCombatAdmission() { }

    public record Pair(UUID event, long generation, long epoch, UUID duel,
                       UUID owner, UUID actor, UUID world) {
        public Pair {
            Objects.requireNonNull(event); Objects.requireNonNull(duel);
            Objects.requireNonNull(owner); Objects.requireNonNull(actor); Objects.requireNonNull(world);
            if (generation <= 0 || epoch <= 0 || owner.equals(actor))
                throw new IllegalArgumentException("Invalid private Echo pair");
        }
        public boolean contains(UUID entity) { return owner.equals(entity) || actor.equals(entity); }
    }

    /** Inactive contexts retain identities until cleanup, without granting permission. */
    public record Context(Pair pair, long tick, boolean active) {
        public Context {
            Objects.requireNonNull(pair);
            if (tick < 0) throw new IllegalArgumentException("Invalid Echo admission tick");
        }
    }

    public record Scope(Pair pair, UUID origin, long startedTick, long expiresTick) {
        public Scope {
            Objects.requireNonNull(pair);
            if (!pair.contains(origin) || startedTick < 0 || expiresTick <= startedTick
                    || expiresTick - startedTick > MAX_SOURCE_TICKS)
                throw new IllegalArgumentException("Invalid Echo source scope");
        }
        public boolean current(Context context, UUID actualOrigin) {
            return context != null && context.active() && pair.equals(context.pair())
                    && origin.equals(actualOrigin) && context.tick() >= startedTick
                    && context.tick() < expiresTick;
        }
    }

    public static boolean mayApply(Context context, UUID source, UUID target, boolean selfEffect) {
        return context != null && context.active() && context.pair().contains(source)
                && context.pair().contains(target) && (selfEffect || !source.equals(target));
    }
}
