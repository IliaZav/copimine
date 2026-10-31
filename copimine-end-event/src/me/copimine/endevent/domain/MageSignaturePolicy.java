package me.copimine.endevent.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * Wave 6 mage identities and the signature each one owns.
 *
 * <p>The user requirement is that the sphere's spell pool follows <em>which</em>
 * mage died, not the ordinal of its death. Death order therefore never selects a
 * signature: it only advances the stage pointer and opens the next prisoner
 * ability slot. The two systems are deliberately independent.</p>
 *
 * <p>This class is pure decision state. It owns no entity, packet or task, so a
 * stage can be validated and replayed without a live server.</p>
 */
public final class MageSignaturePolicy {
    /** The encounter always has exactly five distinct mage identities. */
    public static final int MAGE_COUNT = 5;

    /** Maximum length of a persisted identity token. */
    public static final int MAX_ID_LENGTH = 48;

    private MageSignaturePolicy() {
    }

    /**
     * The five mage identities in their canonical, declaration order.
     *
     * <p>Declaration order is a stable serialization contract, not a difficulty
     * ramp. The run order is a separate persisted shuffle.</p>
     */
    public enum MageId {
        VESPERAL,
        ORRINTH,
        MALZURETH,
        KAILEN,
        NOCTURN
    }

    /**
     * Signatures owned by a mage and later cast by the sphere.
     *
     * <p>{@link #CONVERGENCE} is the existing Barrage-charges-Anchor combination
     * promoted to a signature of its own, as the specification directs. It stays
     * independently usable even when neither standalone parent has been
     * acquired.</p>
     */
    public enum Signature {
        RIFT_BARRAGE,
        GRAVITY_ANCHOR,
        SOUL_BRAND,
        HEAVENLY_JUDGMENT,
        CONVERGENCE
    }

    /**
     * The binding table. This mapping is a user-visible contract and must be
     * stable across restarts, so it is a constant rather than a shuffle.
     */
    public static Signature signatureFor(MageId mage) {
        if (mage == null) {
            throw new IllegalArgumentException("mage identity is required");
        }
        return switch (mage) {
            case VESPERAL -> Signature.RIFT_BARRAGE;
            case ORRINTH -> Signature.GRAVITY_ANCHOR;
            case MALZURETH -> Signature.SOUL_BRAND;
            case KAILEN -> Signature.HEAVENLY_JUDGMENT;
            case NOCTURN -> Signature.CONVERGENCE;
        };
    }

    /** The combat role that backs each identity's elite caster body. */
    public static RitualCasterTacticsPolicy.Role roleFor(MageId mage) {
        if (mage == null) {
            throw new IllegalArgumentException("mage identity is required");
        }
        return switch (mage) {
            case VESPERAL -> RitualCasterTacticsPolicy.Role.RIFT_BARRAGE_CASTER;
            case ORRINTH -> RitualCasterTacticsPolicy.Role.GRAVITY_WELL_CASTER;
            case MALZURETH -> RitualCasterTacticsPolicy.Role.SOUL_BRAND_CASTER;
            case KAILEN -> RitualCasterTacticsPolicy.Role.RIFT_CHAINS_CASTER;
            case NOCTURN -> RitualCasterTacticsPolicy.Role.FINAL_SEAL;
        };
    }

    /**
     * Select and persist one randomized order of all five identities.
     *
     * <p>A Fisher-Yates shuffle over the declaration order. The caller persists
     * the result, so a restart replays the same order instead of rerolling.</p>
     */
    public static List<MageId> selectOrder(RandomGenerator random) {
        if (random == null) {
            throw new IllegalArgumentException("random source is required");
        }
        List<MageId> order = new ArrayList<>(List.of(MageId.values()));
        for (int i = order.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            MageId swap = order.get(i);
            order.set(i, order.get(j));
            order.set(j, swap);
        }
        return List.copyOf(order);
    }

    /** Identity that must be defeated at this zero-based stage index. */
    public static MageId mageAt(List<MageId> order, int stageIndex) {
        requireValidOrder(order);
        if (stageIndex < 0 || stageIndex >= MAGE_COUNT) {
            throw new IllegalArgumentException("stage index outside 0-4: " + stageIndex);
        }
        return order.get(stageIndex);
    }

