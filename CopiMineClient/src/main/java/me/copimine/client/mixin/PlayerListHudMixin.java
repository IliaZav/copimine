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
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Replaces the vanilla player list with the CopiMine panel.
 *
 * <p>The header and footer text arrives from TAB through the ordinary tab-list packet, so this
 * class only restyles it. Skipping the vanilla entry point drops the flat list, its background
 * and its per-row plates in one step, because the caller draws nothing itself.
 */
@Mixin(PlayerListHud.class)
public abstract class PlayerListHudMixin {
    private static final int BACKGROUND_COLOR = TabPanelLayout.PANEL_BACKGROUND_COLOR;
    private static final int BORDER_COLOR = TabPanelLayout.PANEL_BORDER_COLOR;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int LABEL_COLOR = 0xFFC2C9CF;
    private static final int BAR_LIT_COLOR = 0xFF3ACB4B;
    private static final int BAR_UNLIT_COLOR = 0xFF3A3F35;
    private static final int[] BAR_HEIGHTS = {2, 3, 5, 7, 9};
    private static final Text NAME_ELLIPSIS = Text.literal("\u2026");

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
        int maxContentWidth = Math.max(1, width - TabPanelLayout.PANEL_MARGIN * 2 - 24);
        // Splitting at the panel budget keeps long server lines inside the border and preserves
        // their colours, which a string truncation would drop.
        List<OrderedText> headLines = copimine$lines(header, font, maxContentWidth);
        List<OrderedText> footLines = copimine$lines(footer, font, maxContentWidth);
        List<PlayerListEntry> players = ((PlayerListHudAccessor) this).copimine$collectPlayerEntries();

        int headHeight = copimine$blockHeight(headLines);
        int footHeight = copimine$blockHeight(footLines);
        int screenHeight = MinecraftClient.getInstance().getWindow().getScaledHeight();
        int rows = TabPanelLayout.visiblePlayerRows(
                screenHeight, headHeight, footHeight, players.size());

        int contentWidth = 0;
        for (OrderedText line : headLines) {
            contentWidth = Math.max(contentWidth, font.getWidth(line));
        }
        for (OrderedText line : footLines) {
            contentWidth = Math.max(contentWidth, font.getWidth(line));
        }
        for (int index = 0; index < rows; index++) {
            PlayerListEntry player = players.get(index);
            int rowWidth = TabPanelLayout.playerRowWidth(
                    font.getWidth(copimine$nameForDisplay(player)), font.getWidth(copimine$pingLabel(player)));
            contentWidth = Math.max(contentWidth, Math.min(rowWidth, maxContentWidth));
        }

        int panelWidth = Math.min(contentWidth + 24, width - 4);
        int left = (width - panelWidth) / 2;
        int right = left + panelWidth;
        int bodyHeight = headHeight
                + rows * TabPanelLayout.ROW_HEIGHT + TabPanelLayout.ROW_BLOCK_PADDING
                + (footLines.isEmpty() ? 0 : TabPanelLayout.ROW_FOOTER_GAP)
                + footHeight;
        int top = TabPanelLayout.panelTop();

        context.fill(left, top, right, top + bodyHeight, BACKGROUND_COLOR);
        context.drawBorder(left, top, panelWidth, bodyHeight, BORDER_COLOR);

        int y = top;
        for (OrderedText line : headLines) {
            String plainLine = copimine$plainText(line);
            int lineHeight = TabPanelLayout.lineHeight(plainLine);
            y += lineHeight;
            context.drawCenteredTextWithShadow(font, line, (left + right) / 2,
                    y - lineHeight + 1 + TabPanelLayout.textYOffset(plainLine), TEXT_COLOR);
        }

        for (int index = 0; index < rows; index++) {
            PlayerListEntry player = players.get(index);
            String ping = copimine$pingLabel(player);
            int pingWidth = font.getWidth(ping);
            int nameBudget = TabPanelLayout.playerNameWidth(panelWidth, pingWidth);
            Text name = copimine$nameForDisplay(player);
            // Clip the sequence that is actually drawn: capping only the measured width still
            // lets the full styled name overdraw the ping label.
            OrderedText drawnName = copimine$clipName(font, name, nameBudget);
            int nameWidth = font.getWidth(drawnName);
            int nameX = TabPanelLayout.playerRowLeft(left, panelWidth, nameWidth, pingWidth);
            context.drawTextWithShadow(font, drawnName, nameX, y + 2, TEXT_COLOR);
            int pingX = nameX + nameWidth + TabPanelLayout.NAME_GAP;
            context.drawTextWithShadow(font, ping, pingX, y + 2, LABEL_COLOR);
            copimine$drawPingBars(context, pingX + pingWidth + TabPanelLayout.BARS_GAP, y + 11,
                    TabPanelLayout.pingBars(player.getLatency()));
            y += TabPanelLayout.ROW_HEIGHT;
        }

        y += TabPanelLayout.ROW_BLOCK_PADDING / 2 + TabPanelLayout.ROW_FOOTER_GAP;
        for (OrderedText line : footLines) {
            String plainLine = copimine$plainText(line);
            int lineHeight = TabPanelLayout.lineHeight(plainLine);
            y += lineHeight;
            context.drawCenteredTextWithShadow(font, line, (left + right) / 2,
                    y - lineHeight + 1 + TabPanelLayout.textYOffset(plainLine), TEXT_COLOR);
        }
    }

    @Unique
    private static OrderedText copimine$clipName(TextRenderer font, Text name, int budget) {
        if (font.getWidth(name) <= budget) {
            return name.asOrderedText();
        }
        int markerWidth = font.getWidth(NAME_ELLIPSIS);
        int glyphBudget = TabPanelLayout.clippedNameBudget(budget, markerWidth);
        StringVisitable head = font.trimToWidth(name, glyphBudget);
        List<OrderedText> parts = new ArrayList<>();
        head.visit((style, text) -> {
            if (!text.isEmpty()) {
                parts.add(OrderedText.styledForwardsVisitedString(text, style));
            }
            return Optional.empty();
        }, Style.EMPTY);
        parts.add(NAME_ELLIPSIS.asOrderedText());
        return OrderedText.concat(parts);
    }

    @Unique
    private static int copimine$blockHeight(List<OrderedText> lines) {
        int height = 0;
        for (OrderedText line : lines) {
            height += TabPanelLayout.lineHeight(copimine$plainText(line));
        }
        return height;
    }

    @Unique
    private static String copimine$pingLabel(PlayerListEntry player) {
        return "Ping: " + player.getLatency();
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
    private static List<OrderedText> copimine$lines(Text text, TextRenderer font, int maxWidth) {
        if (text == null) {
            return List.of();
        }
        List<OrderedText> drawn = new ArrayList<>();
        for (OrderedText line : font.wrapLines(text, maxWidth)) {
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