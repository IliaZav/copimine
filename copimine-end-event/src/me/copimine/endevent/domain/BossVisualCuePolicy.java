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
        if (!CUES.containsKey(normalized)) {
            return BossAnimationId.UNKNOWN.wireId();
        }
        return switch (normalized) {
            case "phase_shift" -> BossAnimationId.PHASE_TRANSITION.wireId();
            case "final_strike" -> BossAnimationId.FINAL_STRIKE.wireId();
            case "defeat_collapse" -> BossAnimationId.DYING.wireId();
            default -> switch (stage) {
                case TELEGRAPH -> BossAnimationId.CAST_CHARGE.wireId();
                case RELEASE -> spellAnimation(normalized);
                case IMPACT -> BossAnimationId.CAST_IMPACT.wireId();
            };
        };
    }

    private static String spellAnimation(String spellId) {
        return switch (spellId) {
            case "void_blast" -> BossAnimationId.SPELL_VOID_BLAST.wireId();
            case "rift_projectile" -> BossAnimationId.SPELL_RIFT_PROJECTILE.wireId();
            case "rift_arrows" -> BossAnimationId.SPELL_RIFT_ARROWS.wireId();
            case "void_mark" -> BossAnimationId.SPELL_VOID_MARK.wireId();
            case "summon_servants" -> BossAnimationId.SPELL_SUMMON_SERVANTS.wireId();
            case "rift_obelisks" -> BossAnimationId.SPELL_RIFT_OBELISKS.wireId();
            case "arena_inferno" -> BossAnimationId.SPELL_ARENA_INFERNO.wireId();
            default -> BossAnimationId.UNKNOWN.wireId();
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
        int spellIndex = SPELLS.indexOf(spell);
        int stageIndex = stage.ordinal();
        String baseId = spell + ":" + stage.name().toLowerCase(Locale.ROOT);
        String[] sounds = {
                "minecraft:entity.warden.nearby_close",
                "minecraft:entity.evoker.prepare_attack",
                "minecraft:entity.warden.heartbeat",
                "minecraft:block.respawn_anchor.charge",
                "minecraft:entity.enderman.ambient",
                "minecraft:entity.illusioner.prepare_mirror",
                "minecraft:entity.ghast.warn",
                "minecraft:block.beacon.ambient",
                "minecraft:block.amethyst_block.chime",
                "minecraft:block.portal.ambient",
                "minecraft:entity.ender_dragon.shoot",
                "minecraft:entity.enderman.teleport",
                "minecraft:entity.blaze.shoot",
                "minecraft:entity.illusioner.cast_spell",
                "minecraft:entity.evoker.cast_spell",
                "minecraft:entity.ghast.shoot",
                "minecraft:entity.witch.throw",
                "minecraft:entity.warden.sonic_boom",
                "minecraft:entity.trident.throw",
                "minecraft:entity.arrow.shoot",
                "minecraft:entity.generic.explode",
                "minecraft:block.end_portal.spawn",
                "minecraft:entity.lightning_bolt.thunder",
                "minecraft:entity.zombie.attack_iron_door",
                "minecraft:entity.wither.spawn",
                "minecraft:entity.player.hurt",
                "minecraft:block.glass.break",
                "minecraft:block.sculk_shrieker.shriek",
                "minecraft:block.amethyst_block.resonate",
                "minecraft:block.portal.travel",
                "minecraft:entity.ender_dragon.growl",
                "minecraft:block.beacon.activate",
                "minecraft:entity.wither.shoot",
                "minecraft:block.respawn_anchor.deplete",
                "minecraft:entity.wither.ambient",
                "minecraft:entity.ender_dragon.flap"
        };
        String[] primaryParticles = {
                "DRAGON_BREATH",
                "REVERSE_PORTAL",
                "SOUL_FIRE_FLAME",
                "END_ROD",
                "ELECTRIC_SPARK",
                "SCULK_SOUL",
                "CRIT",
                "SMOKE",
                "WITCH",
                "PORTAL"
        };
        String[] accentParticles = {
                "SMOKE",
                "DUST_CYAN",
                "DUST_RED",
                "DUST_PURPLE",
                "DUST_GREEN",
                "DUST_WHITE",
                "FLASH",
                "SOUL",
                "SCULK_CHARGE",
                "DUST_MAGENTA"
        };
        int soundIndex = spellIndex * CueStage.values().length + stageIndex;
        return new Cue(
                baseId,
                baseId + ":animation",
                sounds[soundIndex],
                primaryParticles[spellIndex % primaryParticles.length],
                accentParticles[(spellIndex + stageIndex) % accentParticles.length],
                24 + ((spellIndex * 7 + stageIndex * 5) % 37),
                12 + ((spellIndex + 1) * 3) + stageIndex * 4);
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
