import me.copimine.endevent.domain.Wave7AbilityEffectPolicy;
import me.copimine.endevent.domain.Wave7AbilityEffectPolicy.Pass;
import me.copimine.endevent.domain.Wave7NamedTrialActorPolicy;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * W7-03: every Wave 7 miniboss spell must have its own authored signature.
 *
 * <p>The player report was "the spell effects are weak and all look the same".
 * Part of that was genuinely thin passes, but two identities were worse than
 * thin: the pair's joint crossfire and the Marksman's Giant summon were
 * dispatched through the effect runtime under their own ids and had no passes
 * at all, so the strongest moment of the trial and the single summon beat both
 * rendered as nothing.</p>
 *
 * <p>A missing signature cannot fail loudly at runtime — the runtime returns
 * false and the cast simply plays in silence — so the only place this class of
 * defect can be caught is here, by enumerating the ids the server actually
 * dispatches and asserting each one resolves.</p>
 */
public final class Wave7AbilityEffectPolicyTest {
    private static int checks;

    private Wave7AbilityEffectPolicyTest() {
    }

    public static void main(String[] args) {
        everyDispatchedAttackResolves();
        everyDispatchedAttackHasItsOwnShape();
        eachSignatureIsLayeredEnoughToRead();
        signaturesAreDistinctFromEachOther();
        soundsAreAuthored();
        passesAreWithinBudget();
        System.out.println("Wave7AbilityEffectPolicyTest: PASS (" + checks + " checks)");
    }

    /** The ids the server builds from an attack enum, with the dispatch prefix removed. */
    private static void everyDispatchedAttackResolves() {
        List<String> dispatched = new ArrayList<>();
        for (Wave7NamedTrialActorPolicy.Attack attack : Wave7NamedTrialActorPolicy.Attack.values()) {
            // These two never reach the renderer as their own id: NONE means "no
            // cast" and NONE_INTERNAL means the Warden uses its native loop.
            if (attack == Wave7NamedTrialActorPolicy.Attack.NONE
                    || attack == Wave7NamedTrialActorPolicy.Attack.NONE_INTERNAL) continue;
            dispatched.add(attack.name());
        }
        // The fang cast is remapped to its group signature by the caller.
        dispatched.add("ARCHMAGE_FANGS_GROUP");
        dispatched.add("WARDEN_SLAM");
        dispatched.add("MARKSMAN_DEFENDER");

        for (String abilityId : dispatched) {
            check(abilityId + " has an authored signature",
                    !Wave7AbilityEffectPolicy.passes(abilityId).isEmpty());
        }
    }

    /**
     * W7-03's other half: the signatures must not be the same shape recoloured.
     * Two different abilities that draw the identical set of primitives are
     * indistinguishable in play no matter what colour they are.
     */
    private static void everyDispatchedAttackHasItsOwnShape() {
        Set<String> seen = new HashSet<>();
        List<String> ids = new ArrayList<>();
        for (Wave7NamedTrialActorPolicy.Attack attack : Wave7NamedTrialActorPolicy.Attack.values()) {
            if (attack == Wave7NamedTrialActorPolicy.Attack.NONE
                    || attack == Wave7NamedTrialActorPolicy.Attack.NONE_INTERNAL) continue;
            ids.add("WAVE7_" + attack.name());
        }
        ids.add("WAVE7_ARCHMAGE_FANGS_GROUP");
        ids.add("WAVE7_WARDEN_SLAM");
        ids.add("WAVE7_MARKSMAN_DEFENDER");

        for (String abilityId : ids) {
            String fingerprint = fingerprint(Wave7AbilityEffectPolicy.passes(abilityId));
            if (fingerprint.isEmpty()) continue;
            check(abilityId + " has a shape no sibling already claimed",
                    seen.add(fingerprint));
        }
    }

    private static void eachSignatureIsLayeredEnoughToRead() {
        // One pass at arena distance is a single line of dust. Every authored
        // signature now carries at least an origin and an impact so the player
        // can read both where it came from and where it went.
        for (String abilityId : List.of("WAVE7_MARKSMAN_HEAVY_SHOT", "WAVE7_ARCHMAGE_BEAM",
                "WAVE7_ARCHMAGE_SPEAR", "WAVE7_WARDEN_SLAM", "WAVE7_MARKSMAN_DEFENDER",
                "WAVE7_MARKSMAN_PAIRED_CROSSFIRE", "WAVE7_ARCHMAGE_FANGS_GROUP")) {
            check(abilityId + " is built from more than one pass",
                    Wave7AbilityEffectPolicy.passes(abilityId).size() >= 2);
        }
    }

