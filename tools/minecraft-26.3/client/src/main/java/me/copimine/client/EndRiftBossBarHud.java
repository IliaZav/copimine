package me.copimine.client;

import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Client-only decorative health bar for the End Rift Guardian.
 *
 * The server remains authoritative for health and phase.  This class only
 * renders a bounded snapshot received through the optional bridge, so a
 * missing client mod falls back to the ordinary Bukkit BossBar unchanged.
 */
public final class EndRiftBossBarHud {
    private static final Identifier FRAME = Identifier.fromNamespaceAndPath(
            "copimineclient", "textures/gui/end_rift_bossbar_frame_v3.png");
    private static final Identifier PORTRAIT = Identifier.fromNamespaceAndPath(
            "copimineclient", "textures/gui/end_rift_guardian_head_portrait.png");
    private static final Identifier PHASE_OVERLAY = Identifier.fromNamespaceAndPath(
            "copimineclient", "textures/gui/end_rift_guardian_phase_overlay.png");
    private static final Identifier PORTRAIT_AURA = Identifier.fromNamespaceAndPath(
            "copimineclient", "textures/gui/end_rift_guardian_portrait_aura.png");
    private static final Identifier STATE_GLYPHS = Identifier.fromNamespaceAndPath(
            "copimineclient", "textures/gui/end_rift_guardian_state_glyphs.png");
    // Actual RGBA frame dimensions; the head is separately rendered from
    // the original supplied mesh and UV, without repainting its identity. Keep texture sampling and fill geometry paired.
    private static final int SOURCE_WIDTH = 2172;
    private static final int SOURCE_HEIGHT = 724;
    private static final int WIDTH = 360;
    private static final int HEIGHT = 140;
    private static final int TITLE_Y = 1;
    private static final int DETAIL_Y = 61;
    private static final int FRAME_Y = 30;
    private static final int FRAME_HEIGHT = 120;
    private static final int PHASE_LABEL_OFFSET = 112;
    private static final int CAST_LABEL_OFFSET = 128;
    private static final int INNER_LEFT = 30;
    private static final int INNER_RIGHT = 330;
    private static final int INNER_TOP = 92;
    private static final int INNER_BOTTOM = 101;
    private static final int SEGMENT_COUNT = 20;
    /** Health thresholds for the five transitions between six boss phases. */
    private static final float[] PHASE_MARKERS = {0.20F, 0.30F, 0.45F, 0.60F, 0.80F};
    private static final String[] PHASE_MARKER_LABELS = {"SEAL", "RAGE", "OVER", "RIFT", "HUNT"};
    /** Pre-built text nodes: render() runs every frame and must not allocate. */
    private static final Component TITLE_COMPONENT = Component.literal("СТРАЖ РАЗЛОМА");
    private static final String[] PHASE_IDS =
            {"", "HUNT", "RIFT", "OVERLOAD", "RAGE", "LAST_SEAL"};
    private static final Component[] PHASE_COMPONENTS = new Component[PHASE_IDS.length];
    private static final Component[] MARKER_COMPONENTS = new Component[PHASE_MARKER_LABELS.length];
    private static final Component CAST_COMBAT = Component.literal(castLabel(""));
    private static final Component CAST_CHANNEL = Component.literal(castLabel("TELEGRAPHING"));
    private static final Component CAST_ATTACK = Component.literal(castLabel("EXECUTING"));
    private static final Component CAST_RECOVERY = Component.literal(castLabel("RECOVERY"));

    static {
        for (int i = 0; i < PHASE_COMPONENTS.length; i++) {
            PHASE_COMPONENTS[i] = Component.literal(phaseLabel(PHASE_IDS[i]));
        }
        for (int i = 0; i < MARKER_COMPONENTS.length; i++) {
            MARKER_COMPONENTS[i] = Component.literal(PHASE_MARKER_LABELS[i]);
        }
    }

    static {
        if (PHASE_MARKERS.length == 5 && PHASE_MARKER_LABELS.length != PHASE_MARKERS.length) {
            throw new IllegalStateException("End Rift phase marker labels are out of sync");
        }
    }

