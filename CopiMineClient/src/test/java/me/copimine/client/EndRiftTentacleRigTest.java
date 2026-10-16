package me.copimine.client;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EndRiftTentacleRigTest {
    @Test
    void rigUsesTheSixArtistAuthoredBonesAndNoInventedClaws() {
        assertEquals(8, EndRiftTentacleRig.TEXTURE_UV_WIDTH);
        assertEquals(6, EndRiftTentacleRig.definitions().size());
        assertEquals(EndRiftTentacleRig.REQUIRED_BONES, EndRiftTentacleRig.definitions().stream()
                .map(EndRiftTentacleRig.BoneDefinition::name).toList());
        assertTrue(EndRiftTentacleRig.REQUIRED_BONES.contains("3layer2"));
        assertTrue(EndRiftTentacleRig.REQUIRED_BONES.stream()
                .noneMatch(name -> name.startsWith("tip_claw")));
    }

    @Test
    void importedGeometryStaysInsideTheSourceModelBounds() {
        KaguneModelImporter.ImportedModel model = KaguneModelImporter.load();
        assertEquals(6, model.elements().size());
        assertEquals(6, model.bones().size());
        assertEquals(64, model.textureWidth());
        assertEquals(64, model.textureHeight());
        assertEquals(8, model.textureUvWidth());
        assertEquals(8, model.textureUvHeight());
        assertEquals(7.8681F, model.bounds().height(), 0.002F);
        assertEquals(2.475F, model.bounds().width(), 0.002F);
        assertTrue(model.elements().stream().allMatch(element -> element.faces().size() == 6));
    }

    @Test
    void renderRigKeepsTheThreeArtistAuthoredRootSectionsIndependent() throws Exception {
        Field rootsField = EndRiftTentacleRig.RenderRig.class.getDeclaredField("roots");
        rootsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<KaguneModelImporter.Bone> roots =
                (List<KaguneModelImporter.Bone>) rootsField.get(EndRiftTentacleRig.createRenderRig());

        assertEquals(List.of("1layer", "2layer", "3layer"),
                roots.stream().map(KaguneModelImporter.Bone::name).toList(),
                "render hierarchy must follow the three root sections in the supplied model");
    }
}
