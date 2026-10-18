package me.copimine.client;

import net.minecraft.client.renderer.entity.state.EvokerRenderState;
import net.minecraft.client.renderer.entity.state.SkeletonRenderState;
import net.minecraft.client.renderer.entity.state.WardenRenderState;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.InputStream;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class Wave7NamedTrialVisualPolicyTest {
    @Test
    void namedVisualsDispatchOnlyToTheirMatchingActorRenderStates() {
        assertEquals(Wave7NamedTrialVisualPolicy.ActorModel.WARDEN,
                Wave7NamedTrialVisualPolicy.modelFor(" end_rift_wave7_warden_v1 "));
        assertEquals(Wave7NamedTrialVisualPolicy.ActorModel.ARCHMAGE,
                Wave7NamedTrialVisualPolicy.modelFor(Wave7NamedTrialVisualPolicy.ARCHMAGE_VISUAL));
        assertEquals(Wave7NamedTrialVisualPolicy.ActorModel.MARKSMAN,
                Wave7NamedTrialVisualPolicy.modelFor(Wave7NamedTrialVisualPolicy.MARKSMAN_VISUAL));

        assertTrue(Wave7NamedTrialVisualPolicy.matchesRenderState(
                Wave7NamedTrialVisualPolicy.WARDEN_VISUAL, WardenRenderState.class));
        assertTrue(Wave7NamedTrialVisualPolicy.matchesRenderState(
                Wave7NamedTrialVisualPolicy.ARCHMAGE_VISUAL, EvokerRenderState.class));
        assertTrue(Wave7NamedTrialVisualPolicy.matchesRenderState(
                Wave7NamedTrialVisualPolicy.MARKSMAN_VISUAL, SkeletonRenderState.class));

        assertFalse(Wave7NamedTrialVisualPolicy.matchesRenderState(
                Wave7NamedTrialVisualPolicy.WARDEN_VISUAL, SkeletonRenderState.class));
        assertFalse(Wave7NamedTrialVisualPolicy.matchesRenderState(
                Wave7NamedTrialVisualPolicy.ARCHMAGE_VISUAL, WardenRenderState.class));
        assertFalse(Wave7NamedTrialVisualPolicy.matchesRenderState(
                Wave7NamedTrialVisualPolicy.MARKSMAN_VISUAL, EvokerRenderState.class));
        assertNull(Wave7NamedTrialVisualPolicy.modelFor("END_RIFT_WAVE7_UNKNOWN_V1"));
        assertNull(Wave7NamedTrialVisualPolicy.modelFor(null));
    }

    @Test
    void eachNamedTrialUsesItsDedicatedModelImplementation() {
        var renderer = new Wave7NamedTrialModelRenderer();
        assertTrue(renderer.modelFor(Wave7NamedTrialVisualPolicy.ActorModel.WARDEN)
                instanceof Wave7WardenModel);
        assertTrue(renderer.modelFor(Wave7NamedTrialVisualPolicy.ActorModel.ARCHMAGE)
                instanceof Wave7ArchmageModel);
        assertNull(renderer.modelFor(Wave7NamedTrialVisualPolicy.ActorModel.MARKSMAN));

        var skeletons = new RiftEventSkeletonModelRenderer();
        assertEquals(RiftEventSkeletonModel.Variant.WAVE7_MARKSMAN,
                ((RiftEventSkeletonModel) skeletons.modelFor(
                        Wave7NamedTrialVisualPolicy.MARKSMAN_VISUAL)).variant());
    }

    @Test
    void namedVisualsHaveSeparateClasspathTexturesWithExpectedAtlases() throws Exception {
        Map<String, int[]> expectedSizes = Map.of(
                Wave7NamedTrialVisualPolicy.WARDEN_VISUAL, new int[]{128, 128},
                Wave7NamedTrialVisualPolicy.ARCHMAGE_VISUAL, new int[]{64, 64},
                Wave7NamedTrialVisualPolicy.MARKSMAN_VISUAL, new int[]{64, 32});
        Identifier warden = EndEventTextureCatalog.textureForVisual(
                Wave7NamedTrialVisualPolicy.WARDEN_VISUAL);
        Identifier archmage = EndEventTextureCatalog.textureForVisual(
                Wave7NamedTrialVisualPolicy.ARCHMAGE_VISUAL);
        Identifier marksman = EndEventTextureCatalog.textureForVisual(
                Wave7NamedTrialVisualPolicy.MARKSMAN_VISUAL);
        assertNotNull(warden);
        assertNotNull(archmage);
        assertNotNull(marksman);
        assertNotEquals(warden, archmage);
        assertNotEquals(warden, marksman);
        assertNotEquals(archmage, marksman);

        for (Map.Entry<String, int[]> entry : expectedSizes.entrySet()) {
            Identifier texture = EndEventTextureCatalog.textureForVisual(entry.getKey());
            String resource = "assets/" + texture.getNamespace() + "/" + texture.getPath();
            try (InputStream stream = getClass().getClassLoader().getResourceAsStream(resource)) {
                assertNotNull(stream, "missing named trial texture: " + resource);
                var image = ImageIO.read(stream);
                assertNotNull(image, "invalid PNG texture: " + resource);
                assertEquals(entry.getValue()[0], image.getWidth(), resource);
                assertEquals(entry.getValue()[1], image.getHeight(), resource);
            }
        }
    }
}
