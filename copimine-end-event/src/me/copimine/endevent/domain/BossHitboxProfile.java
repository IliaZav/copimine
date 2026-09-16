package me.copimine.endevent.domain;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The server-side, model-aware approximation of the Rift Guardian mesh.
 *
 * <p>The source geometry is a Bedrock 1.12 model whose coordinates are in
 * model units (sixteen model units per block).  The profile is deliberately a
 * small list of proxy boxes rather than a guess based on the vanilla
 * Enderman AABB.  The values below are generated from the checked-in
 * {@code geometry.json}: every center and extent is the axis-aligned envelope
 * of the corresponding source cubes after their bind-pose bone hierarchy and
 * cube rotations have been applied.  Runtime code never reads the client
 * resource or depends on the client mod being installed.</p>
 */
public final class BossHitboxProfile {
    public static final double MODEL_UNITS_PER_BLOCK = 16.0D;
    public static final int MAX_PROXY_COUNT = 32;

    public enum PartId {
        HEAD,
        CHEST,
        PELVIS,
        LEFT_UPPER_ARM,
        LEFT_FOREARM,
        RIGHT_UPPER_ARM,
        RIGHT_FOREARM,
        LEFT_LEG,
        RIGHT_LEG
    }

    private final List<Part> parts;
    private final Map<PartId, List<Part>> partsById;
    private final Bounds modelBounds;

    public BossHitboxProfile(List<Part> parts) {
        if (parts == null || parts.isEmpty()) {
            throw new IllegalArgumentException("boss hitbox profile must contain parts");
        }
        if (parts.size() > MAX_PROXY_COUNT) {
            throw new IllegalArgumentException("boss hitbox profile exceeds proxy limit: " + parts.size());
        }
        List<Part> copy = new ArrayList<>();
        Set<String> keys = new LinkedHashSet<>();
        for (Part part : parts) {
            if (part == null) {
                throw new IllegalArgumentException("boss hitbox profile contains a null part");
            }
            String key = part.id().name() + ":" + part.segmentIndex();
            if (!keys.add(key)) {
                throw new IllegalArgumentException("duplicate boss hitbox part: " + key);
            }
            copy.add(part);
        }
        this.parts = List.copyOf(copy);
        EnumMap<PartId, List<Part>> byId = new EnumMap<>(PartId.class);
        for (PartId id : PartId.values()) {
            List<Part> matching = this.parts.stream().filter(part -> part.id() == id).toList();
            if (!matching.isEmpty()) {
                byId.put(id, matching);
            }
        }
        this.partsById = Map.copyOf(byId);
        this.modelBounds = boundsOf(this.parts);
    }

    /**
     * Canonical profile derived from the checked-in End Rift geometry.
     * Coordinates use the source model's feet baseline (source Y=0).
     */
    public static BossHitboxProfile canonical() {
        return new BossHitboxProfile(List.of(
                // head: the rotated head bone including its crown/crest cubes
                part(PartId.HEAD, "head", 0.0D, 70.6732992256D, 1.2842263817D,
                        14.4644440107D, 10.4219158504D, 19.9603486544D, 0,
                        0.0D, 61.0D, 0.75D),
                // body lower and upper envelopes are intentionally separate;
                // this avoids turning the empty waist/shoulder space into a
                // single huge target while keeping the real torso hittable.
                // The two envelopes are the y<40 and y>=40 cube groups on
                // the rotated source body bone.
                part(PartId.CHEST, "body", 0.0D, 50.6138129700D, -0.2442505502D,
                        11.5D, 7.8292277219D, 21.7138607270D, 0,
                        0.0D, 62.0D, 0.0D),
                part(PartId.PELVIS, "body", 0.0D, 36.6779816645D, -1.3445918024D,
                        11.0D, 5.8276938105D, 7.1600929259D, 0,
                        0.0D, 62.0D, 0.0D),
                part(PartId.LEFT_UPPER_ARM, "left_hand", -5.2365464907D, 53.0348418104D,
                        -3.3052346583D, 8.1872555779D, 11.4787277965D,
                        21.4306459399D, 0, -7.5D, 62.5D, 0.75D),
                part(PartId.LEFT_FOREARM, "left_hand_low", -5.7856480035D, 31.7140814232D,
                        2.2459368031D, 7.5314943688D, 6.5299723365D,
                        20.2335698117D, 0, -8.0D, 41.25D, 0.75D),
                part(PartId.RIGHT_UPPER_ARM, "right_hand", 5.2365464907D, 53.0348418104D,
                        -3.3052346583D, 8.1872555779D, 11.4787277965D,
                        21.4306459399D, 0, 7.5D, 62.5D, 0.75D),
                part(PartId.RIGHT_FOREARM, "right_hand_low", 5.7856480035D, 31.7140814232D,
                        2.2459368031D, 7.5314943688D, 6.5299723365D,
                        20.2335698117D, 0, 8.0D, 41.25D, 0.75D),
                // Each leg is two source envelopes because the geometry has a
                // long lower assembly plus the raised knee/upper assembly.
                part(PartId.LEFT_LEG, "group6", -3.0D, 29.6703755177D, -1.9648348425D,
                        3.0D, 4.6059119869D, 12.7846393438D, 0,
                        -3.0D, 23.0D, 0.75D),
                part(PartId.LEFT_LEG, "group2+group4", -3.0D, 11.7451726525D,
                        0.0715123060D, 3.0D, 7.6080144079D, 23.3920818671D, 1,
                        -3.0D, 23.5D, 1.75D),
                part(PartId.RIGHT_LEG, "group5", 3.0D, 29.6703755177D, -1.9648348425D,
                        3.0D, 4.6059119869D, 12.7846393438D, 0,
                        3.0D, 23.0D, 0.75D),
                part(PartId.RIGHT_LEG, "group+group3", 3.0D, 11.7451726525D,
                        0.0715123060D, 3.0D, 7.6080144079D, 23.3920818671D, 1,
                        3.0D, 23.5D, 1.75D)
        ));
    }

