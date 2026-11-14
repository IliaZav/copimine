package me.copimine.client;

import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.BossEvent;

/**
 * Authored boss bar for the Wave 7 minibosses.
 *
 * <p>The server creates a real {@code BossBar} per miniboss so that every
 * player, with or without this mod, sees correct progress. This class only
 * repaints that same event: it recognises the miniboss by its authored title,
 * cancels the vanilla body and draws the matching frame and portrait instead.
 * Nothing here decides health, targeting or lifetime.</p>
 *
 * <p>Identity strings are duplicated from
 * {@code me.copimine.endevent.domain.Wave7MinibossBossBarPolicy} on purpose: the
 * client must never depend on server domain classes at runtime, and a contract
 * test pins both copies to the same literals.</p>
 */
public final class Wave7MinibossBossBarHud {
    /** Skeleton marksman pair: bone with End-corrupted marrow. */
    private static final String BONE_TITLE = "Костяной Стрелок Разлома";
    /** The trial Warden. Deliberately not "Хранитель Разлома": that title belongs
     *  to the Guardian bar, and reusing it would make the HUD suppressor hide one
     *  bar as if it were the other. */
    private static final String SCULK_TITLE = "Хранитель Бездны";
    /** The Rift Archmage. */
    private static final String RUNE_TITLE = "Архимаг Разлома";
    /** A player's personal Echo. */
    private static final String MIRROR_TITLE = "Эхо Разлома";

    private static final Identifier FRAME_BONE = texture("wave7_bossbar_bone");
    private static final Identifier FRAME_SCULK = texture("wave7_bossbar_sculk");
    private static final Identifier FRAME_RUNE = texture("wave7_bossbar_rune");
    private static final Identifier FRAME_MIRROR = texture("wave7_bossbar_mirror");
    private static final Identifier PORTRAIT_MARKSMAN = texture("wave7_portrait_marksman");
    private static final Identifier PORTRAIT_WARDEN = texture("wave7_portrait_warden");
    private static final Identifier PORTRAIT_ARCHMAGE = texture("wave7_portrait_archmage");
    private static final Identifier PORTRAIT_ECHO = texture("wave7_portrait_echo");

    private static final int FRAME_WIDTH = 256;
    private static final int FRAME_HEIGHT = 32;
    private static final int WIDTH = 182;
    private static final int HEIGHT = 32;
    private static final int INNER_LEFT = 10;
    private static final int INNER_RIGHT = 172;
    private static final int INNER_TOP = 12;
    private static final int INNER_BOTTOM = 20;
    private static final int SEGMENT_COUNT = 10;
    private static final int PORTRAIT_SIZE = 20;
    /** Below this fraction the fill breathes, matching the server's critical band. */
    private static final float CRITICAL_FRACTION = 0.25F;

    private Wave7MinibossBossBarHud() {
    }

    private static Identifier texture(String name) {
        return Identifier.fromNamespaceAndPath("copimineclient", "textures/gui/" + name + ".png");
    }

    /** Which authored bar a title belongs to, or {@code null} for anyone else's bar. */
    static String styleForTitle(String title) {
        if (title == null || title.isBlank()) {
            return null;
        }
        String trimmed = title.trim();
        if (trimmed.startsWith(BONE_TITLE)) return "bone";
        if (trimmed.startsWith(SCULK_TITLE)) return "sculk";
        if (trimmed.startsWith(RUNE_TITLE)) return "rune";
        if (trimmed.startsWith(MIRROR_TITLE)) return "mirror";
        return null;
    }

    private static Identifier frame(String style) {
        return switch (style) {
            case "bone" -> FRAME_BONE;
            case "sculk" -> FRAME_SCULK;
            case "rune" -> FRAME_RUNE;
            default -> FRAME_MIRROR;
        };
    }

    private static Identifier portrait(String style) {
        return switch (style) {
            case "bone" -> PORTRAIT_MARKSMAN;
            case "sculk" -> PORTRAIT_WARDEN;
            case "rune" -> PORTRAIT_ARCHMAGE;
            default -> PORTRAIT_ECHO;
        };
    }

    private static int accent(String style) {
        return switch (style) {
            case "bone" -> 0xFF9A62FF;
            case "sculk" -> 0xFF37E0D0;
            case "rune" -> 0xFFF34CDB;
            default -> 0xFFBFE9FF;
        };
    }

    private static int glow(String style) {
        return switch (style) {
            case "bone" -> 0xFF5A2E8C;
            case "sculk" -> 0xFF0C6B7A;
            case "rune" -> 0xFF7A2BD6;
            default -> 0xFF42C9FF;
        };
    }

