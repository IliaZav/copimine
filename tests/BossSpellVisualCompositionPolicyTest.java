import java.util.HashSet;
import java.util.List;
import java.util.Set;
import me.copimine.endevent.domain.BossSpellGeometryPolicy;
import me.copimine.endevent.domain.BossSpellVisualCompositionPolicy;
import me.copimine.endevent.domain.EndRiftAiPolicy;

/** Every boss spell must look like itself, not like the shared particle pile. */
public final class BossSpellVisualCompositionPolicyTest {
    /**
     * Shrink floor for the authored roster, not an equality.
     *
     * <p>The table holds twenty-seven compositions. A count is used here only to
     * make deleting an authoring entry a deliberate edit of this number:
     * dropping one silently returns that spell to the generic cue, which is the
     * regression this probe exists to catch. Adding a spell must not need an
     * edit here.</p>
     */
    private static final int MIN_AUTHORED_SPELLS = 27;

    private static int checks;

    private BossSpellVisualCompositionPolicyTest() {
    }

    public static void main(String[] args) {
        everyAuthoredSpellHasAFullSignature();
        noTwoSpellsShareASignature();
        telegraphAndImpactDifferInHeat();
        unknownSpellsFallBackCleanly();
        theVortexIsBounded();
        System.out.println("BossSpellVisualCompositionPolicyTest: PASS (" + checks + " checks)");
    }

    private static void everyAuthoredSpellHasAFullSignature() {
        // Every entry in the table is validated. This used to be a hand-copied
        // list of ten ids: when the miniboss ids were authored the list was not
        // wrong, it had simply stopped being the roster, so half the table was
        // never checked while the probe still passed.
        List<String> spells = BossSpellVisualCompositionPolicy.spellIds();
        check("the authored roster did not shrink below " + MIN_AUTHORED_SPELLS
                        + " spells (found " + spells.size() + ")",
                spells.size() >= MIN_AUTHORED_SPELLS);
        for (String spell : spells) {
            check("authored: " + spell, BossSpellVisualCompositionPolicy.isAuthored(spell));
            var composition = BossSpellVisualCompositionPolicy.composition(spell);
            check("keys itself: " + spell, composition.spellId().equals(spell));
            check("has a primary particle: " + spell, !composition.primaryParticle().isBlank());
            check("has an accent particle: " + spell, !composition.accentParticle().isBlank());
            check("primary and accent differ: " + spell,
                    !composition.primaryParticle().equals(composition.accentParticle()));
            check("has a telegraph sound: " + spell,
                    composition.telegraphSound().startsWith("minecraft:"));
            check("has a readable signature: " + spell, !composition.impactSignature().isBlank());
        }
        // Every miniboss spell the rotation enum can name must be authored. The
        // renderer falls back to the generic cue when a composition is missing,
        // so a new constant added without an entry would fire a cast with no tell.
        for (EndRiftAiPolicy.MiniBossSpell spell : EndRiftAiPolicy.MiniBossSpell.values()) {
            check("authored miniboss spell: " + spell.id(),
                    BossSpellVisualCompositionPolicy.isAuthored(spell.id()));
        }
        // Every BossSpell the boss can select must be authored *and* name a
        // body. `melee_swipe`, `chest_strike` and `ground_slam` really do reach
        // `telegraphBossSpell`, so an unauthored one drew the generic burst
        // instead of its own silhouette; and an authored spell with no shape
        // makes `renderAuthoredBossTelegraph` return on its first line, i.e. a
        // cast with no tell at all. Requiring both means neither regression can
        // come back quietly.
        for (EndRiftAiPolicy.BossSpell spell : EndRiftAiPolicy.BossSpell.values()) {
            check("authored boss spell: " + spell.id(),
                    BossSpellVisualCompositionPolicy.isAuthored(spell.id()));
            check("boss spell names a body: " + spell.id(),
                    BossSpellGeometryPolicy.shape(spell.id()) != null);
        }
    }

    private static void noTwoSpellsShareASignature() {
        // The defect this replaces: two spells could land on the same particle
        // pair through index arithmetic, so the tell was ambiguous. The table
        // promises "every spell has its own colour and its own particle pair",
        // which is why both halves are checked over the whole roster.
        Set<String> hues = new HashSet<>();
        Set<String> primaries = new HashSet<>();
        for (String spell : BossSpellVisualCompositionPolicy.spellIds()) {
            var composition = BossSpellVisualCompositionPolicy.composition(spell);
            check("distinct hue: " + spell + " is 0x"
                            + Integer.toHexString(composition.telegraphHue()),
                    hues.add(Integer.toHexString(composition.telegraphHue())));
            check("distinct primary particle pair: " + spell + " is "
                            + composition.primaryParticle() + "/" + composition.accentParticle(),
                    primaries.add(composition.primaryParticle() + "/" + composition.accentParticle()));
        }
    }

    private static void telegraphAndImpactDifferInHeat() {
        for (String spell : BossSpellVisualCompositionPolicy.spellIds()) {
            var composition = BossSpellVisualCompositionPolicy.composition(spell);
            int telegraph = composition.telegraphHue();
            int impact = composition.impactHue();
            check("impact is not the telegraph colour: " + spell, telegraph != impact);
            // Hotter means more red, less blue: the hit reads as fire, the
            // windup reads as cold magic.
            check("impact is warmer than the telegraph: " + spell,
                    ((impact >> 16) & 0xFF) >= ((telegraph >> 16) & 0xFF));
            check("impact is yellower than the telegraph: " + spell,
                    (impact & 0xFF) <= (telegraph & 0xFF));
        }
    }

    private static void unknownSpellsFallBackCleanly() {
        check("null spell is unknown", !BossSpellVisualCompositionPolicy.isAuthored(null));
        check("blank spell is unknown", !BossSpellVisualCompositionPolicy.isAuthored("   "));
        check("unknown spell yields the fallback marker",
                BossSpellVisualCompositionPolicy.composition("nope").equals(
                        BossSpellVisualCompositionPolicy.UNKNOWN));
        check("lookup is case and dash insensitive",
                BossSpellVisualCompositionPolicy.isAuthored("Void-Blast"));
    }

    private static void theVortexIsBounded() {
        for (String spell : BossSpellVisualCompositionPolicy.spellIds()) {
            float turns = BossSpellVisualCompositionPolicy.composition(spell).vortexTurns();
            check("vortex turns stay finite and bounded: " + spell, turns >= 0.0F && turns <= 4.0F);
        }
    }

    private static void check(String name, boolean condition) {
        checks++;
        if (!condition) {
            throw new AssertionError("FAILED: " + name);
        }
    }
}