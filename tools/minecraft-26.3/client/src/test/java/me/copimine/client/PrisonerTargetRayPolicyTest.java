package me.copimine.client;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrisonerTargetRayPolicyTest {
    private static final int CURRENT_QUERY_LIMIT = 256;
    private static final UUID ALLY = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID MOB = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID UNKNOWN = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final Vec3 CAMERA = new Vec3(0, 1.6, 0);
    private static final Vec3 FORWARD = new Vec3(0, 0, 1);
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
                new AABB(2, 0, 9.7, 2.6, 1.8, 10.3), 10, true, false);
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
        assertNotNull(PrisonerTargetRayPolicy.select(CAMERA, new Vec3(0, 0, 4),
                28, 10.5, List.of(target("ally", ALLY, 10, true, false)), ELIGIBLE::allows));
        assertNull(PrisonerTargetRayPolicy.select(CAMERA, new Vec3(0, 0, 4),
                28, 9.5, List.of(target("ally", ALLY, 10, true, false)), ELIGIBLE::allows));
    }

    @Test
    void thirdPersonCameraOffsetDoesNotChangeAuthoritativeFeetRange() {
        var result = PrisonerTargetRayPolicy.select(new Vec3(0, 1.6, -4), FORWARD,
                32, 32, List.of(target("mob", MOB, 28, false, true)), ELIGIBLE::allows);
        assertNotNull(result);
        assertEquals("mob", result.target());
        assertNull(PrisonerTargetRayPolicy.select(new Vec3(0, 1.6, -4), FORWARD,
                32, 32, List.of(target("too-far", MOB, 28.01, false, true)), ELIGIBLE::allows));
    }

    @Test
    void denseOffRayEntitiesCannotHideAnEligibleTargetBehindTheQueryLimit() {
        List<PrisonerTargetRayPolicy.Candidate<String>> broadPhaseResults = new ArrayList<>();
        for (int index = 0; index < CURRENT_QUERY_LIMIT; index++) {
            UUID id = UUID.nameUUIDFromBytes(
                    ("off-ray-living-entity-" + index).getBytes(StandardCharsets.UTF_8));
            // These boxes fall inside the selector's rayBounds.inflate(1) query,
            // but their X range misses the narrow camera ray. They must not
            // consume result slots because the selector filters by the corridor.
            broadPhaseResults.add(new PrisonerTargetRayPolicy.Candidate<>(
                    "off-ray-" + index, id, new AABB(0.55, 1.0, 5, 0.95, 2.0, 5.6),
                    5, true, false));
        }
        broadPhaseResults.add(target("late-ally", ALLY, 10, true, false));
        Vec3 end = CAMERA.add(FORWARD.scale(28));
        List<PrisonerTargetRayPolicy.Candidate<String>> corridorResults = broadPhaseResults.stream()
                .filter(candidate -> PrisonerTargetRayPolicy.intersectsSegment(
                        CAMERA, end, candidate.bounds()))
                .limit(CURRENT_QUERY_LIMIT)
                .toList();

        var boundedQuerySelection = selectCandidates(corridorResults, 28);
        var completeQuerySelection = selectCandidates(broadPhaseResults, 28);

        assertNotNull(boundedQuerySelection);
        assertEquals("late-ally", boundedQuerySelection.target());
        assertNotNull(completeQuerySelection);
        assertEquals("late-ally", completeQuerySelection.target());
    }

    @Test
    void invalidRayAndNonFiniteDistanceCannotSelect() {
        var ally = target("ally", ALLY, 10, true, false);
        assertNull(PrisonerTargetRayPolicy.select(CAMERA, Vec3.ZERO,
                28, 28, List.of(ally), ELIGIBLE::allows));
        assertNull(PrisonerTargetRayPolicy.select(new Vec3(Double.NaN, 1.6, 0), FORWARD,
                28, 28, List.of(ally), ELIGIBLE::allows));
        assertNull(select(Double.NaN, ally));
        assertNull(select(28, new PrisonerTargetRayPolicy.Candidate<>("invalid", ALLY,
                ally.bounds(), Double.NaN, true, false)));
    }

    @SafeVarargs
    private static PrisonerTargetRayPolicy.Selection<String> select(double colliderDistance,
            PrisonerTargetRayPolicy.Candidate<String>... candidates) {
        return selectCandidates(List.of(candidates), colliderDistance);
    }

    private static PrisonerTargetRayPolicy.Selection<String> selectCandidates(
            List<PrisonerTargetRayPolicy.Candidate<String>> candidates, double colliderDistance) {
        return PrisonerTargetRayPolicy.select(CAMERA, FORWARD, 28,
                colliderDistance, candidates, ELIGIBLE::allows);
    }

    private static PrisonerTargetRayPolicy.Candidate<String> target(String name, UUID id,
            double z, boolean player, boolean mob) {
        return new PrisonerTargetRayPolicy.Candidate<>(name, id,
                new AABB(-0.3, 0, z - 0.3, 0.3, 1.8, z + 0.3), Math.abs(z), player, mob);
    }
}
