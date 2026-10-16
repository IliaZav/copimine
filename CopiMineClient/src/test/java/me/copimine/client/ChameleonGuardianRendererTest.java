package me.copimine.client;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChameleonGuardianRendererTest {
    @Test
    void preservesTheSignedDownFaceUvEndpointsAndChameleonVertexOrder() {
        ChameleonGuardianGeometry geometry = ChameleonGuardianGeometry.load();
        ChameleonGuardianGeometry.Face sourceFace = geometry.findFace("head", 0, "down");
        List<ChameleonGuardianRenderer.EmittedVertex> emitted = new ArrayList<>();

        ChameleonGuardianRenderer.emit(sourceFace, emitted::add);

        assertEquals(4, emitted.size());
        assertEquals(38.0F, emitted.get(0).u(), 0.0001F);
        assertEquals(30.0F, emitted.get(0).v(), 0.0001F);
        assertEquals(30.0F, emitted.get(1).u(), 0.0001F);
        assertEquals(30.0F, emitted.get(1).v(), 0.0001F);
        assertEquals(30.0F, emitted.get(2).u(), 0.0001F);
        assertEquals(21.0F, emitted.get(2).v(), 0.0001F);
        assertEquals(38.0F, emitted.get(3).u(), 0.0001F);
        assertEquals(21.0F, emitted.get(3).v(), 0.0001F);
    }

    @Test
    void sourceInventoryEmitsNoSyntheticOrMissingFacesOrInvalidNormals() {
        List<ChameleonGuardianRenderer.EmittedVertex> emitted = new ArrayList<>();

        ChameleonGuardianRenderer.emitAll(ChameleonGuardianGeometry.load().restFaces(), emitted::add);

        assertEquals(714 * 4, emitted.size());
        assertTrue(emitted.stream().allMatch(vertex -> Float.isFinite(vertex.normalX())
                && Float.isFinite(vertex.normalY()) && Float.isFinite(vertex.normalZ())));
    }
}
