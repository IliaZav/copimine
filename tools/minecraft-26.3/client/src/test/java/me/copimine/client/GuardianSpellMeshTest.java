package me.copimine.client;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GuardianSpellMeshTest {
    /**
     * The authored mesh set, which grew when the Guardian's runes, crowns,
     * summons and volleys received distinct geometry. The exact count is not
     * the contract -- the faces, UVs and unit normals below are -- so this
     * only has to stay in step with {@code GuardianSpellMesh.NAMES}.
     */
    private static final int AUTHORED_MESHES = 16;
    @Test void authoredMeshesHaveBoundedRealFacesUvsAndUnitNormals() {
        assertEquals(AUTHORED_MESHES,GuardianSpellMesh.names().size());
        assertEquals(AUTHORED_MESHES,GuardianSpellMesh.names().stream().filter(name->!name.isBlank()).distinct().count());
        for (String name:GuardianSpellMesh.names()) {
            var vertices=GuardianSpellMesh.vertices(name);
            assertFalse(vertices.isEmpty(),name);
            assertTrue(vertices.size()<=32*24,name);
            assertEquals(0,vertices.size()%24,name);
            for (var v:vertices) {
                assertTrue(Float.isFinite(v.x()+v.y()+v.z()),name);
                assertTrue(v.u()>0 && v.u()<1 && v.v()>0 && v.v()<1,name);
                assertEquals(1,v.nx()*v.nx()+v.ny()*v.ny()+v.nz()*v.nz(),.00001,name);
            }
            for (int i=0;i<vertices.size();i+=4) {
                var a=vertices.get(i);var b=vertices.get(i+1);var c=vertices.get(i+2);
                float ax=b.x()-a.x(),ay=b.y()-a.y(),az=b.z()-a.z();
                float bx=c.x()-b.x(),by=c.y()-b.y(),bz=c.z()-b.z();
                assertTrue((ay*bz-az*by)*a.nx()+(az*bx-ax*bz)*a.ny()+(ax*by-ay*bx)*a.nz()>0,
                        name+" reversed/degenerate face "+i);
            }
        }
    }

    @Test void malformedMeshesCannotCreateUnboundedGeometry() {
        assertThrows(IllegalArgumentException.class,()->GuardianSpellMesh.parse(
                JsonParser.parseString("{\"elements\":[]}").getAsJsonObject()));
        assertTrue(GuardianSpellMesh.vertices("not-a-spell").isEmpty());
    }
}
