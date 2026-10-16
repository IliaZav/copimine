import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import me.copimine.endevent.domain.DragonBreathCloudPolicy;

public final class DragonBreathCloudPolicyTest {
    public static void main(String[] args) {
        var hits = new HashSet<DragonBreathCloudPolicy.Hit>();
        UUID firstCloud = UUID.randomUUID();
        UUID secondCloud = UUID.randomUUID();
        UUID firstPlayer = UUID.randomUUID();
        UUID secondPlayer = UUID.randomUUID();

        Map<UUID, UUID> activeCloudsByCaster = new HashMap<>();
        UUID thirdCloud = UUID.randomUUID();
        check(DragonBreathCloudPolicy.claimCloud(activeCloudsByCaster, firstPlayer, firstCloud),
                "a caster can create its first active cloud");
        check(!DragonBreathCloudPolicy.claimCloud(activeCloudsByCaster, firstPlayer, secondCloud),
                "a caster cannot stack or refresh an active cloud");
        check(DragonBreathCloudPolicy.claimCloud(activeCloudsByCaster, secondPlayer, secondCloud),
                "different casters can each own one active cloud");
        DragonBreathCloudPolicy.releaseCloud(activeCloudsByCaster, firstCloud);
        check(DragonBreathCloudPolicy.claimCloud(activeCloudsByCaster, firstPlayer, thirdCloud),
                "cloud cleanup releases that caster's active-cloud slot");

        check(DragonBreathCloudPolicy.admitHit(hits, firstCloud, firstPlayer),
                "the first cloud-player pair is admitted");
        check(!DragonBreathCloudPolicy.admitHit(hits, firstCloud, firstPlayer),
                "the same player is damaged at most once by one cloud");
        check(DragonBreathCloudPolicy.admitHit(hits, firstCloud, secondPlayer),
                "a second eligible player may be damaged by the cloud");
        check(DragonBreathCloudPolicy.admitHit(hits, secondCloud, firstPlayer),
                "a later cloud gets its own bounded hit for the player");
        check(!DragonBreathCloudPolicy.admitHit(hits, null, firstPlayer)
                        && !DragonBreathCloudPolicy.admitHit(hits, firstCloud, null),
                "missing identities fail closed");

        check(DragonBreathCloudPolicy.shouldSchedulePlayerHit(true, true, true, true),
                "an eligible player in the cloud world and radius is scheduled");
        check(!DragonBreathCloudPolicy.shouldSchedulePlayerHit(false, true, true, true),
                "non-player entities never receive Dragon Breath damage");
        check(!DragonBreathCloudPolicy.shouldSchedulePlayerHit(true, false, true, true),
                "safe-zone and otherwise ineligible players are excluded");
        check(!DragonBreathCloudPolicy.shouldSchedulePlayerHit(true, true, false, true),
                "players in another world are excluded");
        check(!DragonBreathCloudPolicy.shouldSchedulePlayerHit(true, true, true, false),
                "players outside the cloud radius are excluded");

        DragonBreathCloudPolicy.releaseCloud(hits, firstCloud);
        check(!hits.contains(new DragonBreathCloudPolicy.Hit(firstCloud, firstPlayer))
                        && !hits.contains(new DragonBreathCloudPolicy.Hit(firstCloud, secondPlayer))
                        && hits.contains(new DragonBreathCloudPolicy.Hit(secondCloud, firstPlayer)),
                "cloud cleanup releases only hits owned by that cloud");
        check(DragonBreathCloudPolicy.admitHit(hits, firstCloud, firstPlayer),
                "an expired cloud id can be safely reused after cleanup");

        check(!shouldApplyEffects(false, false),
                "missing nested damage outcome fails closed instead of granting blindness");
        check(!shouldApplyEffects(true, true),
                "a canceled nested damage event cannot be followed by blindness");
        check(shouldApplyEffects(true, false),
                "an accepted nested damage event commits player effects once");

        System.out.println("DragonBreathCloudPolicyTest OK");
    }

    private static boolean shouldApplyEffects(boolean damageEventObserved,
                                              boolean damageEventCancelled) {
        return DragonBreathCloudPolicy.shouldApplyEffects(damageEventObserved, damageEventCancelled);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
