package me.copimine.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BlackFogChamberMeshTest {
    @Test void allShelterSizesHaveRollingOpaqueSidesAndACeilingWithoutInteriorGeometry() {
        for (int side=1;side<=3;side++) {
            var mesh=BlackFogChamberMesh.build(0,0,side,side,68,1,5);
            assertFalse(mesh.isEmpty());
            assertTrue(mesh.size()<=960);
            assertNotEquals(mesh,BlackFogChamberMesh.build(0,0,side,side,68,1,6));
            boolean ceiling=false;
            for(var q:mesh) for(var p:java.util.List.of(q.a(),q.b(),q.c(),q.d())) {
                assertTrue(Float.isFinite(p.x()) && Float.isFinite(p.y()) && Float.isFinite(p.z()));
                assertTrue((p.color()>>>24)>=234);
                boolean inside=p.x()>0 && p.x()<side && p.z()>0 && p.z()<side;
                if(inside) { assertTrue(p.y()>=71 && p.y()<=72); ceiling=true; }
                assertFalse(inside && p.y()>68 && p.y()<71,"clean chamber contains no smoke surface");
            }
            assertTrue(ceiling);
        }
    }
    @Test void invalidOrInactiveChambersProduceNoGeometry() {
        assertTrue(BlackFogChamberMesh.build(0,0,3,3,68,0,1).isEmpty());
        assertTrue(BlackFogChamberMesh.build(0,0,4,4,68,1,1).isEmpty());
        assertTrue(BlackFogChamberMesh.build(0,0,3,3,68,1,Double.NaN).isEmpty());
    }
}
