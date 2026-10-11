package me.copimine.client;

import java.util.List;
import java.util.UUID;
import java.util.function.BiPredicate;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Camera-ray preview geometry. Eligibility and positional ranges remain server-authored. */
public final class PrisonerTargetRayPolicy {
    private PrisonerTargetRayPolicy() {
    }

    public static <T> Selection<T> select(Vec3 origin, Vec3 direction,
            double rayLength, double colliderDistance, List<Candidate<T>> candidates,
            BiPredicate<PrisonerHudController.Ability, UUID> allowed) {
        if (!finite(origin) || !finite(direction) || direction.lengthSqr() < 1.0e-12
                || !Double.isFinite(rayLength) || rayLength <= 0 || rayLength > 36
                || !Double.isFinite(colliderDistance) || colliderDistance < 0
                || candidates == null || allowed == null) {
            return null;
        }
        Vec3 end = origin.add(direction.normalize().scale(rayLength));
        double nearest = Math.min(rayLength, colliderDistance);
        Selection<T> result = null;
        for (Candidate<T> candidate : candidates) {
            if (candidate == null || candidate.target() == null || candidate.id() == null
                    || candidate.bounds() == null || candidate.bounds().hasNaN()) continue;
            boolean ally = candidate.player()
                    && allowed.test(PrisonerHudController.Ability.HEAL, candidate.id())
                    && PrisonerTargetRangePolicy.allows(PrisonerHudController.Ability.HEAL,
                            candidate.playerDistance());
            boolean hostile = candidate.mob()
                    && allowed.test(PrisonerHudController.Ability.TURNCOAT, candidate.id())
                    && PrisonerTargetRangePolicy.allows(PrisonerHudController.Ability.TURNCOAT,
                            candidate.playerDistance());
            if (!ally && !hostile) continue;
            Vec3 intersection = candidate.bounds().contains(origin) ? origin
                    : candidate.bounds().clip(origin, end).orElse(null);
            if (intersection == null) continue;
            double hitDistance = origin.distanceTo(intersection);
            // A collider at the same point wins; do not preview through walls or Core blocks.
            if (hitDistance < nearest) {
                nearest = hitDistance;
                result = new Selection<>(candidate.target(), ally, hostile);
            }
        }
        return result;
    }

    private static boolean finite(Vec3 vector) {
        return vector != null && Double.isFinite(vector.x)
                && Double.isFinite(vector.y) && Double.isFinite(vector.z);
    }

    public record Candidate<T>(T target, UUID id, AABB bounds, double playerDistance,
                               boolean player, boolean mob) {
    }

    public record Selection<T>(T target, boolean ally, boolean hostile) {
    }
}
