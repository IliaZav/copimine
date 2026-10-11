package me.copimine.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** Compact four-icon prisoner HUD driven only by server cooldown/unlock snapshots. */
public final class PrisonerHudRenderer {
    private static final int ICON_SIZE = PrisonerHudLayout.ICON_SIZE;
    private static final int SLOT_WIDTH = PrisonerHudLayout.SLOT_WIDTH;
    private static final int SLOT_HEIGHT = PrisonerHudLayout.SLOT_HEIGHT;
    private static final Identifier[] ICONS = {
            icon("end_rift_prisoner_heal.png"),
            icon("end_rift_prisoner_surge.png"),
            icon("end_rift_prisoner_guardian.png"),
            icon("end_rift_prisoner_turncoat.png")
    };
    private static final long[] COOLDOWN_TOTALS = {20_000L, 30_000L, 30_000L, 55_000L};

    private PrisonerHudRenderer() {
    }

    public static void render(GuiGraphicsExtractor context) {
        Minecraft client = Minecraft.getInstance();
        if (context == null || client.player == null || client.gui.screen() != null || client.gui.hud.isHidden()
                || !ClientBridgeProtocol.prisonerHud().activeFor(client.player.getUUID())) {
            return;
        }
        long now = System.currentTimeMillis();
        PrisonerTargetSelector.Preview target = PrisonerTargetSelector.preview(client);
        if (target.entity() != null) {
            String label = client.font.plainSubstrByWidth(
                    "Цель: " + target.entity().getDisplayName().getString(),
                    Math.max(0, context.guiWidth() - 24));
            int color = target.ally() ? 0xFF35E1F5 : 0xFFAC5DFF;
            context.centeredText(client.font, Component.literal(label),
                    context.guiWidth() / 2,
                    context.guiHeight() / 2 + 16, color);
        }
        PrisonerHudLayout.Layout layout = PrisonerHudLayout.compute(
                context.guiWidth(), context.guiHeight(),
                Math.max(client.player.getMaxHealth(), client.player.getHealth()),
                client.player.getAbsorptionAmount());
        if (!layout.visible()) return;
        PrisonerHudLayout.Rect panel = layout.bounds();
        context.fill(panel.left(), panel.top(), panel.right(), panel.bottom(), 0xB7080C16);
        for (PrisonerHudController.Ability ability : PrisonerHudController.Ability.values()) {
            int index = ability.ordinal();
            PrisonerHudLayout.Rect slot = layout.slots().get(index);
            PrisonerHudController.AbilityState state = ClientBridgeProtocol.prisonerHud()
                    .state(ability, client.player.getUUID(), now, target.targetIdFor(ability));
            drawSlot(context, client, ability, index, slot.left(), slot.top(), state);
        }
    }

    private static void drawSlot(GuiGraphicsExtractor context, Minecraft client,
                                 PrisonerHudController.Ability ability, int index,
                                 int x, int y,
                                 PrisonerHudController.AbilityState abilityState) {
        int frame = switch (abilityState.state()) {
            case READY -> 0xFF4ADDF1;
            case NO_TARGET -> 0xFF778093;
            case COOLDOWN -> 0xFF8C6CD8;
            case LOCKED -> 0xFF55596A;
        };
        context.fill(x, y, x + SLOT_WIDTH, y + SLOT_HEIGHT, 0xCC111522);
        context.fill(x, y, x + SLOT_WIDTH, y + 1, frame);
        context.fill(x, y + SLOT_HEIGHT - 1, x + SLOT_WIDTH, y + SLOT_HEIGHT, 0xFF353A49);
        context.fill(x, y, x + 1, y + SLOT_HEIGHT, 0xFF353A49);
        context.fill(x + SLOT_WIDTH - 1, y, x + SLOT_WIDTH, y + SLOT_HEIGHT, 0xFF353A49);
        int iconX = x + (SLOT_WIDTH - ICON_SIZE) / 2;
        int iconY = y + 5;
        context.blit(RenderPipelines.GUI_TEXTURED, ICONS[index], iconX, iconY,
                0F, 0F, ICON_SIZE, ICON_SIZE,
                ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
        if (abilityState.state() == PrisonerHudController.HudState.LOCKED) {
            context.fill(iconX, iconY, iconX + ICON_SIZE, iconY + ICON_SIZE, 0xB8000000);
            drawLock(context, iconX + 21, iconY + 21);
        } else if (abilityState.state() == PrisonerHudController.HudState.NO_TARGET) {
            context.fill(iconX, iconY, iconX + ICON_SIZE, iconY + ICON_SIZE, 0x66090D18);
        } else if (abilityState.state() == PrisonerHudController.HudState.COOLDOWN) {
            long total = COOLDOWN_TOTALS[index];
            int coverHeight = (int) Math.ceil(ICON_SIZE
                    * Math.min(1.0D, abilityState.remainingMillis() / (double) total));
            context.fill(iconX, iconY, iconX + ICON_SIZE,
                    iconY + Math.max(1, coverHeight), 0xA9000000);
            String seconds = Long.toString(Math.max(1L,
                    (abilityState.remainingMillis() + 999L) / 1_000L));
            context.centeredText(client.font, Component.literal(seconds),
                    iconX + ICON_SIZE / 2, iconY + 11, 0xFFF3EFFF);
        } else {
            context.fill(x + 1, y + 1, x + SLOT_WIDTH - 1, y + 2, 0xFF9CFFFF);
        }
        context.centeredText(client.font,
                Component.literal(ability.keyLabel()), x + SLOT_WIDTH / 2,
                y + SLOT_HEIGHT - 9, 0xFFE3E6F2);
    }

    private static void drawLock(GuiGraphicsExtractor context, int x, int y) {
        context.fill(x + 1, y + 4, x + 9, y + 10, 0xFFFFD66E);
        context.fill(x + 2, y + 1, x + 8, y + 5, 0xFFFFD66E);
        context.fill(x + 4, y + 2, x + 6, y + 5, 0xFF272638);
        context.fill(x + 4, y + 6, x + 6, y + 8, 0xFF272638);
    }

    private static Identifier icon(String name) {
        return Identifier.fromNamespaceAndPath("copimineclient", "textures/gui/" + name);
    }
}
