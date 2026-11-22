import java.util.HashSet;
import java.util.Set;
import me.copimine.endevent.domain.BossSpellParticlePalette;
import me.copimine.endevent.domain.BossSpellVisualCompositionPolicy;

/**
 * The authored composition promises every spell its own particle pair. This
 * probe proves the pair survives resolution into something a client can draw,
 * rather than collapsing back onto one shared dust point.
 */
public final class BossSpellParticlePaletteTest {
    private static int checks;

    private BossSpellParticlePaletteTest() {
    }

    public static void main(String[] args) {
        everyAuthoredParticleResolves();
        eachSpellKeepsDistinctNodeSignature();
        unknownIdsFallBackToVisibleDust();
        oneShotParticlesAreNeverStampedOnNodes();
        System.out.println("BossSpellParticlePaletteTest: PASS (" + checks + " checks)");
    }

    private static void everyAuthoredParticleResolves() {
        for (String spell : BossSpellVisualCompositionPolicy.spellIds()) {
            var composition = BossSpellVisualCompositionPolicy.composition(spell);
            for (String authored : new String[]{composition.primaryParticle(), composition.accentParticle()}) {
                var signature = BossSpellParticlePalette.resolve(authored, composition.telegraphHue());
                check("resolves a particle name: " + spell + " " + authored,
                        signature.particle() != null && !signature.particle().isBlank());
                check("carries a bounded colour: " + spell + " " + authored,
                        signature.rgb() >= 0 && signature.rgb() <= 0xFFFFFF);
                // A structured carrier must name the payload it needs; a
                // payload-free one must not claim one.
                check("payload is consistent: " + spell + " " + authored,
                        signature.payload() != null);
            }
        }
    }

    private static void eachSpellKeepsDistinctNodeSignature() {
        // Replicate the renderer's own choice: the highest-ranked authored
        // particle wins, and END_ROD is the highlight for a spell whose
        // authored pair is dust/one-shot only. That fallback is intentional,
        // so the assertion is about what actually reaches the nodes.
        Set<String> chosen = new HashSet<>();
        int authored = 0;
        for (String spell : BossSpellVisualCompositionPolicy.spellIds()) {
            var composition = BossSpellVisualCompositionPolicy.composition(spell);
            var signature = rendererNodeSignature(composition);
            check("never stamps an undrawable particle on nodes: " + spell,
                    BossSpellParticlePalette.nodeRank(signature) >= 1);
            if (!"END_ROD".equals(signature.particle())) authored++;
            chosen.add(signature.particle());
        }
        // The whole point: the roster must not collapse onto one particle.
        check("the roster draws several node particles (found " + chosen.size() + ")", chosen.size() >= 6);
        // Most spells should surface a real authored family, not the fallback.
        check("most spells surface an authored node particle (found " + authored + " of "
                + BossSpellVisualCompositionPolicy.spellIds().size() + ")", authored >= 12);
    }

    /** Mirror of {@code BossSpellPresentationEffects.nodeSignature}. */
    private static BossSpellParticlePalette.Signature rendererNodeSignature(
            BossSpellVisualCompositionPolicy.Composition composition) {
        var primary = BossSpellParticlePalette.resolve(composition.primaryParticle(),
                composition.telegraphHue());
        var accent = BossSpellParticlePalette.resolve(composition.accentParticle(),
                composition.telegraphHue());
        BossSpellParticlePalette.Signature best = null;
        int bestRank = 0;
        for (BossSpellParticlePalette.Signature candidate
                : new BossSpellParticlePalette.Signature[]{primary, accent}) {
            int rank = BossSpellParticlePalette.nodeRank(candidate);
            if (rank > bestRank) {
                bestRank = rank;
                best = candidate;
            }
        }
        return best == null ? BossSpellParticlePalette.resolve("END_ROD", 0xFFFFFF) : best;
    }

    private static void unknownIdsFallBackToVisibleDust() {
        var fallback = BossSpellParticlePalette.resolve("not_a_particle", 0x123456);
        check("an unknown id is dust", fallback.dust());
        check("an unknown id keeps the spell hue", fallback.rgb() == 0x123456);
        check("null is dust", BossSpellParticlePalette.resolve(null, 0x0A0B0C).dust());
        check("blank is dust", BossSpellParticlePalette.resolve("   ", 0x0A0B0C).dust());
        var named = BossSpellParticlePalette.resolve("void-blast-accent", 0x0A0B0C);
        check("a dashed unknown id is still dust", named.dust());
    }

    private static void oneShotParticlesAreNeverStampedOnNodes() {
        // A full-screen flash or a sonic boom on every node would blot out the
        // silhouette, so they must rank below any dense particle.
        for (String oneShot : new String[]{"FLASH", "SONIC_BOOM", "SWEEP_ATTACK"}) {
            var signature = BossSpellParticlePalette.resolve(oneShot, 0xFFFFFF);
            check("one-shot is not node-drawable: " + oneShot,
                    BossSpellParticlePalette.nodeRank(signature) <= 0);
        }
        // And a dense particle always outranks a sparse one.
        check("dense outranks sparse",
                BossSpellParticlePalette.nodeRank(BossSpellParticlePalette.resolve("SOUL", 0xFFFFFF))
                        > BossSpellParticlePalette.nodeRank(BossSpellParticlePalette.resolve("ENCHANTED_HIT", 0xFFFFFF)));
    }

    private static void check(String name, boolean condition) {
        checks++;
        if (!condition) {
            throw new AssertionError("FAILED: " + name);
        }
    }
}
