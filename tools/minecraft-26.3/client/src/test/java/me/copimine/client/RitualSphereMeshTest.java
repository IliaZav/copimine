package me.copimine.client;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RitualSphereMeshTest {
    @Test
    void shieldIsACurvedShapedPlateRatherThanASquareOrARing() {
        assertEquals(48, RitualSphereMesh.shieldQuads().size());
        for (var quad : RitualSphereMesh.shieldQuads()) {
            for (var point : List.of(quad.first(), quad.second(), quad.third(), quad.fourth())) {
                assertTrue(Math.abs(point.x()) <= 0.621F && Math.abs(point.y()) <= 0.801F);
                assertEquals(point.x() * point.x() * 0.32F, point.z(), 1e-6F);
                assertTrue(point.u() >= 0 && point.u() <= 1 && point.v() >= 0 && point.v() <= 1);
                if (point.y() < -0.79F) {
                    assertTrue(Math.abs(point.x()) <= 0.121F, "shield narrows to its bottom point");
                }
            }
        }
    }

    @Test
    void materialHasAReadableRimAndTranslucentCentre() {
        assertEquals(65, RitualSphereMesh.surfaceAlpha(1));
        assertEquals(180, RitualSphereMesh.surfaceAlpha(0));
        assertTrue(RitualSphereMesh.surfaceAlpha(0.3) > RitualSphereMesh.surfaceAlpha(0.8));
        assertEquals(0, RitualSphereMesh.surfaceAlpha(Double.NaN));
    }

    @Test
    void sphereUsesBoundedRoundGeometryWithOutwardFacesAndWrappedUvs() {
        var quads = RitualSphereMesh.quads();
        assertEquals(512, quads.size());
        for (var quad : quads) {
            var a = quad.first();
            var b = quad.second();
            var c = quad.third();
            for (var point : List.of(a, b, c, quad.fourth())) {
                assertEquals(RitualSphereMesh.RADIUS,
                        Math.sqrt(point.x() * point.x() + point.y() * point.y() + point.z() * point.z()),
                        1e-5);
                assertTrue(point.u() >= 0 && point.u() <= 1 && point.v() >= 0 && point.v() <= 1);
            }
            double nx = (b.y() - a.y()) * (c.z() - a.z()) - (b.z() - a.z()) * (c.y() - a.y());
            double ny = (b.z() - a.z()) * (c.x() - a.x()) - (b.x() - a.x()) * (c.z() - a.z());
            double nz = (b.x() - a.x()) * (c.y() - a.y()) - (b.y() - a.y()) * (c.x() - a.x());
            assertTrue(nx * a.x() + ny * a.y() + nz * a.z() >= -1e-8,
                    "shell winding faces outward");
        }
    }

    @Test
    void insideCameraUsesOpenInteriorArcsWhileOutsideCameraSeesTheShell() {
        assertTrue(RitualSphereMesh.visibleFrom(0, 0, 0), "the captive still needs a visible boundary");
        assertTrue(RitualSphereMesh.visibleFrom(0, 0.72, 0));
        assertTrue(RitualSphereMesh.visibleFrom(0, 0, 3));
        assertFalse(RitualSphereMesh.visibleFrom(Double.NaN, 0, 0));
        assertFalse(RitualSphereMesh.visibleFrom(Double.POSITIVE_INFINITY, 0, 0));

        assertFalse(RitualSphereMesh.shellVisibleFrom(0, 0, 0));
        assertFalse(RitualSphereMesh.shellVisibleFrom(0, 0.72, 0));
        assertTrue(RitualSphereMesh.shellVisibleFrom(0, 0, 3));
        assertFalse(RitualSphereMesh.shellVisibleFrom(Double.NaN, 0, 0));

        var view = new RitualSphereMesh.InteriorView(0, 0, 0,
                1, 0, 0, 0, 1, 0);
        var arcs = RitualSphereMesh.interiorQuads(view);
        assertEquals(48, arcs.size());
        assertTrue(RitualSphereMesh.interiorQuads(new RitualSphereMesh.InteriorView(
                0, 0, 3, 1, 0, 0, 0, 1, 0)).isEmpty());
        assertTrue(RitualSphereMesh.interiorQuads(new RitualSphereMesh.InteriorView(
                Double.NaN, 0, 0, 1, 0, 0, 0, 1, 0)).isEmpty());
    }

    @Test
    void interiorArcsRemainOnTheShellAndLeaveAimAndLowerHudClear() {
        var view = new RitualSphereMesh.InteriorView(0, 0, 0,
                1, 0, 0, 0, 1, 0);
        assertEquals(0, RitualSphereMesh.interiorAlpha(RitualSphereMesh.quads().get(8 * 32), view));
        int visible = 0;
        for (var quad : RitualSphereMesh.interiorQuads(view)) {
            int alpha = RitualSphereMesh.interiorAlpha(quad, view);
            assertTrue(alpha >= 0 && alpha <= 64, "the inside never becomes a full membrane wall");
            if (alpha == 0) continue;
            visible++;
            for (var point : List.of(quad.first(), quad.second(), quad.third(), quad.fourth())) {
                double length = Math.sqrt(point.x() * point.x()
                        + point.y() * point.y() + point.z() * point.z());
                assertTrue(point.x() / length < Math.cos(Math.toRadians(12)),
                        "visible vertices stay outside the aim cone");
                assertFalse(point.y() / length < -0.12 && point.x() / length > Math.cos(Math.toRadians(40)),
                        "no visible arc runs through the lower central HUD area");
                assertEquals(RitualSphereMesh.RADIUS, length, 1e-5);
            }
        }
        assertTrue(visible > 0, "peripheral arcs remain visible to identify the prison");

        var arc = RitualSphereMesh.interiorQuads(view).getFirst();
        var a = arc.first();
        var b = arc.second();
        var c = arc.third();
        var d = arc.fourth();
        var near = new RitualSphereMesh.InteriorView(
                (a.x() + b.x() + c.x() + d.x()) / 4 - 0.05,
                (a.y() + b.y() + c.y() + d.y()) / 4,
                (a.z() + b.z() + c.z() + d.z()) / 4,
                1, 0, 0, 0, 1, 0);
        assertEquals(0, RitualSphereMesh.interiorAlpha(arc, near),
                "a close ribbon cannot clip over the screen");
        assertEquals(0, RitualSphereMesh.interiorAlpha(arc,
                new RitualSphereMesh.InteriorView(Double.NaN, 0, 0, 1, 0, 0, 0, 1, 0)));
        assertEquals(0, RitualSphereMesh.interiorAlpha(arc,
                new RitualSphereMesh.InteriorView(0, 0, 0, 0, 0, 0, 0, 1, 0)));
    }

    @Test
    void differentCaptiveAnglesKeepPeripheralArcsInTheNormalFieldOfView() {
        for (double eyeHeight : new double[]{0, 0.72}) {
            for (int pitch : new int[]{-45, 0, 45}) {
                for (int yaw = 0; yaw < 360; yaw += 15) {
                    double a = Math.toRadians(yaw);
                    double b = Math.toRadians(pitch);
                    var view = view(0, eyeHeight, 0, a, b);
                    int inFrame = 0;
                    for (var quad : RitualSphereMesh.interiorQuads(view)) {
                        if (RitualSphereMesh.interiorAlpha(quad, view) <= 0) continue;
                        double x = (quad.first().x() + quad.second().x()
                                + quad.third().x() + quad.fourth().x()) / 4.0;
                        double y = (quad.first().y() + quad.second().y()
                                + quad.third().y() + quad.fourth().y()) / 4.0 - eyeHeight;
                        double z = (quad.first().z() + quad.second().z()
                                + quad.third().z() + quad.fourth().z()) / 4.0;
                        double forward = x * view.forwardX() + y * view.forwardY() + z * view.forwardZ();
                        double up = x * view.upX() + y * view.upY() + z * view.upZ();
                        double side = -x * Math.sin(a) + z * Math.cos(a);
                        if (forward > 0 && Math.abs(side / forward) < Math.tan(Math.toRadians(35))
                                && Math.abs(up / forward) < Math.tan(Math.toRadians(21))) inFrame++;
                    }
                    assertTrue(inFrame > 0,
                            "visible prison arcs at eye=" + eyeHeight + ", yaw=" + yaw + ", pitch=" + pitch);
                }
            }
        }
    }

    @Test
    void unsafeCameraInputsProduceNoInteriorGeometry() {
        assertTrue(RitualSphereMesh.interiorQuads(null).isEmpty());
        assertTrue(RitualSphereMesh.interiorQuads(
                new RitualSphereMesh.InteriorView(Double.NaN, 0, 0,
                        1, 0, 0, 0, 1, 0)).isEmpty());
        assertTrue(RitualSphereMesh.interiorQuads(
                new RitualSphereMesh.InteriorView(0, 0, 0,
                        0, 0, 0, 0, 1, 0)).isEmpty());
        assertTrue(RitualSphereMesh.interiorQuads(
                new RitualSphereMesh.InteriorView(0, 0, 0,
                        1, 0, 0, 1, 0, 0)).isEmpty());
        assertTrue(RitualSphereMesh.interiorQuads(
                new RitualSphereMesh.InteriorView(0, 0, 0,
                        Double.POSITIVE_INFINITY, 0, 0, 0, 1, 0)).isEmpty());
    }

    private static RitualSphereMesh.InteriorView view(double x, double y, double z,
            double yaw, double pitch) {
        return new RitualSphereMesh.InteriorView(x, y, z,
                Math.cos(yaw) * Math.cos(pitch), Math.sin(pitch), Math.sin(yaw) * Math.cos(pitch),
                -Math.cos(yaw) * Math.sin(pitch), Math.cos(pitch), -Math.sin(yaw) * Math.sin(pitch));
    }
}
