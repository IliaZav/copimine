package me.copimine.endevent.domain;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Boss-phase summon admission (master contract 14.6).
 *
 * <p>The contract is categorical: "There must be <b>NO skeleton towers or
 * skeleton archer turrets in any boss phase</b>. The only approved
 * skeleton-on-pillar mechanic is in Wave 6."</p>
 *
 * <p>This gate exists because the failure mode is silent. A summon helper that
 * was written for a wave and then reused by the boss would put a skeleton back
 * on a pillar in a phase the player is already fighting, and nothing in the
 * build would complain. Every boss-phase summon therefore asks this class, and
 * ordinary pressure summons (spiders, endermen, decoys) stay legal so the gate
 * never becomes a blanket ban that gets worked around.</p>
 */
public final class BossSummonAdmissionPolicy {
    /** The only owner of an approved skeleton-on-pillar mechanic. */
    public static final int WAVE_SIX_PILLAR_MECHANIC_OWNER = 6;

    /** The archetypes this policy refuses inside any boss phase. */
    private static final Set<String> SKELETON_ARCHETYPES = Set.of(
            "SKELETON", "STRAY", "BOGGED", "WITHER_SKELETON", "SKELETON_HORSE");
    private static final List<String> FORBIDDEN = List.of(
            "SKELETON_TOWER", "SKELETON_ARCHER_TURRET", "PILLAR_SKELETON");

    private BossSummonAdmissionPolicy() {
    }

    /** No skeleton tower exists in a boss phase. */
    public static boolean allowsSkeletonTower(BossPhase phase) {
        return false;
    }

    /** No skeleton archer turret exists in a boss phase. */
    public static boolean allowsSkeletonArcherTurret(BossPhase phase) {
        return false;
    }

    /** No pillar-mounted skeleton exists in a boss phase. */
    public static boolean allowsPillarSkeleton(BossPhase phase) {
        return false;
    }

    /** A skeleton of any variant is refused as a boss-phase summon. */
    public static boolean allowsSkeletonSummon(BossPhase phase, String entityType) {
        if (phase == null || entityType == null || entityType.isBlank()) {
            return false;
        }
        return !isSkeletonLike(entityType);
    }

    /** The general admission rule used by the summon adapter. */
    public static boolean allowsSummon(BossPhase phase, String archetype) {
        if (phase == null || archetype == null || archetype.isBlank()) {
            return false;
        }
        String key = archetype.trim().toUpperCase(Locale.ROOT);
        if (FORBIDDEN.contains(key)) {
            return false;
        }
        return !isSkeletonLike(key);
    }

    private static boolean isSkeletonLike(String entityType) {
        String key = entityType.trim().toUpperCase(Locale.ROOT);
        if (SKELETON_ARCHETYPES.contains(key)) {
            return true;
        }
        // Any future variant whose name says "skeleton" is refused, so a new
        // entity type cannot slip in through an updated allowlist by omission.
        return key.contains("SKELETON");
    }

    /** Stable naming for diagnostics; logs must not invent another label. */
    public static List<String> forbiddenArchetypes() {
        return FORBIDDEN;
    }

    /** Reason string for a refused summon, so the log explains itself. */
    public static String refusalReason(BossPhase phase, String archetype) {
        return "BOSS_SUMMON_REFUSED phase=" + (phase == null ? "UNKNOWN" : phase.name())
                + " archetype=" + (archetype == null ? "UNKNOWN" : archetype.trim())
                + " reason=skeleton-archetype-forbidden"
                + " approved_pillar_owner=WAVE_" + WAVE_SIX_PILLAR_MECHANIC_OWNER;
    }
}