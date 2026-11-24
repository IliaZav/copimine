package me.copimine.endevent.domain;

import java.util.List;

/**
 * N23-P1-07: one authored spell-presentation cast, as data.
 *
 * <p>V6 asks for "a signed small cast-start/stage/cancel packet carrying
 * epoch/generation, spell ID, origin/rotation or locked lane array, seed, tick
 * timing, stage, style and VFX LOD", so that one client-local instance can own
 * mesh and sprite evolution while Paper keeps every real combat decision. This
 * type is that packet's authoritative shape. It carries only cosmetics: it can
 * never open End access, mutate a world block or claim a hit.</p>
 *
 * <p>The compact constructor clamps every cosmetic field into its wire range and
 * refuses only structurally impossible input (a null phase or a blank spell id),
 * so a malformed producer is corrected rather than allowed to overflow the
 * encoder. Signing is the existing bridge's job; this type is the body.</p>
 */
public record SpellCastPacket(
        Phase phase,
        int generation,
        String spellId,
        long seed,
        int startTick,
        int durationTicks,
        int stage,
        int style,
        int vfxLod,
        float x,
        float y,
        float z,
        float yaw,
        List<Lane> lanes) {

    /** The three life-cycle stages the server may announce. */
    public enum Phase {
        /** First announcement: frozen origin, rotation, seed and locked lanes. */
        START,
        /** A later, strictly cosmetic stage of the same cast. */
        STAGE,
        /** The cast ended early; the client must stop and clean up. */
        CANCEL
    }

    /**
     * One frozen damage lane, as the client draws it.
     *
     * <p>Origin and unit direction in world space, plus the real radius and
     * length so the drawn silhouette can match the server's capsule. The client
     * interpolates only; it never re-derives the lane.</p>
     */
    public record Lane(float x, float y, float z,
                       float dx, float dy, float dz,
                       float radius, float length) {
    }

    public SpellCastPacket {
        if (phase == null) {
            throw new IllegalArgumentException("a cast packet needs a phase");
        }
        if (spellId == null || spellId.isBlank()) {
            throw new IllegalArgumentException("a cast packet needs a spell id");
        }
        spellId = spellId.trim();
        durationTicks = Math.max(0, Math.min(600, durationTicks));
        stage = Math.max(0, Math.min(255, stage));
        style = Math.max(0, Math.min(15, style));
        vfxLod = Math.max(0, Math.min(2, vfxLod));
        lanes = lanes == null ? List.of() : List.copyOf(lanes);
    }
}
