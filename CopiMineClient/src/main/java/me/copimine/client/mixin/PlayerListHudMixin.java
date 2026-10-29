package me.copimine.client.mixin;

import me.copimine.client.TabPanelLayout;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
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
@Mixin(PlayerListHud.class)
public abstract class PlayerListHudMixin {
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
    private Text header;

    @Shadow
    private Text footer;

    @Inject(method = "render(Lnet/minecraft/client/gui/DrawContext;ILnet/minecraft/scoreboard/Scoreboard;Lnet/minecraft/scoreboard/ScoreboardObjective;)V", at = @At("HEAD"), cancellable = true)
    private void copimine$drawPanel(DrawContext context, int width, Scoreboard scoreboard,
                                    ScoreboardObjective objective, CallbackInfo callback) {
        // Without any server text there is nothing to show; the vanilla list is still better.
        if (header == null && footer == null) {
            return;
        }
        copimine$render(context, width);
        callback.cancel();
    }

    @Unique
    private void copimine$render(DrawContext context, int width) {
        TextRenderer font = MinecraftClient.getInstance().textRenderer;
        List<OrderedText> headLines = copimine$lines(header, font);
        List<OrderedText> footLines = copimine$lines(footer, font);
        List<PlayerListEntry> players = ((PlayerListHudAccessor) this).copimine$collectPlayerEntries();

        int contentWidth = 0;
        for (OrderedText line : headLines) {
            contentWidth = Math.max(contentWidth, font.getWidth(line));
        }
        for (OrderedText line : footLines) {
            contentWidth = Math.max(contentWidth, font.getWidth(line));
        }
        for (PlayerListEntry player : players) {
            int rowWidth = font.getWidth(copimine$nameForDisplay(player))
                    + 8 + font.getWidth("Ping: " + player.getLatency()) + 18;
            contentWidth = Math.max(contentWidth, rowWidth);
        }

        int panelWidth = Math.min(contentWidth + 24, width - 4);
        int left = (width - panelWidth) / 2;
        int right = left + panelWidth;
        int bodyHeight = 0;
        for (OrderedText line : headLines) {
            bodyHeight += TabPanelLayout.lineHeight(copimine$plainText(line));
        }
        // shortcut: >20 rows extend past the screen, add scrolling if servers ever exceed 20 listed players
        bodyHeight += players.size() * ROW_HEIGHT + 8;
        for (OrderedText line : footLines) {
            bodyHeight += TabPanelLayout.lineHeight(copimine$plainText(line));
        }
        int top = Math.max(2, (MinecraftClient.getInstance().getWindow().getScaledHeight() - bodyHeight) / 2);

        context.fill(left, top, right, top + bodyHeight, BACKGROUND_COLOR);
        context.drawBorder(left, top, panelWidth, bodyHeight, BORDER_COLOR);

        int y = top;
        for (OrderedText line : headLines) {
            int lineHeight = TabPanelLayout.lineHeight(copimine$plainText(line));
            y += lineHeight;
            context.drawCenteredTextWithShadow(font, line, (left + right) / 2, y - lineHeight + 1, TEXT_COLOR);
        }

        for (PlayerListEntry player : players) {
            context.drawTextWithShadow(font, copimine$nameForDisplay(player), left + PANEL_MARGIN, y + 2, TEXT_COLOR);
            copimine$drawPingBars(context, right - PANEL_MARGIN - BAR_AREA_WIDTH, y + 11,
                    TabPanelLayout.pingBars(player.getLatency()));
            String ping = "Ping: " + player.getLatency();
            int pingX = right - PANEL_MARGIN - BAR_AREA_WIDTH - 3 - font.getWidth(ping);
            context.drawTextWithShadow(font, ping, pingX, y + 2, LABEL_COLOR);
            y += ROW_HEIGHT;
        }

        y += 4;
        for (OrderedText line : footLines) {
            int lineHeight = TabPanelLayout.lineHeight(copimine$plainText(line));
            y += lineHeight;
            context.drawCenteredTextWithShadow(font, line, (left + right) / 2, y - lineHeight + 1, TEXT_COLOR);
        }
    }

    @Unique
    private void copimine$drawPingBars(DrawContext context, int left, int bottom, int litBars) {
        for (int index = 0; index < BAR_HEIGHTS.length; index++) {
            int barHeight = BAR_HEIGHTS[index];
            int color = index < litBars ? BAR_LIT_COLOR : BAR_UNLIT_COLOR;
            int x = left + index * 3;
            context.fill(x, bottom - barHeight, x + 2, bottom, color);
        }
    }

    @Unique
    private Text copimine$nameForDisplay(PlayerListEntry player) {
        // Keeps team colours, prefixes and tablist-name-formatting, unlike getProfile().getName().
        return ((PlayerListHud) (Object) this).getPlayerName(player);
    }

    @Unique
    private static List<OrderedText> copimine$lines(Text text, TextRenderer font) {
        if (text == null) {
            return List.of();
        }
        List<OrderedText> drawn = new ArrayList<>();
        for (OrderedText line : font.wrapLines(text, 1000)) {
            if (TabPanelLayout.isDrawnLine(copimine$plainText(line))) {
                drawn.add(line);
            }
        }
        return drawn;
    }

    @Unique
    private static String copimine$plainText(OrderedText line) {
        StringBuilder builder = new StringBuilder();
        line.accept((index, style, codePoint) -> {
            builder.appendCodePoint(codePoint);
            return true;
        });
        return builder.toString();
    }
}
