package me.copimine.client;

import java.util.Locale;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * Client-only decorative health bar for the End Rift Guardian.
 *
 * The server remains authoritative for health and phase.  This class only
 * renders a bounded snapshot received through the optional bridge, so a
 * missing client mod falls back to the ordinary Bukkit BossBar unchanged.
 */
public final class EndRiftBossBarHud {
    private static final Identifier FRAME = Identifier.of(
            "copimineclient", "textures/gui/end_rift_bossbar_frame.png");
    // The supplied frame is a 256x32 transparent overlay, not a 2172x724
    // panel.  Drawing it with the old dimensions sampled outside the image
    // and produced the oversized flat-purple bar seen in-game.
    private static final int SOURCE_WIDTH = 256;
    private static final int SOURCE_HEIGHT = 32;
    private static final int WIDTH = 320;
    private static final int HEIGHT = 88;
    private static final int TITLE_Y = 0;
    private static final int DETAIL_Y = 11;
    private static final int FRAME_Y = 22;
    private static final int FRAME_HEIGHT = 40;
    private static final int PHASE_LABEL_OFFSET = 40;
    private static final int CAST_LABEL_OFFSET = 74;
    private static final int INNER_LEFT = 21;
    private static final int INNER_RIGHT = 299;
    private static final int INNER_TOP = 34;
    private static final int INNER_BOTTOM = 50;
    private static final int SEGMENT_COUNT = 20;
    /** Health thresholds for the five transitions between six boss phases. */
    private static final float[] PHASE_MARKERS = {0.20F, 0.30F, 0.45F, 0.60F, 0.80F};
    private static final String[] PHASE_MARKER_LABELS = {"SEAL", "RAGE", "OVER", "RIFT", "HUNT"};

    static {
        if (PHASE_MARKERS.length == 5 && PHASE_MARKER_LABELS.length != PHASE_MARKERS.length) {
            throw new IllegalStateException("End Rift phase marker labels are out of sync");
        }
    }

    private EndRiftBossBarHud() {
    }

    public static void render(DrawContext context) {
        if (context == null) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null || client.currentScreen != null) {
            return;
        }
        EndEventClientState.BossBarState state = ClientBridgeProtocol.endBossBar();
        if (state == null || !ClientBridgeProtocol.endEventState().hasActiveBossBar()) {
            return;
        }

        int x = Math.max(0, (context.getScaledWindowWidth() - WIDTH) / 2);
        int y = 4;
        int phaseColor = phaseColor(state.phaseId(), state.castState());
        float progress = clampProgress(state.progress());

        // Opaque backing masks the vanilla bar for this one event while the
        // mixin below cancels its draw call. Other BossBars are untouched.
        context.fill(x + 18, y + 1, x + WIDTH - 18, y + 20, 0xD50A0D18);
        context.drawCenteredTextWithShadow(client.textRenderer, Text.literal("СТРАЖ РАЗЛОМА"),
                context.getScaledWindowWidth() / 2, y + TITLE_Y, 0xFFF6E8FF);
        context.drawTextWithShadow(client.textRenderer,
                Text.literal(phaseLabel(state.phaseId())), x + 22, y + DETAIL_Y, phaseColor);
        context.drawTextWithShadow(client.textRenderer,
                Text.literal(formatHealth(state.health(), state.maxHealth())),
                x + WIDTH - 22 - client.textRenderer.getWidth(formatHealth(state.health(), state.maxHealth())),
                y + DETAIL_Y, 0xFFE7E8F2);

        int filled = Math.round((INNER_RIGHT - INNER_LEFT) * progress);
        if (filled > 0) {
            context.fill(x + INNER_LEFT, y + INNER_TOP,
                    x + INNER_LEFT + filled, y + INNER_BOTTOM, 0xFF171526);
            drawSegmentedFill(context, x, y, filled, phaseColor);
            // A restrained highlight keeps the bar readable without spawning
            // particles or adding per-frame allocations.
            context.fill(x + INNER_LEFT, y + INNER_TOP,
                    x + INNER_LEFT + filled, y + INNER_TOP + 2, brighten(phaseColor));
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
            drawPhaseMarker(context, client, markerX, y + FRAME_Y,
                    PHASE_MARKER_LABELS[marker], progress <= PHASE_MARKERS[marker] + 0.001F,
                    phaseColor);
        }

        context.drawTexture(FRAME, x, y + FRAME_Y, 0, 0, WIDTH, FRAME_HEIGHT,
                SOURCE_WIDTH, SOURCE_HEIGHT);
        context.drawCenteredTextWithShadow(client.textRenderer,
                Text.literal(castLabel(state.castState())),
                context.getScaledWindowWidth() / 2, y + CAST_LABEL_OFFSET, 0xFFBEB8D5);
    }

    private static void drawSegmentedFill(DrawContext context, int x, int y,
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

    private static void drawPhaseMarker(DrawContext context, MinecraftClient client, int x,
                                        int frameY, String label, boolean active, int color) {
        int markerColor = active ? brighten(color) : 0xFF6D6880;
        context.fill(x - 1, frameY + 8, x + 1, frameY + 32, markerColor);
        context.fill(x - 3, frameY + 7, x + 3, frameY + 9, markerColor);
        context.drawCenteredTextWithShadow(client.textRenderer, Text.literal(label), x,
                frameY + PHASE_LABEL_OFFSET, active ? 0xFFF0E8FF : 0xFF8B879B);
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
            default -> "REAL HP · ФАЗОВЫЕ ПРЕДЕЛЫ";
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

    private static int brighten(int color) {
        int red = Math.min(255, ((color >> 16) & 0xFF) + 45);
        int green = Math.min(255, ((color >> 8) & 0xFF) + 45);
        int blue = Math.min(255, (color & 0xFF) + 45);
        return (color & 0xFF000000) | red << 16 | green << 8 | blue;
    }
}
