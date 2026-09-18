import me.copimine.endevent.domain.ChamberIsolationPolicy;
import me.copimine.endevent.domain.RealitySplitPlayerTeleportPolicy;

import java.util.List;
import java.util.UUID;

public final class RealitySplitPlayerTeleportPolicyTest {
    public static void main(String[] args) {
        UUID first = UUID.fromString("00000000-0000-0000-0000-000000000011");
        UUID second = UUID.fromString("00000000-0000-0000-0000-000000000022");
        ChamberIsolationPolicy.Assignment assignment = ChamberIsolationPolicy.assign(List.of(first, second));
        int firstRoom = assignment.chamberByPlayer().get(first);
        int secondRoom = assignment.chamberByPlayer().get(second);
        double firstAngle = ChamberIsolationPolicy.chamberCenterAngle(firstRoom,
                assignment.chamberCount());
        double secondAngle = ChamberIsolationPolicy.chamberCenterAngle(secondRoom,
                assignment.chamberCount());

        check(RealitySplitPlayerTeleportPolicy.allows(first, assignment,
                        Math.cos(firstAngle) * 8.0D, Math.sin(firstAngle) * 8.0D,
                        0.0D, false),
                "a player may teleport inside the assigned closed room");
        check(!RealitySplitPlayerTeleportPolicy.allows(first, assignment,
                        Math.cos(secondAngle) * 8.0D, Math.sin(secondAngle) * 8.0D,
                        0.0D, false),
                "a player may not teleport into another closed room");
        check(!RealitySplitPlayerTeleportPolicy.allows(first, assignment,
                        Math.cos(firstAngle) * 8.0D, Math.sin(firstAngle) * 8.0D,
                        99.0D, false),
                "vertical teleport outside the combat band must be rejected");
        check(RealitySplitPlayerTeleportPolicy.allows(first, assignment,
                        Math.cos(secondAngle) * 8.0D, Math.sin(secondAngle) * 8.0D,
                        0.0D, true),
                "an explicitly merged objective may permit a cross-room teleport");
        check(!RealitySplitPlayerTeleportPolicy.allows(
                        UUID.fromString("00000000-0000-0000-0000-000000000099"), assignment,
                        8.0D, 0.0D, 0.0D, false),
                "an unassigned player must not gain a room teleport permit");

        double firstBoundaryRadius = ChamberIsolationPolicy.INNER_RADIUS + 0.05D;
        double firstInsideCoreRadius = ChamberIsolationPolicy.INNER_RADIUS - 0.20D;
        check(RealitySplitPlayerTeleportPolicy.requiresInnerBoundaryRecovery(
                        first, assignment,
                        Math.cos(firstAngle) * firstBoundaryRadius,
                        Math.sin(firstAngle) * firstBoundaryRadius,
                        Math.cos(firstAngle) * firstInsideCoreRadius,
                        Math.sin(firstAngle) * firstInsideCoreRadius,
                        false),
                "an inward move from the legal inner boundary needs a chamber recovery");
        check(!RealitySplitPlayerTeleportPolicy.requiresInnerBoundaryRecovery(
                        first, assignment,
                        Math.cos(firstAngle) * firstBoundaryRadius,
                        Math.sin(firstAngle) * firstBoundaryRadius,
                        Math.cos(firstAngle) * (firstBoundaryRadius + 0.50D),
                        Math.sin(firstAngle) * (firstBoundaryRadius + 0.50D),
                        false),
                "an outward move must not trigger inner-boundary recovery");
        check(!RealitySplitPlayerTeleportPolicy.requiresInnerBoundaryRecovery(
                        first, assignment,
                        Math.cos(firstAngle) * firstBoundaryRadius,
                        Math.sin(firstAngle) * firstBoundaryRadius,
                        Math.cos(firstAngle) * firstInsideCoreRadius,
                        Math.sin(firstAngle) * firstInsideCoreRadius,
                        true),
                "merged rooms do not use the closed-room inner boundary recovery");

        double firstOuterBoundaryRadius = ChamberIsolationPolicy.OUTER_RADIUS - 0.05D;
        double firstOutsideRoomRadius = ChamberIsolationPolicy.OUTER_RADIUS + 0.20D;
        check(RealitySplitPlayerTeleportPolicy.requiresBoundaryRecovery(
                        first, assignment,
                        Math.cos(firstAngle) * firstOuterBoundaryRadius,
                        Math.sin(firstAngle) * firstOuterBoundaryRadius,
                        Math.cos(firstAngle) * firstOutsideRoomRadius,
                        Math.sin(firstAngle) * firstOutsideRoomRadius,
                        false),
                "an outward move from the legal outer boundary needs a chamber recovery");
        check(!RealitySplitPlayerTeleportPolicy.requiresBoundaryRecovery(
                        first, assignment,
                        Math.cos(firstAngle) * 8.0D,
                        Math.sin(firstAngle) * 8.0D,
                        Math.cos(firstAngle) * (ChamberIsolationPolicy.OUTER_RADIUS + 0.20D),
                        Math.sin(firstAngle) * (ChamberIsolationPolicy.OUTER_RADIUS + 0.20D),
                        false),
                "a move from the room center must keep the normal boundary correction");
        System.out.println("RealitySplitPlayerTeleportPolicyTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
