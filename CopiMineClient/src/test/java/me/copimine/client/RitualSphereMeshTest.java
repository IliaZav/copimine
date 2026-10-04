package me.copimine.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RitualSphereMeshTest {
    @Test void shieldIsACurvedShapedPlateRatherThanASquareOrARing() {
        assertEquals(48, RitualSphereMesh.shieldQuads().size());
        for (var quad : RitualSphereMesh.shieldQuads()) {
            for (var point : java.util.List.of(quad.first(), quad.second(), quad.third(), quad.fourth())) {
                assertTrue(Math.abs(point.x()) <= 0.621F && Math.abs(point.y()) <= 0.801F);
                assertEquals(point.x()*point.x()*0.32F, point.z(), 1e-6F);
                assertTrue(point.u() >= 0 && point.u() <= 1 && point.v() >= 0 && point.v() <= 1);
                if (point.y() < -0.79F) assertTrue(Math.abs(point.x()) <= 0.121F, "shield narrows to its bottom point");
            }
        }
    }
    @Test void materialHasAReadableRimAndTranslucentCentre() {
        assertEquals(65, RitualSphereMesh.surfaceAlpha(1));
        assertEquals(180, RitualSphereMesh.surfaceAlpha(0));
        assertTrue(RitualSphereMesh.surfaceAlpha(0.3) > RitualSphereMesh.surfaceAlpha(0.8));
        assertEquals(0, RitualSphereMesh.surfaceAlpha(Double.NaN));
    }
    @Test void sphereUsesBoundedRoundGeometryWithOutwardFacesAndWrappedUvs() {
        var quads = RitualSphereMesh.quads();
        assertEquals(512, quads.size());
        for (var quad : quads) {
            var a = quad.first(); var b = quad.second(); var c = quad.third();
            for (var p : java.util.List.of(a, b, c, quad.fourth())) {
                assertEquals(2.35, Math.sqrt(p.x()*p.x()+p.y()*p.y()+p.z()*p.z()), 1e-5);
                assertTrue(p.u() >= 0 && p.u() <= 1 && p.v() >= 0 && p.v() <= 1);
            }
            double nx = (b.y()-a.y())*(c.z()-a.z())-(b.z()-a.z())*(c.y()-a.y());
            double ny = (b.z()-a.z())*(c.x()-a.x())-(b.x()-a.x())*(c.z()-a.z());
            double nz = (b.x()-a.x())*(c.y()-a.y())-(b.y()-a.y())*(c.x()-a.x());
            assertTrue(nx*a.x()+ny*a.y()+nz*a.z() >= -1e-8, "shell winding faces outward");
        }
    }
    @Test void insideCameraCanSeeThePrisonBoundaryAndOutsideCanStillSeeTheShell() {
        assertTrue(RitualSphereMesh.visibleFrom(0, 0, 0), "the captive still needs a visible boundary");
        assertTrue(RitualSphereMesh.visibleFrom(0, 0.72, 0));
        assertTrue(RitualSphereMesh.visibleFrom(2.35, 0, 0));
        assertTrue(RitualSphereMesh.visibleFrom(0, 0, 3));
        assertFalse(RitualSphereMesh.visibleFrom(Double.NaN, 0, 0));
        assertFalse(RitualSphereMesh.visibleFrom(Double.POSITIVE_INFINITY, 0, 0));
    }
    @Test void insideUsesOnlyArcsWhileTheOutsideMembraneKeepsItsExistingThreshold() {
        assertFalse(RitualSphereMesh.shellVisibleFrom(0, 0, 0));
        assertFalse(RitualSphereMesh.shellVisibleFrom(0, 0.72, 0));
        assertFalse(RitualSphereMesh.shellVisibleFrom(2.35, 0, 0));
        assertTrue(RitualSphereMesh.shellVisibleFrom(0, 0, 3));
        assertFalse(RitualSphereMesh.shellVisibleFrom(Double.NaN, 0, 0));
    }
    @Test void interiorArcsAreThinBoundedAndFaceThePrisoner() {
        var arcs = RitualSphereMesh.interiorQuads(new RitualSphereMesh.InteriorView(0, 0, 0, 1, 0, 0, 0, 1, 0));
        assertEquals(48, arcs.size());
        double totalArea = 0;
        for (var quad : arcs) {
            var a = quad.first(); var b = quad.second(); var c = quad.third(); var d = quad.fourth();
            for (var p : java.util.List.of(a, b, c, d)) {
                assertEquals(RitualSphereMesh.RADIUS, Math.sqrt(p.x()*p.x()+p.y()*p.y()+p.z()*p.z()), 1e-5);
                assertTrue(p.u() >= 0 && p.u() <= 1 && p.v() >= 0 && p.v() <= 1);
            }
            double nx = (b.y()-a.y())*(c.z()-a.z())-(b.z()-a.z())*(c.y()-a.y());
            double ny = (b.z()-a.z())*(c.x()-a.x())-(b.x()-a.x())*(c.z()-a.z());
            double nz = (b.x()-a.x())*(c.y()-a.y())-(b.y()-a.y())*(c.x()-a.x());
            assertTrue(nx*a.x()+ny*a.y()+nz*a.z() < 0, "arc winding faces inward");
            totalArea += triangleArea(a, b, c) + triangleArea(a, c, d);
        }
        assertTrue(totalArea > 0 && totalArea < 4*Math.PI*RitualSphereMesh.RADIUS*RitualSphereMesh.RADIUS*0.02,
                "the arcs cover less than two percent of the shell, leaving open space");
    }
    @Test void interiorAlphaClearsAimLowerHudAndCloseClippingWhileKeepingPeripheralArcs() {
        var front = new RitualSphereMesh.InteriorView(0, 0, 0, 1, 0, 0, 0, 1, 0);
        assertEquals(0, RitualSphereMesh.interiorAlpha(RitualSphereMesh.quads().get(8*32), front));
        int visible = 0;
        for (var quad : RitualSphereMesh.interiorQuads(front)) {
            int alpha = RitualSphereMesh.interiorAlpha(quad, front);
            assertTrue(alpha >= 0 && alpha <= 64, "the inside never becomes a full membrane wall");
            if (alpha == 0) continue;
            visible++;
            for (var p : java.util.List.of(quad.first(), quad.second(), quad.third(), quad.fourth())) {
                double length = Math.sqrt(p.x()*p.x()+p.y()*p.y()+p.z()*p.z());
                assertTrue(p.x()/length < Math.cos(Math.toRadians(12)), "all visible vertices stay outside the aim cone");
                assertFalse(p.y()/length < -0.12 && p.x()/length > Math.cos(Math.toRadians(40)),
                        "no arc runs through the lower central HUD area");
            }
        }
        assertTrue(visible > 0, "peripheral arcs remain visible to identify the prison");
        var arc = RitualSphereMesh.interiorQuads(front).getFirst();
        var a = arc.first(); var b = arc.second(); var c = arc.third(); var d = arc.fourth();
        var near = new RitualSphereMesh.InteriorView((a.x()+b.x()+c.x()+d.x())/4-0.05,
                (a.y()+b.y()+c.y()+d.y())/4, (a.z()+b.z()+c.z()+d.z())/4, 1, 0, 0, 0, 1, 0);
        assertEquals(0, RitualSphereMesh.interiorAlpha(arc, near), "a close ribbon cannot clip over the screen");
        assertEquals(0, RitualSphereMesh.interiorAlpha(arc,
                new RitualSphereMesh.InteriorView(Double.NaN, 0, 0, 1, 0, 0, 0, 1, 0)));
        assertEquals(0, RitualSphereMesh.interiorAlpha(arc,
                new RitualSphereMesh.InteriorView(0, 0, 0, 0, 0, 0, 0, 1, 0)));
    }
    @Test void captiveCanSeePeripheralArcsInANormalFieldOfViewAtDifferentAngles() {
        for (double eyeHeight : new double[]{0, 0.72}) {
            for (int pitch : new int[]{-45, 0, 45}) {
                for (int yaw = 0; yaw < 360; yaw += 15) {
                    double a = Math.toRadians(yaw), b = Math.toRadians(pitch);
                    var view = new RitualSphereMesh.InteriorView(0, eyeHeight, 0,
                            Math.cos(a)*Math.cos(b), Math.sin(b), Math.sin(a)*Math.cos(b),
                            -Math.cos(a)*Math.sin(b), Math.cos(b), -Math.sin(a)*Math.sin(b));
                    int inFrame = 0;
                    for (var q : RitualSphereMesh.interiorQuads(view)) {
                        if (RitualSphereMesh.interiorAlpha(q, view) <= 0) continue;
                        double x = (q.first().x()+q.second().x()+q.third().x()+q.fourth().x())/4.0;
                        double y = (q.first().y()+q.second().y()+q.third().y()+q.fourth().y())/4.0-eyeHeight;
                        double z = (q.first().z()+q.second().z()+q.third().z()+q.fourth().z())/4.0;
                        double forward = x*view.forwardX()+y*view.forwardY()+z*view.forwardZ();
                        double up = x*view.upX()+y*view.upY()+z*view.upZ();
                        double side = -x*Math.sin(a)+z*Math.cos(a);
                        if (forward > 0 && Math.abs(side/forward) < Math.tan(Math.toRadians(35))
                                && Math.abs(up/forward) < Math.tan(Math.toRadians(21))) inFrame++;
                    }
                    assertTrue(inFrame > 0, "visible prison arcs at eye="+eyeHeight+", yaw="+yaw+", pitch="+pitch);
                }
            }
        }
    }
    @Test void cameraOffsetsAndPitchKeepWholeFacesFiniteAndOutsideAimAndHud() {
        for (double[] position : new double[][]{{0, 0, 0}, {0, 0.72, 0}, {1.8, 0.2, 0}}) {
            for (int pitch : new int[]{-60, -30, 0, 30, 60}) {
                for (int yaw = 0; yaw < 360; yaw += 45) {
                    double a = Math.toRadians(yaw), b = Math.toRadians(pitch);
                    var view = new RitualSphereMesh.InteriorView(position[0], position[1], position[2],
                            Math.cos(a)*Math.cos(b), Math.sin(b), Math.sin(a)*Math.cos(b),
                            -Math.cos(a)*Math.sin(b), Math.cos(b), -Math.sin(a)*Math.sin(b));
                    for (var q : RitualSphereMesh.interiorQuads(view)) {
                        int alpha = RitualSphereMesh.interiorAlpha(q, view);
                        assertTrue(alpha >= 0 && alpha <= 64);
                        for (var p : java.util.List.of(q.first(), q.second(), q.third(), q.fourth())) {
                            assertEquals(RitualSphereMesh.RADIUS, Math.sqrt(p.x()*p.x()+p.y()*p.y()+p.z()*p.z()), 1e-5);
                        }
                        if (alpha == 0) continue;
                        for (int i = 0; i <= 4; i++) {
                            for (int j = 0; j <= 4-i; j++) {
                                assertClearTrianglePoint(q.first(), q.second(), q.third(), i/4.0, j/4.0, view);
                                assertClearTrianglePoint(q.first(), q.third(), q.fourth(), i/4.0, j/4.0, view);
                            }
                        }
                    }
                }
            }
        }
    }
    @Test void unsafeCameraInputsProduceNoInteriorGeometry() {
        assertTrue(RitualSphereMesh.interiorQuads(null).isEmpty());
        assertTrue(RitualSphereMesh.interiorQuads(
                new RitualSphereMesh.InteriorView(Double.NaN, 0, 0, 1, 0, 0, 0, 1, 0)).isEmpty());
        assertTrue(RitualSphereMesh.interiorQuads(
                new RitualSphereMesh.InteriorView(0, 0, 3, 1, 0, 0, 0, 1, 0)).isEmpty());
        assertTrue(RitualSphereMesh.interiorQuads(
                new RitualSphereMesh.InteriorView(0, 0, 0, 0, 0, 0, 0, 1, 0)).isEmpty());
        assertTrue(RitualSphereMesh.interiorQuads(
                new RitualSphereMesh.InteriorView(0, 0, 0, 1, 0, 0, 1, 0, 0)).isEmpty());
        assertTrue(RitualSphereMesh.interiorQuads(
                new RitualSphereMesh.InteriorView(0, 0, 0, Double.POSITIVE_INFINITY, 0, 0, 0, 1, 0)).isEmpty());
    }
    private static void assertClearTrianglePoint(RitualSphereMesh.Point a, RitualSphereMesh.Point b,
                                                RitualSphereMesh.Point c, double u, double v,
                                                RitualSphereMesh.InteriorView view) {
        double x = a.x()*(1-u-v)+b.x()*u+c.x()*v-view.x();
        double y = a.y()*(1-u-v)+b.y()*u+c.y()*v-view.y();
        double z = a.z()*(1-u-v)+b.z()*u+c.z()*v-view.z();
        double length = Math.sqrt(x*x+y*y+z*z);
        double forward = (x*view.forwardX()+y*view.forwardY()+z*view.forwardZ())/length;
        double up = (x*view.upX()+y*view.upY()+z*view.upZ())/length;
        assertTrue(Double.isFinite(forward) && forward < Math.cos(Math.toRadians(12)),
                "triangle interiors stay outside the aim cone, as well as their corners");
        assertFalse(up < -0.12 && forward > Math.cos(Math.toRadians(40)),
                "triangle interiors stay outside the lower central HUD area");
    }
    private static double triangleArea(RitualSphereMesh.Point a, RitualSphereMesh.Point b, RitualSphereMesh.Point c) {
        double nx = (b.y()-a.y())*(c.z()-a.z())-(b.z()-a.z())*(c.y()-a.y());
        double ny = (b.z()-a.z())*(c.x()-a.x())-(b.x()-a.x())*(c.z()-a.z());
        double nz = (b.x()-a.x())*(c.y()-a.y())-(b.y()-a.y())*(c.x()-a.x());
        return Math.sqrt(nx*nx+ny*ny+nz*nz)/2;
    }
}
