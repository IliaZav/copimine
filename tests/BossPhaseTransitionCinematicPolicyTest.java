import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import me.copimine.endevent.domain.BossAnimationId;
import me.copimine.endevent.domain.BossImpactGeometryPolicy;
import me.copimine.endevent.domain.BossPhase;
import me.copimine.endevent.domain.BossPhaseTransitionCinematicPolicy;
import me.copimine.endevent.domain.BossVisualCuePolicy;

/**
 * N23-P1-11: the six phase transitions must each be their own authored moment.
 */
public final class BossPhaseTransitionCinematicPolicyTest {

    public static void main(String[] args) {
        oneCinematicPerPhase();
        durationsAreShortAndDistinct();
        everyAxisIsDistinct();
        posesAndCuesResolve();
        geometryIsDistinctAndBounded();
        neverGrantsInvulnerability();
        unknownPhaseFallsBackWithoutInventing();
        System.out.println("BossPhaseTransitionCinematicPolicyTest OK");
    }

    private static void oneCinematicPerPhase() {
        Map<BossPhase, BossPhaseTransitionCinematicPolicy.Cinematic> table =
                BossPhaseTransitionCinematicPolicy.cinematics();
        check(table.size() == BossPhase.values().length,
                "expected one cinematic per phase, got " + table.size());
        for (BossPhase phase : BossPhase.values()) {
            check(table.containsKey(phase), "missing cinematic for " + phase);
        }
    }

    private static void durationsAreShortAndDistinct() {
        Set<Long> durations = new HashSet<>();
        for (BossPhaseTransitionCinematicPolicy.Cinematic cinematic
                : BossPhaseTransitionCinematicPolicy.cinematics().values()) {
            long duration = cinematic.durationMillis();
            check(duration >= BossPhaseTransitionCinematicPolicy.MIN_DURATION_MILLIS
                            && duration <= BossPhaseTransitionCinematicPolicy.MAX_DURATION_MILLIS,
                    "duration out of bounds: " + duration);
            check(durations.add(duration), "duplicate transition duration: " + duration);
        }
    }

    private static void everyAxisIsDistinct() {
        Set<String> ids = new HashSet<>();
        Set<String> titles = new HashSet<>();
        Set<String> subtitles = new HashSet<>();
        Set<String> poses = new HashSet<>();
        Set<String> cues = new HashSet<>();
        Set<String> signatures = new HashSet<>();
        Set<Integer> colours = new HashSet<>();
        for (BossPhaseTransitionCinematicPolicy.Cinematic cinematic
                : BossPhaseTransitionCinematicPolicy.cinematics().values()) {
            check(ids.add(cinematic.id()), "duplicate id " + cinematic.id());
            check(titles.add(cinematic.title()), "duplicate title " + cinematic.title());
            check(subtitles.add(cinematic.subtitle()), "duplicate subtitle " + cinematic.subtitle());
            check(poses.add(cinematic.poseAnimation()), "duplicate pose " + cinematic.poseAnimation());
            check(cues.add(cinematic.cueId()), "duplicate cue " + cinematic.cueId());
            check(signatures.add(cinematic.signature()), "duplicate signature " + cinematic.signature());
            check(colours.add(cinematic.rgb()), "duplicate rgb " + cinematic.rgb());
        }
    }

    private static void posesAndCuesResolve() {
        for (BossPhaseTransitionCinematicPolicy.Cinematic cinematic
                : BossPhaseTransitionCinematicPolicy.cinematics().values()) {
            check(BossAnimationId.isKnown(cinematic.poseAnimation()),
                    "pose not in the shared catalogue: " + cinematic.poseAnimation());
            check(BossVisualCuePolicy.cue(cinematic.cueBase(), cinematic.cueStage()) != null,
                    "cue does not resolve to an authored row: " + cinematic.cueId());
        }
    }

    private static void geometryIsDistinctAndBounded() {
        Set<java.util.List<BossImpactGeometryPolicy.Point>> shapes = new HashSet<>();
        for (BossPhaseTransitionCinematicPolicy.Cinematic cinematic
                : BossPhaseTransitionCinematicPolicy.cinematics().values()) {
            java.util.List<BossImpactGeometryPolicy.Point> shape =
                    BossImpactGeometryPolicy.phaseTransition(cinematic.signature(), 3.8D, 0.5D);
            check(!shape.isEmpty(), "geometry is empty for signature " + cinematic.signature());
            check(shape.size() <= 256, "geometry too large: " + shape.size());
            check(shapes.add(shape), "two signatures share one geometry: " + cinematic.signature());
        }
        check(BossImpactGeometryPolicy.phaseTransition("nope", 3.8D, 0.5D).isEmpty(),
                "an unknown signature must draw nothing, not a generic ring");
        check(BossImpactGeometryPolicy.phaseTransition(null, 3.8D, 0.5D).isEmpty(),
                "a null signature must draw nothing");
        check(BossImpactGeometryPolicy.phaseTransition("ascension", Double.NaN, 0.5D).isEmpty(),
                "a non-finite radius must draw nothing");
    }

    private static void neverGrantsInvulnerability() {
        check(!BossPhaseTransitionCinematicPolicy.grantsInvulnerability(null),
                "a transition must never shield the boss");
        for (BossPhase phase : BossPhase.values()) {
            check(!BossPhaseTransitionCinematicPolicy.grantsInvulnerability(phase),
                    "transition granted invulnerability for " + phase);
        }
    }

    private static void unknownPhaseFallsBackWithoutInventing() {
        BossPhaseTransitionCinematicPolicy.Cinematic fallback =
                BossPhaseTransitionCinematicPolicy.forPhase(null);
        check(fallback.equals(BossPhaseTransitionCinematicPolicy.forPhase(BossPhase.AWAKENING)),
                "unknown phase must reuse the authored AWAKENING cinematic");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