    /** Envelope of the trailing damage chip, in milliseconds. */
    private static final long DAMAGE_CHIP_MILLIS = 700L;
    /** Below this fraction the bar itself pulses, matching the boss's rage. */
    private static final float CRITICAL_FRACTION = 0.25F;
    /** One-shot sweep when the server accepts a new phase. */
    private static final long PHASE_SWEEP_MILLIS = 900L;
    /** Slow drift of the End-energy veins along the frame. */
    private static final long VEIN_PERIOD_MILLIS = 4200L;
    private static final int VEIN_COUNT = 5;
    private static String trackedBossIdentity = "";
    private static float lastProgress = Float.NaN;
    private static long lastProgressChangeMillis;
    private static float trailingProgress = Float.NaN;
    private static String lastPhaseId = "";
    private static long phaseChangedAtMillis;
    /** Cached health label; rebuilt only when the rounded server values change. */
    private static long cachedHealthKey = Long.MIN_VALUE;
    private static String cachedHealthText = "";
    private static Component cachedHealthComponent = CAST_COMBAT;
    /** Cached bar identity string; the instance and UUID only change on respawn. */
    private static String cachedTrackKey = "";
    private static String cachedInstanceId;
    private static String cachedBossUuid;

    private EndRiftBossBarHud() {
    }

    /** Wall clock independent of the world tick, so the HUD never drifts on pause. */
    private static long nowMillis() {
        return System.currentTimeMillis();
    }

    /**
     * Remember the previous health so a drop can be shown as a fading chip.
     *
     * <p>A boss bar that only ever shows the current value gives the party no
     * feedback that their burst connected. The chip is the difference between
     * the last drawn value and the current one, and it decays on its own
     * clock; it is presentation only and never feeds back into the server
     * authority.</p>
     */
    static void trackProgress(String identity, float progress) {
        if (!java.util.Objects.equals(trackedBossIdentity, identity)) {
            resetTrackingForTest();
            trackedBossIdentity = identity;
        }
        trackProgress(progress);
    }

    private static void trackProgress(float progress) {
        long now = nowMillis();
        if (Float.isNaN(lastProgress)) {
            lastProgress = progress;
            trailingProgress = progress;
            lastProgressChangeMillis = now;
            return;
        }
        if (progress < lastProgress - 0.0005F) {
            // The chip spans the whole burst: once a drop starts, the trailing
            // value is pinned to the highest health seen since the chip began,
            // so a second hit does not shrink the visible lost slice.
            trailingProgress = Float.isNaN(trailingProgress)
                    ? lastProgress : Math.max(trailingProgress, lastProgress);
            lastProgressChangeMillis = now;
        }
        lastProgress = progress;
        if (now - lastProgressChangeMillis > DAMAGE_CHIP_MILLIS) {
            trailingProgress = progress;
        }
    }

    public static void render(GuiGraphicsExtractor context) {
        if (context == null) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null || client.gui.screen() != null) {
            return;
        }
        EndEventClientState.BossBarState state = ClientBridgeProtocol.endBossBar();
        if (state == null || !ClientBridgeProtocol.endEventState().hasActiveBossBar()) {
            return;
        }

