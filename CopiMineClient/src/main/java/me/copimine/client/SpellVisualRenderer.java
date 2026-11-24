package me.copimine.client;

import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/**
 * Draws the authored V5.1 spell meshes from a single server cue.
 *
 * <p>It consumes the bounded {@code v51-<spell>-<seed>} world cue, derives the
 * whole rotating arm / jaw / shell geometry locally and never mutates state.
 * The server remains the only owner of hitboxes, damage, targets and cleanup;
 * this renderer only disappears when the cue disappears.</p>
 */
public final class SpellVisualRenderer {

    public static final Identifier TEXTURE =
            Identifier.of("copimineclient", "textures/entity/end_rift_spell_arc.png");

    private static final int FULL_BRIGHT = 0x00F000F0;
    private static final Vec3d UP = new Vec3d(0.0D, 1.0D, 0.0D);
    private static volatile SpellVisualGeometry.Quality quality =
            SpellVisualGeometry.Quality.BALANCED;

    private SpellVisualRenderer() {
    }

    /** FULL / BALANCED / REDUCED only change sample density, never the footprint. */
    public static void setQuality(SpellVisualGeometry.Quality value) {
        quality = value == null ? SpellVisualGeometry.Quality.BALANCED : value;
    }

    public static SpellVisualGeometry.Quality quality() {
        return quality;
    }

    /** Parse the delivered spell cue from an event world instance. */
    public static SpellVisualGeometry.Spell spellOf(String instance) {
        return SpellVisualGeometry.spellCue(instance);
    }

    public static long seedOf(String instance) {
        return SpellVisualGeometry.cueSeed(instance);
    }

    /**
     * Submit one cue's quads. The caller already translated the matrix by
     * {@code -cameraPos}, so the emitted points are absolute world positions.
     */
    public static void draw(VertexConsumer buffer, MatrixStack.Entry entry,
                            EndEventWorldVfxManager.BeamSnapshot beam, long now) {
        if (buffer == null || entry == null || beam == null || beam.start() == null || beam.end() == null) {
            return;
        }
        SpellVisualGeometry.Spell spell = spellOf(beam.instanceId());
        if (spell == null || now < beam.startedAtMillis() || beam.expiresAtMillis() <= beam.startedAtMillis()) {
            return;
        }
        long elapsed = now - beam.startedAtMillis();
        long remaining = beam.expiresAtMillis() - now;
        double dx = beam.end().x - beam.start().x;
        double dz = beam.end().z - beam.start().z;
        double horizontal = Math.hypot(dx, dz);
        double dirX;
        double dirZ;
        if (!Double.isFinite(horizontal) || horizontal < 0.05D) {
            dirX = 0.0D;
            dirZ = 1.0D;
            horizontal = 0.0D;
        } else {
            dirX = dx / horizontal;
            dirZ = dz / horizontal;
        }
        double rightX = -dirZ;
        double rightZ = dirX;
        double reach = Math.max(1.0D, Math.min(SpellVisualGeometry.MAX_REACH_BLOCKS, horizontal));
        List<SpellVisualGeometry.Stroke> strokes = SpellVisualGeometry.build(
                spell, elapsed, seedOf(beam.instanceId()), reach, quality);
        float fade = (float) (Math.min(1.0D, elapsed / 160.0D) * Math.min(1.0D, remaining / 260.0D));
        if (fade <= 0.01F) {
            return;
        }
        int base = beam.color() & 0xFFFFFF;
        for (SpellVisualGeometry.Stroke stroke : strokes) {
            int tint = shade(base, stroke.brightness());
            int alpha = Math.max(0, Math.min(255, Math.round(255.0F * stroke.alpha() * fade)));
            if (alpha == 0) {
                continue;
            }
            int argb = (alpha << 24) | tint;
            List<SpellVisualGeometry.Point> points = stroke.points();
            for (int index = 0; index < points.size() - 1; index++) {
                Vec3d from = world(beam.start(), dirX, dirZ, rightX, rightZ, points.get(index));
                Vec3d to = world(beam.start(), dirX, dirZ, rightX, rightZ, points.get(index + 1));
                emit(buffer, entry, from, to, stroke.halfWidths().get(index),
                        stroke.halfWidths().get(index + 1), argb);
            }
        }
    }

    private static Vec3d world(Vec3d origin, double dirX, double dirZ,
                               double rightX, double rightZ, SpellVisualGeometry.Point point) {
        return new Vec3d(
                origin.x + dirX * point.x() + rightX * point.z(),
                origin.y + point.y(),
                origin.z + dirZ * point.x() + rightZ * point.z());
    }

    /** One tapered, double-sided emissive ribbon segment. */
    private static void emit(VertexConsumer buffer, MatrixStack.Entry entry, Vec3d from, Vec3d to,
                             float halfWidthFrom, float halfWidthTo, int argb) {
        Vec3d delta = to.subtract(from);
        if (delta.lengthSquared() < 1.0E-8D) {
            return;
        }
        Vec3d direction = delta.normalize();
        Vec3d side = direction.crossProduct(UP);
        if (side.lengthSquared() < 1.0E-6D) {
            side = direction.crossProduct(new Vec3d(1.0D, 0.0D, 0.0D));
        }
        if (side.lengthSquared() < 1.0E-6D) {
            return;
        }
        side = side.normalize();
        Vec3d normal = side.crossProduct(direction).normalize();
        float wa = Math.max(0.01F, Math.min(0.30F, halfWidthFrom));
        float wb = Math.max(0.01F, Math.min(0.30F, halfWidthTo));
        Vec3d aLeft = from.subtract(side.multiply(wa));
        Vec3d aRight = from.add(side.multiply(wa));
        Vec3d bRight = to.add(side.multiply(wb));
        Vec3d bLeft = to.subtract(side.multiply(wb));
        quad(buffer, entry, aLeft, aRight, bRight, bLeft, normal, argb);
        quad(buffer, entry, bLeft, bRight, aRight, aLeft, normal.multiply(-1.0D), argb);
    }

    private static void quad(VertexConsumer buffer, MatrixStack.Entry entry, Vec3d a, Vec3d b,
                             Vec3d c, Vec3d d, Vec3d normal, int argb) {
        vertex(buffer, entry, a, 0.0F, 1.0F, normal, argb);
        vertex(buffer, entry, b, 1.0F, 1.0F, normal, argb);
        vertex(buffer, entry, c, 1.0F, 0.0F, normal, argb);
        vertex(buffer, entry, d, 0.0F, 0.0F, normal, argb);
    }

    private static void vertex(VertexConsumer buffer, MatrixStack.Entry entry, Vec3d point,
                               float u, float v, Vec3d normal, int argb) {
        buffer.vertex(entry, (float) point.x, (float) point.y, (float) point.z)
                .color(argb).texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV).light(FULL_BRIGHT)
                .normal(entry, (float) normal.x, (float) normal.y, (float) normal.z);
    }

    private static int shade(int rgb, float brightness) {
        double factor = Math.max(0.0D, Math.min(1.0D, brightness));
        int red = (int) Math.round(((rgb >> 16) & 0xFF) * factor);
        int green = (int) Math.round(((rgb >> 8) & 0xFF) * factor);
        int blue = (int) Math.round((rgb & 0xFF) * factor);
        return (red << 16) | (green << 8) | blue;
    }

    /** The render layer used for every authored stroke. */
    public static RenderLayer layer() {
        return RenderLayer.getEntityTranslucentEmissive(TEXTURE);
    }
}
