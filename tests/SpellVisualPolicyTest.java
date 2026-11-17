import me.copimine.endevent.domain.EndRiftAiPolicy;
import me.copimine.endevent.domain.SpellVisualPolicy;

/** Pure checks for the small, named particle silhouettes used by event spells. */
public final class SpellVisualPolicyTest {
    /**
     * The full authored roster. This is a containment claim, never a count: the
     * table already moved from fourteen spells to nineteen when the miniboss
     * rotation gained its missing ids, and a hardcoded `size() == n` turned that
     * correct authoring into a probe failure. Every entry in the table is
     * validated below, so an added spell cannot slip through unnoticed.
     */
    private static final String[] EXPECTED_SPELLS = {
            "void_blast", "rift_projectile", "rift_arrows", "arrow_salvo",
            "void_mark", "summon_servants", "arena_inferno",
            "rift_obelisks", "boss_fireball", "final_strike",
            "rift_step", "void_snare", "echo_pulse", "rift_euphoria",
            "arrow_rain", "rift_bolt", "dragon_breath", "void_burst", "web_shot"
    };

    /**
     * The five constants `miniBossSpellFor` actually dispatches. The other
     * MiniBossSpell constants are authored but never emitted, so the rotation
     * half of the table is repeated here as the live requirement it satisfies.
     */
    private static final EndRiftAiPolicy.MiniBossSpell[] LIVE_ROTATION = {
            EndRiftAiPolicy.MiniBossSpell.ARROW_RAIN,
            EndRiftAiPolicy.MiniBossSpell.RIFT_BOLT,
            EndRiftAiPolicy.MiniBossSpell.DRAGON_BREATH,
            EndRiftAiPolicy.MiniBossSpell.VOID_BURST,
            EndRiftAiPolicy.MiniBossSpell.WEB_SHOT
    };

    public static void main(String[] args) {
        var profiles = SpellVisualPolicy.profiles();
        require(profiles.size() >= EXPECTED_SPELLS.length,
                "the authored roster shrank to " + profiles.size() + " spells");

        // The table is its own source of truth: validate every authored entry,
        // so a newly added spell with a stub profile fails instead of passing
        // because it was left out of a hand-copied list.
        for (var entry : profiles.entrySet()) {
            String id = entry.getKey();
            SpellVisualPolicy.VisualProfile profile = entry.getValue();
            require(profile != null, id + " must have a visual profile");
            require(id.equals(profile.id()), id + " must be keyed by its own id");
            require(profile.displayName() != null && !profile.displayName().isBlank(),
                    id + " must have a player-facing name");
            require(!profile.displayName().equals(id),
                    id + " must not fall back to an internal id");
            require(profile.layers() >= 3, id + " needs layered particles");
            require(profile.estimatedParticles() > 0,
                    id + " needs a bounded particle budget");
            require(!profile.primaryParticle().equals(profile.accentParticle()),
                    id + " needs a distinct accent particle");
        }

        for (String spell : EXPECTED_SPELLS) {
            require(SpellVisualPolicy.profile(spell) != null,
                    spell + " must have a visual profile");
        }

        // Every miniboss spell id must be authored: the renderer returns on its
        // first line when the profile is null, so a missing id means that cast
        // draws nothing but the generic fallback particle.
        for (EndRiftAiPolicy.MiniBossSpell spell : EndRiftAiPolicy.MiniBossSpell.values()) {
            require(SpellVisualPolicy.profile(spell.id()) != null,
                    spell.id() + " is a miniboss spell id and needs a visual profile");
        }

        // And the half the rotation really emits must never regress to no visual.
        for (EndRiftAiPolicy.MiniBossSpell spell : LIVE_ROTATION) {
            SpellVisualPolicy.VisualProfile profile = SpellVisualPolicy.profile(spell.id());
            require(profile != null && !profile.displayName().isBlank(),
                    spell.id() + " is dispatched by the miniboss rotation and must stay authored");
        }

        require(SpellVisualPolicy.profile("unknown") == null,
                "unknown spells must not invent an unsafe visual");
        System.out.println("SpellVisualPolicyTest OK");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}