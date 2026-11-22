package me.copimine.endevent.domain;

import java.util.Locale;

/**
 * Resolves an authored composition particle id into something a client can
 * actually be told to draw.
 *
 * <p>The composition table authors a distinct primary/accent particle pair per
 * spell, but the runtime frame used to draw {@code DUST} for every point of
 * every spell and {@code END_ROD} on the bright nodes. The authored pair was
 * therefore metadata only: a bone volley and a void rupture still rendered as
 * the same grey dust. This policy is the missing bridge.</p>
 *
 * <p>Pure: strings and numbers in, a payload contract out. Bukkit is not
 * imported here on purpose, so the whole mapping is exercised by plain Java
 * tests. The renderer turns {@link Payload} into the concrete Bukkit payload.</p>
 */
public final class BossSpellParticlePalette {

    /**
     * The payload a Bukkit particle variant requires.
     *
     * <p>{@link #NONE} is a payload-free particle. {@link #FLOAT},
     * {@link #INTEGER}, {@link #DUST} and {@link #BLOCK} are the structured
     * carriers this renderer knows how to feed. A renderer that cannot supply
     * a payload must fall back to dust rather than spawn the particle bare,
     * because Paper throws "missing required data class" and that exception has
     * already aborted whole wave ticks once.</p>
     */
    public enum Payload {
        NONE, FLOAT, INTEGER, DUST, BLOCK
    }

    /**
     * How many points of a silhouette may carry this particle.
     *
     * <p>{@link #DENSE} particles are small and can draw the body of the
     * shape. {@link #SPARSE} particles are large, bright or full-screen reads
     * (a sweep arc, a sonic boom, a flash). Drawing those on every sampled
     * point would both hide the telegraph and multiply the visual budget, so
     * they are emitted only on the sparse bright nodes.</p>
     */
    public enum Weight {
        DENSE, SPARSE
    }

    /**
     * One renderable particle.
     *
     * @param particle canonical Bukkit enum name
     * @param payload  the payload the enum requires
     * @param rgb      dust colour, or the fallback tint for a structured
     *                 particle that has no colour of its own
     * @param scale    dust size; ignored by every non-dust particle
     */
    public record Signature(String particle, Payload payload, int rgb, float scale, Weight weight) {
        public Signature {
            particle = particle == null || particle.isBlank() ? "DUST" : particle.trim();
            payload = payload == null ? Payload.DUST : payload;
            weight = weight == null ? Weight.DENSE : weight;
            rgb &= 0xFFFFFF;
            if (!Float.isFinite(scale) || scale <= 0.0F) scale = 1.0F;
        }

        /** A signature that must be spawned with a dust payload. */
        public boolean dust() {
            return payload == Payload.DUST;
        }
    }

    private BossSpellParticlePalette() {
    }

