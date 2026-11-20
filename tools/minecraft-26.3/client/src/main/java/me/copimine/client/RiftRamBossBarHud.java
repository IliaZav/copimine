package me.copimine.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.BossEvent;
import java.util.UUID;

/** Real vanilla-network HP plus armour from the UUID-bound signed Ram visual. */
public final class RiftRamBossBarHud {
    private static final Identifier FRAME = texture("forged_rail");
    private static final Identifier CHAIN = texture("chain");
    private static final Identifier SEALED = texture("plate_sealed");
    private static final Identifier SPENT = texture("plate_spent");
    private static final int WIDTH = 268;
    private static final int PLATE_BASE = 139;
    private static final int PLATE_STRIDE = 17;
    private static UUID displayedBar;
    private static long displayedGeneration = -1;
    private static int displayedImpacts;
    private static int breakingPlate = -1;
    private static long breakStarted;

    private RiftRamBossBarHud() { }

    private static Identifier texture(String part) {
        return Identifier.fromNamespaceAndPath("copimineclient", "textures/gui/rift_ram_" + part + ".png");
    }

    static int remainingPlates(String visual) {
        if (visual == null || !visual.matches("END_RIFT_RAM_ARMOR[0-7]_V1")) return -1;
        return 7 - (visual.charAt("END_RIFT_RAM_ARMOR".length()) - '0');
    }

    public static boolean render(GuiGraphicsExtractor gui, int ignoredX, int y, BossEvent bar) {
        if (gui == null || bar == null || bar.getName() == null
                || !bar.getName().getString().startsWith("Таран Разлома")) return false;
        var client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return false;
        var state = ClientBridgeProtocol.endEventState();
        int plates = -1;
        for (String id : state.eventVisualEntityIds()) {
            int count = remainingPlates(state.visualForEntity(id));
            if (count >= 0) { plates = count; break; }
        }
        if (plates < 0) return false;
        int impacts = 7 - plates;
        long now = System.nanoTime() / 1_000_000L;
        if (!bar.getId().equals(displayedBar) || displayedGeneration != state.generation()) {
            displayedBar = bar.getId();
            displayedGeneration = state.generation();
            displayedImpacts = impacts;
            breakingPlate = -1;
        } else if (impacts > displayedImpacts) {
            breakingPlate = impacts == displayedImpacts + 1 ? displayedImpacts : -1;
            breakStarted = now;
            displayedImpacts = impacts;
        } else if (impacts < displayedImpacts) {
            // A stale model binding cannot re-seal a plate within this bar.
            impacts = displayedImpacts;
        }

        float scale = Math.min(1F, Math.max(1, gui.guiWidth() - 8) / (float) WIDTH);
        gui.pose().pushMatrix();
        gui.pose().translate(gui.guiWidth() * .5F, y);
        gui.pose().scale(scale, scale);
        try {
            int x = -WIDTH / 2;
            float progress = Float.isFinite(bar.getProgress()) ? Math.clamp(bar.getProgress(), 0F, 1F) : 0F;
            int filled = progress > 0F ? Math.max(1, Math.round(119 * progress)) : 0;
            blit(gui, FRAME, x, -4, WIDTH, 20);
            if (filled > 0) {
                gui.fill(x + 10, 3, x + 10 + filled, 10, impacts < 7 ? 0xFF835698 : 0xFFB74152);
                gui.fill(x + 10, 3, x + 10 + filled, 5, impacts < 7 ? 0xFFD6A6DA : 0xFFEC8892);
            }
            long age = now - breakStarted;
            boolean reduced = client.options.screenEffectScale().get() <= .05;
            for (int plate = 0; plate < 7; plate++) {
                int px = x + PLATE_BASE + plate * PLATE_STRIDE;
                boolean falling = !reduced && plate == breakingPlate && age >= 0 && age < 440;
                if (plate >= impacts) {
                    blit(gui, CHAIN, px - 4, -7, 26, 16);
                    blit(gui, SEALED, px, -1, 16, 16);
                } else {
                    blit(gui, SPENT, px, -1, 16, 16);
                    if (falling) drawBreak(gui, px, age, plate >= 4);
                }
            }
            gui.centeredText(client.font, "ТАРАН РАЗЛОМА", x + WIDTH / 2, -13, 0xFFF1E5F6);
        } finally {
            gui.pose().popMatrix();
        }
        return true;
    }

    private static void drawBreak(GuiGraphicsExtractor gui, int px, long age, boolean tower) {
        if (age < 105) {
            int alpha = 150 * (105 - (int) age) / 105;
            gui.fill(px + 1, 1, px + 15, 13, alpha << 24 | 0xE8BCD8);
        }
        if (age < 320) {
            float t = age / 320F;
            int alpha = (int) (200 * (1F - t));
            for (int i = 0; i < (tower ? 5 : 3); i++) {
                int sx = px + 7 + Math.round((i - 2) * (2F + 4F * t));
                int sy = 5 - Math.round((5F + i) * t - 12F * t * t);
                gui.fill(sx, sy, sx + 2, sy + 2, alpha << 24 | 0xC7ADBF);
            }
        }
        float t = age / 440F;
        blit(gui, CHAIN, px - 4 + Math.round(4F * t), -7 + Math.round(12F * t * t), 26, 16);
    }

    private static void blit(GuiGraphicsExtractor gui, Identifier id, int x, int y, int width, int height) {
        gui.blit(RenderPipelines.GUI_TEXTURED, id, x, y, 0F, 0F, width, height, width, height, width, height);
    }
}
