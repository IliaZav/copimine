package me.copimine.dungeon.domain;

import java.util.Objects;

/**
 * A physical gate definition (spec OV4.5). {@code bounds} is the exact door
 * occupancy mask; the animated client model is visual-only and must never
 * desynchronise from real Paper collision clearing time.
 */
public record GateDefinition(String id, String roomId, GateStyle style, UnlockMode unlock,
                             String world, Cuboid bounds, String direction, boolean initialOpen) {

    public GateDefinition {
        id = requireId(id, "gateId");
        roomId = requireId(roomId, "roomId");
        Objects.requireNonNull(style, "style");
        Objects.requireNonNull(unlock, "unlock");
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(bounds, "bounds");
        direction = (direction == null || direction.isBlank()) ? "auto" : direction.trim();
    }

    public static GateDefinition of(String id, String roomId, GateStyle style, UnlockMode unlock,
                                    String world, Cuboid bounds, String direction, boolean initialOpen) {
        return new GateDefinition(id, roomId, style, unlock, world, bounds, direction, initialOpen);
    }

    public GateState initialState() {
        return initialOpen ? GateState.OPEN : GateState.CLOSED;
    }

    public Builder toBuilder() {
        Builder builder = new Builder(id);
        builder.roomId = roomId;
        builder.style = style;
        builder.unlock = unlock;
        builder.world = world;
        builder.bounds = bounds;
        builder.direction = direction;
        builder.initialOpen = initialOpen;
        return builder;
    }

    private static String requireId(String value, String label) {
        Objects.requireNonNull(value, label);
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Пустой идентификатор: " + label);
        }
        return trimmed;
    }

    /** Fluent builder used by the editor's {@code gate style|unlock|bind|direction} commands. */
    public static final class Builder {
        private final String id;
        private String roomId;
        private GateStyle style;
        private UnlockMode unlock;
        private String world;
        private Cuboid bounds;
        private String direction;
        private boolean initialOpen;

        private Builder(String id) {
            this.id = id;
        }

        public Builder roomId(String value) {
            this.roomId = value;
            return this;
        }

        public Builder style(GateStyle value) {
            this.style = value;
            return this;
        }

        public Builder unlock(UnlockMode value) {
            this.unlock = value;
            return this;
        }

        public Builder world(String value) {
            this.world = value;
            return this;
        }

        public Builder bounds(Cuboid value) {
            this.bounds = value;
            return this;
        }

        public Builder direction(String value) {
            this.direction = value;
            return this;
        }

        public Builder initialOpen(boolean value) {
            this.initialOpen = value;
            return this;
        }

        public GateDefinition build() {
            return new GateDefinition(id, roomId, style, unlock, world, bounds, direction, initialOpen);
        }
    }
}
