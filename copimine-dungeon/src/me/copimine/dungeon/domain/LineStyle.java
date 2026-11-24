package me.copimine.dungeon.domain;

/**
 * Line styles that accompany overlay colour (spec OV4.3): colourblind-safe
 * because every type also carries a distinct dash/dot/double pattern, never
 * colour alone.
 */
public enum LineStyle {
    SOLID,
    DASHED,
    DOUBLE,
    DOTTED,
    RETICLE,
    DIAMOND,
    CROSS_HATCH,
    DRAFT_DASHED
}
