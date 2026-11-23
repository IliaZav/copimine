import me.copimine.endevent.domain.BossAnimationId;
import me.copimine.endevent.domain.BossSpellVisualCompositionPolicy;
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
            "rift_obelisks",
            "arena_inferno",
            "phase_shift",
            "defeat_collapse",
            "final_strike");

    /** Spells that are one moment, not a windup and a hit. */
    private static final List<String> SINGLE_MOMENT_SPELLS = List.of(
            "phase_shift", "final_strike", "defeat_collapse");

    public static void main(String[] args) {
        Map<String, Map<BossVisualCuePolicy.CueStage, BossVisualCuePolicy.Cue>> catalog = BossVisualCuePolicy.cues();
        require(catalog.size() == CURRENT_SPELLS.size(), "every boss spell needs one cue catalog");

        Set<String> cueIds = new LinkedHashSet<>();
        Set<String> spellAnimations = new LinkedHashSet<>();
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
                // The animation has to name the spell AND the stage. It used to be
                // baseId + ":animation", so all three stages of a spell carried the
                // same pose and the client could not tell a windup from a hit.
                require(BossAnimationId.isKnown(cue.animationId()),
                        spell + " " + stage + " animation must be in the shared catalog");
                // Uniqueness is per spell, not across the whole catalog:
                // BossAnimationId only owns a handful of release poses, so two
                // spells may legitimately share one. What must never happen is
                // one spell using the same pose for two of its own stages — that
                // is the defect this now guards, and it is what the previous
                // baseId + ":animation" produced for all three.
                // Phase shift, the final strike and the defeat collapse name a
                // single moment, so one pose for all three stages is the
                // authored intent. Every other spell must not repeat itself.
                if (!SINGLE_MOMENT_SPELLS.contains(spell)) {
                    require(spellAnimations.add(spell + "|" + cue.animationId()),
                            spell + " " + stage + " must not reuse another stage's animation");
                }
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

            require(BossAnimationId.isKnown(
                            BossVisualCuePolicy.animationFor(spell, BossVisualCuePolicy.CueStage.TELEGRAPH)),
                    spell + " telegraph animation must be in the shared catalog");
            require(BossAnimationId.isKnown(
                            BossVisualCuePolicy.animationFor(spell, BossVisualCuePolicy.CueStage.RELEASE)),
                    spell + " release animation must be in the shared catalog");
            require(BossAnimationId.isKnown(
                            BossVisualCuePolicy.animationFor(spell, BossVisualCuePolicy.CueStage.IMPACT)),
                    spell + " impact animation must be in the shared catalog");
        }

        require("CAST_CHARGE".equals(BossVisualCuePolicy.animationFor(
                        "void_blast", BossVisualCuePolicy.CueStage.TELEGRAPH)),
                "telegraph must use the explicit charge pose");
        require("SPELL_RIFT_OBELISKS".equals(BossVisualCuePolicy.animationFor(
                        "rift_obelisks", BossVisualCuePolicy.CueStage.RELEASE)),
                "obelisk release must use its dedicated pose");
        require("PHASE_TRANSITION".equals(BossVisualCuePolicy.animationFor(
                        "phase_shift", BossVisualCuePolicy.CueStage.IMPACT)),
                "phase transition must use its dedicated pose");
        require("DYING".equals(BossVisualCuePolicy.animationFor(
                        "defeat_collapse", BossVisualCuePolicy.CueStage.IMPACT)),
                "defeat collapse must use the dying pose");

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
        BossVisualCuePolicy.CueToken impactCue = new BossVisualCuePolicy.CueToken(
                7L, owner, 12L, "void_blast:impact", 140L);
        BossVisualCuePolicy.CueToken terminalCue = new BossVisualCuePolicy.CueToken(
                7L, owner, 13L, "defeat_collapse:impact", 160L);

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

        BossVisualCuePolicy.CueToken resetCue =
                BossVisualCuePolicy.resetTokenForAcceptedCue(impactCue, false);
        require(resetCue.equals(impactCue),
                "the accepted IMPACT cue must own the reset token");
        require(!BossVisualCuePolicy.canReset(first, resetCue, true, true, false),
                "the RELEASE reset must be rejected after IMPACT");
        String animation = "CAST_IMPACT";
        if (BossVisualCuePolicy.canReset(resetCue, resetCue, true, true, false)) {
            animation = "IDLE_BREATH";
        }
        require("IDLE_BREATH".equals(animation),
                "RELEASE to IMPACT must eventually reset the current animation to IDLE_BREATH");
        require(BossVisualCuePolicy.resetTokenForAcceptedCue(terminalCue, true) == null,
                "terminal cues must not schedule an animation reset");

        // The release pose must exist for the whole boss kit, not only the ten
        // cue spells. Regression: the chest beam and the boss fireball released
        // through animationFor(), which returned UNKNOWN for anything outside
        // the cue table, and the client draws UNKNOWN as the idle breath --
        // the Guardian visibly stopped performing its own ultimate.
        require("CHEST_STRIKE".equals(BossVisualCuePolicy.releaseAnimation("chest_beam")),
                "the chest beam must release on the chest pose it charged in");
        require("CHEST_STRIKE".equals(BossVisualCuePolicy.releaseAnimation("chest_strike")),
                "the chest strike must release on its own pose");
        require("MELEE_SWIPE".equals(BossVisualCuePolicy.releaseAnimation("melee_swipe")),
                "the claw swipe must release on its own pose");
        require("GROUND_SLAM".equals(BossVisualCuePolicy.releaseAnimation("ground_slam")),
                "the ground slam must release on its own pose");
        require("SPELL_VOID_BLAST".equals(BossVisualCuePolicy.releaseAnimation("void_blast")),
                "a cue spell must keep its dedicated release pose");
        require("CAST_RELEASE".equals(BossVisualCuePolicy.releaseAnimation("boss_fireball")),
                "an uncatalogued spell must fall back to the shared cast pose, not idle");
        require("CAST_RELEASE".equals(BossVisualCuePolicy.releaseAnimation("ambush_grab")),
                "every unauthored boss spell must still resolve to a real pose");
        require("PHASE_TRANSITION".equals(BossVisualCuePolicy.releaseAnimation("phase_shift")),
                "a single-moment spell must name the same pose for its release");
        require("FINAL_STRIKE".equals(BossVisualCuePolicy.releaseAnimation("final_strike")),
                "the final strike release must keep its dedicated pose");
        require("CAST_RELEASE".equals(BossVisualCuePolicy.releaseAnimation(null)),
                "a null spell id must fail closed to a real pose");
        require("CAST_RELEASE".equals(BossVisualCuePolicy.releaseAnimation("   ")),
                "a blank spell id must fail closed to a real pose");
        for (String spell : List.of("void_blast", "rift_projectile", "void_mark", "rift_arrows",
                "summon_servants", "arena_inferno", "boss_fireball", "melee_swipe",
                "chest_strike", "ground_slam", "chest_beam", "ambush_grab", "small_kagune",
                "kagune_stab", "kagune_eruption", "final_strike")) {
            require(BossAnimationId.isKnown(BossVisualCuePolicy.releaseAnimation(spell))
                            && !"UNKNOWN".equals(BossVisualCuePolicy.releaseAnimation(spell)),
                    spell + " must never release on the residual UNKNOWN pose");
        }

        // Authored spells outside the legacy cue table still need the per-stage
        // cooldown, so the windup cannot be replayed twice for one tell. The
        // helper must agree with the cue record it replaced.
        require(BossVisualCuePolicy.cooldownForStage(BossVisualCuePolicy.CueStage.TELEGRAPH) == 40,
                "a telegraph may not be replayed inside its own tell");
        require(BossVisualCuePolicy.cooldownForStage(BossVisualCuePolicy.CueStage.RELEASE) == 24,
                "a release needs its own shorter dedup window");
        require(BossVisualCuePolicy.cooldownForStage(BossVisualCuePolicy.CueStage.IMPACT) == 30,
                "an impact needs its own dedup window");
        require(BossVisualCuePolicy.cooldownForStage(null) == 40,
                "a null stage must fail closed to the telegraph window");
        for (String authored : BossSpellVisualCompositionPolicy.spellIds()) {
            BossVisualCuePolicy.Cue spelled =
                    BossVisualCuePolicy.cue(authored, BossVisualCuePolicy.CueStage.TELEGRAPH);
            int expected = BossVisualCuePolicy.cooldownForStage(
                    BossVisualCuePolicy.CueStage.TELEGRAPH);
            if (spelled != null) {
                require(spelled.cooldownTicks() == expected,
                        authored + " cue cooldown must match the shared stage window");
            }
        }
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
