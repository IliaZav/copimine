package me.copimine.client;

import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PrisonerTargetRayPolicyTest {
    private static final UUID ALLY = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID MOB = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID UNKNOWN = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final Vec3d CAMERA = new Vec3d(0, 1.6, 0);
    private static final Vec3d FORWARD = new Vec3d(0, 0, 1);
    private static final PrisonerTargetEligibility ELIGIBLE = PrisonerTargetEligibility.parse(
            "PRISONER_TARGETS_V1|" + ALLY + "|" + MOB);

    @Test
    void supportAtTenBlocksIsSelectedWithoutAVanillaEntityHit() {
        var result = select(28, target("ally", ALLY, 10, true, false));
        assertNotNull(result);
        assertEquals("ally", result.target());
        assertTrue(result.ally());
        assertFalse(result.hostile());
    }

    @Test
    void supportBoundaryAndTurncoatBoundaryFollowServerPositionRanges() {
        assertNotNull(select(28, target("ally", ALLY, 24, true, false)));
        assertNull(select(28, target("ally", ALLY, 24.01, true, false)));
        assertNotNull(select(28, target("mob", MOB, 28, false, true)));
        assertNull(select(28, target("mob", MOB, 28.01, false, true)));
    }

    @Test
    void nearestEligibleBoxIntersectionWinsRegardlessOfListOrder() {
        var far = target("far", MOB, 17, false, true);
        var near = target("near", ALLY, 10, true, false);
        assertEquals("near", select(28, far, near).target());
        assertEquals("near", select(28, near, far).target());
    }

    @Test
    void wallOrProtectedCoreColliderBeforeTargetBlocksPreview() {
        assertNull(select(9.5, target("ally", ALLY, 10, true, false)));
        assertNull(select(9.7, target("ally", ALLY, 10, true, false)));
        assertNotNull(select(10.5, target("ally", ALLY, 10, true, false)));
    }

    @Test
    void offAxisAndBehindCameraBoxesAreNotSelected() {
        var offAxis = new PrisonerTargetRayPolicy.Candidate<>("off-axis", ALLY,
                new Box(2, 0, 9.7, 2.6, 1.8, 10.3), 10, true, false);
        assertNull(select(28, offAxis));
        assertNull(select(28, target("behind", ALLY, -10, true, false)));
    }

    @Test
    void serverWhitelistAndEntityRoleBothFailClosed() {
        assertNull(select(28, target("unknown", UNKNOWN, 5, true, false)));
        assertNull(select(28, target("wrong-role", MOB, 5, true, false)));
        assertNull(select(28, target("wrong-role", ALLY, 5, false, true)));
        assertNull(select(28, target("display", MOB, 5, false, false)));
        assertEquals("ally", select(28, target("unknown", UNKNOWN, 5, true, false),
                target("ally", ALLY, 10, true, false)).target());
    }

    @Test
    void cameraDirectionIsNormalizedBeforeClippingToColliderDistance() {
        assertNotNull(PrisonerTargetRayPolicy.select(CAMERA, new Vec3d(0, 0, 4),
                28, 10.5, List.of(target("ally", ALLY, 10, true, false)), ELIGIBLE::allows));
        assertNull(PrisonerTargetRayPolicy.select(CAMERA, new Vec3d(0, 0, 4),
                28, 9.5, List.of(target("ally", ALLY, 10, true, false)), ELIGIBLE::allows));
    }

    @Test
    void thirdPersonCameraOffsetDoesNotChangeAuthoritativeFeetRange() {
        var result = PrisonerTargetRayPolicy.select(new Vec3d(0, 1.6, -4), FORWARD,
                32, 32, List.of(target("mob", MOB, 28, false, true)), ELIGIBLE::allows);
        assertNotNull(result);
        assertEquals("mob", result.target());
        assertNull(PrisonerTargetRayPolicy.select(new Vec3d(0, 1.6, -4), FORWARD,
                32, 32, List.of(target("too-far", MOB, 28.01, false, true)), ELIGIBLE::allows));
    }

    @Test
    void invalidRayAndNonFiniteDistanceCannotSelect() {
        var ally = target("ally", ALLY, 10, true, false);
        assertNull(PrisonerTargetRayPolicy.select(CAMERA, Vec3d.ZERO,
                28, 28, List.of(ally), ELIGIBLE::allows));
        assertNull(PrisonerTargetRayPolicy.select(new Vec3d(Double.NaN, 1.6, 0), FORWARD,
                28, 28, List.of(ally), ELIGIBLE::allows));
        assertNull(select(Double.NaN, ally));
        assertNull(select(28, new PrisonerTargetRayPolicy.Candidate<>("invalid", ALLY,
                ally.bounds(), Double.NaN, true, false)));
    }

    @SafeVarargs
    private static PrisonerTargetRayPolicy.Selection<String> select(double colliderDistance,
            PrisonerTargetRayPolicy.Candidate<String>... candidates) {
        return PrisonerTargetRayPolicy.select(CAMERA, FORWARD, 28, colliderDistance,
                List.of(candidates), ELIGIBLE::allows);
    }

    private static PrisonerTargetRayPolicy.Candidate<String> target(String name, UUID id,
            double z, boolean player, boolean mob) {
        return new PrisonerTargetRayPolicy.Candidate<>(name, id,
                new Box(-0.3, 0, z - 0.3, 0.3, 1.8, z + 0.3), Math.abs(z), player, mob);
    }
}
