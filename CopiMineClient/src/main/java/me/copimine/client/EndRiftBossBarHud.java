package me.copimine.client;

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
    // The artwork is intentionally kept at a dense source resolution and
    // drawn smaller than native.  This keeps the crystals and bone filigree
    // clean on both a 854x480 test client and larger screens.
    private static final int SOURCE_WIDTH = 2172;
    private static final int SOURCE_HEIGHT = 724;
    private static final int WIDTH = 384;
    private static final int HEIGHT = 128;
    private static final int INNER_LEFT = 54;
    private static final int INNER_RIGHT = 330;
    private static final int INNER_TOP = 48;
    private static final int INNER_BOTTOM = 80;

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

        // Opaque backing masks the vanilla bar for this one event while the
        // mixin below cancels its draw call. Other BossBars are untouched.
        context.fill(x + INNER_LEFT - 2, y + INNER_TOP - 2,
                x + INNER_RIGHT + 2, y + INNER_BOTTOM + 2, 0xE50A0D18);
        context.fill(x + INNER_LEFT, y + INNER_TOP,
                x + INNER_RIGHT, y + INNER_BOTTOM, 0xFF171526);

        int filled = Math.round((INNER_RIGHT - INNER_LEFT) * state.progress());
        if (filled > 0) {
            context.fill(x + INNER_LEFT, y + INNER_TOP,
                    x + INNER_LEFT + filled, y + INNER_BOTTOM, phaseColor);
            // A restrained highlight keeps the bar readable without spawning
            // particles or adding per-frame allocations.
            context.fill(x + INNER_LEFT, y + INNER_TOP,
                    x + INNER_LEFT + filled, y + INNER_TOP + 2, brighten(phaseColor));
        }
        int notchStep = (INNER_RIGHT - INNER_LEFT) / 10;
        for (int notch = 1; notch < 10; notch++) {
            int notchX = x + INNER_LEFT + notch * notchStep;
            context.fill(notchX, y + INNER_TOP + 1, notchX + 1,
                    y + INNER_BOTTOM - 1, 0x6A080914);
        }

        context.drawTexture(FRAME, x, y, 0, 0, WIDTH, HEIGHT,
                SOURCE_WIDTH, SOURCE_HEIGHT);

        String title = "СТРАЖ РАЗЛОМА";
        String phase = phaseLabel(state.phaseId(), state.castState());
        String health = phase + "  •  " + state.health() + " / " + state.maxHealth() + " HP";
        context.drawCenteredTextWithShadow(client.textRenderer, Text.literal(title),
                context.getScaledWindowWidth() / 2, y + 42, 0xFFF6E8FF);
        context.drawCenteredTextWithShadow(client.textRenderer, Text.literal(health),
                context.getScaledWindowWidth() / 2, y + 59, 0xFFFFFFFF);
    }

    private static String phaseLabel(String phaseId, String castState) {
        return switch (castState) {
            case "JUDGMENT_CAST" -> "СУД РАЗЛОМА";
            case "ABSORPTION_CHANNEL" -> "ПОГЛОЩЕНИЕ";
            case "EXHAUSTED" -> "ИСТОЩЕНИЕ";
            default -> switch (phaseId) {
                case "AWAKENING" -> "ПРОБУЖДЕНИЕ";
                case "HUNTER" -> "ОХОТА";
                case "DISTORTION" -> "ИСКАЖЕНИЕ";
                case "ABSORPTION" -> "ПОГЛОЩЕНИЕ";
                case "CATASTROPHE" -> "КАТАСТРОФА";
                default -> "РАЗЛОМ";
            };
        };
    }

    private static int phaseColor(String phaseId, String castState) {
        if ("JUDGMENT_CAST".equals(castState)) {
            return 0xFFE33D62;
        }
        if ("ABSORPTION_CHANNEL".equals(castState)) {
            return 0xFFFFC857;
        }
        if ("EXHAUSTED".equals(castState)) {
            return 0xFFB8B8C8;
        }
        return switch (phaseId) {
            case "AWAKENING" -> 0xFF9A62FF;
            case "HUNTER" -> 0xFF42C9FF;
            case "DISTORTION" -> 0xFFF34CDB;
            case "ABSORPTION" -> 0xFFFFC857;
            case "CATASTROPHE" -> 0xFFFF4F61;
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
