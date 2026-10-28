package me.copimine.client;

/** Mapping-free geometry for the CopiMine TAB panel, shared by the drag-free code paths of both clients. */
public final class TabPanelLayout {
    public static final int LINE_HEIGHT = 9;
    /** Height of {@code \uE000}, which maps to minecraft:gui/copimine_tab.png at height 30. */
    public static final int LOGO_LINE_HEIGHT = 30;
    /** Height of {@code \uE300}, which maps to copimine:gui/tab/copimine_logo.png at height 52. */
    public static final int LARGE_LOGO_LINE_HEIGHT = 52;

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

    /** Blank lines carry height only when nothing else is on them, so skip them entirely. */
    public static boolean isDrawnLine(String plainLine) {
        return !plainLine.isBlank();
    }
}