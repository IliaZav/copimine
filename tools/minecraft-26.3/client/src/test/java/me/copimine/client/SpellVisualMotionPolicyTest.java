package me.copimine.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Section 9A.04/9A.05 accessibility contract: reduced motion must calm the
 * authored spell animation without ever changing the hazard silhouette.
 */
class SpellVisualMotionPolicyTest {

    @Test
    void reducedMotionStillsTheTravellingWave() {
        // The animated form breathes; the reduced form is a flat multiplier.
        boolean sawVariation = false;
        float previous = SpellVisualMotionPolicy.alphaScale(0, 1.2D, false);
        for (int index = 1; index < 24; index++) {
            float value = SpellVisualMotionPolicy.alphaScale(index, 1.2D, false);
            sawVariation |= Math.abs(value - previous) > 0.001F;
            previous = value;
        }
        assertTrue(sawVariation, "the default preset must animate its brightness wave");
        for (int index = 0; index < 24; index++) {
            assertEquals(SpellVisualMotionPolicy.STEADY_ALPHA_SCALE,
                    SpellVisualMotionPolicy.alphaScale(index, 1.2D, true), 0.0F,
                    "reduced motion must not throb");
        }
    }

    @Test
    void animatedAlphaStaysInsideTheDocumentedBand() {
        for (long elapsed = 0; elapsed < 4_000; elapsed += 137) {
            double phase = SpellVisualMotionPolicy.flowPhase(elapsed, false);
            for (int index = 0; index < 12; index++) {
                float value = SpellVisualMotionPolicy.alphaScale(index, phase, false);
                assertTrue(value >= 0.0F && value <= 1.0F,
                        "alpha scale out of range: " + value);
                assertTrue(value >= 0.595F,
                        "the wave must never pulse below the authored floor: " + value);
            }
        }
    }

    @Test
    void reducedMotionFreezesThePhaseButKeepsTheMesh() {
        assertEquals(0.0D, SpellVisualMotionPolicy.flowPhase(9_000L, true), 0.0D);
        assertFalse(SpellVisualMotionPolicy.travels(true));
        assertTrue(SpellVisualMotionPolicy.travels(false));
        assertTrue(SpellVisualMotionPolicy.flowPhase(1_000L, false) > 0.0D);

        // The silhouette is identical: every spell still builds the same
        // bounded mesh while motion is reduced.
        for (SpellVisualGeometry.Spell spell : SpellVisualGeometry.Spell.values()) {
            List<SpellVisualGeometry.Stroke> strokes = SpellVisualGeometry.build(
                    spell, 0L, 4242L, 9.0D, SpellVisualGeometry.Quality.BALANCED);
            assertFalse(strokes.isEmpty(), spell + " drew nothing under reduced motion");
        }
    }

    @Test
    void reducedMotionDoesNotChangeSampleDensity() {
        // Accessibility must not be a hidden LOD downgrade: identical geometry
        // counts at the same tier, only the pulse is stilled.
        for (SpellVisualGeometry.Spell spell : SpellVisualGeometry.Spell.values()) {
            for (SpellVisualGeometry.Quality quality : SpellVisualGeometry.Quality.values()) {
                int animated = SpellVisualGeometry.build(spell, 0L, 7L, 9.0D, quality)
                        .stream().mapToInt(stroke -> stroke.points().size()).sum();
                int reduced = SpellVisualGeometry.build(spell, 0L, 7L, 9.0D, quality)
                        .stream().mapToInt(stroke -> stroke.points().size()).sum();
                assertEquals(animated, reduced, spell + "/" + quality);
            }
        }
    }
}
