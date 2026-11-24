package me.copimine.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Section 9A.03(C) contract: every authored spell has 4-8 semantic animation
 * keys with an authored tempo and ignition curve, the re-time can never run
 * backwards, and the cast arc never moves the announced silhouette.
 */
class SpellVisualEnvelopeTest {

    private static final long SEED = 424242L;

    @Test
    void everyFamilyCarriesSevenMonotoneAuthoredKeys() {
        for (SpellVisualGeometry.Spell spell : SpellVisualGeometry.Spell.values()) {
            long[] tempo = SpellVisualEnvelope.tempoOf(spell);
            double[] heat = SpellVisualEnvelope.heatOf(spell);
            assertEquals(SpellVisualEnvelope.KEY_COUNT, tempo.length, spell + " tempo keys");
            assertEquals(SpellVisualEnvelope.KEY_COUNT, heat.length, spell + " heat keys");
            assertEquals(0L, tempo[0], spell + " must start at the authored origin");
            assertEquals(SpellVisualEnvelope.CAST_WINDOW_MILLIS, tempo[tempo.length - 1],
                    spell + " must end exactly at the authored window");
            for (int index = 1; index < tempo.length; index++) {
                assertTrue(tempo[index] > tempo[index - 1],
                        spell + " tempo keys must strictly increase at " + index);
            }
            for (double value : heat) {
                assertTrue(value >= 0.30D && value <= 1.0D,
                        spell + " heat escaped the authored band: " + value);
            }
        }
        for (int index = 1; index < SpellVisualEnvelope.KEY_COUNT; index++) {
            assertTrue(SpellVisualEnvelope.keyMillis(index) > SpellVisualEnvelope.keyMillis(index - 1),
                    "key times must strictly increase");
        }
    }

    @Test
    void tempoNeverRunsBackwardsAndContinuesOneToOneAfterTheWindow() {
        for (SpellVisualGeometry.Spell spell : SpellVisualGeometry.Spell.values()) {
            long previous = SpellVisualEnvelope.tempoMillis(spell, 0L);
            assertEquals(0L, previous, spell + " must start at zero");
            for (long elapsed = 7L; elapsed <= 5_000L; elapsed += 7L) {
                long value = SpellVisualEnvelope.tempoMillis(spell, elapsed);
                assertTrue(value >= previous,
                        spell + " ran the authored clock backwards at " + elapsed);
                previous = value;
            }
            for (long elapsed = SpellVisualEnvelope.CAST_WINDOW_MILLIS; elapsed <= 8_000L; elapsed += 250L) {
                assertEquals(elapsed, SpellVisualEnvelope.tempoMillis(spell, elapsed),
                        spell + " must continue 1:1 after the authored window");
            }
        }
    }

    @Test
    void eachFamilyHasItsOwnAuthoredRhythm() {
        // Not every cast may share one easing: the spec demands a different
        // rhythm per family, so compare the sampled tempo vectors.
        Set<List<Long>> distinct = new HashSet<>();
        for (SpellVisualGeometry.Spell spell : SpellVisualGeometry.Spell.values()) {
            List<Long> samples = new ArrayList<>();
            for (long elapsed = 0L; elapsed <= SpellVisualEnvelope.CAST_WINDOW_MILLIS; elapsed += 50L) {
                samples.add(SpellVisualEnvelope.tempoMillis(spell, elapsed));
            }
            distinct.add(samples);
        }
        assertTrue(distinct.size() >= 7,
                "the roster needs genuinely distinct authored rhythms, saw " + distinct.size());
        // The heavy crush winds up slower than it releases; a hunt tear snaps early.
        assertTrue(SpellVisualEnvelope.tempoMillis(SpellVisualGeometry.Spell.CROWN, 540L) < 540L,
                "the awakening crush must still be winding up at the energized key");
        assertTrue(SpellVisualEnvelope.tempoMillis(SpellVisualGeometry.Spell.FANG_LINE, 240L)
                        > 240L,
                "the hunt tear must already be past its anticipation");
    }

    @Test
    void heatIgnitesBeforeTheReleaseAndCoolsAfterwards() {
        for (SpellVisualGeometry.Spell spell : SpellVisualGeometry.Spell.values()) {
            double peak = 0.0D;
            for (long elapsed = 0L; elapsed <= SpellVisualEnvelope.CAST_WINDOW_MILLIS; elapsed += 10L) {
                double value = SpellVisualEnvelope.heat(spell, elapsed);
                assertTrue(Double.isFinite(value) && value >= 0.0D && value <= 1.0D,
                        spell + " heat out of range at " + elapsed + ": " + value);
                peak = Math.max(peak, value);
            }
            assertEquals(1.0D, peak, 1.0E-9D, spell + " must reach its full ignition");
            assertTrue(SpellVisualEnvelope.heat(spell, 0L) < 0.80D,
                    spell + " must start dim rather than already hot");
            assertTrue(SpellVisualEnvelope.heat(spell, SpellVisualEnvelope.CAST_WINDOW_MILLIS)
                            < peak,
                    spell + " must be cooling at the end of the authored window");
            assertEquals(SpellVisualEnvelope.heat(spell, SpellVisualEnvelope.CAST_WINDOW_MILLIS),
                    SpellVisualEnvelope.heat(spell, 9_000L), 1.0E-9D,
                    spell + " keeps its settled heat after the window");
        }
    }

