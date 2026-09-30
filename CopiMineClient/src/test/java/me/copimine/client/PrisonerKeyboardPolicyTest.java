package me.copimine.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrisonerKeyboardPolicyTest {
    @Test
    void passesKeyReleasesThroughSoBindingsCannotStickAfterCapture() {
        assertFalse(PrisonerKeyboardPolicy.shouldCaptureKeyEvent(true, false, false, 0));
        assertTrue(PrisonerKeyboardPolicy.shouldCaptureKeyEvent(true, false, false, 1));
        assertTrue(PrisonerKeyboardPolicy.shouldCaptureKeyEvent(true, false, false, 2));
        assertFalse(PrisonerKeyboardPolicy.shouldCaptureKeyEvent(false, false, false, 1));
        assertFalse(PrisonerKeyboardPolicy.shouldCaptureKeyEvent(true, true, false, 1));
        assertFalse(PrisonerKeyboardPolicy.shouldCaptureKeyEvent(true, false, true, 1));
    }
}