    /**
     * Resolve one authored id.
     *
     * @param authoredId the id from a composition; may be null or unknown
     * @param fallbackRgb the spell's own hue, used by {@code DUST} and by any
     *                    structured particle that has no colour of its own
     * @return a renderable signature; never null
     */
    public static Signature resolve(String authoredId, int fallbackRgb) {
        String id = normalize(authoredId);
        int rgb = fallbackRgb & 0xFFFFFF;
        return switch (id) {
            // Payload-free particles: the authored signature is drawn as-is.
            case "END_ROD" -> plain("END_ROD", rgb, 1.0F);
            case "DRAGON_BREATH" -> typed("DRAGON_BREATH", Payload.FLOAT, rgb, 1.15F);
            case "REVERSE_PORTAL" -> plain("REVERSE_PORTAL", rgb, 1.0F);
            case "PORTAL" -> plain("PORTAL", rgb, 1.0F);
            case "ELECTRIC_SPARK" -> plain("ELECTRIC_SPARK", rgb, 1.0F);
            case "SCULK_SOUL" -> plain("SCULK_SOUL", rgb, 1.0F);
            case "CRIT" -> plain("CRIT", rgb, 1.0F);
            case "ENCHANTED_HIT" -> sparse("ENCHANTED_HIT", rgb, 1.0F);
            case "DAMAGE_INDICATOR" -> sparse("DAMAGE_INDICATOR", rgb, 1.0F);
            case "SWEEP_ATTACK" -> sparse("SWEEP_ATTACK", rgb, 1.0F);
            case "SONIC_BOOM" -> sparse("SONIC_BOOM", rgb, 1.0F);
            case "FLASH" -> sparse("FLASH", rgb, 1.0F);
            case "SMOKE" -> plain("SMOKE", rgb, 1.0F);
            case "WITCH" -> plain("WITCH", rgb, 1.0F);
            case "SOUL" -> plain("SOUL", rgb, 1.0F);
            case "SOUL_FIRE_FLAME" -> plain("SOUL_FIRE_FLAME", rgb, 1.0F);
            case "FLAME" -> plain("FLAME", rgb, 1.0F);
            // Structured carriers we can feed a payload for.
            case "SCULK_CHARGE" -> typed("SCULK_CHARGE", Payload.FLOAT, rgb, 1.0F);
            case "BLOCK" -> typed("BLOCK_CRUMBLE", Payload.BLOCK, rgb, 1.0F);
            case "SLIME" -> sparse("ITEM_SLIME", rgb, 1.0F);
            // Authored dust accents carry their own colour, so two spells that
            // share a hue cannot silently share an accent.
            case "DUST" -> typed("DUST", Payload.DUST, rgb, 1.0F);
            case "DUST_CYAN" -> typed("DUST", Payload.DUST, 0x45DAFF, 1.15F);
            case "DUST_RED" -> typed("DUST", Payload.DUST, 0xFF4848, 1.15F);
            case "DUST_PURPLE" -> typed("DUST", Payload.DUST, 0xBE4CFF, 1.15F);
            case "DUST_GREEN" -> typed("DUST", Payload.DUST, 0x7AFF48, 1.10F);
            case "DUST_WHITE" -> typed("DUST", Payload.DUST, 0xFFFFFF, 1.00F);
            case "DUST_MAGENTA" -> typed("DUST", Payload.DUST, 0xF43CFF, 1.10F);
            // An unauthored or future id still has to be visible: the spell's
            // own hue is always better than an invisible particle or a throw.
            default -> typed("DUST", Payload.DUST, rgb, 1.0F);
        };
    }

    /**
     * True when the authored id resolves to its own authored particle rather
     * than the generic dust fallback.
     */
    public static boolean isDistinct(String authoredId) {
        Signature signature = resolve(authoredId, 0xFFFFFF);
        String id = normalize(authoredId);
        if (id.isEmpty()) return false;
        // A dust accent is authored, but it is still a dust point. Only a
        // real particle family counts as a distinct, spell-identifying one.
        return !signature.dust();
    }

    /**
     * Rank a resolved signature for the sparse bright nodes of a silhouette.
     *
     * <p>The nodes are where a spell shows its authored particle family. A
     * DENSE particle (dust, a soul wisp, a flame) can carry a node. A SPARSE
     * particle is a one-shot read and is allowed only when nothing denser is
     * authored. A one-shot that would blot the shape or flash the screen
     * ({@code FLASH}, {@code SONIC_BOOM}, {@code SWEEP_ATTACK}) is never
     * stamped on a node at all; those belong to the impact beat.</p>
     *
     * @return 2 for a dense node particle, 1 for a sparse one, 0 for dust,
     *         -1 for a particle that must not be drawn per node
     */
    public static int nodeRank(Signature signature) {
        if (signature == null) return 0;
        if (signature.dust()) return 0;
        if (ONE_SHOT_NODE_FORBIDDEN.contains(signature.particle())) return -1;
        return signature.weight() == Weight.DENSE ? 2 : 1;
    }

    private static final java.util.Set<String> ONE_SHOT_NODE_FORBIDDEN =
            java.util.Set.of("FLASH", "SONIC_BOOM", "SWEEP_ATTACK");

    private static Signature plain(String particle, int rgb, float scale) {
        return new Signature(particle, Payload.NONE, rgb, scale, Weight.DENSE);
    }

    private static Signature sparse(String particle, int rgb, float scale) {
        return new Signature(particle, Payload.NONE, rgb, scale, Weight.SPARSE);
    }

    private static Signature typed(String particle, Payload payload, int rgb, float scale) {
        return new Signature(particle, payload, rgb, scale, Weight.DENSE);
    }

    private static String normalize(String value) {
        if (value == null) return "";
        return value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }
}
