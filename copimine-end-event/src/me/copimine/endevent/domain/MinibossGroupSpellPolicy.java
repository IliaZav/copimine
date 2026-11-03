package me.copimine.endevent.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * How a Wave 7 miniboss spell picks its victims.
 *
 * <p>The trial actors were single-target: every swing, shot and cast had exactly
 * one victim, chosen by {@link Wave7TrialTargetPolicy}. A party of five therefore
 * fought three opponents that could only ever threaten one person at a time, and
 * the rest of the group was free to stand in the open and free-hit. The request
 * was explicit: the minibosses must work the GROUP.</p>
 *
 * <p>Spells are the right carrier, because they already have an area. This policy
 * decides which members of the party that area should land on:</p>
 * <ol>
 *   <li><b>Hit as many as the spell allows.</b> A spell with room for four never
 *       targets one.</li>
 *   <li><b>Prefer the pile.</b> Victims are taken nearest-first, so a clustered
 *       party eats the whole spell while a spread party only loses the players
 *       actually inside the area.</li>
 *   <li><b>Never the same faces twice.</b> Players the caster already caught in
 *       its previous spell are deferred while there is anyone else in range, so
 *       a healer is not removed from the fight three spells in a row.</li>
 *   <li><b>Respect the cap.</b> Damage per victim is divided by how many victims
 *       there are, so a group spell is a group threat, not a group wipe.</li>
 * </ol>
 *
 * <p>Pure: no Bukkit types, no clock, no randomness.</p>
 */
public final class MinibossGroupSpellPolicy {
    /**
     * How close to the impact point a player must be to count as the direct
     * victim rather than splash.
     *
     * <p>They are standing where the spell landed, so the tolerance is small:
     * one block covers the gap between a player's feet and a projectile's
     * detonation without swallowing a neighbour.</p>
     */
    public static final double DIRECT_VICTIM_TOLERANCE_BLOCKS = 1.0D;

    private MinibossGroupSpellPolicy() {
    }

    /**
     * One player the spell may land on.
     *
     * @param id              stable identity
     * @param dx              horizontal offset from the caster, blocks (east +)
     * @param dz              horizontal offset from the caster, blocks (south +)
     * @param inLineOfSight   the caster can actually see them
     * @param lastHitSpell    casts ago this caster last caught them, or a large
     *                        number when it never has
     */
    public record Candidate(UUID id, double dx, double dz, boolean inLineOfSight,
                            int lastHitSpell) {
        public Candidate {
            if (id == null) {
                throw new IllegalArgumentException("candidate id is required");
            }
            if (!Double.isFinite(dx) || !Double.isFinite(dz)) {
                throw new IllegalArgumentException("offsets must be finite");
            }
        }

        /** Squared horizontal distance to the caster. */
        public double distanceSquared() {
            return dx * dx + dz * dz;
        }

        /** Horizontal distance from a point offset {@code (ox, oz)} from the caster. */
        public double distanceSquaredFrom(double ox, double oz) {
            double ex = dx - ox;
            double ez = dz - oz;
            return ex * ex + ez * ez;
        }
    }

    /** A spell's authored reach and capacity. */
    public record Spell(double rangeBlocks, int maxTargets, double baseDamage,
                        double splashRadiusBlocks) {
        public Spell {
            if (!Double.isFinite(rangeBlocks) || rangeBlocks <= 0.0D) {
                throw new IllegalArgumentException("range must be positive");
            }
            if (maxTargets < 1) {
                throw new IllegalArgumentException("a spell must reach at least one target");
            }
            if (!Double.isFinite(baseDamage) || baseDamage < 0.0D) {
                throw new IllegalArgumentException("damage must be finite and non-negative");
            }
            if (!Double.isFinite(splashRadiusBlocks) || splashRadiusBlocks < 0.0D) {
                throw new IllegalArgumentException("splash radius must be finite");
            }
        }
    }

