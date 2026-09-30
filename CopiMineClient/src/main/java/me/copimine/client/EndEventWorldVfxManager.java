package me.copimine.client;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Main-thread-owned world VFX for the End Rift event.
 *
 * The server sends endpoints and a short lifetime.  This manager only draws
 * the received geometry; it never decides damage, targets, phases or hits.
 * Entries are intentionally few, short-lived and generation-scoped so a
 * stale packet cannot leave a beam behind after a reset or world change.
 */
public final class EndEventWorldVfxManager {
    static final int MAX_ACTIVE_BEAMS = 64;
    private static final int MAX_KEY_LENGTH = 96;
    private static final int MAX_DIMENSION_LENGTH = 48;
    private static final long MAX_BEAM_LIFETIME_MILLIS = 2_000L;
    private static final float MIN_WIDTH = 0.02F;
    private static final float MAX_WIDTH = 0.45F;
    private static final double MAX_COORDINATE = 30_000_000.0D;

    private final Map<String, Beam> beams = new LinkedHashMap<>();
    private String eventId = "";
    private long generation;

    /**
     * Apply one server-authored beam packet.  The wire format is:
     *
     * <pre>
     * mode        = dimension|beam-key|x,y,z
     * clearPolicy = x,y,z|RRGGBB
     * intensity   = line width in blocks
     * </pre>
     */
    public synchronized boolean applyBeam(BridgePayload payload, long nowMillis) {
        if (payload == null || nowMillis < 0L
                || !Objects.equals(payload.type(), ClientBridgeProtocol.TYPE_END_EVENT_PREFIX + "END_WORLD_BEAM")) {
            return false;
        }
        if (!acceptEnvelope(payload.sessionId(), payload.seq())) {
            return false;
        }
        String[] startParts = split(payload.mode(), 3);
        String[] endParts = split(payload.clearPolicy(), 2);
        if (startParts == null || endParts == null
                || !validDimension(startParts[0])
                || !validKey(startParts[1])) {
            return false;
        }
        Vec3d start = parsePoint(startParts[2]);
        Vec3d end = parsePoint(endParts[0]);
        int color = parseColor(endParts[1]);
        if (start == null || end == null || color < 0) {
            return false;
        }
        float width = payload.intensity();
        if (!Float.isFinite(width)) {
            return false;
        }
        width = Math.max(MIN_WIDTH, Math.min(MAX_WIDTH, width));
        long lifetime = Math.max(1L, Math.min(MAX_BEAM_LIFETIME_MILLIS,
                payload.durationMillis()));
        if (payload.clientVersion().isBlank() || payload.clientVersion().length() > MAX_KEY_LENGTH) {
            return false;
        }
        if (beams.size() >= MAX_ACTIVE_BEAMS && !beams.containsKey(payload.clientVersion())) {
            return false;
        }
        beams.put(payload.clientVersion(), new Beam(
                payload.clientVersion(), startParts[0], start, end, color, width,
                nowMillis, nowMillis + lifetime));
        return true;
    }

    /** Remove one server-owned beam instance after event/phase cleanup. */
    public synchronized boolean applyClear(BridgePayload payload, long nowMillis) {
        if (payload == null || nowMillis < 0L
                || !Objects.equals(payload.type(), ClientBridgeProtocol.TYPE_END_EVENT_PREFIX + "END_WORLD_VFX_CLEAR")) {
            return false;
        }
        if (!acceptEnvelope(payload.sessionId(), payload.seq())) {
            return false;
        }
        String instance = payload.clientVersion();
        if (instance.isBlank()) {
            return false;
        }
        return beams.remove(instance) != null;
    }

