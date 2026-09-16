package me.copimine.client;

import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class EndermanRendererSelectionTest {
    private static final String ENTITY_UUID = "11111111-1111-1111-1111-111111111111";
    private static final Identifier VALID_TEXTURE = Identifier.of(
            "copimineclient", "textures/entity/end_rift_user_boss.png");

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
    void ordinaryEventEndermanUsesItsOwnGeometry() {
        EndermanRendererSelection.Decision decision = EndermanRendererSelection.selectVisual(
                ENTITY_UUID, "END_RIFT_ENDERMAN_V1", null,
                Identifier.of("copimineclient", "textures/entity/end_rift_user_enderman.png"), true);

        assertEquals(EndermanRendererSelection.Kind.EVENT_ENDERMAN, decision.kind());
        assertEquals("END_RIFT_ENDERMAN_V1", decision.modelId());
        assertEquals("end_rift_enderman_v1", decision.geometryId());
        assertNotNull(decision.animationSet());
    }

    @Test
    void diagnosticLineExposesTheCompleteRuntimeSelectionContract() {
        EndermanRendererSelection.Decision decision = EndermanRendererSelection.selectVisual(
                ENTITY_UUID, "END_RIFT_ENDERMAN_V1", null,
                Identifier.of("copimineclient", "textures/entity/end_rift_user_enderman.png"), true);

        assertEquals(
                "variant=EVENT_ENDERMAN, model=END_RIFT_ENDERMAN_V1, "
                        + "geometry=end_rift_enderman_v1, "
                        + "texture=copimineclient:textures/entity/end_rift_user_enderman.png, "
                        + "animationSet=END_RIFT_ENDERMAN",
                decision.diagnosticLine());
    }

    @Test
    void diagnosticLineForSpiderExposesItsIndependentRendererContract() {
        Identifier texture = Identifier.of(
                "copimineclient", "textures/entity/end_rift_user_spider.png");

        assertEquals(
                "variant=EVENT_SPIDER, model=END_RIFT_SPIDER_V1, "
                        + "geometry=end_rift_spider_v1, texture=copimineclient:textures/entity/end_rift_user_spider.png, "
                        + "animationSet=END_RIFT_SPIDER, resourcePresent=true",
                EndermanRendererSelection.diagnosticLineForVisual(
                        "END_RIFT_SPIDER_V1", texture, true));
    }

    @Test
    void diagnosticAcceptsTheServerBossVisualAlias() {
        assertEquals(
                "variant=GUARDIAN, model=END_RIFT_GUARDIAN_V1, "
                        + "geometry=end_rift_guardian, "
                        + "texture=copimineclient:textures/entity/end_rift_user_boss.png, "
                        + "animationSet=END_RIFT_GUARDIAN, resourcePresent=true",
                EndermanRendererSelection.diagnosticLineForVisual(
                        "END_RIFT_GUARDIAN", VALID_TEXTURE, true));
    }

    @Test
    void eliteEventEndermanUsesEliteGeometryAndDoesNotBecomeGuardian() {
        EndermanRendererSelection.Decision decision = EndermanRendererSelection.selectVisual(
                ENTITY_UUID, "END_RIFT_ELITE_V1", null,
                Identifier.of("copimineclient", "textures/entity/end_rift_elite.png"), true);

        assertEquals(EndermanRendererSelection.Kind.ELITE, decision.kind());
        assertEquals("END_RIFT_ELITE_V1", decision.modelId());
        assertEquals("end_rift_elite_v1", decision.geometryId());
        assertEquals(EndermanRendererSelection.Kind.ELITE, decision.kind());
    }

    @Test
    void specialWaveGuardianUsesItsOwnGeometryAndTextureContract() {
        EndermanRendererSelection.Decision decision = EndermanRendererSelection.selectVisual(
                ENTITY_UUID, "END_RIFT_WAVE_GUARDIAN_ENDERMAN_V1", null,
                Identifier.of("copimineclient", "textures/entity/end_rift_wave_guardian_enderman.png"), true);

        assertEquals("END_RIFT_WAVE_GUARDIAN_ENDERMAN_V1", decision.modelId());
        assertEquals("end_rift_wave_guardian_enderman_v1", decision.geometryId());
        assertEquals("END_RIFT_WAVE_GUARDIAN", decision.animationSet());
        assertTrue(decision.usesCustomModel());
    }

    @Test
    void ordinaryVanillaEndermanRemainsVanillaAndHasNoEventMetadata() {
        EndermanRendererSelection.Decision decision = EndermanRendererSelection.selectVisual(
                ENTITY_UUID, "", null,
                Identifier.of("minecraft", "textures/entity/enderman/enderman.png"), true);

        assertEquals(EndermanRendererSelection.Kind.VANILLA, decision.kind());
        assertEquals("VANILLA_ENDERMAN", decision.modelId());
        assertEquals("vanilla", decision.geometryId());
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
