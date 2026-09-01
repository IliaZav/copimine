import me.copimine.endevent.domain.SpellVisualPolicy;

/** Pure checks for the small, named particle silhouettes used by event spells. */
public final class SpellVisualPolicyTest {
    private static final String[] SPELLS = {
            "void_blast", "rift_projectile", "rift_arrows", "arrow_salvo",
            "void_mark", "summon_servants", "will_distortion", "arena_inferno",
            "rift_obelisks", "final_strike",
            "rift_step", "void_snare", "echo_pulse", "rift_euphoria"
    };

    public static void main(String[] args) {
        require(SpellVisualPolicy.profiles().size() == SPELLS.length,
                "every supported spell must have one visual profile");
        for (String spell : SPELLS) {
            SpellVisualPolicy.VisualProfile profile = SpellVisualPolicy.profile(spell);
            require(profile != null, spell + " must have a visual profile");
            require(profile.displayName() != null && !profile.displayName().isBlank(),
                    spell + " must have a player-facing name");
            require(!profile.displayName().equals(spell),
                    spell + " must not fall back to an internal id");
            require(profile.layers() >= 3, spell + " needs layered particles");
            require(profile.estimatedParticles() > 0,
                    spell + " needs a bounded particle budget");
            require(!profile.primaryParticle().equals(profile.accentParticle()),
                    spell + " needs a distinct accent particle");
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