    public List<Part> parts() {
        return parts;
    }

    /** Returns the first segment for an id; use {@link #parts(PartId)} for all segments. */
    public Part part(PartId id) {
        List<Part> matching = parts(id);
        return matching.isEmpty() ? null : matching.get(0);
    }

    public List<Part> parts(PartId id) {
        return id == null ? List.of() : partsById.getOrDefault(id, List.of());
    }

    public int proxyCount() {
        return parts.size();
    }

    public Bounds modelBounds() {
        return modelBounds;
    }

    private static Part part(PartId id, String boneName, double centerX, double centerY,
                             double centerZ, double width, double depth, double height,
                             int segmentIndex, double pivotX, double pivotY, double pivotZ) {
        return new Part(id, boneName, new Vec3(centerX, centerY, centerZ),
                width, depth, height, segmentIndex, new Vec3(pivotX, pivotY, pivotZ));
    }

    private static Bounds boundsOf(List<Part> parts) {
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (Part part : parts) {
            minX = Math.min(minX, part.minX());
            minY = Math.min(minY, part.minY());
            minZ = Math.min(minZ, part.minZ());
            maxX = Math.max(maxX, part.maxX());
            maxY = Math.max(maxY, part.maxY());
            maxZ = Math.max(maxZ, part.maxZ());
        }
        return new Bounds(minX, minY, minZ, maxX, maxY, maxZ);
    }

    public record Vec3(double x, double y, double z) {
        public Vec3 {
            requireFinite(x, "x");
            requireFinite(y, "y");
            requireFinite(z, "z");
        }
    }

    public record Part(PartId id, String boneName, Vec3 centerModel,
                       double widthModel, double depthModel, double heightModel,
                       int segmentIndex, Vec3 posePivotModel) {
        public Part(PartId id, String boneName, Vec3 centerModel,
                    double widthModel, double depthModel, double heightModel,
                    int segmentIndex) {
            this(id, boneName, centerModel, widthModel, depthModel, heightModel,
                    segmentIndex, centerModel);
        }

        public Part {
            if (id == null) {
                throw new IllegalArgumentException("hitbox part id is required");
            }
            if (boneName == null || boneName.isBlank()) {
                throw new IllegalArgumentException("hitbox part bone name is required: " + id);
            }
            if (centerModel == null) {
                throw new IllegalArgumentException("hitbox part center is required: " + id);
            }
            if (posePivotModel == null) {
                throw new IllegalArgumentException("hitbox part pose pivot is required: " + id);
            }
            requirePositiveFinite(widthModel, "widthModel");
            requirePositiveFinite(depthModel, "depthModel");
            requirePositiveFinite(heightModel, "heightModel");
            if (segmentIndex < 0) {
                throw new IllegalArgumentException("hitbox segment index must not be negative");
            }
        }

        double minX() {
            return centerModel.x() - widthModel / 2.0D;
        }

        double minY() {
            return centerModel.y() - heightModel / 2.0D;
        }

        double minZ() {
            return centerModel.z() - depthModel / 2.0D;
        }

        double maxX() {
            return centerModel.x() + widthModel / 2.0D;
        }

        double maxY() {
            return centerModel.y() + heightModel / 2.0D;
        }

        double maxZ() {
            return centerModel.z() + depthModel / 2.0D;
        }
    }

    public record Bounds(double minX, double minY, double minZ,
                         double maxX, double maxY, double maxZ) {
        public Bounds {
            requireFinite(minX, "minX");
            requireFinite(minY, "minY");
            requireFinite(minZ, "minZ");
            requireFinite(maxX, "maxX");
            requireFinite(maxY, "maxY");
            requireFinite(maxZ, "maxZ");
            if (maxX <= minX || maxY <= minY || maxZ <= minZ) {
                throw new IllegalArgumentException("hitbox bounds must be non-empty");
            }
        }

        public double width() {
            return maxX - minX;
        }

        public double height() {
            return maxY - minY;
        }

        public double depth() {
            return maxZ - minZ;
        }
    }

    private static void requirePositiveFinite(double value, String name) {
        requireFinite(value, name);
        if (value <= 0.0D) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }
}
