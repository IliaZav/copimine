package me.copimine.endevent.domain;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Reserves bounded physical projectile children for complete named casts.
 * A reservation charges every child before launch so a volley is either
 * admitted whole or rejected whole. A Dragon Breath cloud may replace the
 * fireball child that created it without adding a second physical slot.
 */
public final class WaveProjectileAdmissionController {
    private final Map<UUID, GroupLease> groups = new LinkedHashMap<>();
    private final Map<UUID, UUID> groupByProjectile = new LinkedHashMap<>();
    private int occupiedProjectileSlots;
    private int occupiedOrdinaryProjectileSlots;
    private UUID activeNamedArrowRainGroup;

    public boolean reserveGroup(UUID groupId, int livingPlayers, int projectileCount) {
        return reserveGroup(groupId, livingPlayers, projectileCount, false);
    }

    /** The only cast allowed to use the five-child floor for a two- or three-player party. */
    public boolean reserveNamedFiveArrowRain(UUID groupId, int livingPlayers) {
        return reserveGroup(groupId, livingPlayers,
                PressureBudgetController.MIN_NAMED_VOLLEY_PROJECTILE_COUNT, true);
    }

    private boolean reserveGroup(UUID groupId, int livingPlayers, int projectileCount,
                                 boolean namedFiveArrowRain) {
        if (groupId == null || projectileCount <= 0 || groups.containsKey(groupId)) {
            return false;
        }
        int ordinaryCap = PressureBudgetController.configuredProjectileCap(livingPlayers);
        boolean isNamedRain = namedFiveArrowRain
                && projectileCount == PressureBudgetController.MIN_NAMED_VOLLEY_PROJECTILE_COUNT;
        if (ordinaryCap <= 0) {
            return false;
        }
        if (isNamedRain) {
            if (activeNamedArrowRainGroup != null
                    || occupiedProjectileSlots > effectiveProjectileCap(livingPlayers) - projectileCount) {
                return false;
            }
            activeNamedArrowRainGroup = groupId;
        } else if (projectileCount > ordinaryCap
                || occupiedOrdinaryProjectileSlots > ordinaryCap - projectileCount) {
            return false;
        }
        groups.put(groupId, new GroupLease(projectileCount, isNamedRain));
        occupiedProjectileSlots += projectileCount;
        if (!isNamedRain) occupiedOrdinaryProjectileSlots += projectileCount;
        return true;
    }

    /** Attach one physical entity to a previously reserved child slot. */
    public boolean trackProjectile(UUID groupId, UUID projectileId) {
        if (groupId == null || projectileId == null || groupByProjectile.containsKey(projectileId)) {
            return false;
        }
        GroupLease group = groups.get(groupId);
        if (group == null || group.unmaterializedChildren <= 0) {
            return false;
        }
        group.unmaterializedChildren--;
        group.projectileIds.add(projectileId);
        groupByProjectile.put(projectileId, groupId);
        return true;
    }

    /** Keep the physical slot when a projectile becomes its owned cloud carrier. */
    public boolean transferProjectile(UUID projectileId, UUID replacementId) {
        if (projectileId == null || replacementId == null) {
            return false;
        }
        UUID groupId = groupByProjectile.get(projectileId);
        GroupLease group = groupId == null ? null : groups.get(groupId);
        if (group == null || groupByProjectile.containsKey(replacementId)) {
            return false;
        }
        if (projectileId.equals(replacementId)) {
            return true;
        }
        group.projectileIds.remove(projectileId);
        group.projectileIds.add(replacementId);
        groupByProjectile.remove(projectileId);
        groupByProjectile.put(replacementId, groupId);
        return true;
    }

    /** Release one tracked child after impact, expiry, or generation cleanup. */
    public boolean releaseProjectile(UUID projectileId) {
        if (projectileId == null) {
            return false;
        }
        UUID groupId = groupByProjectile.remove(projectileId);
        GroupLease group = groupId == null ? null : groups.get(groupId);
        if (group == null || !group.projectileIds.remove(projectileId)) {
            return false;
        }
        occupiedProjectileSlots = Math.max(0, occupiedProjectileSlots - 1);
        if (!group.namedArrowRain) {
            occupiedOrdinaryProjectileSlots = Math.max(0, occupiedOrdinaryProjectileSlots - 1);
        }
        discardEmptyGroup(groupId, group);
        return true;
    }

    /**
     * Release child slots reserved for a cast when launch is cancelled or
     * fewer children were created. Already tracked physical children stay live.
     */
    public int releaseUnspawnedReservations(UUID groupId) {
        GroupLease group = groupId == null ? null : groups.get(groupId);
        if (group == null || group.unmaterializedChildren <= 0) {
            return 0;
        }
        int released = group.unmaterializedChildren;
        group.unmaterializedChildren = 0;
        occupiedProjectileSlots = Math.max(0, occupiedProjectileSlots - released);
        if (!group.namedArrowRain) {
            occupiedOrdinaryProjectileSlots = Math.max(0, occupiedOrdinaryProjectileSlots - released);
        }
        discardEmptyGroup(groupId, group);
        return released;
    }

    /** Release all tracked and reserved children for a rejected or cancelled cast. */
    public Set<UUID> releaseGroup(UUID groupId) {
        GroupLease group = groupId == null ? null : groups.remove(groupId);
        if (group == null) {
            return Set.of();
        }
        occupiedProjectileSlots = Math.max(0, occupiedProjectileSlots
                - group.projectileIds.size() - group.unmaterializedChildren);
        if (!group.namedArrowRain) {
            occupiedOrdinaryProjectileSlots = Math.max(0, occupiedOrdinaryProjectileSlots
                    - group.projectileIds.size() - group.unmaterializedChildren);
        }
        if (group.namedArrowRain) activeNamedArrowRainGroup = null;
        Set<UUID> children = Set.copyOf(group.projectileIds);
        for (UUID projectileId : children) {
            groupByProjectile.remove(projectileId, groupId);
        }
        return children;
    }

    public UUID groupIdForProjectile(UUID projectileId) {
        return projectileId == null ? null : groupByProjectile.get(projectileId);
    }

    public boolean hasGroup(UUID groupId) {
        return groupId != null && groups.containsKey(groupId);
    }

    public Set<UUID> projectileIds(UUID groupId) {
        GroupLease group = groupId == null ? null : groups.get(groupId);
        return group == null ? Set.of() : Set.copyOf(group.projectileIds);
    }

    public int activeGroupCount() {
        return groups.size();
    }

    /** Includes both live children and reserved physical child slots. */
    public int activeProjectileCount() {
        return occupiedProjectileSlots;
    }

    public int effectiveProjectileCap(int livingPlayers) {
        return PressureBudgetController.effectiveProjectileCap(livingPlayers);
    }

    public void clear() {
        groups.clear();
        groupByProjectile.clear();
        occupiedProjectileSlots = 0;
        occupiedOrdinaryProjectileSlots = 0;
        activeNamedArrowRainGroup = null;
    }

    private void discardEmptyGroup(UUID groupId, GroupLease group) {
        if (group.projectileIds.isEmpty() && group.unmaterializedChildren == 0) {
            groups.remove(groupId, group);
            if (group.namedArrowRain) activeNamedArrowRainGroup = null;
        }
    }

    private static final class GroupLease {
        private final Set<UUID> projectileIds = new LinkedHashSet<>();
        private final boolean namedArrowRain;
        private int unmaterializedChildren;

        private GroupLease(int projectileCount, boolean namedArrowRain) {
            this.unmaterializedChildren = projectileCount;
            this.namedArrowRain = namedArrowRain;
        }
    }
}
