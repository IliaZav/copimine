package me.copimine.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Draws the authored V5.1 spell meshes from a single server cue (26.3 client).
 *
 * <p>It consumes the bounded {@code v51-<spell>-<seed>} world cue, derives the
 * whole rotating arm / jaw / shell geometry locally and never mutates state.
 * The server remains the only owner of hitboxes, damage, targets and cleanup;
 * this renderer only disappears when the cue disappears.</p>
 *
 * <p>Each ribbon samples the authored arc texture across its width (bright core,
 * dark feathered edges) and runs a travelling brightness wave along its length,
 * so the mesh reads as flowing energy instead of a static decal.</p>
 */
public final class SpellVisualRenderer {

    public static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(
            "copimineclient", "textures/entity/end_rift_spell_arc.png");

    private static final int FULL_BRIGHT = 0x00F000F0;
    private static final Vec3 UP = new Vec3(0.0D, 1.0D, 0.0D);
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

    /** The render type used for every authored stroke. */
    public static RenderType layer() {
        return RenderTypes.entityTranslucentEmissive(TEXTURE);
    }

    /**
     * Submit one cue's quads. The caller's {@code matrices} already carry the
     * camera translation, so the emitted points are absolute world positions.
     */
    public static void draw(PoseStack matrices, VertexConsumer buffer,
                            EndEventWorldVfxManager.BeamSnapshot beam, long now) {
        if (matrices == null || buffer == null || beam == null
                || beam.start() == null || beam.end() == null) {
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
        PoseStack.Pose pose = matrices.last();
        // One global phase makes the wave travel along every stroke in unison;
        // the style offset keeps neighbouring families from pulsing as clones.
        double flowPhase = Math.toRadians(elapsed * 0.42D);
        for (SpellVisualGeometry.Stroke stroke : strokes) {
            int tint = shade(base, stroke.brightness());
            int alpha = Math.max(0, Math.min(255, Math.round(255.0F * stroke.alpha() * fade)));
            if (alpha == 0) {
                continue;
            }
            double stylePhase = flowPhase - stroke.style().ordinal() * 0.9D;
            List<SpellVisualGeometry.Point> points = stroke.points();
            for (int index = 0; index < points.size() - 1; index++) {
                Vec3 from = world(beam.start(), dirX, dirZ, rightX, rightZ, points.get(index));
                Vec3 to = world(beam.start(), dirX, dirZ, rightX, rightZ, points.get(index + 1));
                float pulseFrom = pulse(index, stylePhase);
                float pulseTo = pulse(index + 1, stylePhase);
                int argbFrom = (scaleAlpha(alpha, pulseFrom) << 24) | tint;
                int argbTo = (scaleAlpha(alpha, pulseTo) << 24) | tint;
                emit(buffer, pose, from, to, stroke.halfWidths().get(index),
                        stroke.halfWidths().get(index + 1), argbFrom, argbTo);
            }
        }
    }

    private static float pulse(int vertexIndex, double phase) {
        return (float) (0.80D + 0.20D * Math.sin(vertexIndex * 0.85D - phase));
    }

    private static int scaleAlpha(int alpha, float pulse) {
        int scaled = Math.round(alpha * Math.max(0.0F, Math.min(1.0F, pulse)));
        return Math.max(0, Math.min(255, scaled));
    }

    private static Vec3 world(Vec3 origin, double dirX, double dirZ,
                              double rightX, double rightZ, SpellVisualGeometry.Point point) {
        return new Vec3(
                origin.x + dirX * point.x() + rightX * point.z(),
                origin.y + point.y(),
                origin.z + dirZ * point.x() + rightZ * point.z());
    }

    /** One tapered, double-sided emissive ribbon segment. */
    private static void emit(VertexConsumer buffer, PoseStack.Pose pose, Vec3 from, Vec3 to,
                             float halfWidthFrom, float halfWidthTo, int argbFrom, int argbTo) {
        Vec3 delta = to.subtract(from);
        if (delta.lengthSqr() < 1.0E-8D) {
            return;
        }
        Vec3 direction = delta.normalize();
        Vec3 side = direction.cross(UP);
        if (side.lengthSqr() < 1.0E-6D) {
            side = direction.cross(new Vec3(1.0D, 0.0D, 0.0D));
        }
        if (side.lengthSqr() < 1.0E-6D) {
            return;
        }
        side = side.normalize();
        Vec3 normal = side.cross(direction).normalize();
        float wa = Math.max(0.01F, Math.min(0.30F, halfWidthFrom));
        float wb = Math.max(0.01F, Math.min(0.30F, halfWidthTo));
        Vec3 aLeft = from.subtract(side.scale(wa));
        Vec3 aRight = from.add(side.scale(wa));
        Vec3 bRight = to.add(side.scale(wb));
        Vec3 bLeft = to.subtract(side.scale(wb));
        quad(buffer, pose, aLeft, aRight, bRight, bLeft, normal, argbFrom, argbTo);
        quad(buffer, pose, bLeft, bRight, aRight, aLeft, normal.scale(-1.0D), argbTo, argbFrom);
    }

    /**
     * Emit a double-sided quad whose {@code u} runs along the stroke length and
     * {@code v} spans the ribbon width. The authored texture is a bright core
     * band with feathered edges, so this mapping draws a clean glowing ribbon.
     */
    private static void quad(VertexConsumer buffer, PoseStack.Pose pose, Vec3 a, Vec3 b,
                             Vec3 c, Vec3 d, Vec3 normal, int argbStart, int argbEnd) {
        vertex(buffer, pose, a, 0.02F, 0.02F, normal, argbStart);
        vertex(buffer, pose, b, 0.02F, 0.98F, normal, argbStart);
        vertex(buffer, pose, c, 0.98F, 0.98F, normal, argbEnd);
        vertex(buffer, pose, d, 0.98F, 0.02F, normal, argbEnd);
    }

    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, Vec3 point,
                               float u, float v, Vec3 normal, int argb) {
        buffer.addVertex(pose, (float) point.x, (float) point.y, (float) point.z)
                .setColor(argb).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT)
                .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
    }

    private static int shade(int rgb, float brightness) {
        double factor = Math.max(0.0D, Math.min(1.0D, brightness));
        int red = (int) Math.round(((rgb >> 16) & 0xFF) * factor);
        int green = (int) Math.round(((rgb >> 8) & 0xFF) * factor);
        int blue = (int) Math.round((rgb & 0xFF) * factor);
        return (red << 16) | (green << 8) | blue;
    }
}
