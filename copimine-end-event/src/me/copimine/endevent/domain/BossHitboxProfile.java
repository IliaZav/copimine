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
 * {@code geometry.json}: every center and extent is the union of the cubes on
 * the corresponding source bone(s).  Runtime code never reads the client
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
                // head: head bone including its crown/crest cubes
                part(PartId.HEAD, "head", 0.0D, 70.75D, 0.5D,
                        14.5D, 9.0D, 19.0D, 0),
                // body lower and upper envelopes are intentionally separate;
                // this avoids turning the empty waist/shoulder space into a
                // single huge target while keeping the real torso hittable.
                part(PartId.CHEST, "body", 0.0D, 49.625D, 0.625D,
                        11.5D, 6.25D, 22.5D, 0),
                part(PartId.PELVIS, "body", 0.0D, 37.75D, 0.75D,
                        10.0D, 5.5D, 8.5D, 0),
                part(PartId.LEFT_UPPER_ARM, "left_hand", -7.75D, 51.875D, 0.5D,
                        4.0D, 4.0D, 21.25D, 0),
                part(PartId.LEFT_FOREARM, "left_hand_low", -8.0D, 31.25D, 0.5D,
                        3.0D, 3.0D, 20.0D, 0),
                part(PartId.RIGHT_UPPER_ARM, "right_hand", 7.75D, 51.875D, 0.5D,
                        4.0D, 4.0D, 21.25D, 0),
                part(PartId.RIGHT_FOREARM, "right_hand_low", 8.0D, 31.25D, 0.5D,
                        3.0D, 3.0D, 20.0D, 0),
                // Each leg is two source envelopes because the geometry has a
                // long lower assembly plus the raised knee/upper assembly.
                part(PartId.LEFT_LEG, "group2", -3.0D, 10.5D, 0.125D,
                        3.0D, 3.25D, 21.0D, 0),
                part(PartId.LEFT_LEG, "group6", -3.0D, 29.75D, 0.75D,
                        3.0D, 3.0D, 12.5D, 1),
                part(PartId.RIGHT_LEG, "group", 3.0D, 10.5D, 0.125D,
                        3.0D, 3.25D, 21.0D, 0),
                part(PartId.RIGHT_LEG, "group5", 3.0D, 29.75D, 0.75D,
                        3.0D, 3.0D, 12.5D, 1)
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
                             int segmentIndex) {
        return new Part(id, boneName, new Vec3(centerX, centerY, centerZ),
                width, depth, height, segmentIndex);
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
                       int segmentIndex) {
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
