package me.copimine.endevent.domain;

import java.util.List;
import java.util.Locale;

/**
 * Particle signature for the elite spell projectiles.
 *
 * All three elite spells used to burst into the same vanilla
 * {@code DRAGON_BREATH} puff on impact and produced no trail in flight, so the
 * only thing distinguishing one cast from another was luck. This policy gives
 * each spell a layered signature of its own: a trail that follows the projectile
 * and an impact burst whose particle kinds, counts and colours match the spell.
 *
 * The policy is deliberately data only. It names vanilla particle kinds by their
 * Minecraft enum name so the pure policy carries no Bukkit dependency, and the
 * server resolves the enum when it spawns the effect. That keeps the contract
 * testable without a running server while still pinning the real behaviour.
 */
public final class EliteSpellVfxPolicy {

    /** One particle pass: the vanilla particle name plus its shaping settings. */
    public record Pass(String particle,
                       int count,
                       double spreadX,
                       double spreadY,
                       double spreadZ,
                       double extra,
                       float chance,
                       String dustHex) {
    }

    private EliteSpellVfxPolicy() {
    }

    /**
     * Trail emitted every tick while the spell is in flight.
     *
     * A trail is what makes the projectile read as energy rather than a thrown
     * prop, so every authored spell has one. The void burst trails sparsely
     * because it is a heavy orb; the bolt trails densely because it is fast.
     */
    public static List<Pass> flightTrail(String spell) {
        return switch (identity(spell)) {
            case "RIFT_BOLT" -> List.of(
                    new Pass("END_ROD", 2, 0.02D, 0.02D, 0.02D, 0.0D, 1.0F, null),
                    new Pass("DUSK", 1, 0.01D, 0.01D, 0.01D, 0.0D, 0.45F, null));
            case "VOID_BURST" -> List.of(
                    new Pass("SQUID_INK", 1, 0.04D, 0.04D, 0.04D, 0.0D, 0.5F, null),
                    new Pass("WITCH", 1, 0.03D, 0.03D, 0.03D, 0.0D, 0.35F, null));
            case "DRAGON_BREATH" -> List.of(
                    new Pass("FLAME", 2, 0.05D, 0.05D, 0.05D, 0.01D, 0.8F, null),
                    new Pass("LAVA", 1, 0.03D, 0.03D, 0.03D, 0.0D, 0.4F, null));
            default -> List.of();
        };
    }

    /**
     * Impact burst when the spell lands.
     *
     * This replaces the single shared {@code DRAGON_BREATH} puff. Each spell
     * bursts in its own materials: the bolt shatters into arcane sparks, the orb
     * implodes into darkness, and the breath ignites.
     */
    public static List<Pass> impactBurst(String spell) {
        return switch (identity(spell)) {
            case "RIFT_BOLT" -> List.of(
                    new Pass("END_ROD", 18, 0.28D, 0.28D, 0.28D, 0.04D, 1.0F, null),
                    new Pass("DUSK", 10, 0.35D, 0.35D, 0.35D, 0.02D, 1.0F, null),
                    new Pass("DUST", 16, 0.30D, 0.30D, 0.30D, 0.0D, 1.0F, "C87BFF"));
            case "VOID_BURST" -> List.of(
                    new Pass("SQUID_INK", 14, 0.45D, 0.45D, 0.45D, 0.06D, 1.0F, null),
                    new Pass("REVERSE_PORTAL", 12, 0.40D, 0.40D, 0.40D, 0.05D, 1.0F, null),
                    new Pass("DUST", 18, 0.35D, 0.35D, 0.35D, 0.0D, 1.0F, "E9D4FB"));
            case "DRAGON_BREATH" -> List.of(
                    new Pass("FLAME", 16, 0.32D, 0.20D, 0.32D, 0.03D, 1.0F, null),
                    new Pass("LAVA", 10, 0.30D, 0.12D, 0.30D, 0.0D, 1.0F, null),
                    new Pass("DUST", 14, 0.30D, 0.30D, 0.30D, 0.0D, 1.0F, "FFB45C"));
            default -> List.of();
        };
    }

    /** True when this spell has an authored signature rather than a generic puff. */
    public static boolean hasSignature(String spell) {
        return !impactBurst(spell).isEmpty();
    }

    private static String identity(String spell) {
        return spell == null ? "" : spell.trim().toUpperCase(Locale.ROOT);
    }
}
