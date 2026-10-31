package me.copimine.client.mixin;

import me.copimine.client.TabPanelLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
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
import java.util.Optional;

/**
 * Replaces the vanilla player list with the CopiMine panel.
 *
 * <p>The header and footer text arrives from TAB through the ordinary tab-list packet, so this
 * class only restyles it. Skipping the vanilla entry point drops the flat list, its background
 * and its per-row plates in one step, because the caller draws nothing itself.
 */
@Mixin(PlayerTabOverlay.class)
public abstract class PlayerTabOverlayMixin {
    private static final int BACKGROUND_COLOR = TabPanelLayout.PANEL_BACKGROUND_COLOR;
    private static final int BORDER_COLOR = TabPanelLayout.PANEL_BORDER_COLOR;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int LABEL_COLOR = 0xFFC2C9CF;
    private static final int BAR_LIT_COLOR = 0xFF3ACB4B;
    private static final int BAR_UNLIT_COLOR = 0xFF3A3F35;
    private static final int[] BAR_HEIGHTS = {2, 3, 5, 7, 9};
    private static final Component NAME_ELLIPSIS = Component.literal("\u2026");

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
        int maxContentWidth = Math.max(1, width - TabPanelLayout.PANEL_MARGIN * 2 - 24);
        // Splitting at the panel budget keeps long server lines inside the border and preserves
        // their colours, which a string truncation would drop.
        List<FormattedCharSequence> headLines = copimine$lines(header, font, maxContentWidth);
        List<FormattedCharSequence> footLines = copimine$lines(footer, font, maxContentWidth);
        List<PlayerInfo> players = ((PlayerTabOverlayAccessor) this).copimine$getPlayerInfos();

        int headHeight = copimine$blockHeight(headLines);
        int footHeight = copimine$blockHeight(footLines);
        int screenHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        int rows = TabPanelLayout.visiblePlayerRows(
                screenHeight, headHeight, footHeight, players.size());

        int contentWidth = 0;
        for (FormattedCharSequence line : headLines) {
            contentWidth = Math.max(contentWidth, font.width(line));
        }
        for (FormattedCharSequence line : footLines) {
            contentWidth = Math.max(contentWidth, font.width(line));
        }
        for (int index = 0; index < rows; index++) {
            PlayerInfo player = players.get(index);
            int rowWidth = TabPanelLayout.playerRowWidth(
                    font.width(copimine$nameForDisplay(player)), font.width(copimine$pingLabel(player)));
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
        // outline takes a width/height pair, not the right/bottom edges fill takes.
        context.outline(left, top, panelWidth, bodyHeight, BORDER_COLOR);

        int y = top;
        for (FormattedCharSequence line : headLines) {
            String plainLine = copimine$plainText(line);
            int lineHeight = TabPanelLayout.lineHeight(plainLine);
            y += lineHeight;
            context.centeredText(font, line, (left + right) / 2,
                    y - lineHeight + 1 + TabPanelLayout.textYOffset(plainLine), TEXT_COLOR);
        }

        for (int index = 0; index < rows; index++) {
            PlayerInfo player = players.get(index);
            String ping = copimine$pingLabel(player);
            int pingWidth = font.width(ping);
            int nameBudget = TabPanelLayout.playerNameWidth(panelWidth, pingWidth);
            Component name = copimine$nameForDisplay(player);
            // Clip the sequence that is actually drawn: capping only the measured width still
            // lets the full styled name overdraw the ping label.
            FormattedCharSequence drawnName = copimine$clipName(font, name, nameBudget);
            int nameWidth = font.width(drawnName);
            int nameX = TabPanelLayout.playerRowLeft(left, panelWidth, nameWidth, pingWidth);
            context.text(font, drawnName, nameX, y + 2, TEXT_COLOR);
            int pingX = nameX + nameWidth + TabPanelLayout.NAME_GAP;
            context.text(font, ping, pingX, y + 2, LABEL_COLOR);
            copimine$drawPingBars(context, pingX + pingWidth + TabPanelLayout.BARS_GAP, y + 11,
                    TabPanelLayout.pingBars(player.getLatency()));
            y += TabPanelLayout.ROW_HEIGHT;
        }

        y += TabPanelLayout.ROW_BLOCK_PADDING / 2 + TabPanelLayout.ROW_FOOTER_GAP;
        for (FormattedCharSequence line : footLines) {
            String plainLine = copimine$plainText(line);
            int lineHeight = TabPanelLayout.lineHeight(plainLine);
            y += lineHeight;
            context.centeredText(font, line, (left + right) / 2,
                    y - lineHeight + 1 + TabPanelLayout.textYOffset(plainLine), TEXT_COLOR);
        }
    }

    @Unique
    private static int copimine$blockHeight(List<FormattedCharSequence> lines) {
        int height = 0;
        for (FormattedCharSequence line : lines) {
            height += TabPanelLayout.lineHeight(copimine$plainText(line));
        }
        return height;
    }

    @Unique
    private static String copimine$pingLabel(PlayerInfo player) {
        return "Ping: " + player.getLatency();
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

    /**
     * Cuts the drawn sequence to the row budget and marks the cut with an ellipsis.
     * Style is preserved per glyph because the splitter walks the original sequence.
     */
    @Unique
    private static FormattedCharSequence copimine$clipName(Font font, Component name, int budget) {
        if (font.width(name) <= budget) {
            return name.getVisualOrderText();
        }
        int markerWidth = font.width(NAME_ELLIPSIS);
        int glyphBudget = TabPanelLayout.clippedNameBudget(budget, markerWidth);
        FormattedText head = font.substrByWidth(name, glyphBudget);
        List<FormattedCharSequence> parts = new ArrayList<>();
        head.visit((style, text) -> {
            if (!text.isEmpty()) {
                parts.add(FormattedCharSequence.forward(text, style));
            }
            return Optional.empty();
        }, Style.EMPTY);
        parts.add(NAME_ELLIPSIS.getVisualOrderText());
        return FormattedCharSequence.composite(parts);
    }

    @Unique
    private static List<FormattedCharSequence> copimine$lines(Component text, Font font, int maxWidth) {
        if (text == null) {
            return List.of();
        }
        List<FormattedCharSequence> drawn = new ArrayList<>();
        for (FormattedCharSequence line : font.split(text, maxWidth)) {
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