    /**
     * Reject a persisted order that is malformed.
     *
     * <p>A duplicate or missing identity would let a stage repeat and hand the
     * sphere the wrong signature, so it is a hard failure rather than a repair.</p>
     */
    public static void requireValidOrder(List<MageId> order) {
        if (order == null || order.size() != MAGE_COUNT) {
            throw new IllegalArgumentException("mage order must contain exactly five identities");
        }
        boolean[] seen = new boolean[MAGE_COUNT];
        for (MageId mage : order) {
            if (mage == null) {
                throw new IllegalArgumentException("mage order must not contain a null identity");
            }
            int ordinal = mage.ordinal();
            if (seen[ordinal]) {
                throw new IllegalArgumentException("duplicate mage identity in order: " + mage);
            }
            seen[ordinal] = true;
        }
    }

    /**
     * Bidirectional {@code mageId -> signature} binding for persisted state.
     *
     * <p>Stored as the identity's own name rather than its ordinal, so inserting
     * a new identity later cannot silently re-point an existing saved run.</p>
     */
    public static String encodeBinding(MageId mage) {
        return mage.name() + "=" + signatureFor(mage).name();
    }

    public static MageId parseMage(String token) {
        if (token == null || token.isBlank() || token.length() > MAX_ID_LENGTH) {
            throw new IllegalArgumentException("mage identity token is missing or oversized");
        }
        try {
            return MageId.valueOf(token);
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("unknown mage identity: " + token, invalid);
        }
    }

    public static Signature parseSignature(String token) {
        if (token == null || token.isBlank() || token.length() > MAX_ID_LENGTH) {
            throw new IllegalArgumentException("signature token is missing or oversized");
        }
        try {
            return Signature.valueOf(token);
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("unknown signature: " + token, invalid);
        }
    }

    /**
     * Signatures the sphere currently owns, in the caller-supplied run order.
     *
     * <p>Accepts the full run order or the already-defeated prefix of it, so the
     * sphere can be queried mid-encounter. Members are always real identities.</p>
     */
    public static List<Signature> ownedSignatures(List<MageId> mages) {
        if (mages == null) {
            throw new IllegalArgumentException("mage identity list is required");
        }
        List<Signature> owned = new ArrayList<>(MAGE_COUNT);
        for (MageId mage : mages) {
            if (mage == null) {
                throw new IllegalArgumentException("mage identity list must not contain null");
            }
            Signature signature = signatureFor(mage);
            if (!owned.contains(signature)) {
                owned.add(signature);
            }
        }
        return List.copyOf(owned);
    }

    /**
     * The next not-yet-defeated identity, or {@code null} when the run is done.
     *
     * <p>Defeat is recorded by identity, so a duplicated or stale death callback
     * for an already-defeated mage cannot advance the encounter.</p>
     */
    public static MageId nextUndefeated(List<MageId> order, List<MageId> defeated) {
        requireValidOrder(order);
        if (defeated == null) {
            throw new IllegalArgumentException("defeated identity list is required");
        }
        for (MageId mage : order) {
            if (!defeated.contains(mage)) {
                return mage;
            }
        }
        return null;
    }

    /**
     * Record one legitimate identity death.
     *
     * <p>Returns the new defeated list, or {@code null} when the death is a
     * duplicate and therefore carries no progression receipt.</p>
     */
    public static List<MageId> recordDefeat(List<MageId> order, List<MageId> defeated, MageId mage) {
        requireValidOrder(order);
        if (defeated == null || mage == null) {
            throw new IllegalArgumentException("defeated list and mage identity are required");
        }
        if (!order.contains(mage) || defeated.contains(mage)) {
            return null;
        }
        List<MageId> next = new ArrayList<>(defeated);
        next.add(mage);
        return List.copyOf(next);
    }

    /** True only once every identity has a committed defeat receipt. */
    public static boolean allDefeated(List<MageId> order, List<MageId> defeated) {
        requireValidOrder(order);
        return defeated != null && defeated.containsAll(order);
    }
}
