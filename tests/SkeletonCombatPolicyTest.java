import me.copimine.endevent.domain.EndRiftObjective;
import me.copimine.endevent.domain.SkeletonCombatPolicy;

/** Pure regression checks for both wave skeleton variants. */
public final class SkeletonCombatPolicyTest {
    public static void main(String[] args) {
        require(SkeletonCombatPolicy.canTargetPlayersOnly("PLAYER", true),
                "eligible players must be targetable");
        require(!SkeletonCombatPolicy.canTargetPlayersOnly("PLAYER", false),
                "dead/non-combat players must not be targetable");
        require(!SkeletonCombatPolicy.canTargetPlayersOnly("ZOMBIE", true),
                "skeleton AI must never target another mob");
        require(!SkeletonCombatPolicy.canTargetPlayersOnly("SKELETON", true),
                "skeleton AI must never target another skeleton");

        SkeletonCombatPolicy.ArrowProfile common = SkeletonCombatPolicy.arrowProfile(false);
        require(common.arrowCount() == 1 && common.damage() == 5.0D,
                "common skeleton profile must fire one readable arrow");
        require(common.particlePattern().equals("bone_tracer"),
                "common skeletons need a distinct arrow trail");

        SkeletonCombatPolicy.ArrowProfile elite = SkeletonCombatPolicy.arrowProfile(true);
        require(elite.arrowCount() == 3 && elite.damage() == 8.0D,
                "elite skeleton profile must fire a bounded three-arrow salvo");
        require(elite.cooldownTicks() >= 50 && elite.cooldownTicks() <= 100,
                "elite salvo cooldown must be bounded");
        require(elite.particlePattern().equals("rift_salvo"),
                "elite skeleton spell needs its own particle pattern");

        require(SkeletonCombatPolicy.hasArrowSpell(
                        EndRiftObjective.Objective.RIFT_GATES, true),
                "elite skeletons must have the arrow spell in gate wave");
        require(!SkeletonCombatPolicy.hasArrowSpell(
                        EndRiftObjective.Objective.RIFT_GATES, false),
                "common skeletons must keep the spell for the miniboss variant");

        require(SkeletonCombatPolicy.behaviorFor(
                        EndRiftObjective.Objective.RIFT_CARRIERS, false).id()
                        .equals("carrier_screen"),
                "carrier wave skeletons must hold the readable screen");
        require(SkeletonCombatPolicy.behaviorFor(
                        EndRiftObjective.Objective.RIFT_HUNT, false).focusMarkedPlayer(),
                "wave two skeletons must focus the marked player");
        require(SkeletonCombatPolicy.shouldPrioritizeMarkedTarget(
                        EndRiftObjective.Objective.RIFT_HUNT, true, true),
                "an eligible marked player must override a stale skeleton target");
        require(!SkeletonCombatPolicy.shouldPrioritizeMarkedTarget(
                        EndRiftObjective.Objective.RIFT_CARRIERS, true, true),
                "marked-target override must be limited to the hunt wave");
        require(!SkeletonCombatPolicy.shouldPrioritizeMarkedTarget(
                        EndRiftObjective.Objective.RIFT_HUNT, true, false),
                "an offline or invalid marked player must never become a target");
        require(SkeletonCombatPolicy.behaviorFor(
                        EndRiftObjective.Objective.RIFT_GATES, false).guardsObjective(),
                "wave three skeletons must guard the portal objective");
        require(SkeletonCombatPolicy.behaviorFor(
                        EndRiftObjective.Objective.OBELISK_ASSAULT, false).id()
                        .equals("obelisk_cover"),
                "wave four skeletons must cover the obelisk objective");
        require(SkeletonCombatPolicy.behaviorFor(
                        EndRiftObjective.Objective.BLACK_FOG, false).hazardAware(),
                "wave five skeletons must use the hazard-aware kite posture");
        require(SkeletonCombatPolicy.behaviorFor(
                        EndRiftObjective.Objective.REALITY_SPLIT, true).id()
                        .equals("chamber_fireline"),
                "last wave miniboss skeletons must keep a chamber fireline");
        require(SkeletonCombatPolicy.behaviorFor(
                        EndRiftObjective.Objective.RIFT_GATES, true).minimumRange()
                        < SkeletonCombatPolicy.behaviorFor(
                                EndRiftObjective.Objective.RIFT_GATES, false).minimumRange(),
                "miniboss skeletons must close their ranged lane slightly");

        require(SkeletonCombatPolicy.maneuverFor(
                        EndRiftObjective.Objective.RIFT_CARRIERS, false, 1, 0)
                        == SkeletonCombatPolicy.Maneuver.SIDE_STEP,
                "wave one skeletons need a small readable side step");
        require(SkeletonCombatPolicy.maneuverFor(
                        EndRiftObjective.Objective.OBELISK_ASSAULT, false, 0, 0)
                        == SkeletonCombatPolicy.Maneuver.ROTATE_COVER,
                "wave four skeletons need a rotating cover beat");
        require(SkeletonCombatPolicy.maneuverFor(
                        EndRiftObjective.Objective.BLACK_FOG, true, 2, 0)
                        == SkeletonCombatPolicy.Maneuver.CROSS_FIRE,
                "wave five miniboss skeletons need a bounded cross-fire beat");
        SkeletonCombatPolicy.Maneuver firstManeuver = SkeletonCombatPolicy.maneuverFor(
                EndRiftObjective.Objective.BLACK_FOG, true, 2, 0);
        SkeletonCombatPolicy.Maneuver secondManeuver = SkeletonCombatPolicy.maneuverFor(
                EndRiftObjective.Objective.BLACK_FOG, true, 2, 0);
        require(firstManeuver == secondManeuver,
                "skeleton maneuver selection must be deterministic");
        System.out.println("SkeletonCombatPolicyTest OK");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