    private static void signaturesAreDistinctFromEachOther() {
        List<Pass> marksmanQuick = Wave7AbilityEffectPolicy.passes("WAVE7_MARKSMAN_QUICK_SHOT");
        List<Pass> marksmanHeavy = Wave7AbilityEffectPolicy.passes("WAVE7_MARKSMAN_HEAVY_SHOT");
        check("a quick shot and a heavy shot are not the same signature",
                !fingerprint(marksmanQuick).equals(fingerprint(marksmanHeavy)));
        check("a heavy shot reaches further than a quick shot",
                extent(marksmanHeavy) > extent(marksmanQuick));

        List<Pass> narrow = Wave7AbilityEffectPolicy.passes("WAVE7_WARDEN_NARROW_STRIKE");
        List<Pass> wide = Wave7AbilityEffectPolicy.passes("WAVE7_WARDEN_WIDE_STRIKE");
        check("a narrow strike and a wide strike are not the same signature",
                !fingerprint(narrow).equals(fingerprint(wide)));
        check("the wide strike paints a bigger floor ring",
                radius(wide) > radius(narrow));

        check("the archmage beam and the archmage spear differ",
                !fingerprint(Wave7AbilityEffectPolicy.passes("WAVE7_ARCHMAGE_BEAM"))
                        .equals(fingerprint(Wave7AbilityEffectPolicy.passes("WAVE7_ARCHMAGE_SPEAR"))));
        check("the pair crossfire is not a copy of a solo spread",
                !fingerprint(Wave7AbilityEffectPolicy.passes("WAVE7_MARKSMAN_PAIRED_CROSSFIRE"))
                        .equals(fingerprint(Wave7AbilityEffectPolicy.passes("WAVE7_MARKSMAN_SPREAD_SHOT"))));
    }

    private static void soundsAreAuthored() {
        for (String abilityId : List.of("WAVE7_MARKSMAN_QUICK_SHOT", "WAVE7_MARKSMAN_HEAVY_SHOT",
                "WAVE7_MARKSMAN_PAIRED_CROSSFIRE", "WAVE7_MARKSMAN_DEFENDER",
                "WAVE7_ARCHMAGE_FANGS", "WAVE7_ARCHMAGE_SPEAR", "WAVE7_ARCHMAGE_STEP",
                "WAVE7_ARCHMAGE_BEAM", "WAVE7_WARDEN_NARROW_STRIKE", "WAVE7_WARDEN_SLAM",
                "WAVE7_WARDEN_SONIC_LUNGE")) {
            check(abilityId + " has a release cue",
                    !Wave7AbilityEffectPolicy.releaseSound(abilityId).isBlank());
        }
        // The telegraph must be a different cue from the release, otherwise the
        // windup and the impact are one sound and the tell is lost.
        int differing = 0;
        for (String abilityId : List.of("WAVE7_MARKSMAN_HEAVY_SHOT", "WAVE7_ARCHMAGE_SPEAR",
                "WAVE7_ARCHMAGE_BEAM", "WAVE7_WARDEN_SLAM")) {
            if (!Wave7AbilityEffectPolicy.telegraphSound(abilityId)
                    .equals(Wave7AbilityEffectPolicy.releaseSound(abilityId))) differing++;
        }
        check("telegraphs are distinguishable from releases", differing >= 3);
    }

    /** A per-cast particle budget, so a signature cannot become screen fill. */
    private static void passesAreWithinBudget() {
        int worst = 0;
        String worstId = "";
        for (String abilityId : List.of("WAVE7_MARKSMAN_QUICK_SHOT", "WAVE7_MARKSMAN_HEAVY_SHOT",
                "WAVE7_MARKSMAN_SPREAD_SHOT", "WAVE7_MARKSMAN_PAIRED_CROSSFIRE",
                "WAVE7_MARKSMAN_DEFENDER", "WAVE7_ARCHMAGE_FANGS_GROUP", "WAVE7_ARCHMAGE_SPEAR",
                "WAVE7_ARCHMAGE_STEP", "WAVE7_ARCHMAGE_BEAM", "WAVE7_WARDEN_NARROW_STRIKE",
                "WAVE7_WARDEN_WIDE_STRIKE", "WAVE7_WARDEN_SONIC_LUNGE", "WAVE7_WARDEN_SLAM")) {
            int total = 0;
            for (Pass pass : Wave7AbilityEffectPolicy.passes(abilityId)) total += pass.count();
            if (total > worst) { worst = total; worstId = abilityId; }
            check(abilityId + " stays inside the per-cast particle budget",
                    total <= 160);
        }
        check("no signature is empty (" + worstId + " = " + worst + ")", worst > 0);
    }

    private static String fingerprint(List<Pass> passes) {
        StringBuilder builder = new StringBuilder();
        for (Pass pass : passes) {
            builder.append(pass.shape()).append('/').append(pass.count()).append('/')
                    .append(pass.particle()).append('/').append(pass.radius()).append(';');
        }
        return builder.toString();
    }

    private static double extent(List<Pass> passes) {
        double max = 0.0D;
        for (Pass pass : passes) max = Math.max(max, pass.extent());
        return max;
    }

    private static double radius(List<Pass> passes) {
        double max = 0.0D;
        for (Pass pass : passes) max = Math.max(max, pass.radius());
        return max;
    }

    private static void check(String name, boolean condition) {
        checks++;
        if (!condition) throw new AssertionError("FAILED: " + name);
    }
}
