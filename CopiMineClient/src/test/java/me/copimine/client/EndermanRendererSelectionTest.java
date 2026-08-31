package me.copimine.client;

import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EndermanRendererSelectionTest {
    private static final String ENTITY_UUID = "11111111-1111-1111-1111-111111111111";
    private static final Identifier VALID_TEXTURE = Identifier.of(
            "copimineclient", "textures/entity/rift_guardian_awakening.png");

    @Test
    void unboundEntityUsesVanillaSelection() {
        EndermanRendererSelection.Decision decision = select(ENTITY_UUID, null, VALID_TEXTURE, true);

        assertEquals(EndermanRendererSelection.Kind.VANILLA, decision.kind());
    }

    @Test
    void boundEntityWithAvailableTextureUsesGuardianSelection() {
        EndermanRendererSelection.Decision decision = select(ENTITY_UUID, ENTITY_UUID, VALID_TEXTURE, true);

        assertEquals(EndermanRendererSelection.Kind.GUARDIAN, decision.kind());
    }

    @Test
    void boundEntityWithMissingTextureUsesVanillaSelection() {
        EndermanRendererSelection.Decision decision = select(ENTITY_UUID, ENTITY_UUID, VALID_TEXTURE, false);

        assertEquals(EndermanRendererSelection.Kind.VANILLA, decision.kind());
    }

    @Test
    void boundEntityWithInvalidTextureUsesVanillaSelection() {
        EndermanRendererSelection.Decision decision = select(ENTITY_UUID, ENTITY_UUID,
                Identifier.of("minecraft", "textures/entity/enderman.png"), true);

        assertEquals(EndermanRendererSelection.Kind.VANILLA, decision.kind());
    }

    @Test
    void mismatchedBindingUsesVanillaSelection() {
        EndermanRendererSelection.Decision decision = select(
                ENTITY_UUID, "22222222-2222-2222-2222-222222222222", VALID_TEXTURE, true);

        assertEquals(EndermanRendererSelection.Kind.VANILLA, decision.kind());
    }

    @Test
    void modelSwapRestoresVanillaModelAndCleansUp() {
        EndermanRendererSelection.Decision decision = select(ENTITY_UUID, ENTITY_UUID, VALID_TEXTURE, true);
        Object vanilla = new Object();
        Object guardian = new Object();
        EndermanRendererSelection.ModelSwap<Object> swap = EndermanRendererSelection.begin(vanilla, guardian, decision);

        assertSame(guardian, swap.currentModel());
        assertTrue(swap.isActive());
        assertSame(vanilla, swap.restore());
        assertFalse(swap.isActive());
        assertSame(vanilla, swap.currentModel());
    }

    private static EndermanRendererSelection.Decision select(
            String entityUuid, String boundUuid, Identifier texture, boolean available) {
        return EndermanRendererSelection.select(entityUuid, boundUuid, texture, available);
    }
}
