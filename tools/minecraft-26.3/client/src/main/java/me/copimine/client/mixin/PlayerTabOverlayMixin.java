package me.copimine.client.mixin;

import me.copimine.client.TabPanelLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Replaces the vanilla player list with the CopiMine panel.
 *
 * <p>The header and footer text arrives from TAB through the ordinary tab-list packet, so this
 * class only restyles it. Skipping the vanilla entry point drops the flat list, its background
 * and its per-row plates in one step, because the caller draws nothing itself.
 */
@Mixin(PlayerTabOverlay.class)
public abstract class PlayerTabOverlayMixin {
    private static final int BACKGROUND_COLOR = 0xE62A3324;
    private static final int BORDER_COLOR = 0xFF7C8F64;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int LABEL_COLOR = 0xFFC2C9CF;
    private static final int BAR_LIT_COLOR = 0xFF3ACB4B;
    private static final int BAR_UNLIT_COLOR = 0xFF3A3F35;
    private static final int ROW_HEIGHT = 12;
    private static final int PANEL_MARGIN = 6;
    private static final int BAR_AREA_WIDTH = 14;
    private static final int[] BAR_HEIGHTS = {2, 3, 5, 7, 9};

    @Shadow
    private Component header;

    @Shadow
    private Component footer;

    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;ILnet/minecraft/world/scores/Scoreboard;Lnet/minecraft/world/scores/Objective;)V", at = @At("HEAD"), cancellable = true)
    private void copimine$drawPanel(GuiGraphicsExtractor context, int width, Scoreboard scoreboard,
                                    Objective objective, CallbackInfo callback) {
        // Without any server text there is nothing to show; the vanilla list is still better.
        if (header == null && footer == null) {
            return;
        }
        copimine$render(context, width);
        callback.cancel();
    }

    @Unique
    private void copimine$render(GuiGraphicsExtractor context, int width) {
        Font font = Minecraft.getInstance().font;
        List<FormattedCharSequence> headLines = copimine$lines(header, font);
        List<FormattedCharSequence> footLines = copimine$lines(footer, font);
        List<PlayerInfo> players = ((PlayerTabOverlayAccessor) this).copimine$getPlayerInfos();

        int contentWidth = 0;
        for (FormattedCharSequence line : headLines) {
            contentWidth = Math.max(contentWidth, font.width(line));
        }
        for (FormattedCharSequence line : footLines) {
            contentWidth = Math.max(contentWidth, font.width(line));
        }
        for (PlayerInfo player : players) {
            int rowWidth = font.width(copimine$nameForDisplay(player))
                    + 8 + font.width("Ping: " + player.getLatency()) + 18;
            contentWidth = Math.max(contentWidth, rowWidth);
        }

        int panelWidth = Math.min(contentWidth + 24, width - 4);
        int left = (width - panelWidth) / 2;
        int right = left + panelWidth;
        int bodyHeight = 0;
        for (FormattedCharSequence line : headLines) {
            bodyHeight += TabPanelLayout.lineHeight(copimine$plainText(line));
        }
        // shortcut: >20 rows extend past the screen, add scrolling if servers ever exceed 20 listed players
        bodyHeight += players.size() * ROW_HEIGHT + 8;
        for (FormattedCharSequence line : footLines) {
            bodyHeight += TabPanelLayout.lineHeight(copimine$plainText(line));
        }
        int top = Math.max(2, (Minecraft.getInstance().getWindow().getGuiScaledHeight() - bodyHeight) / 2);

        context.fill(left, top, right, top + bodyHeight, BACKGROUND_COLOR);
        // outline takes a width/height pair, not the right/bottom edges fill takes.
        context.outline(left, top, panelWidth, bodyHeight, BORDER_COLOR);

        int y = top;
        for (FormattedCharSequence line : headLines) {
            int lineHeight = TabPanelLayout.lineHeight(copimine$plainText(line));
            y += lineHeight;
            context.centeredText(font, line, (left + right) / 2, y - lineHeight + 1, TEXT_COLOR);
        }

        for (PlayerInfo player : players) {
            context.text(font, copimine$nameForDisplay(player), left + PANEL_MARGIN, y + 2, TEXT_COLOR);
            copimine$drawPingBars(context, right - PANEL_MARGIN - BAR_AREA_WIDTH, y + 11,
                    TabPanelLayout.pingBars(player.getLatency()));
            String ping = "Ping: " + player.getLatency();
            int pingX = right - PANEL_MARGIN - BAR_AREA_WIDTH - 3 - font.width(ping);
            context.text(font, ping, pingX, y + 2, LABEL_COLOR);
            y += ROW_HEIGHT;
        }

        y += 4;
        for (FormattedCharSequence line : footLines) {
            int lineHeight = TabPanelLayout.lineHeight(copimine$plainText(line));
            y += lineHeight;
            context.centeredText(font, line, (left + right) / 2, y - lineHeight + 1, TEXT_COLOR);
        }
    }

    @Unique
    private void copimine$drawPingBars(GuiGraphicsExtractor context, int left, int bottom, int litBars) {
        for (int index = 0; index < BAR_HEIGHTS.length; index++) {
            int barHeight = BAR_HEIGHTS[index];
            int color = index < litBars ? BAR_LIT_COLOR : BAR_UNLIT_COLOR;
            int x = left + index * 3;
            context.fill(x, bottom - barHeight, x + 2, bottom, color);
        }
    }

    @Unique
    private Component copimine$nameForDisplay(PlayerInfo player) {
        // Keeps team colours, prefixes and tablist-name-formatting, unlike profile.getName().
        return ((PlayerTabOverlay) (Object) this).getNameForDisplay(player);
    }

    @Unique
    private static List<FormattedCharSequence> copimine$lines(Component text, Font font) {
        if (text == null) {
            return List.of();
        }
        List<FormattedCharSequence> drawn = new ArrayList<>();
        for (FormattedCharSequence line : font.split(text, 1000)) {
            if (TabPanelLayout.isDrawnLine(copimine$plainText(line))) {
                drawn.add(line);
            }
        }
        return drawn;
    }

    @Unique
    private static String copimine$plainText(FormattedCharSequence line) {
        StringBuilder builder = new StringBuilder();
        line.accept((index, style, codePoint) -> {
            builder.appendCodePoint(codePoint);
            return true;
        });
        return builder.toString();
    }
}
