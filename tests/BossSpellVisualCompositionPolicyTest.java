import java.util.HashSet;
import java.util.List;
import java.util.Set;
import me.copimine.endevent.domain.BossSpellVisualCompositionPolicy;

/** Every boss spell must look like itself, not like the shared particle pile. */
public final class BossSpellVisualCompositionPolicyTest {
    private static int checks;

    private BossSpellVisualCompositionPolicyTest() {
    }

    public static void main(String[] args) {
        everyBossSpellIsAuthored();
        noTwoSpellsShareASignature();
        telegraphAndImpactDifferInHeat();
        unknownSpellsFallBackCleanly();
        theVortexIsBounded();
        System.out.println("BossSpellVisualCompositionPolicyTest: PASS (" + checks + " checks)");
    }

    private static void everyBossSpellIsAuthored() {
        List<String> expected = List.of("void_blast", "rift_projectile", "rift_arrows", "void_mark",
                "summon_servants", "arena_inferno", "boss_fireball", "final_strike",
                "rift_obelisks", "phase_shift");
        for (String spell : expected) {
            check("authored: " + spell, BossSpellVisualCompositionPolicy.isAuthored(spell));
            check("has a primary particle: " + spell,
                    !BossSpellVisualCompositionPolicy.composition(spell).primaryParticle().isBlank());
            check("has an accent particle: " + spell,
                    !BossSpellVisualCompositionPolicy.composition(spell).accentParticle().isBlank());
            check("has a telegraph sound: " + spell,
                    BossSpellVisualCompositionPolicy.composition(spell).telegraphSound()
                            .startsWith("minecraft:"));
            check("has a readable signature: " + spell,
                    !BossSpellVisualCompositionPolicy.composition(spell).impactSignature().isBlank());
        }
    }

    private static void noTwoSpellsShareASignature() {
        // The defect this replaces: two spells could land on the same particle
        // pair through index arithmetic, so the tell was ambiguous.
        Set<String> hues = new HashSet<>();
        Set<String> primaries = new HashSet<>();
        for (String spell : BossSpellVisualCompositionPolicy.spellIds()) {
            var composition = BossSpellVisualCompositionPolicy.composition(spell);
            check("distinct hue: " + spell, hues.add(Integer.toHexString(composition.telegraphHue())));
            check("distinct primary particle pair: "
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
