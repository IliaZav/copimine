package me.copimine.dungeon.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Durable room definition (spec OV4.2/OV4.4, schema example OV4.3).
 *
 * <p>This is the controller's data model only. It never owns the friend's
 * physical decor: the world block mutation mask is a separate immutable
 * revision (WorldStructureOwnershipManifest).</p>
 */
public final class DungeonRoomDefinition {

    private final String id;
    private final String displayName;
    private final boolean enabled;
    private final String world;
    private final Cuboid bounds;
    private final Cuboid trigger;
    private final ParticipationPolicy participation;
    private final ScalingMode scalingMode;
    private final int maxConcurrentEnemies;
    private final List<WaveSpec> waves;
    private final List<String> spawnAnchorIds;
    private final List<String> gateIds;
    private final List<String> secretIds;
    private final RewardSpec reward;
    private final int resetCooldownSeconds;

    private DungeonRoomDefinition(Builder builder) {
        this.id = requireId(builder.id);
        this.displayName = builder.displayName == null ? builder.id : builder.displayName;
        this.enabled = builder.enabled;
        this.world = builder.world;
        this.bounds = builder.bounds;
        this.trigger = builder.trigger == null ? builder.bounds : builder.trigger;
        this.participation = builder.participation == null
                ? ParticipationPolicy.joinSafeUnlimited() : builder.participation;
        this.scalingMode = builder.scalingMode == null ? ScalingMode.ADAPTIVE : builder.scalingMode;
        this.maxConcurrentEnemies = Math.max(0, builder.maxConcurrentEnemies);
        this.waves = List.copyOf(builder.waves);
        this.spawnAnchorIds = List.copyOf(builder.spawnAnchorIds);
        this.gateIds = List.copyOf(builder.gateIds);
        this.secretIds = List.copyOf(builder.secretIds);
        this.reward = builder.reward == null ? RewardSpec.personal("") : builder.reward;
        this.resetCooldownSeconds = Math.max(0, builder.resetCooldownSeconds);
    }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    public Builder toBuilder() {
        Builder builder = new Builder(id);
        builder.displayName = displayName;
        builder.enabled = enabled;
        builder.world = world;
        builder.bounds = bounds;
        builder.trigger = trigger;
        builder.participation = participation;
        builder.scalingMode = scalingMode;
        builder.maxConcurrentEnemies = maxConcurrentEnemies;
        builder.waves = new ArrayList<>(waves);
        builder.spawnAnchorIds = new ArrayList<>(spawnAnchorIds);
        builder.gateIds = new ArrayList<>(gateIds);
        builder.secretIds = new ArrayList<>(secretIds);
        builder.reward = reward;
        builder.resetCooldownSeconds = resetCooldownSeconds;
        return builder;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public boolean enabled() {
        return enabled;
    }

    public String world() {
        return world;
    }

    public Cuboid bounds() {
        return bounds;
    }

    public Cuboid trigger() {
        return trigger;
    }

    public ParticipationPolicy participation() {
        return participation;
    }

    public ScalingMode scalingMode() {
        return scalingMode;
    }

    public int maxConcurrentEnemies() {
        return maxConcurrentEnemies;
    }

    public List<WaveSpec> waves() {
        return waves;
    }

    public List<String> spawnAnchorIds() {
        return spawnAnchorIds;
    }

    public List<String> gateIds() {
        return gateIds;
    }

    public List<String> secretIds() {
        return secretIds;
    }

    public RewardSpec reward() {
        return reward;
    }

    public int resetCooldownSeconds() {
        return resetCooldownSeconds;
    }

    private static String requireId(String value) {
        Objects.requireNonNull(value, "id");
        String trimmed = value.trim();
        if (trimmed.isEmpty() || !trimmed.equals(trimmed.toLowerCase(java.util.Locale.ROOT))) {
            throw new IllegalArgumentException("Идентификатор комнаты должен быть непустым и в нижнем регистре: "
                    + value);
        }
        return trimmed;
    }

    /** Fluent builder used by the editor command tree and the importer. */
    public static final class Builder {
        private final String id;
        private String displayName;
        private boolean enabled = true;
        private String world = "";
        private Cuboid bounds;
        private Cuboid trigger;
        private ParticipationPolicy participation = ParticipationPolicy.joinSafeUnlimited();
        private ScalingMode scalingMode = ScalingMode.ADAPTIVE;
        private int maxConcurrentEnemies;
        private List<WaveSpec> waves = new ArrayList<>();
        private List<String> spawnAnchorIds = new ArrayList<>();
        private List<String> gateIds = new ArrayList<>();
        private List<String> secretIds = new ArrayList<>();
        private RewardSpec reward = RewardSpec.personal("");
        private int resetCooldownSeconds;

        private Builder(String id) {
            this.id = id;
        }

        public Builder displayName(String value) {
            this.displayName = value;
            return this;
        }

        public Builder enabled(boolean value) {
            this.enabled = value;
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

        public Builder trigger(Cuboid value) {
            this.trigger = value;
            return this;
        }

        public Builder participation(ParticipationPolicy value) {
            this.participation = value;
            return this;
        }

        public Builder scalingMode(ScalingMode value) {
            this.scalingMode = value;
            return this;
        }

        public Builder maxConcurrentEnemies(int value) {
            this.maxConcurrentEnemies = value;
            return this;
        }

        public Builder waves(List<WaveSpec> value) {
            this.waves = new ArrayList<>(value);
            return this;
        }

        public Builder addWave(WaveSpec value) {
            this.waves.add(value);
            return this;
        }

        public Builder spawnAnchorIds(List<String> value) {
            this.spawnAnchorIds = new ArrayList<>(value);
            return this;
        }

        public Builder addSpawnAnchorId(String value) {
            this.spawnAnchorIds.add(value);
            return this;
        }

        public Builder gateIds(List<String> value) {
            this.gateIds = new ArrayList<>(value);
            return this;
        }

        public Builder addGateId(String value) {
            this.gateIds.add(value);
            return this;
        }

        public Builder secretIds(List<String> value) {
            this.secretIds = new ArrayList<>(value);
            return this;
        }

        public Builder addSecretId(String value) {
            this.secretIds.add(value);
            return this;
        }

        public Builder reward(RewardSpec value) {
            this.reward = value;
            return this;
        }

        /** Current reward value (used by {@code reward mode|anchor|loot} edits). */
        public RewardSpec reward() {
            return reward;
        }

        /** Current (mutable copy) wave list. */
        public java.util.List<WaveSpec> waves() {
            return waves;
        }

        public String id() {
            return id;
        }

        public Builder resetCooldownSeconds(int value) {
            this.resetCooldownSeconds = value;
            return this;
        }

        public DungeonRoomDefinition build() {
            return new DungeonRoomDefinition(this);
        }
    }
}
