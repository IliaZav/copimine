package me.copimine.dungeon.domain;

import java.util.Objects;

/**
 * A concealed passage (spec OV4.5 GATE-06). A secret is a special gate category
 * plus concealment and unlock rule, not a separate engine: it reuses an existing
 * gate style mechanism under the hood.
 */
public record SecretDefinition(String id, String roomId, GateStyle style, UnlockMode unlock,
                               String world, Cuboid bounds, String entryRule) {

    public SecretDefinition {
        id = requireId(id, "secretId");
        roomId = requireId(roomId, "roomId");
        Objects.requireNonNull(style, "style");
        Objects.requireNonNull(unlock, "unlock");
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(bounds, "bounds");
        entryRule = (entryRule == null || entryRule.isBlank()) ? "rune" : entryRule.trim();
    }

    public static SecretDefinition of(String id, String roomId, GateStyle style, UnlockMode unlock,
                                      String world, Cuboid bounds, String entryRule) {
        return new SecretDefinition(id, roomId, style, unlock, world, bounds, entryRule);
    }

    public GateDefinition physicalGate() {
        return GateDefinition.of("secret:" + id, roomId, style, unlock, world, bounds, "auto", false);
    }

    /** Concealment style presented to authorized builders only. */
    public GateStyle revealStyle() {
        return GateStyle.SECRET_DISGUISED;
    }

    public Builder toBuilder() {
        Builder builder = new Builder(id);
        builder.roomId = roomId;
        builder.style = style;
        builder.unlock = unlock;
        builder.world = world;
        builder.bounds = bounds;
        builder.entryRule = entryRule;
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

    /** Fluent builder used by the editor's {@code secret style|unlock} commands. */
    public static final class Builder {
        private final String id;
        private String roomId;
        private GateStyle style;
        private UnlockMode unlock;
        private String world;
        private Cuboid bounds;
        private String entryRule;

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

        public Builder entryRule(String value) {
            this.entryRule = value;
            return this;
        }

        public SecretDefinition build() {
            return new SecretDefinition(id, roomId, style, unlock, world, bounds, entryRule);
        }
    }
}