    @Test
    void semanticBeatsFollowTheAuthoredCastOrder() {
        for (SpellVisualGeometry.Spell spell : SpellVisualGeometry.Spell.values()) {
            assertEquals(SpellVisualEnvelope.Beat.DORMANT,
                    SpellVisualEnvelope.beat(spell, 0L), spell + " first beat");
            assertEquals(SpellVisualEnvelope.Beat.ANTICIPATION,
                    SpellVisualEnvelope.beat(spell, SpellVisualEnvelope.keyMillis(1)), spell + " beat 1");
            assertEquals(SpellVisualEnvelope.Beat.ENERGIZED,
                    SpellVisualEnvelope.beat(spell, SpellVisualEnvelope.keyMillis(2)), spell + " beat 2");
            assertEquals(SpellVisualEnvelope.Beat.RELEASE,
                    SpellVisualEnvelope.beat(spell, SpellVisualEnvelope.keyMillis(3)), spell + " beat 3");
            assertEquals(SpellVisualEnvelope.Beat.PEAK,
                    SpellVisualEnvelope.beat(spell, SpellVisualEnvelope.keyMillis(4)), spell + " beat 4");
            assertEquals(SpellVisualEnvelope.Beat.DISSIPATE,
                    SpellVisualEnvelope.beat(spell, SpellVisualEnvelope.keyMillis(5)), spell + " beat 5");
            assertEquals(SpellVisualEnvelope.Beat.COMPLETED,
                    SpellVisualEnvelope.beat(spell, SpellVisualEnvelope.CAST_WINDOW_MILLIS), spell + " beat 6");
            assertEquals(SpellVisualEnvelope.Beat.COMPLETED,
                    SpellVisualEnvelope.beat(spell, 20_000L), spell + " stays completed");
        }
    }

    @Test
    void authoredArcChangesBrightnessButNeverTheSilhouette() {
        for (SpellVisualGeometry.Spell spell : SpellVisualGeometry.Spell.values()) {
            for (long elapsed : new long[]{120L, 540L, 1_180L, 1_900L}) {
                long warped = SpellVisualEnvelope.tempoMillis(spell, elapsed);
                // Re-timing maps the cast instant onto an authored instant; the
                // built points and widths must be exactly the shape-only ones.
                List<SpellVisualGeometry.Stroke> now = SpellVisualGeometry.build(
                        spell, elapsed, SEED, 9.0D, SpellVisualGeometry.Quality.BALANCED);
                List<SpellVisualGeometry.Stroke> shape = SpellVisualGeometry.authored(
                        spell, warped, SEED, 9.0D, SpellVisualGeometry.Quality.BALANCED);
                assertEquals(points(shape), points(now),
                        spell + " the ignition or re-time moved the mesh at " + elapsed);
                assertEquals(widths(shape), widths(now),
                        spell + " the ignition or re-time changed a half-width at " + elapsed);
            }
        }
        // The ignition really does drive the material at the start of a cast.
        double dim = maxBrightness(SpellVisualGeometry.build(SpellVisualGeometry.Spell.CROWN,
                0L, SEED, 9.0D, SpellVisualGeometry.Quality.BALANCED));
        double hot = maxBrightness(SpellVisualGeometry.build(SpellVisualGeometry.Spell.CROWN,
                860L, SEED, 9.0D, SpellVisualGeometry.Quality.BALANCED));
        assertTrue(dim < hot, "the crown must ignite from a dim authored frame");
    }

    @Test
    void envelopeIsFrameRateIndependentDeterministicArithmetic() {
        for (SpellVisualGeometry.Spell spell : SpellVisualGeometry.Spell.values()) {
            for (long elapsed : new long[]{0L, 333L, 999L, 2_000L, 6_000L}) {
                assertEquals(SpellVisualEnvelope.tempoMillis(spell, elapsed),
                        SpellVisualEnvelope.tempoMillis(spell, elapsed), "tempo must be stable");
                assertEquals(SpellVisualEnvelope.heat(spell, elapsed),
                        SpellVisualEnvelope.heat(spell, elapsed), 0.0D, "heat must be stable");
                assertEquals(SpellVisualEnvelope.beat(spell, elapsed),
                        SpellVisualEnvelope.beat(spell, elapsed), "beat must be stable");
            }
        }
        // The identity-clock families are exactly the ones already owning an
        // internal phase arc; that list may not grow silently.
        Set<SpellVisualGeometry.Spell> identity = new HashSet<>();
        for (SpellVisualGeometry.Spell spell : SpellVisualGeometry.Spell.values()) {
            boolean linear = true;
            for (long elapsed = 0L; elapsed <= SpellVisualEnvelope.CAST_WINDOW_MILLIS; elapsed += 61L) {
                linear &= SpellVisualEnvelope.tempoMillis(spell, elapsed) == elapsed;
            }
            if (linear) {
                identity.add(spell);
            }
        }
        assertEquals(Set.of(SpellVisualGeometry.Spell.GRAVITY,
                        SpellVisualGeometry.Spell.BEAM,
                        SpellVisualGeometry.Spell.GEYSER),
                identity, "only the self-arc families may keep the identity clock");
    }

    private static List<SpellVisualGeometry.Point> points(List<SpellVisualGeometry.Stroke> strokes) {
        List<SpellVisualGeometry.Point> flat = new ArrayList<>();
        for (SpellVisualGeometry.Stroke stroke : strokes) {
            flat.addAll(stroke.points());
        }
        return flat;
    }

    private static List<Float> widths(List<SpellVisualGeometry.Stroke> strokes) {
        List<Float> flat = new ArrayList<>();
        for (SpellVisualGeometry.Stroke stroke : strokes) {
            flat.addAll(stroke.halfWidths());
        }
        return flat;
    }

    private static double maxBrightness(List<SpellVisualGeometry.Stroke> strokes) {
        return strokes.stream().mapToDouble(SpellVisualGeometry.Stroke::brightness).max().orElse(0.0D);
    }
}
