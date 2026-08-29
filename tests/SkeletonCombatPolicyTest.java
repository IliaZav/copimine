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

        require(SkeletonCombatPolicy.hasArrowSpell(true, 3),
                "elite skeletons must have the arrow spell from wave three");
        require(!SkeletonCombatPolicy.hasArrowSpell(false, 3),
                "common skeletons must keep the spell for the miniboss variant");

        require(SkeletonCombatPolicy.behaviorForWave(1, false).id().equals("bone_line"),
                "wave one common skeletons must hold the readable bone line");
        require(SkeletonCombatPolicy.behaviorForWave(2, false).focusMarkedPlayer(),
                "wave two skeletons must focus the marked player");
        require(SkeletonCombatPolicy.shouldPrioritizeMarkedTarget(2, true, true),
                "an eligible marked player must override a stale skeleton target");
        require(!SkeletonCombatPolicy.shouldPrioritizeMarkedTarget(1, true, true),
                "marked-target override must be limited to the hunt wave");
        require(!SkeletonCombatPolicy.shouldPrioritizeMarkedTarget(2, true, false),
                "an offline or invalid marked player must never become a target");
        require(SkeletonCombatPolicy.behaviorForWave(3, false).guardsObjective(),
                "wave three skeletons must guard the portal objective");
        require(SkeletonCombatPolicy.behaviorForWave(4, false).id().equals("tower_artillery"),
                "wave four skeletons must use the artillery tower posture");
        require(SkeletonCombatPolicy.behaviorForWave(5, false).hazardAware(),
                "wave five skeletons must use the hazard-aware kite posture");
        require(SkeletonCombatPolicy.behaviorForWave(6, true).id().equals("final_volley"),
                "final miniboss skeletons must use the final volley posture");
        require(SkeletonCombatPolicy.behaviorForWave(3, true).minimumRange()
                        < SkeletonCombatPolicy.behaviorForWave(3, false).minimumRange(),
                "miniboss skeletons must close their ranged lane slightly");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