    /**
     * Choose this cast's victims.
     *
     * @param candidates every eligible party member
     * @param spell      the authored spell
     * @return victims, nearest first; empty when nobody is in range
     */
    public static List<UUID> chooseVictims(List<Candidate> candidates, Spell spell) {
        if (candidates == null || candidates.isEmpty() || spell == null) {
            return List.of();
        }
        double range = spell.rangeBlocks();
        double rangeSquared = range * range;
        List<Candidate> reachable = candidates.stream()
                .filter(candidate -> candidate != null && candidate.inLineOfSight())
                .filter(candidate -> candidate.distanceSquared() <= rangeSquared)
                // Freshest faces first, then nearest: the deferral in rule 3 has
                // to outrank distance, otherwise a stationary pile is hit forever.
                .sorted(Comparator
                        .comparingInt(Candidate::lastHitSpell)
                        .thenComparingDouble(Candidate::distanceSquared))
                .toList();
        if (reachable.isEmpty()) {
            return List.of();
        }
        int take = Math.min(spell.maxTargets(), reachable.size());
        List<UUID> victims = new ArrayList<>(take);
        for (Candidate candidate : reachable.subList(0, take)) {
            victims.add(candidate.id());
        }
        return List.copyOf(victims);
    }

    /**
     * Damage one victim takes from a group spell.
     *
     * <p>The authored base is what a single victim would eat. A group spell that
     * hit five for full damage would simply delete a party, so the authored
     * damage is divided by how many victims are caught -- with the square root
     * taken so that hitting more people still costs more total health than
     * hitting one.</p>
     */
    public static double damagePerVictim(Spell spell, int victimCount) {
        if (spell == null) {
            return 0.0D;
        }
        // A count below one is not "no damage": it means the caller has not yet
        // resolved the victim list, and silently returning zero there would turn
        // a whole group spell into a harmless light show.
        int victims = Math.max(1, victimCount);
        return spell.baseDamage() / Math.sqrt(victims);
    }

    /**
     * Victims of a splash spell around an impact point, ignoring line of sight.
     *
     * <p>Used by spells that detonate rather than aim: everything inside the
     * radius is caught, which is exactly what makes a pile of players the wrong
     * formation to stand in.</p>
     */
    public static List<UUID> splashVictims(List<Candidate> candidates, Spell spell,
                                           double impactDx, double impactDz) {
        return splashVictims(candidates, spell, impactDx, impactDz, 0.0D, 0.0D, true);
    }

    /**
     * Splash around an impact point, with the direct victim excluded.
     *
     * @param casterDx horizontal offset of the caster, so the direct victim can
     *                 be identified
     * @param casterDz ...
     * @param excludeDirectVictim drop the player standing exactly where the spell
     *                            landed; they already took the direct hit and
     *                            must not be charged twice
     */
    public static List<UUID> splashVictims(List<Candidate> candidates, Spell spell,
                                           double impactDx, double impactDz,
                                           double casterDx, double casterDz,
                                           boolean excludeDirectVictim) {
        if (candidates == null || candidates.isEmpty() || spell == null
                || !Double.isFinite(impactDx) || !Double.isFinite(impactDz)
                || !Double.isFinite(casterDx) || !Double.isFinite(casterDz)) {
            return List.of();
        }
        double radius = spell.splashRadiusBlocks();
        if (radius <= 0.0D) {
            return List.of();
        }
        double radiusSquared = radius * radius;
        return candidates.stream()
                .filter(candidate -> candidate != null)
                .filter(candidate -> candidate.distanceSquaredFrom(impactDx, impactDz)
                        <= radiusSquared)
                // The direct victim is not splash: charging them twice would make
                // a group spell secretly better against one target than many.
                .filter(candidate -> !excludeDirectVictim
                        || Math.hypot(candidate.dx() - casterDx, candidate.dz() - casterDz)
                        > DIRECT_VICTIM_TOLERANCE_BLOCKS)
                .sorted(Comparator.comparingInt(Candidate::lastHitSpell)
                        .thenComparingDouble(Candidate::distanceSquared))
                .map(Candidate::id)
                .limit(spell.maxTargets())
                .toList();
    }
}