    /** Expire short-lived entries; called on the client tick thread. */
    public synchronized void tick(long nowMillis) {
        if (nowMillis < 0L) {
            return;
        }
        Iterator<Map.Entry<String, Beam>> iterator = beams.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue().expiresAtMillis() <= nowMillis) {
                iterator.remove();
            }
        }
    }

    /** Clear all event geometry on disconnect, world change or local reset. */
    public synchronized void clear() {
        beams.clear();
        eventId = "";
        generation = 0L;
    }

    /** Clear all geometry for the current event without retaining stale ids. */
    public synchronized void clearEvent(String expectedEventId, long expectedGeneration) {
        if (expectedEventId == null || expectedEventId.isBlank()
                || !Objects.equals(eventId, expectedEventId)
                || expectedGeneration < generation) {
            return;
        }
        beams.clear();
    }

    public synchronized int activeBeamCount() {
        return beams.size();
    }

    public synchronized List<BeamSnapshot> snapshots() {
        List<BeamSnapshot> result = new ArrayList<>();
        for (Beam beam : beams.values()) {
            result.add(new BeamSnapshot(beam.instanceId(), beam.dimension(), beam.start(),
                    beam.end(), beam.color(), beam.width(), beam.expiresAtMillis()));
        }
        return Collections.unmodifiableList(result);
    }

    /** Render continuous world-space ribbons; no dense particle line is used. */
    public synchronized void render(WorldRenderContext context) {
        if (context == null || context.world() == null || beams.isEmpty()) {
            return;
        }
        long nowMillis = System.currentTimeMillis();
        tick(nowMillis);
        if (beams.isEmpty() || context.consumers() == null || context.matrixStack() == null) {
            return;
        }
        String dimension = dimensionId(context);
        if (dimension.isBlank()) {
            return;
        }
        Camera camera = context.camera();
        if (camera == null || camera.getPos() == null) {
            return;
        }
        MatrixStack matrices = context.matrixStack();
        VertexConsumerProvider consumers = context.consumers();
        VertexConsumer buffer = consumers.getBuffer(RenderLayer.getLines());
        Vec3d cameraPos = camera.getPos();
        matrices.push();
        matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        MatrixStack.Entry entry = matrices.peek();
        for (Beam beam : beams.values()) {
            if (!dimension.equals(beam.dimension())) {
                continue;
            }
            drawRibbon(buffer, entry, beam, nowMillis);
        }
        matrices.pop();
    }

    private boolean acceptEnvelope(String incomingEventId, long incomingGeneration) {
        if (incomingEventId == null || incomingEventId.isBlank() || incomingGeneration <= 0L) {
            return false;
        }
        if (eventId.isBlank() || !Objects.equals(eventId, incomingEventId)
                || incomingGeneration > generation) {
            beams.clear();
            eventId = incomingEventId;
            generation = incomingGeneration;
            return true;
        }
        return incomingGeneration >= generation;
    }

    private static String[] split(String raw, int expectedParts) {
        if (raw == null || raw.length() > MAX_KEY_LENGTH + 160) {
            return null;
        }
        String[] parts = raw.split("\\|", -1);
        if (parts.length != expectedParts) {
            return null;
        }
        for (String part : parts) {
            if (part == null || part.isBlank()) {
                return null;
            }
        }
        return parts;
    }

    private static boolean validDimension(String dimension) {
        return dimension != null && !dimension.isBlank()
                && dimension.length() <= MAX_DIMENSION_LENGTH
                && dimension.matches("[a-z0-9_:-]+");
    }

    private static boolean validKey(String key) {
        return key != null && !key.isBlank() && key.length() <= MAX_KEY_LENGTH
                && key.matches("[A-Za-z0-9_-]+");
    }

    private static Vec3d parsePoint(String raw) {
        if (raw == null || raw.length() > 96) {
            return null;
        }
        String[] values = raw.split(",", -1);
        if (values.length != 3) {
            return null;
        }
        try {
            double x = Double.parseDouble(values[0]);
            double y = Double.parseDouble(values[1]);
            double z = Double.parseDouble(values[2]);
            if (!finiteCoordinate(x) || !finiteCoordinate(y) || !finiteCoordinate(z)) {
                return null;
            }
            return new Vec3d(x, y, z);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static boolean finiteCoordinate(double value) {
        return Double.isFinite(value) && Math.abs(value) <= MAX_COORDINATE;
    }

    private static int parseColor(String raw) {
        if (raw == null || !raw.matches("[0-9A-Fa-f]{6}")) {
            return -1;
        }
        try {
            return Integer.parseInt(raw, 16);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static String dimensionId(WorldRenderContext context) {
        String path = context.world().getRegistryKey().getValue().getPath();
        return path == null ? "" : path.toLowerCase(Locale.ROOT);
    }

    private static void drawRibbon(VertexConsumer buffer, MatrixStack.Entry entry,
                                   Beam beam, long nowMillis) {
        Vec3d delta = beam.end().subtract(beam.start());
        double length = delta.length();
        if (!Double.isFinite(length) || length < 0.01D) {
            return;
        }
        Vec3d direction = delta.multiply(1.0D / length);
        Vec3d reference = Math.abs(direction.y) < 0.92D
                ? new Vec3d(0.0D, 1.0D, 0.0D)
                : new Vec3d(1.0D, 0.0D, 0.0D);
        Vec3d side = direction.crossProduct(reference).normalize().multiply(beam.width());
        Vec3d other = direction.crossProduct(side.normalize()).normalize().multiply(beam.width() * 0.72D);
        int red = (beam.color() >> 16) & 0xFF;
        int green = (beam.color() >> 8) & 0xFF;
        int blue = beam.color() & 0xFF;
        long ageMillis = Math.max(0L, nowMillis - beam.startedAtMillis());
        long remainingMillis = Math.max(0L, beam.expiresAtMillis() - nowMillis);
        float fadeIn = Math.min(1.0F, ageMillis / 120.0F);
        float fadeOut = Math.min(1.0F, remainingMillis / 180.0F);
        float alphaScale = Math.max(0.08F, fadeIn * fadeOut);
        drawFadedLine(buffer, entry, beam.start(), beam.end(), red, green, blue,
                scaleAlpha(235, alphaScale));
        drawFadedLine(buffer, entry, beam.start().add(side), beam.end().add(side),
                red, green, blue, scaleAlpha(150, alphaScale * 0.9F));
        drawFadedLine(buffer, entry, beam.start().subtract(side), beam.end().subtract(side),
                red, green, blue, scaleAlpha(150, alphaScale * 0.9F));
        drawFadedLine(buffer, entry, beam.start().add(other), beam.end().add(other),
                red, green, blue, scaleAlpha(120, alphaScale * 0.8F));
        drawFadedLine(buffer, entry, beam.start().subtract(other), beam.end().subtract(other),
                red, green, blue, scaleAlpha(120, alphaScale * 0.8F));

        // A narrow bright pulse travels along the continuous ribbon.  This is
        // a procedural flow cue, not a chain of particle points, and remains
        // bounded to one extra line per active beam.
        double flowPhase = Math.floorMod(nowMillis - beam.startedAtMillis(), 900L) / 900.0D;
        double flowCenter = flowPhase * length;
        double flowHalf = Math.min(0.55D, Math.max(0.12D, length * 0.14D));
        double flowStart = Math.max(0.0D, flowCenter - flowHalf);
        double flowEnd = Math.min(length, flowCenter + flowHalf);
        if (flowEnd - flowStart > 0.01D) {
            Vec3d flowFrom = beam.start().add(direction.multiply(flowStart));
            Vec3d flowTo = beam.start().add(direction.multiply(flowEnd));
            drawLine(buffer, entry, flowFrom, flowTo, 225, 255, 255,
                    scaleAlpha(245, alphaScale));
        }
    }

    private static void drawFadedLine(VertexConsumer buffer, MatrixStack.Entry entry,
                                      Vec3d start, Vec3d end, int red, int green,
                                      int blue, int alpha) {
        Vec3d delta = end.subtract(start);
        double length = delta.length();
        if (!Double.isFinite(length) || length < 0.01D) {
            return;
        }
        if (length < 0.20D) {
            drawLine(buffer, entry, start, end, red, green, blue, alpha);
            return;
        }
        Vec3d direction = delta.multiply(1.0D / length);
        double edge = Math.min(0.32D, length * 0.16D);
        Vec3d innerStart = start.add(direction.multiply(edge));
        Vec3d innerEnd = end.subtract(direction.multiply(edge));
        drawLine(buffer, entry, start, innerStart, red, green, blue,
                scaleAlpha(alpha, 0.12F));
        drawLine(buffer, entry, innerStart, innerEnd, red, green, blue, alpha);
        drawLine(buffer, entry, innerEnd, end, red, green, blue,
                scaleAlpha(alpha, 0.12F));
    }

    private static int scaleAlpha(int alpha, float scale) {
        if (!Float.isFinite(scale)) {
            return 0;
        }
        return Math.max(0, Math.min(255, Math.round(alpha * Math.max(0.0F, Math.min(1.0F, scale)))));
    }

    private static void drawLine(VertexConsumer buffer, MatrixStack.Entry entry,
                                 Vec3d start, Vec3d end, int red, int green, int blue, int alpha) {
        buffer.vertex(entry, (float) start.x, (float) start.y, (float) start.z)
                .color(red, green, blue, Math.max(0, Math.min(255, alpha)))
                .normal(entry, 0.0F, 1.0F, 0.0F);
        buffer.vertex(entry, (float) end.x, (float) end.y, (float) end.z)
                .color(red, green, blue, Math.max(0, Math.min(255, alpha)))
                .normal(entry, 0.0F, 1.0F, 0.0F);
    }

    public record BeamSnapshot(String instanceId, String dimension, Vec3d start,
                               Vec3d end, int color, float width, long expiresAtMillis) {
    }

    private record Beam(String instanceId, String dimension, Vec3d start, Vec3d end,
                        int color, float width, long startedAtMillis, long expiresAtMillis) {
    }
}
