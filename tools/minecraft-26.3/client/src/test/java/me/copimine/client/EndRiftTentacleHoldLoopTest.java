package me.copimine.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The grab chain broke its own animation.
 *
 * <p>Two defects combined. The supplied HOLD clip is authored with its last
 * keyframe at 0.50 s but declares a 1.208 s length, and the renderer sampled it
 * against the declared length, so the rig spent the final 58 percent of every
 * hold frozen on the endpoint. HOLD was also driven as a once-clip even though
 * the server keeps the player locked in the socket for the entire window and
 * then transitions to THROW, whose clock re-bases to zero.</p>
 */
public final class EndRiftTentacleHoldLoopTest {
    @Test
    void holdIsDrivenAsALoop() {
        assertTrue(EndRiftTentacleAnimator.loops("HOLD"),
                "HOLD is a sustained grab and must cycle, not freeze on its endpoint");
        assertEquals(EndRiftTentacleAnimator.loops("READY"),
                EndRiftTentacleAnimator.loops("READY"));
    }

    @Test
    void throwAndTelegraphStayOnceClips() {
        // These are one-shot beats: looping them would replay the wind-up
        // inside a single server window.
        assertFalse(EndRiftTentacleAnimator.loops("THROW"));
        assertFalse(EndRiftTentacleAnimator.loops("GRAB_SUCCESS"));
        assertFalse(EndRiftTentacleAnimator.loops("TELEGRAPH_GRAB"));
        assertFalse(EndRiftTentacleAnimator.loops("MISS_RECOVERY"));
    }

    @Test
    void holdSamplingSpansTheKeyframedExtent() {
        // The clip's real motion ends at 0.50 of 1.208 seconds. Progress must be
        // rescaled so the authored motion fills the whole window instead of
        // stalling at 41 percent.
        float mid = EndRiftTentacleAnimator.clipProgress("HOLD", 0.5F);
        assertTrue(mid > 0.85F && mid <= 1.0F,
                "halfway through HOLD must already be near the authored endpoint, was " + mid);
        assertEquals(0.0F, EndRiftTentacleAnimator.clipProgress("HOLD", 0.0F), 1.0e-6F);
        assertEquals(1.0F, EndRiftTentacleAnimator.clipProgress("HOLD", 1.0F), 1.0e-6F);
    }

    @Test
    void clipsWithCompleteKeyframesAreNotRescaled() {
        // A clip whose keyframes already cover its declared length must be
        // sampled one-to-one; rescaling it would speed the animation up.
        for (String state : new String[]{"THROW", "TELEGRAPH_GRAB", "MISS_RECOVERY", "READY"}) {
            assertEquals(0.5F, EndRiftTentacleAnimator.clipProgress(state, 0.5F), 1.0e-5F,
                    state + " must sample progress one-to-one");
        }
    }

    @Test
    void outOfRangeProgressIsClamped() {
        assertEquals(0.0F, EndRiftTentacleAnimator.clipProgress("HOLD", -3.0F), 1.0e-6F);
        assertEquals(1.0F, EndRiftTentacleAnimator.clipProgress("HOLD", 9.0F), 1.0e-6F);
        assertEquals(0.0F, EndRiftTentacleAnimator.clipProgress("HOLD", Float.NaN), 1.0e-6F);
    }
}
