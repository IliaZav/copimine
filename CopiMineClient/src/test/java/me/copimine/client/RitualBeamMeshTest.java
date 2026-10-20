package me.copimine.client;

import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class RitualBeamMeshTest {
    @Test void ritualBeamHasAContinuousCoreAndBoundedMovingFilaments() {
        Vec3d from = new Vec3d(3, 70, -42), to = new Vec3d(8, 72, -38);
        var quads = RitualBeamMesh.quads(from, to, 0.14F, 450L);
        assertEquals(112, quads.size(), "the geometry budget is fixed per beam, not per block");
        assertTrue(quads.stream().anyMatch(q -> q.layer() == RitualBeamMesh.Layer.CORE));
        assertTrue(quads.stream().anyMatch(q -> q.layer() == RitualBeamMesh.Layer.FLOW));
        Vec3d axis = to.subtract(from).normalize();
        double length = from.distanceTo(to);
        for (var quad : quads) {
            for (var vertex : List.of(quad.a(), quad.b(), quad.c(), quad.d())) {
                Vec3d offset = vertex.position().subtract(from);
                double projected = offset.dotProduct(axis);
                assertTrue(projected >= -1e-8 && projected <= length + 1e-8,
                        "a filament must never extend beyond its server-authored endpoints");
                assertTrue(offset.subtract(axis.multiply(projected)).length() <= 0.16D);
                assertTrue(Float.isFinite(vertex.u()) && Float.isFinite(vertex.v()));
            }
        }
    }

    @Test void animationMovesWithinTheSameEndpointsAndSupportsVerticalBeams() {
        Vec3d from = new Vec3d(8, 68, -38), to = new Vec3d(8, 75, -38);
        var first = RitualBeamMesh.quads(from, to, 0.14F, 0);
        var later = RitualBeamMesh.quads(from, to, 0.14F, 450);
        assertEquals(first.size(), later.size());
        assertNotEquals(first, later, "texture flow and the charged pulse must animate");
        assertEquals(first.get(0).a().position(), later.get(0).a().position());
        assertEquals(first.get(0).c().position(), later.get(0).c().position());
        assertTrue(later.stream().flatMap(q -> List.of(q.a(), q.b(), q.c(), q.d()).stream())
                .allMatch(v -> Double.isFinite(v.position().x) && Double.isFinite(v.position().z)));
    }

    @Test void malformedAndUnboundedGeometryCannotAllocateAWorldSizedBeam() {
        assertTrue(RitualBeamMesh.quads(Vec3d.ZERO, Vec3d.ZERO, .14F, 0).isEmpty());
        assertTrue(RitualBeamMesh.quads(Vec3d.ZERO, new Vec3d(0, 1e6, 0), .14F, 0).isEmpty());
        assertTrue(RitualBeamMesh.quads(Vec3d.ZERO, new Vec3d(Double.NaN, 1, 0), .14F, 0).isEmpty());
        assertTrue(RitualBeamMesh.quads(Vec3d.ZERO, new Vec3d(0, 5, 0), Float.NaN, 0).isEmpty());
    }
}
