package me.copimine.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Section 9A.03(F)/9A.05: every authored cast owns one original sound that
 * fires once per cast, stays silent while the same telegraph refreshes and
 * comes back for the next cast. These are the pure decisions behind that
 * contract, testable without a live client.
 */
class SpellVisualSoundRuntimeTest {

    @Test
    void aCueSoundsOnFirstSightAndAgainOnANewCast() {
        assertTrue(SpellVisualSoundRuntime.shouldSound(null, 1_000L));
        assertTrue(SpellVisualSoundRuntime.shouldSound(1_000L, 5_000L));
    }

    @Test
    void refreshingTheSameTelegraphStaysSilent() {
        assertFalse(SpellVisualSoundRuntime.shouldSound(4_200L, 4_200L));
    }

    @Test
    void pitchStaysInsideTheAuthoredBandAndIsDeterministic() {
        for (long seed = 0L; seed < 500L; seed += 37L) {
            float pitch = SpellVisualSoundRuntime.pitchFor(seed);
            assertEquals(pitch, SpellVisualSoundRuntime.pitchFor(seed));
            assertTrue(pitch >= 0.94F && pitch <= 1.06F, "pitch out of band: " + pitch);
        }
    }

    @Test
    void everyAuthoredSpellHasItsOwnSoundEvent() {
        for (SpellVisualGeometry.Spell spell : SpellVisualGeometry.Spell.values()) {
            assertEquals("copimineclient", SpellVisualSoundRuntime.soundId(spell).getNamespace());
            assertEquals("spell." + spell.token(), SpellVisualSoundRuntime.soundId(spell).getPath());
        }
    }

    @Test
    void thePerFrameVoiceCapIsSmallAndPositive() {
        assertTrue(SpellVisualSoundRuntime.MAX_SOUNDS_PER_FRAME > 0);
        assertTrue(SpellVisualSoundRuntime.MAX_SOUNDS_PER_FRAME <= 8,
                "a burst must never stack into a wall of sound");
    }
}
