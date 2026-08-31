import me.copimine.endevent.domain.BossVisualCuePolicy;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class BossVisualCuePolicyTest {
    private static final List<String> CURRENT_SPELLS = List.of(
            "void_blast",
            "rift_projectile",
            "rift_arrows",
            "void_mark",
            "summon_servants",
            "will_distortion",
            "arena_inferno",
            "phase_shift",
            "final_awaken",
            "defeat_collapse");

    public static void main(String[] args) {
        Map<String, Map<BossVisualCuePolicy.CueStage, BossVisualCuePolicy.Cue>> catalog = BossVisualCuePolicy.cues();
        require(catalog.size() == CURRENT_SPELLS.size(), "every boss spell needs one cue catalog");

        Set<String> cueIds = new LinkedHashSet<>();
        Set<String> animationIds = new LinkedHashSet<>();
        Set<String> sounds = new LinkedHashSet<>();

        for (String spell : CURRENT_SPELLS) {
            Map<BossVisualCuePolicy.CueStage, BossVisualCuePolicy.Cue> stages = catalog.get(spell);
            require(stages != null, spell + " must have a stage catalog");
            require(stages.size() == BossVisualCuePolicy.CueStage.values().length,
                    spell + " must expose TELEGRAPH, RELEASE and IMPACT");

            for (BossVisualCuePolicy.CueStage stage : BossVisualCuePolicy.CueStage.values()) {
                BossVisualCuePolicy.Cue cue = BossVisualCuePolicy.cue(spell, stage);
                require(cue != null, spell + " " + stage + " cue must exist");
                require(!cue.id().isBlank(), spell + " " + stage + " cue id must be stable");
                require(cueIds.add(cue.id()), spell + " " + stage + " cue id must be unique");
                require(!cue.animationId().isBlank(), spell + " " + stage + " animation id must be stable");
                require(animationIds.add(cue.animationId()),
                        spell + " " + stage + " animation id must be unique");
                require(!cue.soundId().isBlank() && cue.soundId().startsWith("minecraft:"),
                        spell + " " + stage + " sound id must be a vanilla id");
                require(sounds.add(cue.soundId()), spell + " " + stage + " sound id must be unique enough to bind");
                require(!cue.primaryParticle().isBlank(), spell + " " + stage + " primary particle must exist");
                require(!cue.accentParticle().isBlank(), spell + " " + stage + " accent particle must exist");
                require(!cue.primaryParticle().equals(cue.accentParticle()),
                        spell + " " + stage + " needs two distinct particles");
                require(cue.particleBudget() > 0 && cue.particleBudget() <= 96,
                        spell + " " + stage + " particle budget must stay bounded");
                require(cue.cooldownTicks() > 0, spell + " " + stage + " cooldown must be positive");
            }
        }

        requireThrows(UnsupportedOperationException.class, () -> catalog.put("extra", Map.of()),
                "cue catalog must be immutable");
        requireThrows(UnsupportedOperationException.class,
                () -> catalog.get("void_blast").put(BossVisualCuePolicy.CueStage.TELEGRAPH, catalog.get("void_blast").get(BossVisualCuePolicy.CueStage.TELEGRAPH)),
                "stage catalog must be immutable");

        require(BossVisualCuePolicy.cue("unknown_spell", BossVisualCuePolicy.CueStage.TELEGRAPH) == null,
                "unknown spell ids must fail closed");
        require(BossVisualCuePolicy.cue("   ", BossVisualCuePolicy.CueStage.RELEASE) == null,
                "blank spell ids must fail closed");
        require(BossVisualCuePolicy.cue("void_blast", null) == null,
                "invalid cue stages must fail closed");

        UUID owner = UUID.randomUUID();
        BossVisualCuePolicy.CueToken first = new BossVisualCuePolicy.CueToken(
                7L, owner, 11L, "void_blast:release", 120L);
        BossVisualCuePolicy.CueToken sameCue = new BossVisualCuePolicy.CueToken(
                7L, owner, 11L, "void_blast:release", 120L);
        BossVisualCuePolicy.CueToken newerCue = new BossVisualCuePolicy.CueToken(
                7L, owner, 12L, "phase_shift:impact", 140L);

        require(BossVisualCuePolicy.shouldSuppressDuplicate(first, sameCue, false, 119L),
                "same cue before its deadline must be suppressed");
        require(!BossVisualCuePolicy.shouldSuppressDuplicate(first, sameCue, false, 120L),
                "same cue at its deadline must be allowed");
        require(!BossVisualCuePolicy.shouldSuppressDuplicate(first, sameCue, true, 119L),
                "forced cues must bypass duplicate suppression");
        require(BossVisualCuePolicy.canReset(first, first, true, true, false),
                "current owned live cue may reset");
        require(!BossVisualCuePolicy.canReset(first, newerCue, true, true, false),
                "a stale reset must not supersede a newer cue");
        require(!BossVisualCuePolicy.canReset(first, first, true, true, true),
                "a defeated boss must not be reset");
        System.out.println("BossVisualCuePolicyTest OK");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void requireThrows(Class<? extends Throwable> expected, ThrowingRunnable action, String message) {
        try {
            action.run();
        } catch (Throwable thrown) {
            if (expected.isInstance(thrown)) {
                return;
            }
            throw new AssertionError(message + " (unexpected " + thrown.getClass().getSimpleName() + ")", thrown);
        }
        throw new AssertionError(message);
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