        float scale = Math.min(1.0F, Math.max(1, context.guiWidth() - 8) / (float) WIDTH);
        context.pose().pushMatrix();
        context.pose().scale(scale, scale);
        try {
        int x = Math.max(0, Math.round((context.guiWidth() / scale - WIDTH) / 2.0F));
        int y = 4;
        int phaseColor = phaseColor(state.phaseId(), state.castState());
        float progress = clampProgress(state.progress());
        int phase = phaseIndex(state.phaseId());
        boolean reduced = reducedMotion();
        long now = nowMillis();

        // Keep the ornate frame transparent over the world. The native bar
        // for this bound boss is independently suppressed by its own mixin.
        context.fill(x + INNER_LEFT, y + 2, x + INNER_RIGHT, y + 12, 0x680A0611);
        context.centeredText(client.font, TITLE_COMPONENT,
                x + (INNER_LEFT + INNER_RIGHT) / 2, y + TITLE_Y, 0xFFF6E8FF);
        // A silhouette-first state glyph sits before the phase word, so the
        // six states stay distinguishable without relying on colour alone.
        if (!reduced && (phase >= 4 || "EXECUTING".equals(state.castState()))) {
            // Danger states breathe behind the glyph; the glyph itself keeps a
            // stable silhouette so motion never reduces legibility.
            float pulse = 0.5F + 0.5F * (float) Math.sin(now / 420.0D);
            int glow = (int) (18.0F + 46.0F * pulse);
            context.fill(x + INNER_LEFT, y + DETAIL_Y - 3,
                    x + INNER_LEFT + 20, y + DETAIL_Y + 15,
                    (glow << 24) | (phaseColor & 0x00FFFFFF));
        }
        context.blit(RenderPipelines.GUI_TEXTURED, STATE_GLYPHS,
                x + INNER_LEFT + 2, y + DETAIL_Y - 2, phase * 16F, 0F,
                16, 16, 16, 16, 96, 16);
        context.text(client.font, PHASE_COMPONENTS[phase],
                x + INNER_LEFT + 22, y + DETAIL_Y, phaseColor);
        Component healthLabel = healthComponent(state.health(), state.maxHealth());
        context.text(client.font, healthLabel,
                x + INNER_RIGHT - 2 - client.font.width(cachedHealthText),
                y + DETAIL_Y, 0xFFE7E8F2);

        trackProgress(trackKey(state.instanceId(), state.bossUuid()), progress);
        String currentPhaseId = state.phaseId() == null ? "" : state.phaseId();
        if (!currentPhaseId.equals(lastPhaseId)) {
            // Trigger once per accepted server phase change. A late join starts
            // in the correct state with no replay of an old fanfare.
            if (!lastPhaseId.isEmpty()) {
                phaseChangedAtMillis = now;
            }
            lastPhaseId = currentPhaseId;
        }
        int filled = Math.round((INNER_RIGHT - INNER_LEFT) * progress);
        // Fading chip over the health the boss just lost: the party sees its
        // damage register even when the bar is already past a phase marker.
        int chipFilled = Math.round((INNER_RIGHT - INNER_LEFT) * trailingProgress);
        if (chipFilled > filled) {
            long age = nowMillis() - lastProgressChangeMillis;
            float fade = 1.0F - Math.min(1.0F, age / (float) DAMAGE_CHIP_MILLIS);
            if (fade > 0.0F) {
                int alpha = (int) (fade * 130.0F) & 0xFF;
                context.fill(x + INNER_LEFT + filled, y + INNER_TOP,
                        x + INNER_LEFT + chipFilled, y + INNER_BOTTOM,
                        (alpha << 24) | 0x00FF6B7A);
            }
        }
        if (filled > 0) {
            context.fill(x + INNER_LEFT, y + INNER_TOP,
                    x + INNER_LEFT + filled, y + INNER_BOTTOM, 0xFF171526);
            drawSegmentedFill(context, x, y, filled, phaseColor);
            // A restrained highlight keeps the bar readable without spawning
            // particles or adding per-frame allocations.
            context.fill(x + INNER_LEFT, y + INNER_TOP,
                    x + INNER_LEFT + filled, y + INNER_TOP + 2, brighten(phaseColor));
        }
        // Past the critical threshold the fill breathes, which is the one cue
        // that survives a player watching the boss instead of the bar.
        if (progress > 0.0F && progress <= CRITICAL_FRACTION && filled > 0 && !reduced) {
            float pulse = 0.5F + 0.5F * (float) Math.sin(nowMillis() / 620.0D);
            int glowAlpha = (int) (30.0F + 70.0F * pulse) & 0xFF;
            context.fill(x + INNER_LEFT, y + INNER_TOP - 1,
                    x + INNER_LEFT + filled, y + INNER_TOP,
                    (glowAlpha << 24) | 0x00FF4F61);
            context.fill(x + INNER_LEFT, y + INNER_BOTTOM,
                    x + INNER_LEFT + filled, y + INNER_BOTTOM + 1,
                    (glowAlpha << 24) | 0x00FF4F61);
        }
        for (int notch = 1; notch < SEGMENT_COUNT; notch++) {
            int notchX = x + INNER_LEFT + Math.round(
                    (INNER_RIGHT - INNER_LEFT) * notch / (float) SEGMENT_COUNT);
            context.fill(notchX, y + INNER_TOP - 1, notchX + 1,
                    y + INNER_BOTTOM + 1, 0xA0080914);
        }
        for (int marker = 0; marker < PHASE_MARKERS.length; marker++) {
            int markerX = x + INNER_LEFT + Math.round(
                    (INNER_RIGHT - INNER_LEFT) * PHASE_MARKERS[marker]);
            drawPhaseMarker(context, client, markerX, y,
                    MARKER_COMPONENTS[marker], progress <= PHASE_MARKERS[marker] + 0.001F,
                    phaseColor);
        }
        // Phase transition sweep: one pass, triggered only by an accepted
        // server phase change, never by render-call count.
        if (!reduced && phaseChangedAtMillis > 0L) {
            long age = now - phaseChangedAtMillis;
            if (age >= 0L && age < PHASE_SWEEP_MILLIS) {
                float t = age / (float) PHASE_SWEEP_MILLIS;
                int innerWidth = INNER_RIGHT - INNER_LEFT;
                int sweepX = x + INNER_LEFT + Math.round(innerWidth * t);
                int alpha = Math.max(0, (int) ((1.0F - t) * 110.0F));
                context.fill(x + INNER_LEFT, y + INNER_TOP - 1, x + INNER_RIGHT,
                        y + INNER_BOTTOM + 1, (alpha / 3 << 24) | (phaseColor & 0x00FFFFFF));
                context.fill(sweepX - 2, y + INNER_TOP - 3, sweepX + 3,
                        y + INNER_BOTTOM + 3, (alpha << 24) | (phaseColor & 0x00FFFFFF));
            }
        }

        context.blit(RenderPipelines.GUI_TEXTURED, FRAME, x, y + FRAME_Y,
                0F, 0F, WIDTH, FRAME_HEIGHT,
                SOURCE_WIDTH, SOURCE_HEIGHT, SOURCE_WIDTH, SOURCE_HEIGHT);
        // Phase-specific metal, obsidian, veins and cracks. The atlas cell is
        // sampled with an explicit source rect so atlas height never stretches
        // the artwork.
        context.blit(RenderPipelines.GUI_TEXTURED, PHASE_OVERLAY, x, y,
                0F, phase * (float) HEIGHT, WIDTH, HEIGHT,
                WIDTH, HEIGHT, WIDTH, HEIGHT * 6);
        if (!reduced) {
            // Restrained End-energy drift along the frame. Wall-clock driven,
            // five thin veins, and it never crosses the health track recess.
            float drift = (now % VEIN_PERIOD_MILLIS) / (float) VEIN_PERIOD_MILLIS;
            for (int vein = 0; vein < VEIN_COUNT; vein++) {
                float offset = (drift + vein / (float) VEIN_COUNT) % 1.0F;
                int veinX = x + 44 + Math.round((WIDTH - 88) * offset);
                float breathe = 0.5F + 0.5F * (float) Math.sin(now / 700.0D + vein);
                int alpha = (int) (12.0F + 26.0F * breathe);
                context.fill(veinX, y + 36, veinX + 1, y + INNER_TOP - 2,
                        (alpha << 24) | (phaseColor & 0x00FFFFFF));
                context.fill(veinX, y + INNER_BOTTOM + 3, veinX + 1,
                        y + FRAME_Y + FRAME_HEIGHT - 8,
                        (alpha << 24) | (phaseColor & 0x00FFFFFF));
            }
        }
        // Exact UV-rendered head of the supplied 37-cube model; no replacement creature.
        int portraitY = y + 14 + (reduced ? 0 : portraitBob(now));
        context.blit(RenderPipelines.GUI_TEXTURED, PORTRAIT, x + (WIDTH - 44) / 2, portraitY,
                0F, 0F, 44, 44, 128, 128, 128, 128);
        // A phase aura over the real head: the centre stays transparent, so the
        // supplied portrait remains the identity.
        context.blit(RenderPipelines.GUI_TEXTURED, PORTRAIT_AURA,
                x + (WIDTH - 44) / 2, portraitY,
                phase * 64F, 0F, 44, 44,
                64, 64, 64 * 6, 64);
        context.centeredText(client.font, castComponent(state.castState()),
                x + (INNER_LEFT + INNER_RIGHT) / 2, y + CAST_LABEL_OFFSET, 0xFFBEB8D5);
        } finally {
            context.pose().popMatrix();
        }
    }

    private static void drawSegmentedFill(GuiGraphicsExtractor context, int x, int y,
                                          int filled, int color) {
        int innerWidth = INNER_RIGHT - INNER_LEFT;
        for (int segment = 0; segment < SEGMENT_COUNT; segment++) {
            // Do not truncate the 278px inner track to 260px by using an
            // integer segment width.  Rounded boundaries distribute the
            // remainder across the twenty segments and make full health
            // reach the frame on both sides.
            int segmentLeft = Math.round(innerWidth * segment / (float) SEGMENT_COUNT);
            int segmentRight = Math.round(innerWidth * (segment + 1) / (float) SEGMENT_COUNT);
            int left = x + INNER_LEFT + segmentLeft;
            int right = Math.min(x + INNER_LEFT + filled,
                    x + INNER_LEFT + segmentRight);
            if (right > left) {
                context.fill(left, y + INNER_TOP, right, y + INNER_BOTTOM, color);
            }
        }
    }

    private static void drawPhaseMarker(GuiGraphicsExtractor context, Minecraft client, int x,
                                        int frameY, Component label, boolean active, int color) {
        int markerColor = active ? brighten(color) : 0xFF6D6880;
        context.fill(x - 1, frameY + INNER_TOP - 2, x + 1, frameY + INNER_BOTTOM + 2, markerColor);
        context.fill(x - 3, frameY + INNER_TOP - 3, x + 3, frameY + INNER_TOP - 1, markerColor);
        context.centeredText(client.font, label, x,
                frameY + PHASE_LABEL_OFFSET, active ? 0xFFF0E8FF : 0xFF8B879B);
    }

    /** Cached health label; formatted once per distinct rounded value. */
    private static Component healthComponent(double health, double maxHealth) {
        double safeHealth = Math.max(0.0D, Double.isFinite(health) ? health : 0.0D);
        double safeMax = Math.max(1.0D, Double.isFinite(maxHealth) ? maxHealth : 1.0D);
        long roundedHealth = Math.round(Math.min(safeHealth, safeMax));
        long roundedMax = Math.round(safeMax);
        long key = (roundedHealth << 32) ^ (roundedMax & 0xFFFFFFFFL);
        if (key != cachedHealthKey) {
            cachedHealthKey = key;
            cachedHealthText = formatHealth(safeHealth, safeMax);
            cachedHealthComponent = Component.literal(cachedHealthText);
        }
        return cachedHealthComponent;
    }

    /** Cached bar identity; rebuilt only when the server rotates the instance. */
    private static String trackKey(String instanceId, String bossUuid) {
        if (!java.util.Objects.equals(instanceId, cachedInstanceId)
                || !java.util.Objects.equals(bossUuid, cachedBossUuid)) {
            cachedInstanceId = instanceId;
            cachedBossUuid = bossUuid;
            cachedTrackKey = String.valueOf(instanceId) + ":" + bossUuid;
        }
        return cachedTrackKey;
    }

    private static Component castComponent(String castState) {
        return switch (castState == null ? "" : castState) {
            case "TELEGRAPHING" -> CAST_CHANNEL;
            case "EXECUTING" -> CAST_ATTACK;
            case "RECOVERY" -> CAST_RECOVERY;
            default -> CAST_COMBAT;
        };
    }

    private static String formatHealth(double health, double maxHealth) {
        double safeHealth = Math.max(0.0D, Double.isFinite(health) ? health : 0.0D);
        double safeMax = Math.max(1.0D, Double.isFinite(maxHealth) ? maxHealth : 1.0D);
        return String.format(Locale.ROOT, "%.0f / %.0f", Math.min(safeHealth, safeMax), safeMax);
    }

    private static String phaseLabel(String phaseId) {
        return switch (phaseId == null ? "" : phaseId) {
            case "HUNT" -> "ОХОТА";
            case "RIFT" -> "РАЗЛОМ";
            case "OVERLOAD" -> "ПЕРЕГРУЗКА";
            case "RAGE" -> "ЯРОСТЬ";
            case "LAST_SEAL" -> "ПОСЛЕДНЯЯ ПЕЧАТЬ";
            default -> "ПРОБУЖДЕНИЕ";
        };
    }

    private static String castLabel(String castState) {
        return switch (castState == null ? "" : castState) {
            case "TELEGRAPHING" -> "КАНАЛИЗАЦИЯ";
            case "EXECUTING" -> "АТАКА";
            case "RECOVERY" -> "ВОССТАНОВЛЕНИЕ";
            default -> "БОЙ";
        };
    }

    private static float clampProgress(double value) {
        return (float) Math.max(0.0D, Math.min(1.0D,
                Double.isFinite(value) ? value : 0.0D));
    }

    private static int phaseColor(String phaseId, String castState) {
        if ("EXECUTING".equals(castState)) {
            return 0xFFE33D62;
        }
        if ("TELEGRAPHING".equals(castState)) {
            return 0xFFFFC857;
        }
        if ("RECOVERY".equals(castState)) {
            return 0xFFB8B8C8;
        }
        return switch (phaseId) {
            case "AWAKENING" -> 0xFF9A62FF;
            case "HUNT" -> 0xFF42C9FF;
            case "RIFT" -> 0xFFF34CDB;
            case "OVERLOAD" -> 0xFFFFC857;
            case "RAGE" -> 0xFFFF4F61;
            case "LAST_SEAL" -> 0xFFF4F4FF;
            default -> 0xFFB56CFF;
        };
    }

    // Test seams: the renderer itself needs a live GuiGraphics context, but the
    // chip arithmetic and the phase thresholds are plain numbers and are the
    // parts that can actually regress unnoticed.
    static void resetTrackingForTest() {
        trackedBossIdentity = "";
        lastProgress = Float.NaN;
        trailingProgress = Float.NaN;
        lastProgressChangeMillis = 0L;
        lastPhaseId = "";
        phaseChangedAtMillis = 0L;
    }

    /** Index into the six-frame phase overlay/aura/glyph atlases. */
    static int phaseIndex(String phaseId) {
        return switch (phaseId == null ? "" : phaseId) {
            case "HUNT" -> 1;
            case "RIFT" -> 2;
            case "OVERLOAD" -> 3;
            case "RAGE" -> 4;
            case "LAST_SEAL" -> 5;
            default -> 0;
        };
    }

    private static int portraitBob(long now) {
        return (int) Math.round(Math.sin(now / 900.0D));
    }

    private static boolean reducedMotion() {
        ClientConfig config = CopiMineClient.config();
        return config != null && config.reducedMotion();
    }

    static void trackProgressForTest(float progress) {
        trackProgress(progress);
    }

    static float trailingProgressForTest() {
        return trailingProgress;
    }

    static float clampProgressForTest(double value) {
        return clampProgress(value);
    }

    static float[] phaseMarkersForTest() {
        return PHASE_MARKERS.clone();
    }

    private static int brighten(int color) {
        int red = Math.min(255, ((color >> 16) & 0xFF) + 45);
        int green = Math.min(255, ((color >> 8) & 0xFF) + 45);
        int blue = Math.min(255, (color & 0xFF) + 45);
        return (color & 0xFF000000) | red << 16 | green << 8 | blue;
    }
}
