package me.copimine.client;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.joml.Quaternionf;

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
    // Server instance = UUID + ':' + positive long generation + ':world:'
    // + a sanitized key of at most 64 characters (at most 127 in total).
    private static final int MAX_INSTANCE_LENGTH = 128;
    private static final int MAX_RETIRED_EVENTS = 64;
    private static final int MAX_INSTANCE_VERSIONS = 256;
    private static final int MAX_DIMENSION_LENGTH = 48;
    private static final long MAX_BEAM_LIFETIME_MILLIS = 2_000L;
    private static final float MIN_WIDTH = 0.02F;
    private static final float MAX_WIDTH = 0.45F;
    private static final double MAX_COORDINATE = 30_000_000.0D;

    private final Map<String, Beam> beams = new LinkedHashMap<>();
    private record InstanceVersion(long timestampMillis, boolean cleared) { }
    private final Map<String, InstanceVersion> instanceVersions = new LinkedHashMap<>();
    private String eventId = "";
    private long generation;
    private long clearedGeneration;
    private long lastEnvelopeTimestamp;
    private long resumedAtTimestamp;
    private boolean locallyCleared;
    private final LinkedHashSet<String> retiredEvents = new LinkedHashSet<>();

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
        if (payload == null || nowMillis < 0L || payload.timestampMillis() <= 0L
                || !Objects.equals(payload.messageType(), ClientBridgeProtocol.TYPE_END_EVENT_PREFIX + "END_WORLD_BEAM")) {
            return false;
        }
        String[] startParts = split(payload.mode(), 3);
        String[] endParts = split(payload.clearPolicy(), 2);
        if (startParts == null || endParts == null
                || !validDimension(startParts[0])
                || !validKey(startParts[1])) {
            return false;
        }
        Vec3 start = parsePoint(startParts[2]);
        Vec3 end = parsePoint(endParts[0]);
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
        if (payload.clientVersion().isBlank() || payload.clientVersion().length() > MAX_INSTANCE_LENGTH) {
            return false;
        }
        if (!acceptEnvelope(payload.sessionId(), payload.seq())) return false;
        if (payload.timestampMillis() < resumedAtTimestamp) return false;
        InstanceVersion version = instanceVersions.get(payload.clientVersion());
        if (version != null && payload.timestampMillis() <= version.timestampMillis()) return false;
        if (!reserveInstanceVersion(payload.clientVersion())) return false;
        if (beams.size() >= MAX_ACTIVE_BEAMS && !beams.containsKey(payload.clientVersion())) {
            return false;
        }
        Beam previous = beams.get(payload.clientVersion());
        long started = previous == null || previous.expiresAtMillis() <= nowMillis
                ? nowMillis : previous.startedAtMillis();
        boolean interpolate = (startParts[1].equals("wave1-carrier-objective")
                || startParts[1].startsWith("wave6-ritual-"))
                && previous != null && previous.expiresAtMillis() > nowMillis
                && previous.dimension().equals(startParts[0])
                && previous.start().distanceToSqr(start) <= 16
                && previous.end().distanceToSqr(end) <= 16;
        beams.put(payload.clientVersion(), new Beam(
                payload.clientVersion(), startParts[0], start, end, color, width,
                started, nowMillis + lifetime, interpolate ? previous.sampleStart(nowMillis) : start,
                interpolate ? previous.sampleEnd(nowMillis) : end, nowMillis));
        instanceVersions.put(payload.clientVersion(), new InstanceVersion(payload.timestampMillis(), false));
        lastEnvelopeTimestamp = Math.max(lastEnvelopeTimestamp, payload.timestampMillis());
        return true;
    }

    /** Remove one server-owned beam instance after event/phase cleanup. */
    public synchronized boolean applyClear(BridgePayload payload, long nowMillis) {
        if (payload == null || nowMillis < 0L || payload.timestampMillis() <= 0L
                || !Objects.equals(payload.messageType(), ClientBridgeProtocol.TYPE_END_EVENT_PREFIX + "END_WORLD_VFX_CLEAR")) {
            return false;
        }
        String instance = payload.clientVersion();
        if (instance.isBlank() || instance.length() > MAX_INSTANCE_LENGTH
                || !Objects.equals(payload.sessionId(), eventId)
                || payload.seq() != generation) {
            return false;
        }
        InstanceVersion version = instanceVersions.get(instance);
        if (version != null && (payload.timestampMillis() < version.timestampMillis()
                || version.cleared() && payload.timestampMillis() == version.timestampMillis())) return false;
        if (!reserveInstanceVersion(instance)) return false;
        instanceVersions.put(instance, new InstanceVersion(payload.timestampMillis(), true));
        lastEnvelopeTimestamp = Math.max(lastEnvelopeTimestamp, payload.timestampMillis());
        return beams.remove(instance) != null;
    }

    private boolean reserveInstanceVersion(String instance) {
        if (instanceVersions.containsKey(instance) || instanceVersions.size() < MAX_INSTANCE_VERSIONS) return true;
        // Do not evict a clear fence and thereby re-admit queued packets. A new generation or transport resets it.
        beams.clear();
        clearedGeneration = Math.max(clearedGeneration, generation);
        locallyCleared = false;
        return false;
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

    /** Terminal local clear retains the fence against already queued packets. */
    public synchronized void clear() {
        beams.clear();
        locallyCleared = locallyCleared || generation > clearedGeneration;
        clearedGeneration = Math.max(clearedGeneration, generation);
    }

    public synchronized boolean resumeAfterLocalExit(String expectedEventId, long expectedGeneration, long timestamp) {
        if (!locallyCleared || !Objects.equals(expectedEventId, eventId) || expectedGeneration != generation
                || timestamp <= lastEnvelopeTimestamp) return false;
        clearedGeneration = 0L;
        locallyCleared = false;
        resumedAtTimestamp = timestamp;
        lastEnvelopeTimestamp = timestamp;
        instanceVersions.clear();
        return true;
    }

    /** A new network connection has no packets from the previous connection. */
    public synchronized void reset() {
        beams.clear();
        instanceVersions.clear();
        eventId = "";
        generation = 0L;
        clearedGeneration = 0L;
        lastEnvelopeTimestamp = 0L;
        resumedAtTimestamp = 0L;
        locallyCleared = false;
        retiredEvents.clear();
    }

    /** Clear all geometry for the current event without retaining stale ids. */
    public synchronized void clearEvent(String expectedEventId, long expectedGeneration) {
        if (expectedEventId == null || expectedEventId.isBlank()
                || !Objects.equals(eventId, expectedEventId)
                || expectedGeneration < generation) {
            return;
        }
        beams.clear();
        generation = expectedGeneration;
        clearedGeneration = Math.max(clearedGeneration, expectedGeneration);
        locallyCleared = false;
    }

    public synchronized int activeBeamCount() {
        return beams.size();
    }

    public synchronized List<BeamSnapshot> snapshots() {
        return snapshots(Long.MAX_VALUE);
    }

    public synchronized List<BeamSnapshot> snapshots(long nowMillis) {
        List<BeamSnapshot> result = new ArrayList<>();
        for (Beam beam : beams.values()) {
            result.add(new BeamSnapshot(beam.instanceId(), beam.dimension(), beam.sampleStart(nowMillis),
                    beam.sampleEnd(nowMillis), beam.color(), beam.width(), beam.startedAtMillis(), beam.expiresAtMillis()));
        }
        return Collections.unmodifiableList(result);
    }

    /** Render continuous world-space ribbons; no dense particle line is used. */
    public synchronized void render(LevelRenderContext context) {
        Minecraft client = Minecraft.getInstance();
        if (context == null || client.level == null || beams.isEmpty()) {
            return;
        }
        long nowMillis = System.currentTimeMillis();
        tick(nowMillis);
        if (beams.isEmpty()) return;
        String dimension = dimensionId(client.level);
        if (dimension.isBlank()) {
            return;
        }
        Camera camera = client.gameRenderer.mainCamera();
        if (camera == null || !camera.isInitialized()) return;
        Vec3 cameraPos = camera.position();
        Quaternionf cameraRotation = new Quaternionf(camera.rotation());
        Map<RitualSpellPresentationPolicy.WorldPass, List<BeamSnapshot>> batches = new LinkedHashMap<>();
        RitualSpellPresentationPolicy.drawWorldPasses(snapshots(nowMillis), dimension, nowMillis, pass -> {
            List<BeamSnapshot> batch = new ArrayList<>();
            batches.put(pass, batch);
            return batch::add;
        });
        for (var entry : batches.entrySet()) {
            RitualSpellPresentationPolicy.WorldPass pass = entry.getKey();
            List<BeamSnapshot> batch = List.copyOf(entry.getValue());
            RenderType layer = switch (pass.kind()) {
                case RIBBON -> RenderTypes.lines();
                case CHANNEL -> RenderTypes.entityTranslucentEmissive(
                        Identifier.withDefaultNamespace("textures/entity/end_crystal/end_crystal_beam.png"));
                case RITUAL -> RenderTypes.lightning();
                case GLYPH -> RenderTypes.entityTranslucentEmissive(spellTexture(pass.spell()));
                case WAVE -> RenderTypes.entityTranslucentEmissive(
                        Identifier.fromNamespaceAndPath("copimineclient", "textures/entity/wave_combat_glyphs.png"));
            };
            EndRiftRenderSubmission.submit(context, cameraPos, layer, (matrices, buffer) -> {
                PoseStack.Pose entryPose = matrices.last();
                for (BeamSnapshot beam : batch) {
                    switch (pass.kind()) {
                        case RIBBON -> drawRibbon(buffer, entryPose, beam, nowMillis);
                        case CHANNEL -> drawRitualBeam(buffer, entryPose, beam, nowMillis, true);
                        case RITUAL -> drawRitualBeam(buffer, entryPose, beam, nowMillis);
                        case GLYPH -> drawSpellGlyph(buffer, matrices, cameraPos, cameraRotation, beam, nowMillis);
                        case WAVE -> drawWaveCombatGlyph(buffer, matrices, cameraPos, cameraRotation, beam, nowMillis);
                    }
                }
            });
        }
    }

    /** Target-only screen packets share the world manager's lifetime, generation and cleanup fences. */
    public void renderHud(GuiGraphicsExtractor context) {
        Minecraft client = Minecraft.getInstance();
        if (context == null || client.player == null || client.level == null || client.player.isDeadOrDying()
                || client.gui.screen() != null || client.gui.hud.isHidden()) return;
        long now = System.currentTimeMillis();
        var beam = RitualSpellPresentationPolicy.screenCue(snapshots(),
                client.level.dimension().identifier().getPath(), now);
        if (beam == null) return;
        var cue = RitualSpellPresentationPolicy.parse(beam.instanceId());
        var prisonerLayout = PrisonerHudLayout.compute(context.guiWidth(), context.guiHeight(),
                Math.max(client.player.getMaxHealth(), client.player.getHealth()), client.player.getAbsorptionAmount());
        List<PrisonerHudLayout.Rect> reserved = new ArrayList<>();
        reserved.add(prisonerLayout.vanillaHud());
        if (ClientBridgeProtocol.prisonerHud().activeFor(client.player.getUUID())) reserved.add(prisonerLayout.bounds());
        var layout = RitualSpellPresentationPolicy.screenLayout(
                context.guiWidth(), context.guiHeight(), reserved);
        if (!layout.visible()) return;
        int alpha = RitualSpellPresentationPolicy.screenAlpha(cue.stage(), beam.startedAtMillis(), beam.expiresAtMillis(), now);
        if (alpha == 0) return;
        for (var edge : layout.edgeCues()) {
            context.fill(edge.left(), edge.top(), edge.right(), edge.bottom(), (alpha << 24) | beam.color());
        }
        var panel = layout.panel();
        int labelAlpha = Math.min(255, alpha * 7);
        context.fill(panel.left(), panel.top(), panel.right(), panel.top() + 1,
                (labelAlpha << 24) | beam.color());
        context.fill(panel.left() + 2, panel.top() + 3, panel.left() + 24, panel.top() + 25,
                (labelAlpha << 24) | beam.color());
        context.blit(RenderPipelines.GUI_TEXTURED, spellTexture(cue.spell()), panel.left() + 2, panel.top() + 3,
                0F, 0F, 20, 20, 256, 256, 256, 256);
        String title = (cue.stage() == RitualSpellPresentationPolicy.Stage.WARNING ? "Готовится: " : "Активно: ")
                + cue.spell().title();
        title = client.font.plainSubstrByWidth(title, panel.right() - panel.left() - 30);
        context.text(client.font, title, panel.left() + 27, panel.top() + 9,
                (labelAlpha << 24) | 0xF1E8FF);
    }

    private static Identifier spellTexture(RitualSpellPresentationPolicy.Spell spell) {
        return Identifier.fromNamespaceAndPath("copimineclient", spell.texture());
    }

    private static void drawWaveCombatGlyph(VertexConsumer buffer, PoseStack matrices,
                                             Vec3 cameraPosition, Quaternionf cameraRotation,
                                             BeamSnapshot beam, long now) {
        var cue = WaveCombatPresentationPolicy.parse(beam.instanceId());
        if (cue == null || !WaveCombatPresentationPolicy.visible(cue, cameraPosition.distanceToSqr(beam.start()))) return;
        int alpha = WaveCombatPresentationPolicy.alpha(cue, beam.startedAtMillis(), beam.expiresAtMillis(), now);
        if (alpha == 0) return;
        Vec3 delta = beam.end().subtract(beam.start());
        float width = (float) WaveCombatPresentationPolicy.laneHalfWidth(beam.width());
        float length = (float) WaveCombatPresentationPolicy.laneLength(Math.sqrt(delta.x*delta.x+delta.z*delta.z));
        boolean billboard = cue.shape() == WaveCombatPresentationPolicy.Shape.BILLBOARD;
        if (cue.shape() == WaveCombatPresentationPolicy.Shape.FLOOR) {
            width = cue.ability().equals("pulse") ? length > .01F ? Math.min(5,length) : 5 : 2.1F;
            length = width*2;
        }
        matrices.pushPose();
        try {
            Vec3 origin = cue.shape() == WaveCombatPresentationPolicy.Shape.FLOOR && !cue.ability().equals("pulse")
                    ? beam.end() : beam.start();
            matrices.translate(origin.x, origin.y + (billboard ? .3 : .06), origin.z);
            if (billboard) matrices.rotate(cameraRotation);
            else if (cue.shape() == WaveCombatPresentationPolicy.Shape.LANE)
                matrices.rotate(com.mojang.math.Axis.YP.rotation((float)Math.atan2(delta.x,delta.z)));
            float near = cue.shape() == WaveCombatPresentationPolicy.Shape.FLOOR ? -length/2 : 0;
            float far = cue.shape() == WaveCombatPresentationPolicy.Shape.FLOOR ? length/2 : Math.max(.5F,length);
            float[][] points = billboard ? new float[][]{{-.55F,-.55F,0},{.55F,-.55F,0},{.55F,.55F,0},{-.55F,.55F,0}}
                    : new float[][]{{-width,0,near},{-width,0,far},{width,0,far},{width,0,near}};
            float u0=cue.tile()/8F, u1=(cue.tile()+1)/8F;
            float[][] uv={{u0,1},{u0,0},{u1,0},{u1,1}};
            for(int i=0;i<4;i++) {
                buffer.addVertex(matrices.last(),points[i][0],points[i][1],points[i][2])
                        .setColor((alpha<<24)|beam.color()).setUv(uv[i][0],uv[i][1])
                        .setOverlay(OverlayTexture.NO_OVERLAY).setLight(0x00F000F0)
                        .setNormal(matrices.last(),0,billboard?0:1,billboard?1:0);
            }
        } finally { matrices.popPose(); }
        float impactWidth = WaveCombatPresentationPolicy.impactHalfWidth(cue);
        if (impactWidth > 0 && cue.shape() == WaveCombatPresentationPolicy.Shape.LANE) {
            matrices.pushPose();
            try {
                matrices.translate(beam.end().x, beam.end().y + .07, beam.end().z);
                float[][] corners={{-impactWidth,0,-impactWidth},{-impactWidth,0,impactWidth},
                        {impactWidth,0,impactWidth},{impactWidth,0,-impactWidth}};
                float[][] uv={{0,1},{0,0},{.125F,0},{.125F,1}};
                for (int i=0;i<4;i++) buffer.addVertex(matrices.last(),corners[i][0],0,corners[i][2])
                        .setColor((alpha<<24)|beam.color()).setUv(uv[i][0],uv[i][1])
                        .setOverlay(OverlayTexture.NO_OVERLAY).setLight(0x00F000F0).setNormal(matrices.last(),0,1,0);
            } finally { matrices.popPose(); }
        }
    }

    private static void drawSpellGlyph(VertexConsumer buffer, PoseStack matrices,
                                       Vec3 cameraPosition, Quaternionf cameraRotation,
                                       BeamSnapshot beam, long now) {
        var cue = RitualSpellPresentationPolicy.parse(beam.instanceId());
        if (!RitualSpellPresentationPolicy.glyphVisibleFrom(cue, cameraPosition.distanceToSqr(beam.start()))) return;
        int alpha = RitualSpellPresentationPolicy.glyphAlpha(cue, beam.startedAtMillis(), beam.expiresAtMillis(), now);
        if (alpha == 0) return;
        boolean billboard = cue.spell().orientation() == RitualSpellPresentationPolicy.Orientation.BILLBOARD;
        matrices.pushPose();
        try {
            matrices.translate(beam.start().x, beam.start().y, beam.start().z);
            if (billboard) matrices.rotate(cameraRotation);
            for (var point : RitualSpellPresentationPolicy.glyphQuad(cue)) {
                buffer.addVertex(matrices.last(), point.x(), point.y(), point.z())
                        .setColor((alpha << 24) | beam.color()).setUv(point.u(), point.v())
                        .setOverlay(OverlayTexture.NO_OVERLAY).setLight(0x00F000F0)
                        .setNormal(matrices.last(), 0, billboard ? 0 : 1, billboard ? 1 : 0);
            }
        } finally {
            matrices.popPose();
        }
    }

    private boolean acceptEnvelope(String incomingEventId, long incomingGeneration) {
        if (incomingEventId == null || incomingEventId.isBlank()
                || incomingEventId.length() > 128 || incomingGeneration <= 0L
                || retiredEvents.contains(incomingEventId)) {
            return false;
        }
        if (!eventId.isBlank() && !Objects.equals(eventId, incomingEventId)) {
            // Fail closed at the bounded session-history cap. Reset only when
            // the transport changes; evicting old ids could admit old packets.
            if (retiredEvents.size() >= MAX_RETIRED_EVENTS) return false;
            retiredEvents.add(eventId);
            clearedGeneration = 0L;
            generation = 0L;
        }
        if (eventId.isBlank() || !Objects.equals(eventId, incomingEventId)
                || incomingGeneration > generation) {
            beams.clear();
            instanceVersions.clear();
            lastEnvelopeTimestamp = 0L;
            resumedAtTimestamp = 0L;
            locallyCleared = false;
            eventId = incomingEventId;
            generation = incomingGeneration;
            return true;
        }
        return incomingGeneration >= generation && incomingGeneration > clearedGeneration;
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

    private static Vec3 parsePoint(String raw) {
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
            return new Vec3(x, y, z);
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

    private static String dimensionId(net.minecraft.client.multiplayer.ClientLevel level) {
        String path = level.dimension().identifier().getPath();
        return path == null ? "" : path.toLowerCase(Locale.ROOT);
    }

    private static void drawRibbon(VertexConsumer buffer, PoseStack.Pose entry,
                                   BeamSnapshot beam, long nowMillis) {
        Vec3 delta = beam.end().subtract(beam.start());
        double length = delta.length();
        if (!Double.isFinite(length) || length < 0.01D) {
            return;
        }
        Vec3 direction = delta.scale(1.0D / length);
        Vec3 reference = Math.abs(direction.y) < 0.92D
                ? new Vec3(0.0D, 1.0D, 0.0D)
                : new Vec3(1.0D, 0.0D, 0.0D);
        Vec3 side = direction.cross(reference).normalize().scale(beam.width());
        Vec3 other = direction.cross(side.normalize()).normalize().scale(beam.width() * 0.72D);
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
            Vec3 flowFrom = beam.start().add(direction.scale(flowStart));
            Vec3 flowTo = beam.start().add(direction.scale(flowEnd));
            drawLine(buffer, entry, flowFrom, flowTo, 225, 255, 255,
                    scaleAlpha(245, alphaScale));
        }
    }

    private static void drawRitualBeam(VertexConsumer buffer, PoseStack.Pose entry, BeamSnapshot beam, long nowMillis) {
        drawRitualBeam(buffer, entry, beam, nowMillis, false);
    }

    private static void drawRitualBeam(VertexConsumer buffer, PoseStack.Pose entry, BeamSnapshot beam,
                                       long nowMillis, boolean textured) {
        long age = Math.max(0L, nowMillis - beam.startedAtMillis());
        long remaining = Math.max(0L, beam.expiresAtMillis() - nowMillis);
        float fade = Math.min(1.0F, age / 120.0F) * Math.min(1.0F, remaining / 180.0F);
        int red = (beam.color() >> 16) & 255, green = (beam.color() >> 8) & 255, blue = beam.color() & 255;
        for (RitualBeamMesh.Quad quad : RitualBeamMesh.quads(beam.start(), beam.end(), beam.width(), age)) {
            float white = switch (quad.layer()) { case GLOW -> 0; case BODY -> .20F; case CORE -> .78F; case FLOW -> .50F; };
            int alpha = scaleAlpha(switch (quad.layer()) { case GLOW -> 40; case BODY -> 95; case CORE -> 220; case FLOW -> 190; }, fade);
            int r = Math.round(red + (255 - red) * white);
            int g = Math.round(green + (255 - green) * white);
            int b = Math.round(blue + (255 - blue) * white);
            Vec3 normal = quad.b().position().subtract(quad.a().position())
                    .cross(quad.d().position().subtract(quad.a().position())).normalize();
            for (RitualBeamMesh.Vertex vertex : List.of(quad.a(), quad.b(), quad.c(), quad.d())) {
                Vec3 point = vertex.position();
                buffer.addVertex(entry, (float) point.x, (float) point.y, (float) point.z)
                        .setColor(r, g, b, alpha);
                if (textured) buffer.setUv(vertex.u(), vertex.v())
                        .setOverlay(OverlayTexture.NO_OVERLAY).setLight(0x00F000F0)
                        .setNormal(entry, (float) normal.x, (float) normal.y, (float) normal.z);
            }
        }
    }

    private static void drawFadedLine(VertexConsumer buffer, PoseStack.Pose entry,
                                      Vec3 start, Vec3 end, int red, int green,
                                      int blue, int alpha) {
        Vec3 delta = end.subtract(start);
        double length = delta.length();
        if (!Double.isFinite(length) || length < 0.01D) {
            return;
        }
        if (length < 0.20D) {
            drawLine(buffer, entry, start, end, red, green, blue, alpha);
            return;
        }
        Vec3 direction = delta.scale(1.0D / length);
        double edge = Math.min(0.32D, length * 0.16D);
        Vec3 innerStart = start.add(direction.scale(edge));
        Vec3 innerEnd = end.subtract(direction.scale(edge));
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

    private static void drawLine(VertexConsumer buffer, PoseStack.Pose entry,
                                 Vec3 start, Vec3 end, int red, int green, int blue, int alpha) {
        Vec3 delta = end.subtract(start);
        double length = delta.length();
        if (!Double.isFinite(length) || length < 1.0E-6D) return;
        Vec3 direction = delta.scale(1.0D / length);
        int boundedAlpha = Math.max(0, Math.min(255, alpha));
        buffer.addVertex(entry, (float) start.x, (float) start.y, (float) start.z)
                .setColor(red, green, blue, boundedAlpha)
                .setNormal(entry, (float) direction.x, (float) direction.y, (float) direction.z)
                .setLineWidth(2.0F);
        buffer.addVertex(entry, (float) end.x, (float) end.y, (float) end.z)
                .setColor(red, green, blue, boundedAlpha)
                .setNormal(entry, (float) direction.x, (float) direction.y, (float) direction.z)
                .setLineWidth(2.0F);
    }

    public record BeamSnapshot(String instanceId, String dimension, Vec3 start,
                               Vec3 end, int color, float width, long startedAtMillis, long expiresAtMillis) {
    }

    private record Beam(String instanceId, String dimension, Vec3 start, Vec3 end,
                        int color, float width, long startedAtMillis, long expiresAtMillis,
                        Vec3 previousStart, Vec3 previousEnd, long updatedAtMillis) {
        double fraction(long now) { return Math.max(0, Math.min(1, (now - updatedAtMillis) / 150.0)); }
        Vec3 sampleStart(long now) { return previousStart.lerp(start, fraction(now)); }
        Vec3 sampleEnd(long now) { return previousEnd.lerp(end, fraction(now)); }
    }
}
