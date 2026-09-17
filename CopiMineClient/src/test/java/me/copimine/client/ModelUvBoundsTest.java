package me.copimine.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ModelUvBoundsTest {
    @Test
    void standardBoxRejectsUvFootprintOutsideAtlas() {
        assertThrows(IllegalArgumentException.class, () ->
                ModelUvBounds.requireStandardBoxFits(
                        64, 32,
                        56.0F, 0.0F,
                        6.0F, 1.3F, 0.45F));
    }

    @Test
    void standardBoxAcceptsFootprintInsideAtlas() {
        assertDoesNotThrow(() ->
                ModelUvBounds.requireStandardBoxFits(
                        64, 32,
                        0.0F, 0.0F,
                        8.0F, 8.0F, 8.0F));
    }
}
