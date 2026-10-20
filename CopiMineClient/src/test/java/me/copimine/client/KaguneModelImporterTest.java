package me.copimine.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KaguneModelImporterTest {
    @Test
    void importsTheActualBlockbenchHierarchyUvRectanglesAndEverySuppliedClip() {
        KaguneModelImporter.ImportedModel model = KaguneModelImporter.load();

        assertEquals("1layer", model.bone("1layer2").parentName());
        assertEquals("2layer", model.bone("2layer2").parentName());
        assertEquals("3layer", model.bone("3layer2").parentName());
        assertEquals(12, model.animations().size());
        assertTrue(model.animations().keySet().containsAll(java.util.Set.of(
                "idle", "emerge", "telegraph_grab", "grab_success", "hold", "trow",
                "grab_miss", "hurt", "death", "retract", "spawn_under_player",
                "shield_channel")));

        KaguneModelImporter.Cuboid first = model.elements().getFirst();
        assertEquals(0.0F, first.faces().get("east").u0(), 0.0001F);
        assertEquals(2.0F, first.faces().get("east").u1(), 0.0001F);
        assertEquals(2.0F, first.faces().get("east").v0(), 0.0001F);
        assertEquals(4.0F, first.faces().get("east").v1(), 0.0001F);
        assertEquals(4.0F, first.faces().get("up").u0(), 0.0001F);
        assertEquals(2.0F, first.faces().get("up").u1(), 0.0001F,
                "the signed source UV direction must be preserved");
    }

    @Test
    void sampledPoseComesFromArtistAnimationRatherThanTheOldProceduralClawRig() {
        KaguneModelImporter.ImportedModel model = KaguneModelImporter.load();
        KaguneModelImporter.Vec3 end = model.sample("telegraph_grab", "1layer",
                "rotation", 1.0F);

        assertNotNull(end);
        assertEquals(0.0F, end.x(), 0.0001F);
        assertEquals(-45.0F, end.y(), 0.0001F);
        assertEquals(0.0F, end.z(), 0.0001F);
        assertEquals(1.25F, model.animation("telegraph_grab").lengthSeconds(), 0.0001F);
    }

    @Test
    void idleTurnKeepsAngularVelocityContinuousAcrossItsAuthoredTwoSecondKey() {
        KaguneModelImporter.ImportedModel model = KaguneModelImporter.load();
        KaguneModelImporter.Animation idle = model.animation("idle");
        double sampleWindowSeconds = 0.05D;
        float beforeProgress = (float) ((2.0D - sampleWindowSeconds) / idle.lengthSeconds());
        float keyProgress = (float) (2.0D / idle.lengthSeconds());
        float afterProgress = (float) ((2.0D + sampleWindowSeconds) / idle.lengthSeconds());

        double before = model.sample("idle", "3layer", "rotation", beforeProgress).z();
        double atKey = model.sample("idle", "3layer", "rotation", keyProgress).z();
        double after = model.sample("idle", "3layer", "rotation", afterProgress).z();
        double incomingVelocity = (atKey - before) / sampleWindowSeconds;
        double outgoingVelocity = (after - atKey) / sampleWindowSeconds;

        assertTrue(Math.abs(incomingVelocity) > 0.2D && Math.abs(outgoingVelocity) > 0.2D,
                "motion must remain measurable immediately before and after the true authored turning key");
        assertTrue(Math.abs(Math.abs(outgoingVelocity) - Math.abs(incomingVelocity)) < 0.1D,
                "the approach and departure speeds around the authored extremum must agree");
        assertTrue(Math.abs(outgoingVelocity - incomingVelocity) < 1.0D,
                "the 8.5-degree authored idle turn must not reverse angular velocity abruptly");
    }

    @Test
    void idleKeepsMeasurableSymmetricMotionAcrossItsFourSecondLoopSeam() {
        KaguneModelImporter.ImportedModel model = KaguneModelImporter.load();
        double sampleWindowSeconds = 0.05D;
        float beforeSeamProgress = (float) ((4.0D - sampleWindowSeconds) / 4.0D);
        float seamProgress = 1.0F;
        float afterSeamProgress = (float) (sampleWindowSeconds / 4.0D);

        double beforeSeam = model.sample("idle", "3layer", "rotation", beforeSeamProgress).z();
        double seam = model.sample("idle", "3layer", "rotation", seamProgress).z();
        double afterSeam = model.sample("idle", "3layer", "rotation", afterSeamProgress).z();
        double incomingSpeed = Math.abs((seam - beforeSeam) / sampleWindowSeconds);
        double outgoingSpeed = Math.abs((afterSeam - seam) / sampleWindowSeconds);

        assertEquals(0.0D, seam, 0.0001D, "the supplied 4s endpoint must match the initial pose");
        assertTrue(incomingSpeed > 0.2D && outgoingSpeed > 0.2D,
                "the sampled loop must move on both sides of its exact zero-speed seam");
        assertTrue(Math.abs(incomingSpeed - outgoingSpeed) < 0.1D,
                "the authored idle loop must enter and leave its seam at matching speeds");
    }
}
