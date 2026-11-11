package me.copimine.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.BossEvent;

/** Real vanilla-network HP plus armour from the UUID-bound signed Ram visual. */
public final class RiftRamBossBarHud {
    private static final Identifier PLATE = Identifier.withDefaultNamespace("textures/block/netherite_block.png");
    private static final Identifier SEAL = Identifier.withDefaultNamespace("textures/block/crying_obsidian.png");
    private RiftRamBossBarHud() { }

    static int remainingPlates(String visual) {
        if (visual == null || !visual.matches("END_RIFT_RAM_ARMOR[0-3]_V1")) return -1;
        return 3 - (visual.charAt("END_RIFT_RAM_ARMOR".length()) - '0');
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
        float progress = Float.isFinite(bar.getProgress()) ? Math.max(0, Math.min(1, bar.getProgress())) : 0;
        int x = (gui.guiWidth() - 232) / 2;
        gui.fill(x - 2, y - 2, x + 188, y + 10, 0xFF111018);
        gui.fill(x - 1, y - 1, x + 187, y + 9, 0xFF77717F);
        gui.fill(x, y, x + 186, y + 8, 0xFF24202D);
        int filled = Math.round(184 * progress);
        int body = plates > 0 ? 0xFF8753AA : 0xFFBA3748;
        gui.fill(x + 1, y + 1, x + 1 + filled, y + 7, body);
        gui.fill(x + 1, y + 1, x + 1 + filled, y + 3, plates > 0 ? 0xFFD098EC : 0xFFFF8D8F);
        for (int plate = 0; plate < 3; plate++) {
            int px = x + 193 + plate * 13;
            gui.fill(px - 1, y - 3, px + 11, y + 11, 0xFF17131C);
            if (plate < plates) {
                gui.blit(RenderPipelines.GUI_TEXTURED, PLATE, px, y - 2, 0F, 0F, 10, 12, 16, 16, 16, 16);
                gui.fill(px, y - 2, px + 10, y - 1, 0xFFD6C2D9);
                gui.blit(RenderPipelines.GUI_TEXTURED, SEAL, px + 3, y + 2, 0F, 0F, 4, 4, 16, 16, 16, 16);
            } else {
                gui.fill(px, y + 1, px + 10, y + 7, 0xFF302632);
                gui.fill(px + 4, y, px + 6, y + 8, 0xFF0A090D);
            }
        }
        gui.centeredText(client.font, "ТАРАН РАЗЛОМА", x + 93, y - 12, 0xFFF1E5F6);
        return true;
    }
}
