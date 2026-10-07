package me.copimine.client;

import java.util.ArrayList;
import java.util.List;

/** Scaled GUI geometry, including the vanilla hotbar, offhand, hearts, armor and air rows. */
public final class PrisonerHudLayout {
    public static final int ICON_SIZE = 32;
    public static final int SLOT_WIDTH = 40;
    public static final int SLOT_HEIGHT = 44;
    private static final int GAP = 4;
    private static final int PADDING = 3;
    private static final int MARGIN = 6;

    private PrisonerHudLayout() {
    }

    public static Layout compute(int width, int height, double health, double absorption) {
        double totalHealth = Math.max(20, finiteHealth(health)) + Math.ceil(finiteHealth(absorption));
        int rows = Math.max(1, (int) Math.ceil(totalHealth / 20));
        int rowSpacing = Math.max(10 - (rows - 2), 3);
        // Vanilla's baseline is h-39, with armor above the top heart row.
        // Seven extra pixels cover heart bobbing/regeneration and a small separation.
        int statusHeight = 56 + (rows - 1) * rowSpacing;
        Rect vanilla = new Rect(Math.max(0, width / 2 - 122),
                Math.max(0, height - statusHeight), Math.min(width, width / 2 + 122), height);
        for (int columns : new int[]{4, 2}) {
            int panelWidth = columns * SLOT_WIDTH + (columns - 1) * GAP + 2 * PADDING;
            int panelHeight = (4 / columns) * SLOT_HEIGHT + (4 / columns - 1) * GAP + 2 * PADDING;
            Rect above = new Rect((width - panelWidth) / 2,
                    vanilla.top() - MARGIN - panelHeight,
                    (width - panelWidth) / 2 + panelWidth, vanilla.top() - MARGIN);
            if (fits(above, width, height)) return layout(above, vanilla, columns);
        }
        // On a short, wide viewport retain full-size icons in a 2x2 panel beside the HUD.
        int panelWidth = 2 * SLOT_WIDTH + GAP + 2 * PADDING;
        int panelHeight = 2 * SLOT_HEIGHT + GAP + 2 * PADDING;
        Rect side = new Rect(MARGIN, MARGIN, MARGIN + panelWidth, MARGIN + panelHeight);
        if (fits(side, width, height) && !side.intersects(vanilla)) {
            return layout(side, vanilla, 2);
        }
        // An impossibly small viewport has no space for four 32px icons plus vanilla controls.
        return new Layout(new Rect(0, 0, 0, 0), vanilla, List.of(), 0);
    }

    private static Layout layout(Rect panel, Rect vanilla, int columns) {
        List<Rect> slots = new ArrayList<>(4);
        for (int index = 0; index < 4; index++) {
            int x = panel.left() + PADDING + index % columns * (SLOT_WIDTH + GAP);
            int y = panel.top() + PADDING + index / columns * (SLOT_HEIGHT + GAP);
            slots.add(new Rect(x, y, x + SLOT_WIDTH, y + SLOT_HEIGHT));
        }
        return new Layout(panel, vanilla, List.copyOf(slots), columns);
    }

    private static double finiteHealth(double value) {
        return Double.isFinite(value) ? Math.min(1_024, Math.max(0, value)) : 0;
    }

    private static boolean fits(Rect panel, int width, int height) {
        return panel.left() >= MARGIN && panel.top() >= MARGIN
                && panel.right() <= width - MARGIN && panel.bottom() <= height - MARGIN;
    }

    public record Layout(Rect bounds, Rect vanillaHud, List<Rect> slots, int columns) {
        public boolean visible() {
            return slots.size() == 4;
        }
    }

    public record Rect(int left, int top, int right, int bottom) {
        public boolean intersects(Rect other) {
            return left < other.right && right > other.left
                    && top < other.bottom && bottom > other.top;
        }
    }
}
