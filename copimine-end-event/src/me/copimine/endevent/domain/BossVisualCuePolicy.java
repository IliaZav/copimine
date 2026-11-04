package me.copimine.endevent.domain;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable cue table for End Rift boss visuals.
 */
public final class BossVisualCuePolicy {
    private static final List<String> SPELLS = List.of(
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

    /**
     * One authored signature per spell: the colour pair and the sound triple a
     * player learns to associate with it. Everything about a cue is written
     * here by hand, so a spell can never inherit another spell's tell.
     */
    private record Bank(String primaryParticle, String calmAccent, String hotAccent,
                        String telegraphSound, String releaseSound, String impactSound) {
        Bank {
            if (primaryParticle.isBlank() || calmAccent.isBlank() || hotAccent.isBlank()) {
                throw new IllegalArgumentException("boss cue bank particles must be present");
            }
            if (primaryParticle.equals(calmAccent)
                    || primaryParticle.equals(hotAccent)
                    || calmAccent.equals(hotAccent)) {
                throw new IllegalArgumentException("boss cue bank particles must be distinct");
            }
            if (!telegraphSound.startsWith("minecraft:")
                    || !releaseSound.startsWith("minecraft:")
                    || !impactSound.startsWith("minecraft:")) {
                throw new IllegalArgumentException("boss cue sounds must be vanilla ids");
            }
        }

        String accentParticle(CueStage stage) {
            return stage == CueStage.IMPACT ? hotAccent : calmAccent;
        }

        String sound(CueStage stage) {
            return switch (stage) {
                case TELEGRAPH -> telegraphSound;
                case RELEASE -> releaseSound;
                case IMPACT -> impactSound;
            };
        }

        /** Impacts throw more than windups, so the hit is the loudest moment. */
        int particleBudget(CueStage stage) {
            return switch (stage) {
                case TELEGRAPH -> 26;
                case RELEASE -> 40;
                case IMPACT -> 56;
            };
        }

        /** A windup may not be replayed for the length of the tell itself. */
        int cooldownTicks(CueStage stage) {
            return switch (stage) {
                case TELEGRAPH -> 40;
                case RELEASE -> 24;
                case IMPACT -> 30;
            };
        }
    }

    private static final Map<String, Bank> BANK = buildBanks();
    private static final Map<String, Map<CueStage, Cue>> CUES = buildCues();

    private BossVisualCuePolicy() {
    }

    public static Cue cue(String spellId, CueStage stage) {
        if (spellId == null || stage == null) {
            return null;
        }
        Map<CueStage, Cue> stages = CUES.get(normalize(spellId));
        return stages == null ? null : stages.get(stage);
    }

    public static boolean shouldSuppressDuplicate(CueToken active, CueToken requested,
                                                   boolean forced, long currentTick) {
        return !forced
                && active != null
                && requested != null
                && active.generation() == requested.generation()
                && Objects.equals(active.owner(), requested.owner())
                && Objects.equals(active.cueId(), requested.cueId())
                && currentTick < active.deadlineTick();
    }

    public static boolean canReset(CueToken callback, CueToken active,
                                   boolean taskOwned, boolean bossLive,
                                   boolean defeatCommitted) {
        return !defeatCommitted
                && taskOwned
                && bossLive
                && callback != null
                && active != null
                && callback.sequence() > 0L
                && callback.generation() == active.generation()
                && Objects.equals(callback.owner(), active.owner())
                && callback.sequence() == active.sequence()
                && Objects.equals(callback.cueId(), active.cueId());
    }

    public static CueToken resetTokenForAcceptedCue(CueToken acceptedCue, boolean terminal) {
        return terminal || acceptedCue == null || acceptedCue.sequence() < 1L
                || acceptedCue.owner() == null || acceptedCue.cueId().isBlank()
                ? null : acceptedCue;
    }

    public static Map<String, Map<CueStage, Cue>> cues() {
        return CUES;
    }

    /**
     * Resolves the animation that must be sent to the client for a cue stage.
     * Telegraph and impact use shared timing poses; the release pose remains
     * spell-specific so every boss ability has a readable silhouette.
     */
    public static String animationFor(String spellId, CueStage stage) {
        if (spellId == null || stage == null) {
            return BossAnimationId.UNKNOWN.wireId();
        }
        String normalized = normalize(spellId);
        return SPELLS.contains(normalized)
                ? spellAnimation(normalized, stage)
                : BossAnimationId.UNKNOWN.wireId();
    }

    /**
     * The animation for a spell's release.
     *
     * <p>This used to be {@code baseId + ":animation"} for every stage, so all
     * three stages of one spell produced the same animation id — only the spell
     * index and the stage ordinal differ in the authored catalog, and the
     * animation did not carry either. Two stages of the same spell were
     * therefore indistinguishable to the client.</p>
     */
    private static String spellAnimation(String spellId, CueStage stage) {
        // Phase shift, the final strike and the collapse name one moment each:
        // they are the same pose for every stage, and that is deliberate.
        String fixed = switch (spellId) {
            case "phase_shift" -> BossAnimationId.PHASE_TRANSITION.wireId();
            case "final_strike" -> BossAnimationId.FINAL_STRIKE.wireId();
            case "defeat_collapse" -> BossAnimationId.DYING.wireId();
            default -> "";
        };
        if (!fixed.isEmpty()) {
            return fixed;
        }
        return switch (stage) {
            case TELEGRAPH -> BossAnimationId.CAST_CHARGE.wireId();
            case RELEASE -> spellReleaseAnimation(spellId);
            case IMPACT -> BossAnimationId.CAST_IMPACT.wireId();
        };
    }

    /**
     * The spell's own silhouette for a release.
     *
     * <p>{@link BossAnimationId} has a dedicated pose for six spells; the
     * remaining ones release as the shared {@code CAST_RELEASE}. Every pose
     * here must also exist in that catalog, because the client resolves the id
     * through it and an unknown id is a soft failure there, not a crash.</p>
     */
    private static String spellReleaseAnimation(String spellId) {
        return switch (spellId) {
            case "void_blast" -> BossAnimationId.SPELL_VOID_BLAST.wireId();
            case "rift_projectile" -> BossAnimationId.SPELL_RIFT_PROJECTILE.wireId();
            case "void_mark" -> BossAnimationId.SPELL_VOID_MARK.wireId();
            case "summon_servants" -> BossAnimationId.SPELL_SUMMON_SERVANTS.wireId();
            case "rift_obelisks" -> BossAnimationId.SPELL_RIFT_OBELISKS.wireId();
            case "arena_inferno" -> BossAnimationId.SPELL_ARENA_INFERNO.wireId();
            default -> BossAnimationId.CAST_RELEASE.wireId();
        };
    }

    private static Map<String, Map<CueStage, Cue>> buildCues() {
        LinkedHashMap<String, Map<CueStage, Cue>> catalog = new LinkedHashMap<>();
        for (String spell : SPELLS) {
            LinkedHashMap<CueStage, Cue> stages = new LinkedHashMap<>();
            for (CueStage stage : CueStage.values()) {
                stages.put(stage, buildCue(spell, stage));
            }
            catalog.put(spell, Map.copyOf(stages));
        }
        return Map.copyOf(catalog);
    }

    private static Cue buildCue(String spell, CueStage stage) {
        // Spell and stage are authored, never indexed.
        //
        // This used to be arithmetic: the sound came from a shared table at
        // `spellIndex * stages + stageIndex` and the particles from two more
        // tables at `spellIndex % length` and `(spellIndex + stageIndex) %
        // length`. With ten spells and three stages that is thirty sound slots
        // against a thirty-six entry table, so adding one spell silently moved
        // every later spell onto a different sound; the particles wrapped after
        // ten, so a void blast and a summon shared a primary. A cue a player is
        // meant to read must not depend on where a spell sits in a list.
        Bank bank = BANK.get(spell);
        if (bank == null) {
            throw new IllegalArgumentException("no authored cue bank for boss spell " + spell);
        }
        String baseId = spell + ":" + stage.name().toLowerCase(Locale.ROOT);
        return new Cue(
                baseId,
                SPELLS.contains(spell) ? spellAnimation(spell, stage) : BossAnimationId.UNKNOWN.wireId(),
                bank.sound(stage),
                bank.primaryParticle(),
                bank.accentParticle(stage),
                bank.particleBudget(stage),
                bank.cooldownTicks(stage));
    }

    private static Map<String, Bank> buildBanks() {
        LinkedHashMap<String, Bank> banks = new LinkedHashMap<>();
        // Cold violet breath collapses into the target.
        banks.put("void_blast", new Bank("DRAGON_BREATH", "DUST_PURPLE", "DUST_MAGENTA",
                "minecraft:entity.warden.nearby_close",
                "minecraft:entity.ender_dragon.shoot",
                "minecraft:entity.wither.shoot"));
        // A cyan lance spins down the flight path.
        banks.put("rift_projectile", new Bank("REVERSE_PORTAL", "DUST_CYAN", "FLASH",
                "minecraft:block.respawn_anchor.charge",
                "minecraft:entity.ender_dragon.flap",
                "minecraft:entity.trident.throw"));
        // Three red lanes snap together.
        banks.put("rift_arrows", new Bank("CRIT", "DUST_RED", "DUST_WHITE",
                "minecraft:entity.arrow.shoot",
                "minecraft:entity.skeleton.shoot",
                "minecraft:entity.arrow.hit_player"));
        // A purple sigil turns on the floor: a mark, not a shot.
        banks.put("void_mark", new Bank("ELECTRIC_SPARK", "DUST_PURPLE", "DUST_MAGENTA",
                "minecraft:block.sculk_shrieker.shriek",
                "minecraft:entity.witch.throw",
                "minecraft:entity.player.hurt"));
        // A soul coil rises at the cast point.
        banks.put("summon_servants", new Bank("WITCH", "SOUL", "SOUL_FIRE_FLAME",
                "minecraft:entity.evoker.prepare_attack",
                "minecraft:entity.evoker.cast_spell",
                "minecraft:entity.evoker.ambient"));
        // An amber ring widens across the arena floor.
        banks.put("arena_inferno", new Bank("SOUL_FIRE_FLAME", "DUST_GREEN", "DUST_RED",
                "minecraft:entity.ghast.warn",
                "minecraft:entity.ghast.shoot",
                "minecraft:entity.generic.explode"));
        // A hard shaft drops out of the sky onto the target.
        banks.put("boss_fireball", new Bank("FLAME", "DUST_WHITE", "DUST_RED",
                "minecraft:entity.blaze.shoot",
                "minecraft:entity.blaze.burn",
                "minecraft:block.fire.extinguish"));
        // The seal: slow, heavy, unmistakable.
        banks.put("final_strike", new Bank("SCULK_CHARGE", "DUST_CYAN", "DUST_RED",
                "minecraft:entity.warden.sonic_boom",
                "minecraft:block.respawn_anchor.deplete",
                "minecraft:entity.wither.spawn"));
        // The same constellation the obelisk wave raises.
        banks.put("rift_obelisks", new Bank("REVERSE_PORTAL", "DUST_CYAN", "DUST_WHITE",
                "minecraft:block.amethyst_block.resonate",
                "minecraft:block.beacon.activate",
                "minecraft:block.beacon.deactivate"));
        // The boss unmakes and rebuilds itself; the sky answers.
        banks.put("phase_shift", new Bank("PORTAL", "END_ROD", "DUST_MAGENTA",
                "minecraft:block.portal.ambient",
                "minecraft:block.portal.travel",
                "minecraft:block.end_portal.spawn"));
        // Breaking apart: fragments fall, then everything thunders.
        banks.put("defeat_collapse", new Bank("SMOKE", "DUST_WHITE", "DUST_PURPLE",
                "minecraft:block.glass.break",
                "minecraft:entity.ender_dragon.growl",
                "minecraft:entity.lightning_bolt.thunder"));
        return Map.copyOf(banks);
    }

    private static String normalize(String spellId) {
        return spellId.trim().toLowerCase(Locale.ROOT);
    }

    public enum CueStage {
        TELEGRAPH,
        RELEASE,
        IMPACT
    }

    public record CueToken(long generation, UUID owner, long sequence,
                           String cueId, long deadlineTick) {
        public CueToken {
            cueId = cueId == null ? "" : cueId.trim().toLowerCase(Locale.ROOT);
        }
    }

    public record Cue(String id, String animationId, String soundId, String primaryParticle,
                      String accentParticle, int particleBudget, int cooldownTicks) {
        public Cue {
            id = normalizeText(id);
            animationId = normalizeText(animationId);
            soundId = normalizeText(soundId);
            primaryParticle = normalizeText(primaryParticle);
            accentParticle = normalizeText(accentParticle);
            if (id.isBlank() || animationId.isBlank() || soundId.isBlank()
                    || primaryParticle.isBlank() || accentParticle.isBlank()) {
                throw new IllegalArgumentException("invalid boss cue");
            }
            if (particleBudget < 1 || particleBudget > 96) {
                throw new IllegalArgumentException("invalid boss cue particle budget");
            }
            if (cooldownTicks < 1) {
                throw new IllegalArgumentException("invalid boss cue cooldown");
            }
            if (primaryParticle.equals(accentParticle)) {
                throw new IllegalArgumentException("boss cue particles must be distinct");
            }
        }

        private static String normalizeText(String value) {
            return value == null ? "" : value.trim();
        }
    }
}