    static float clampProgress(double value) {
        if (!Double.isFinite(value)) return 0.0F;
        return (float) Math.max(0.0D, Math.min(1.0D, value));
    }

    static boolean critical(float progress) {
        return progress > 0.0F && progress <= CRITICAL_FRACTION;
    }

    static int notchX(int index, int count) {
        int inner = INNER_RIGHT - INNER_LEFT;
        return Math.round(inner * index / (float) count);
    }

    /**
     * Draw the authored bar for this boss event.
     *
     * @return true when the event was a Wave 7 miniboss and the caller must
     *         suppress the vanilla body and title.
     */
    public static boolean render(GuiGraphicsExtractor gui, int ignoredX, int y, BossEvent bar) {
        if (gui == null || bar == null || bar.getName() == null) {
            return false;
        }
        String style = styleForTitle(bar.getName().getString());
        if (style == null) {
            return false;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null || client.font == null) {
            return false;
        }
        float progress = clampProgress(bar.getProgress());
        int x = Math.max(0, (gui.guiWidth() - WIDTH) / 2);
        int fillColor = accent(style);
        int innerWidth = INNER_RIGHT - INNER_LEFT;
        int filled = Math.round(innerWidth * progress);

        // Track, then the lost slice in the deep accent so a hit reads even
        // when the bar is already low.
        gui.fill(x + INNER_LEFT, y + INNER_TOP, x + INNER_RIGHT, y + INNER_BOTTOM, glow(style));
        gui.fill(x + INNER_LEFT, y + INNER_TOP, x + INNER_LEFT + filled, y + INNER_BOTTOM, 0xFF141120);

        drawSegments(gui, x, y, filled, fillColor);
        if (filled > 0) {
            gui.fill(x + INNER_LEFT, y + INNER_TOP, x + INNER_LEFT + filled, y + INNER_TOP + 1,
                    brighten(fillColor));
        }
        if (critical(progress) && filled > 0) {
            float pulse = 0.5F + 0.5F * (float) Math.sin(System.currentTimeMillis() / 620.0D);
            int alpha = (int) (30.0F + 70.0F * pulse) & 0xFF;
            gui.fill(x + INNER_LEFT, y + INNER_TOP - 1, x + INNER_LEFT + filled, y + INNER_TOP,
                    (alpha << 24) | (fillColor & 0x00FFFFFF));
        }
        for (int notch = 1; notch < SEGMENT_COUNT; notch++) {
            int notchX = x + INNER_LEFT + notchX(notch, SEGMENT_COUNT);
            gui.fill(notchX, y + INNER_TOP - 1, notchX + 1, y + INNER_BOTTOM + 1, 0xA0080914);
        }

        gui.blit(RenderPipelines.GUI_TEXTURED, frame(style), x, y,
                0F, 0F, WIDTH, HEIGHT, FRAME_WIDTH, FRAME_HEIGHT, FRAME_WIDTH, FRAME_HEIGHT);
        gui.blit(RenderPipelines.GUI_TEXTURED, portrait(style),
                x + INNER_LEFT, y + (HEIGHT - PORTRAIT_SIZE) / 2 - 1,
                0F, 0F, PORTRAIT_SIZE, PORTRAIT_SIZE, 128, 128, 128, 128);
        gui.centeredText(client.font, bar.getName(),
                x + WIDTH / 2 + PORTRAIT_SIZE / 2, y - 10, 0xFFF2ECFA);
        return true;
    }

    private static void drawSegments(GuiGraphicsExtractor gui, int x, int y,
                                     int filled, int color) {
        for (int segment = 0; segment < SEGMENT_COUNT; segment++) {
            int left = x + INNER_LEFT + notchX(segment, SEGMENT_COUNT);
            int right = Math.min(x + INNER_LEFT + filled,
                    x + INNER_LEFT + notchX(segment + 1, SEGMENT_COUNT));
            if (right > left) {
                gui.fill(left, y + INNER_TOP, right, y + INNER_BOTTOM, color);
            }
        }
    }

    private static int brighten(int color) {
        int red = Math.min(255, ((color >> 16) & 0xFF) + 45);
        int green = Math.min(255, ((color >> 8) & 0xFF) + 45);
        int blue = Math.min(255, (color & 0xFF) + 45);
        return (color & 0xFF000000) | red << 16 | green << 8 | blue;
    }

    /** Test seam: exposes the recognised literals without touching Minecraft. */
    static String[] titlesForTest() {
        return new String[] {BONE_TITLE, SCULK_TITLE, RUNE_TITLE, MIRROR_TITLE};
    }

    static String describeForTest(String style) {
        return String.format(Locale.ROOT, "%s:%06X", style, accent(style) & 0xFFFFFF);
    }
}