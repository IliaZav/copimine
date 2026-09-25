package me.copimine.client;

import net.minecraft.client.model.ModelPart;
import net.minecraft.util.math.Direction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regression coverage for the supplied Bedrock per-face UV importer.
 *
 * <p>UV replacement must keep the vertex topology produced by vanilla's
 * ModelPart.Cuboid. Reconstructing the six quads from min/max coordinates
 * changes face orientation on the imported model and makes the packed
 * Bedrock atlas land on the wrong physical surfaces.</p>
 */
class UserEndBossFaceUvTest {
    private static final float EPSILON = 0.0001F;

    @Test
    void remappingFaceUvKeepsVanillaQuadVertexTopology() {
        ModelPart.Vertex[] source = {
                new ModelPart.Vertex(1.0F, 2.0F, 3.0F, 0.0F, 0.0F),
                new ModelPart.Vertex(4.0F, 5.0F, 6.0F, 0.0F, 0.0F),
                new ModelPart.Vertex(7.0F, 8.0F, 9.0F, 0.0F, 0.0F),
                new ModelPart.Vertex(10.0F, 11.0F, 12.0F, 0.0F, 0.0F)
        };
        ModelPart.Quad template = new ModelPart.Quad(
                source, 0.0F, 0.0F, 1.0F, 1.0F,
                128.0F, 128.0F, false, Direction.NORTH);

        float[][] expectedPositions = new float[template.vertices.length][3];
        for (int index = 0; index < template.vertices.length; index++) {
            expectedPositions[index][0] = template.vertices[index].pos.x();
            expectedPositions[index][1] = template.vertices[index].pos.y();
            expectedPositions[index][2] = template.vertices[index].pos.z();
        }

        ModelPart.Quad remapped = UserEndBossModelData.remapFace(
                template, 8.0F, 16.0F, 24.0F, 32.0F, Direction.NORTH);

        assertEquals(4, remapped.vertices.length);
        for (int index = 0; index < remapped.vertices.length; index++) {
            assertEquals(expectedPositions[index][0], remapped.vertices[index].pos.x(), EPSILON);
            assertEquals(expectedPositions[index][1], remapped.vertices[index].pos.y(), EPSILON);
            assertEquals(expectedPositions[index][2], remapped.vertices[index].pos.z(), EPSILON);
        }

        assertEquals(24.0F / 128.0F, remapped.vertices[0].u, EPSILON);
        assertEquals(16.0F / 128.0F, remapped.vertices[0].v, EPSILON);
        assertEquals(8.0F / 128.0F, remapped.vertices[1].u, EPSILON);
        assertEquals(16.0F / 128.0F, remapped.vertices[1].v, EPSILON);
        assertEquals(8.0F / 128.0F, remapped.vertices[2].u, EPSILON);
        assertEquals(32.0F / 128.0F, remapped.vertices[2].v, EPSILON);
        assertEquals(24.0F / 128.0F, remapped.vertices[3].u, EPSILON);
        assertEquals(32.0F / 128.0F, remapped.vertices[3].v, EPSILON);
    }
}
