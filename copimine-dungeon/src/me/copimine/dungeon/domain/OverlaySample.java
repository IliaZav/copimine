package me.copimine.dungeon.domain;

/**
 * One editor-overlay sample point. {@code pillar} marks the vertical corner
 * columns so the renderer can draw them slightly thicker than the plane edges.
 */
public record OverlaySample(int x, int y, int z, int color, LineStyle style, boolean pillar) {

    public static OverlaySample of(OverlayType type, int x, int y, int z, boolean pillar) {
        return new OverlaySample(x, y, z, type.color(), type.lineStyle(), pillar);
    }
}
