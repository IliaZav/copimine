package me.copimine.endevent.domain;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Authoring for the visual signature of every End Rift spell.
 *
 * <p>SUPERSEDED (2026-11-03): the spell cue used to be drawn from two shared
 * particle arrays indexed by {@code spellIndex % length} and {@code
 * (spellIndex + stageIndex) % length}. That produced collisions — two
 * different spells could land on the same pair of particles — and told the
 * player nothing about what was coming: a void blast and an arena inferno
 * could look alike. The user's verdict on it was blunt and fair.</p>
 *
 * <p>This policy replaces the index arithmetic with one authored composition
 * per spell and per stage, so the cue is a readable signature:</p>
 * <ul>
 *   <li>every spell has its own colour and its own particle pair;</li>
 *   <li>the telegraph always converges inward (something is being pulled to
 *       the target), the impact always expands outward (it landed), so the
 *       direction of the motion alone reads as tell-versus-hit;</li>
 *   <li>the impact colour is hot and the telegraph colour is cold, so a glance
 *       is enough to tell "windup" from "now".</li>
 * </ul>
 *
 * <p>Pure: strings and numbers in, a composition out. No Bukkit types here so
 * the whole authoring table is testable.</p>
 */
public final class BossSpellVisualCompositionPolicy {

    /** Marker for "no authored composition" — the caller falls back to the cue table. */
    public static final Composition UNKNOWN = new Composition("", 0x9A6BFF, "END_ROD", "DUST_PURPLE",
            0.0F, "minecraft:entity.enderman.ambient", "");

    private static final Map<String, Composition> COMPOSITIONS = build();

    private BossSpellVisualCompositionPolicy() {
    }

    /**
     * One authored spell signature.
     *
     * @param spellId     lowercase spell id
     * @param telegraphHue cold colour used while the spell is winding up
     * @param primaryParticle  main particle of the composition
     * @param accentParticle   secondary particle that gives it depth
     * @param vortexTurns how many turns the converging telegraph makes
     *                    (0 = no vortex, the stage draws a flat sigil instead)
     * @param telegraphSound sound played on the first telegraph tick
     * @param impactSignature short human-readable description used by tests and
     *                        by the operator diagnostic
     */
    public record Composition(String spellId, int telegraphHue, String primaryParticle,
                              String accentParticle, float vortexTurns, String telegraphSound,
                              String impactSignature) {
        public Composition {
            spellId = spellId == null ? "" : spellId.trim().toLowerCase(Locale.ROOT);
            primaryParticle = primaryParticle == null ? "END_ROD" : primaryParticle.trim();
            accentParticle = accentParticle == null ? "END_ROD" : accentParticle.trim();
            telegraphSound = telegraphSound == null ? "" : telegraphSound.trim();
            impactSignature = impactSignature == null ? "" : impactSignature.trim();
            if (!Float.isFinite(vortexTurns) || vortexTurns < 0.0F) {
                throw new IllegalArgumentException("invalid vortex turns");
            }
        }

        /** The impact colour: the telegraph hue pushed towards white-hot red. */
        public int impactHue() {
            return impactColor(telegraphHue);
        }
    }

    /**
     * The hit colour for any telegraph hue.
     *
     * <p>Exposed so a cue that is not one of the authored spells (a phase
     * shift, the final strike, the defeat collapse) still reaches the same hot
     * end of the palette as the real impacts do, instead of a hard-coded hex
     * that happened to match nothing.</p>
     */
    public static int impactColor(int telegraphHue) {
        int r = Math.min(255, ((telegraphHue >> 16) & 0xFF) + 90);
        int g = Math.min(255, ((telegraphHue >> 8) & 0xFF) / 2);
        int b = Math.min(255, (telegraphHue & 0xFF) / 3);
        return (r << 16) | (g << 8) | b;
    }

    public static Composition composition(String spellId) {
        if (spellId == null) return UNKNOWN;
        Composition authored = COMPOSITIONS.get(normalize(spellId));
        return authored == null ? UNKNOWN : authored;
    }

    public static boolean isAuthored(String spellId) {
        return !composition(spellId).spellId().isEmpty();
    }

    public static Map<String, Composition> compositions() {
        return COMPOSITIONS;
    }

    /** Every authored spell id, in authoring order. */
    public static java.util.List<String> spellIds() {
        return java.util.List.copyOf(COMPOSITIONS.keySet());
    }

    private static Map<String, Composition> build() {
        LinkedHashMap<String, Composition> result = new LinkedHashMap<>();
        // A collapsing sphere: the blast is drawn inward hard, then released.
        put(result, "void_blast", 0x7A3BFF, "DRAGON_BREATH", "DUST_PURPLE", 1.5F,
                "minecraft:entity.warden.nearby_close", "collapsing sphere");
        // A spinning lance along the flight path.
        put(result, "rift_projectile", 0x2FD8FF, "REVERSE_PORTAL", "DUST_CYAN", 2.5F,
                "minecraft:block.respawn_anchor.charge", "spinning lance");
        // Three converging lanes that snap together at release.
        put(result, "rift_arrows", 0xFF4A5C, "CRIT", "DUST_RED", 0.0F,
                "minecraft:entity.trident.throw", "three closing lanes");
        // A rotating sigil on the ground: something is being marked, not shot.
        put(result, "void_mark", 0xBE4CFF, "ELECTRIC_SPARK", "DUST_PURPLE", 0.0F,
                "minecraft:block.sculk_shrieker.shriek", "rotating sigil");
        // A rising coil at the boss, converging on the cast point.
        put(result, "summon_servants", 0x9A6BFF, "WITCH", "SOUL", 2.0F,
                "minecraft:entity.evoker.prepare_attack", "rising coil");
        // A widening ring on the floor: the whole arena is being claimed.
        put(result, "arena_inferno", 0xFF7A18, "SOUL_FIRE_FLAME", "DUST_RED", 0.0F,
                "minecraft:entity.ghast.warn", "widening floor ring");
        // A hard vertical shaft over the target: the sky is the threat.
        put(result, "boss_fireball", 0xFF4A18, "FLAME", "REVERSE_PORTAL", 1.0F,
                "minecraft:entity.blaze.shoot", "vertical shaft");
        // The seal: slow, heavy, and unmistakable.
        put(result, "final_strike", 0xFF2A2A, "SCULK_CHARGE", "DUST_RED", 0.0F,
                "minecraft:entity.warden.sonic_boom", "sealed core");
        put(result, "rift_obelisks", 0x45DAFF, "REVERSE_PORTAL", "ELECTRIC_SPARK", 1.0F,
                "minecraft:block.amethyst_block.resonate", "obelisk constellation");
        put(result, "phase_shift", 0xC9A8FF, "PORTAL", "END_ROD", 3.0F,
                "minecraft:block.end_portal.spawn", "unfurling ring");
        return Map.copyOf(result);
    }

    private static void put(Map<String, Composition> target, String spellId, int hue,
                            String primary, String accent, float turns, String sound,
                            String signature) {
        target.put(spellId, new Composition(spellId, hue, primary, accent, turns, sound, signature));
    }

    private static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }
}
