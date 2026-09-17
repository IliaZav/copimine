package me.copimine.client;

import net.minecraft.client.model.ModelPartBuilder;

/** Validates and builds the standard cuboid UV footprint used by the models. */
public final class ModelUvBounds {
    private static final double BOUNDS_EPSILON = 1.0E-4D;

    private ModelUvBounds() {
    }

    /**
     * Requires the six-face vanilla box layout to fit inside the declared
     * atlas.  Calculations use double precision even though model dimensions
     * are floats so values near an atlas edge cannot overflow silently.
     */
    public static void requireStandardBoxFits(int atlasWidth, int atlasHeight,
                                              float u, float v,
                                              float width, float height, float depth) {
        if (atlasWidth <= 0 || atlasHeight <= 0) {
            throw new IllegalArgumentException("texture atlas dimensions must be positive");
        }
        if (!Float.isFinite(u) || !Float.isFinite(v)
                || !Float.isFinite(width) || !Float.isFinite(height)
                || !Float.isFinite(depth)) {
            throw new IllegalArgumentException("UV coordinates and cuboid dimensions must be finite");
        }
        if (u < 0.0F || v < 0.0F || width <= 0.0F || height <= 0.0F || depth <= 0.0F) {
            throw new IllegalArgumentException("UV coordinates and cuboid dimensions must be positive");
        }
        double maxU = (double) u + 2.0D * width + 2.0D * depth;
        double maxV = (double) v + depth + height;
        if (maxU > atlasWidth + BOUNDS_EPSILON
                || maxV > atlasHeight + BOUNDS_EPSILON) {
            throw new IllegalArgumentException("standard cuboid UV footprint exceeds atlas: "
                    + "u=" + u + " v=" + v + " maxU=" + maxU + " maxV=" + maxV
                    + " atlas=" + atlasWidth + "x" + atlasHeight);
        }
    }

    public static ModelPartBuilder cuboid(int atlasWidth, int atlasHeight,
                                          int u, int v,
                                          float x, float y, float z,
                                          float width, float height, float depth) {
        return append(ModelPartBuilder.create(), atlasWidth, atlasHeight, u, v,
                x, y, z, width, height, depth);
    }

    public static ModelPartBuilder cuboid(int atlasWidth, int atlasHeight,
                                          int u, int v,
                                          float x, float y, float z,
                                          float width, float height, float depth,
                                          boolean mirrored) {
        ModelPartBuilder builder = ModelPartBuilder.create();
        if (mirrored) {
            builder.mirrored();
        }
        return append(builder, atlasWidth, atlasHeight, u, v,
                x, y, z, width, height, depth);
    }

    /** Appends one checked cuboid to a builder, preserving any prior cuboids. */
    public static ModelPartBuilder append(ModelPartBuilder builder,
                                          int atlasWidth, int atlasHeight,
                                          int u, int v,
                                          float x, float y, float z,
                                          float width, float height, float depth) {
        if (builder == null) {
            throw new IllegalArgumentException("model part builder is required");
        }
        requireStandardBoxFits(atlasWidth, atlasHeight, u, v, width, height, depth);
        return builder.uv(u, v).cuboid(x, y, z, width, height, depth);
    }

    /** A compact descriptor for a group containing multiple checked boxes. */
    public record Box(int u, int v, float x, float y, float z,
                      float width, float height, float depth) {
    }

    public static ModelPartBuilder boxes(int atlasWidth, int atlasHeight, Box... boxes) {
        if (boxes == null || boxes.length == 0) {
            throw new IllegalArgumentException("at least one model box is required");
        }
        ModelPartBuilder builder = ModelPartBuilder.create();
        for (Box box : boxes) {
            if (box == null) {
                throw new IllegalArgumentException("model box is required");
            }
            builder = append(builder, atlasWidth, atlasHeight, box.u(), box.v(),
                    box.x(), box.y(), box.z(), box.width(), box.height(), box.depth());
        }
        return builder;
    }
}
