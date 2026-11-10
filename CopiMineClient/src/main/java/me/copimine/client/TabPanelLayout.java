package me.copimine.client;

/** Mapping-free geometry for the CopiMine TAB panel, shared by the drag-free code paths of both clients. */
public final class TabPanelLayout {
    public static final int LINE_HEIGHT = 9;
    /** Height of {@code \uE000}, which maps to minecraft:gui/copimine_tab.png at height 30. */
    public static final int LOGO_LINE_HEIGHT = 30;
    /** Height of {@code \uE300}, which maps to copimine:gui/tab/copimine_logo.png at height 52. */
    public static final int LARGE_LOGO_LINE_HEIGHT = 52;
    private static final int SMALL_LOGO_BASELINE_OFFSET = LOGO_LINE_HEIGHT - 14;
    private static final int LARGE_LOGO_BASELINE_OFFSET = LARGE_LOGO_LINE_HEIGHT - 36;
    /** Small gap under the top screen edge; the panel hangs from the top instead of being centred. */
    public static final int PANEL_MARGIN = 6;
    /**
     * Semi-transparent dark slate plate. The user asked for a translucent backdrop rather than a
     * black one, so the world stays visible through the panel: alpha 0x8C (55%) keeps the text
     * readable while no longer reading as an opaque black box. Green must not dominate either
     * channel, so the tint stays neutral instead of the old olive.
     */
    public static final int PANEL_BACKGROUND_COLOR = 0x8C24282C;
    /** Neutral slate border, low saturation so it never competes with team colours. */
    public static final int PANEL_BORDER_COLOR = 0xFF3A4048;
    private static final int PANEL_HORIZONTAL_PADDING = 24;
    /** Gap between the name and the ping label inside a row. */
    public static final int NAME_GAP = 6;
    /** Gap between the ping label and its bars. */
    public static final int BARS_GAP = 3;
    public static final int ROW_HEIGHT = 12;
    public static final int BAR_AREA_WIDTH = 14;
    /** Padding above and below the player rows inside the plate. */
    public static final int ROW_BLOCK_PADDING = 8;
    /** Gap between the last player row and the footer block. */
    public static final int ROW_FOOTER_GAP = 4;

    private TabPanelLayout() {
    }

    /** Matches the vanilla ping sprite thresholds and lights no bar when the latency is unknown. */
    public static int pingBars(int latencyMillis) {
        if (latencyMillis < 0) {
            return 0;
        }
        if (latencyMillis < 150) {
            return 5;
        }
        if (latencyMillis < 300) {
            return 4;
        }
        if (latencyMillis < 600) {
            return 3;
        }
        if (latencyMillis < 1000) {
            return 2;
        }
        return 1;
    }

    /** Vertical space a header/footer line needs, so the glyph rows never overlap the next line. */
    public static int lineHeight(String plainLine) {
        if (plainLine.indexOf('\uE300') >= 0) {
            return LARGE_LOGO_LINE_HEIGHT;
        }
        if (plainLine.indexOf('\uE000') >= 0) {
            return LOGO_LINE_HEIGHT;
        }
        return LINE_HEIGHT;
    }

    /**
     * Resource-pack glyphs use an ascent larger than vanilla's 8px text baseline.
     * Offset their draw origin so the full bitmap sits inside its reserved line box.
     */
    public static int textYOffset(String plainLine) {
        if (plainLine.indexOf('\uE300') >= 0) {
            return LARGE_LOGO_BASELINE_OFFSET;
        }
        if (plainLine.indexOf('\uE000') >= 0) {
            return SMALL_LOGO_BASELINE_OFFSET;
        }
        return 0;
    }

    /**
     * Top edge of the panel. Centring made the plate drift across the screen as the roster grew,
     * so it always hangs from the same small margin under the top edge.
     */
    public static int panelTop() {
        return PANEL_MARGIN;
    }

    /** Width a single player row needs, name included. */
    public static int playerRowWidth(int nameWidth, int pingWidth) {
        return nameWidth + NAME_GAP + pingWidth + BARS_GAP + BAR_AREA_WIDTH;
    }

    /** Left edge for the centered name, ping label, and bars row. */
    public static int playerRowLeft(int panelLeft, int panelWidth, int nameWidth, int pingWidth) {
        return panelLeft + (panelWidth - playerRowWidth(nameWidth, pingWidth)) / 2;
    }

    /**
     * Width budget for a player name inside a plate that is already capped at the screen width.
     * Long rank prefixes and names are clamped instead of drawing outside the border.
     */
    public static int playerNameWidth(int panelWidth, int pingWidth) {
        int available = panelWidth - PANEL_HORIZONTAL_PADDING
                - NAME_GAP - pingWidth - BARS_GAP - BAR_AREA_WIDTH;
        return Math.max(1, available);
    }

    /**
     * Budget left for the visible glyphs once the truncation marker is reserved.
     * The marker is part of the row, so it must come out of the name budget rather than
     * overdrawing the ping label; the floor keeps a single glyph readable on tiny panels.
     */
    public static int clippedNameBudget(int nameBudget, int markerWidth) {
        return Math.max(1, nameBudget - Math.max(0, markerWidth));
    }

    /**
     * Player rows that still fit between the header and footer inside the screen.
     * shortcut: rows beyond this are not drawn, add scrolling if a server lists more than fits
     */
    public static int visiblePlayerRows(int screenHeight, int headHeight, int footHeight, int playerCount) {
        int reserved = PANEL_MARGIN + headHeight + ROW_BLOCK_PADDING + ROW_FOOTER_GAP + footHeight;
        int fitting = Math.max(0, screenHeight - reserved) / ROW_HEIGHT;
        return Math.min(Math.max(0, playerCount), fitting);
    }

    /** Preserve blank lines as deliberate vertical spacing in the server-provided layout. */
    public static boolean isDrawnLine(String plainLine) {
        return true;
    }
}