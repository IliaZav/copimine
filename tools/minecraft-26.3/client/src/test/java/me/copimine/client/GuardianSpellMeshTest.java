package me.copimine.client;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GuardianSpellMeshTest {
    @Test void authoredMeshesHaveBoundedRealFacesUvsAndUnitNormals() {
        assertEquals(8,GuardianSpellMesh.names().size());
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
