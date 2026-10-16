package me.copimine.endevent.runtime;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.UUID;

/** Thin group coordination on the existing server tick; owns no tasks or entities. */
public final class WaveCombatCoordinator {
    private static final int MAX_SQUAD_COHORTS = 32;
    private static final double PLAYER_CLUSTER_RANGE_SQUARED = 24.0D * 24.0D;
    public static final int MAX_MELEE_ATTACKERS_PER_PLAYER = 2;
    public static final long MELEE_ATTACK_COOLDOWN_TICKS = 20L;

    private long generation = Long.MIN_VALUE;
    private long nonce;
    private Lease lease;
    private long meleeAttackNonce;
    private final Map<UUID, MeleeAttackLease> meleeAttackLeases = new HashMap<>();
    private long squadIdentityNonce;
    private final LinkedHashMap<CohortKey, CohortState> squadCohorts =
            new LinkedHashMap<>(16, 0.75f, true);

    public record Lease(long generation, long nonce, UUID owner, UUID target, long expiresTick) { }

    /** An admitted direct melee hit occupies one player's short attack window. */
    public record MeleeAttackLease(long generation, long nonce, UUID attacker,
                                   UUID target, long expiresTick) { }

    public record Squad(long identity, int index, List<UUID> fighters, UUID target) {
        public Squad { fighters = List.copyOf(fighters); }

        public Squad(int index, List<UUID> fighters, UUID target) {
            this(-1L, index, fighters, target);
        }
    }

    public record SquadPlan(List<Squad> squads, Map<UUID, UUID> targets) {
        public SquadPlan {
            squads = List.copyOf(squads);
            targets = Map.copyOf(targets);
        }
    }

    /** Position snapshot scoped to one live world/chamber cohort. */
    public record Position(double x, double y, double z) {
        public Position {
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z))
                throw new IllegalArgumentException("squad positions must be finite");
        }
    }

    private record PlayerCluster(UUID identity, List<UUID> players, Position center) {
        private PlayerCluster {
            players = List.copyOf(players);
        }
    }

    public synchronized void begin(long generation) {
        if (generation <= 0) throw new IllegalArgumentException("positive generation required");
        if (this.generation != generation) squadCohorts.clear();
        this.generation = generation;
        lease = null;
        meleeAttackLeases.clear();
    }
    public boolean owns(long expected) { return expected > 0 && expected == generation; }

    public Lease reserve(long expected, UUID owner, UUID target, long now, long expires) {
        expire(now);
        if (expected != generation || owner == null || target == null || now < 0
                || expires <= now || expires - now > 160 || lease != null) return null;
        lease = new Lease(expected, ++nonce, owner, target, expires);
        return lease;
    }

    public boolean valid(Lease token, long expected, long now) {
        expire(now);
        return token != null && token == lease && expected == generation
                && token.generation() == expected;
    }

    public boolean busy(long expected, long now) {
        expire(now);
        return expected == generation && lease != null;
    }

    public boolean recovering(UUID owner, long expected, long now) {
        return busy(expected, now) && lease.owner().equals(owner);
    }

    public Lease currentLease(long expected, long now) {
        return busy(expected, now) ? lease : null;
    }

    /** The approved melee pressure is two live attackers per eligible player. */
    public static int maxMeleeAttackersPerPlayer(int livingPlayers) {
        return livingPlayers <= 0 ? 0 : MAX_MELEE_ATTACKERS_PER_PLAYER;
    }

    /**
     * Admit a direct hit independently from the fighter's persistent squad
     * target. Successful hits hold their slot for the native 20-tick attack
     * cycle; rejected Bukkit events release the token immediately.
     */
    public synchronized MeleeAttackLease reserveMeleeAttack(long expectedGeneration,
            UUID attacker, UUID target, int livingPlayers, long nowTick) {
        expireMeleeAttacks(nowTick);
        int maximumForTarget = maxMeleeAttackersPerPlayer(livingPlayers);
        if (expectedGeneration != generation || attacker == null || target == null
                || maximumForTarget == 0 || nowTick < 0L
                || nowTick > Long.MAX_VALUE - MELEE_ATTACK_COOLDOWN_TICKS
                || meleeAttackLeases.containsKey(attacker)) {
            return null;
        }
        long focusedCount = meleeAttackLeases.values().stream()
                .filter(active -> active.target().equals(target))
                .count();
        if (focusedCount >= maximumForTarget) return null;
        MeleeAttackLease admitted = new MeleeAttackLease(expectedGeneration,
                ++meleeAttackNonce, attacker, target,
                nowTick + MELEE_ATTACK_COOLDOWN_TICKS);
        meleeAttackLeases.put(attacker, admitted);
        return admitted;
    }

    /** A rejected or cancelled damage event relinquishes its admission. */
    public synchronized void finishMeleeAttack(MeleeAttackLease admitted, boolean accepted) {
        if (admitted != null && !accepted
                && meleeAttackLeases.get(admitted.attacker()) == admitted) {
            meleeAttackLeases.remove(admitted.attacker());
        }
    }

    private void expireMeleeAttacks(long nowTick) {
        if (nowTick < 0L) return;
        meleeAttackLeases.values().removeIf(admitted -> nowTick >= admitted.expiresTick());
    }

    public void release(Lease token) { if (token != null && token == lease) lease = null; }
    public synchronized void remove(UUID owner) {
        if (lease != null && lease.owner().equals(owner)) lease = null;
        if (owner != null) meleeAttackLeases.remove(owner);
    }
    public synchronized void cancelTarget(UUID target) {
        if (lease != null && lease.target().equals(target)) lease = null;
        if (target != null) meleeAttackLeases.values().removeIf(admitted -> admitted.target().equals(target));
    }
    public synchronized void clear() {
        generation = Long.MIN_VALUE;
        lease = null;
        meleeAttackLeases.clear();
        squadCohorts.clear();
    }

    /**
     * Drop state for rooms/cohorts that are no longer part of this generation.
     * Call once after the caller has collected the complete live cohort set.
     */
    public synchronized void retainCohorts(long expectedGeneration, Set<String> activeCohorts) {
        if (expectedGeneration != generation)
            throw new IllegalStateException("cohort reconciliation belongs to a stale generation");
        if (activeCohorts == null)
            throw new IllegalArgumentException("active squad cohorts are required");
        Set<String> retained = new HashSet<>();
        for (String cohort : activeCohorts) {
            if (cohort == null || cohort.isBlank())
                throw new IllegalArgumentException("nonblank active squad cohort required");
            retained.add(cohort);
        }
        squadCohorts.keySet().removeIf(key -> key.generation() == expectedGeneration
                && !retained.contains(key.cohort()));
    }

    private void expire(long now) { if (lease != null && now >= lease.expiresTick()) lease = null; }

    /** Only one third of hunters focus the mark. Other jobs pressure its allies. */
    public static UUID chooseTarget(List<UUID> eligible, UUID current, UUID marked,
                                    int wave, int slot, Map<UUID, Integer> loads) {
        if (eligible == null || eligible.isEmpty()) return null;
        Map<UUID, Integer> pressure = loads == null ? Map.of() : loads;
        boolean hunt = wave == 2 && marked != null && eligible.contains(marked);
        if (hunt && Math.floorMod(slot, 3) == 0) return marked;
        List<UUID> pool = hunt && eligible.size() > 1
                ? eligible.stream().filter(id -> !id.equals(marked)).toList() : eligible;
        int lowest = pool.stream().mapToInt(id -> pressure.getOrDefault(id, 0)).min().orElse(0);
        if (current != null && pool.contains(current)
                && pressure.getOrDefault(current, 0) <= lowest + 1) return current;
        List<UUID> leastBusy = pool.stream().filter(id -> pressure.getOrDefault(id, 0) == lowest).toList();
        return leastBusy.get(Math.floorMod(slot, leastBusy.size()));
    }

    /**
     * Assign fighters to deterministic 3-5 member squads, then give each squad
     * one player assignment. This is target and pathing ownership only; it
     * does not grant attack or ability-release permission. Major ability
     * wind-ups must obtain the coordinator's single lease. Existing assignments
     * survive while their target remains eligible and the pressure distribution
     * stays within one squad.
     */
    public static SquadPlan planSquads(List<UUID> fighterIds, List<UUID> eligiblePlayerIds,
                                       Map<UUID, UUID> currentTargets, int wave, UUID marked) {
        List<UUID> fighters = fighterIds == null ? List.of() : fighterIds.stream()
                .filter(id -> id != null).distinct().sorted().toList();
        List<UUID> players = eligiblePlayerIds == null ? List.of() : eligiblePlayerIds.stream()
                .filter(id -> id != null).distinct().sorted().toList();
        if (fighters.isEmpty() || players.isEmpty()) return new SquadPlan(List.of(), Map.of());

        int squadCount = Math.max(1, (fighters.size() + 4) / 5);
        int baseSize = fighters.size() / squadCount;
        int largerSquads = fighters.size() % squadCount;
        Map<UUID, Integer> playerLoads = new java.util.HashMap<>();
        Map<UUID, UUID> assignments = new java.util.LinkedHashMap<>();
        List<Squad> squads = new java.util.ArrayList<>(squadCount);
        int offset = 0;
        for (int index = 0; index < squadCount; index++) {
            int size = baseSize + (index < largerSquads ? 1 : 0);
            List<UUID> members = fighters.subList(offset, offset + size);
            offset += size;

            List<UUID> pool = rolePool(players, wave, marked, index);
            int lowest = pool.stream().mapToInt(id -> playerLoads.getOrDefault(id, 0))
                    .min().orElse(0);
            UUID prior = mostCommonEligibleTarget(members, currentTargets, pool);
            UUID selected = prior != null
                    && playerLoads.getOrDefault(prior, 0) <= lowest
                    ? prior : leastLoadedPlayer(pool, playerLoads, index);
            for (UUID fighter : members) assignments.put(fighter, selected);
            playerLoads.merge(selected, size, Integer::sum);
            squads.add(new Squad(index, members, selected));
        }
        return new SquadPlan(squads, assignments);
    }

    /**
     * Plan against persistent squad membership for one generation and stable
     * wave/room/eligibility cohort. Player roster changes update targets without
     * rebuilding the fighter groups.
     */
    public synchronized SquadPlan planSquads(long expectedGeneration, String cohort,
                                             List<UUID> fighterIds,
                                             List<UUID> eligiblePlayerIds,
                                             Map<UUID, UUID> currentTargets,
                                             int wave, UUID marked) {
        return planSquads(expectedGeneration, cohort, fighterIds, eligiblePlayerIds,
                currentTargets, wave, marked, Map.of(), Map.of());
    }

    public synchronized SquadPlan planSquads(long expectedGeneration, String cohort,
                                             List<UUID> fighterIds,
                                             List<UUID> eligiblePlayerIds,
                                             Map<UUID, UUID> currentTargets,
                                             int wave, UUID marked,
                                             Map<UUID, Position> fighterPositions,
                                             Map<UUID, Position> playerPositions) {
        if (expectedGeneration != generation)
            throw new IllegalStateException("squad plan belongs to a stale generation");
        if (cohort == null || cohort.isBlank())
            throw new IllegalArgumentException("nonblank squad cohort required");

        List<UUID> fighters = fighterIds == null ? List.of() : fighterIds.stream()
                .filter(id -> id != null).distinct().sorted().toList();
        List<UUID> players = eligiblePlayerIds == null ? List.of() : eligiblePlayerIds.stream()
                .filter(id -> id != null).distinct().sorted().toList();
        CohortKey key = new CohortKey(expectedGeneration, cohort);
        Map<UUID, Position> safeFighterPositions = fighterPositions == null
                ? Map.of() : fighterPositions;
        Map<UUID, Position> safePlayerPositions = playerPositions == null
                ? Map.of() : playerPositions;
        if (fighters.isEmpty()) {
            squadCohorts.remove(key);
            return new SquadPlan(List.of(), Map.of());
        }

        CohortState state = squadCohorts.computeIfAbsent(key, ignored -> new CohortState());
        Set<UUID> live = new HashSet<>(fighters);
        for (SquadState squad : state.squads)
            squad.fighters.removeIf(fighter -> !live.contains(fighter));
        state.squads.removeIf(squad -> squad.fighters.isEmpty());

        Set<UUID> assignedFighters = new HashSet<>();
        for (SquadState squad : state.squads) assignedFighters.addAll(squad.fighters);
        List<UUID> newcomers = fighters.stream()
                .filter(fighter -> !assignedFighters.contains(fighter)).toList();
        if (state.squads.isEmpty()) {
            addNewSquads(state, fighters, safeFighterPositions);
        } else {
            for (UUID newcomer : newcomers) {
                SquadState smallestAvailable = state.squads.stream()
                        .filter(squad -> squad.fighters.size() < 5)
                        .min((left, right) -> {
                            int bySize = Integer.compare(left.fighters.size(), right.fighters.size());
                            return bySize != 0 ? bySize : Integer.compare(left.roleOrdinal, right.roleOrdinal);
                        }).orElse(null);
                if (smallestAvailable == null) break;
                smallestAvailable.fighters.add(newcomer);
            }
            Set<UUID> retained = new HashSet<>();
            for (SquadState squad : state.squads) retained.addAll(squad.fighters);
            List<UUID> unassigned = fighters.stream().filter(fighter -> !retained.contains(fighter)).toList();
            if (!unassigned.isEmpty()) addNewSquads(state, unassigned, safeFighterPositions);
        }

        rebalanceUndersizedSquads(state);
        state.squads.sort((left, right) -> Integer.compare(left.roleOrdinal, right.roleOrdinal));
        if (players.isEmpty()) {
            for (SquadState squad : state.squads) squad.target = null;
            trimSquadCohorts();
            return new SquadPlan(state.squads.stream()
                    .map(squad -> new Squad(squad.identity, squad.roleOrdinal, squad.fighters, null)).toList(),
                    Map.of());
        }

        Map<UUID, Integer> playerLoads = new HashMap<>();
        Map<UUID, Integer> playerClusterLoads = new HashMap<>();
        Map<UUID, UUID> assignments = new LinkedHashMap<>();
        List<Squad> planned = new ArrayList<>(state.squads.size());
        Map<List<UUID>, List<PlayerCluster>> clusterCache = new HashMap<>();
        for (int squadIndex = 0; squadIndex < state.squads.size(); squadIndex++) {
            SquadState squad = state.squads.get(squadIndex);
            List<UUID> pool = rolePool(players, wave, marked, squadIndex);
            List<PlayerCluster> clusters = clusterCache.computeIfAbsent(pool,
                    ignored -> clusterPlayers(pool, safePlayerPositions));
            int lowest = pool.stream().mapToInt(player -> playerLoads.getOrDefault(player, 0))
                    .min().orElse(0);
            UUID observed = mostCommonEligibleTarget(squad.fighters, currentTargets, pool);
            UUID prior = squad.target != null && pool.contains(squad.target) ? squad.target : observed;
            UUID spatial = nearestBalancedPlayer(squad.fighters, pool, playerLoads,
                    playerClusterLoads, squad.roleOrdinal, safeFighterPositions,
                    safePlayerPositions, clusters);
            boolean pressureAllowsRetention = prior != null
                    && playerLoads.getOrDefault(prior, 0) - lowest <= squad.fighters.size();
            UUID selected = pressureAllowsRetention
                    && (spatial == null || spatial.equals(prior)
                    || targetCost(prior, squad.fighters, playerLoads,
                    safeFighterPositions, safePlayerPositions)
                    - targetCost(spatial, squad.fighters, playerLoads,
                    safeFighterPositions, safePlayerPositions) < 144.0D)
                    ? prior : spatial != null ? spatial
                    : leastLoadedPlayer(pool, playerLoads, squad.roleOrdinal);
            squad.target = selected;
            for (UUID fighter : squad.fighters) assignments.put(fighter, selected);
            playerLoads.merge(selected, squad.fighters.size(), Integer::sum);
            PlayerCluster selectedCluster = clusterForPlayer(clusters, selected);
            if (selectedCluster != null)
                playerClusterLoads.merge(selectedCluster.identity(), squad.fighters.size(), Integer::sum);
            planned.add(new Squad(squad.identity, squad.roleOrdinal, squad.fighters, selected));
        }
        trimSquadCohorts();
        return new SquadPlan(planned, assignments);
    }

    /**
     * Repair undersized packs after a roster change while keeping viable packs
     * intact. Prefer a merge into a squad with the same target; if the remaining
     * packs are full, move only the minimum members needed to restore the floor.
     */
    private static void rebalanceUndersizedSquads(CohortState state) {
        int liveFighters = state.squads.stream().mapToInt(squad -> squad.fighters.size()).sum();
        if (liveFighters < 3 || state.squads.size() < 2) return;

        while (true) {
            state.squads.sort((left, right) -> Integer.compare(left.roleOrdinal, right.roleOrdinal));
            SquadState undersized = state.squads.stream()
                    .filter(squad -> squad.fighters.size() < 3)
                    .min((left, right) -> {
                        int bySize = Integer.compare(left.fighters.size(), right.fighters.size());
                        return bySize != 0 ? bySize : Integer.compare(left.roleOrdinal, right.roleOrdinal);
                    }).orElse(null);
            if (undersized == null) return;

            int membersToMove = undersized.fighters.size();
            List<SquadState> recipients = state.squads.stream()
                    .filter(squad -> squad != undersized && squad.fighters.size() < 5)
                    .sorted((left, right) -> {
                        int byTarget = Boolean.compare(!java.util.Objects.equals(left.target, undersized.target),
                                !java.util.Objects.equals(right.target, undersized.target));
                        if (byTarget != 0) return byTarget;
                        int bySize = Integer.compare(left.fighters.size(), right.fighters.size());
                        return bySize != 0 ? bySize : Integer.compare(left.roleOrdinal, right.roleOrdinal);
                    }).toList();
            int totalCapacity = recipients.stream().mapToInt(squad -> 5 - squad.fighters.size()).sum();

            if (totalCapacity >= membersToMove) {
                for (SquadState recipient : recipients) {
                    int transferCount = Math.min(5 - recipient.fighters.size(),
                            undersized.fighters.size());
                    if (transferCount <= 0) continue;
                    recipient.fighters.addAll(new ArrayList<>(undersized.fighters.subList(0, transferCount)));
                    undersized.fighters.subList(0, transferCount).clear();
                    if (undersized.fighters.isEmpty()) break;
                }
                state.squads.remove(undersized);
                continue;
            }

            int deficit = 3 - undersized.fighters.size();
            List<SquadState> donors = state.squads.stream()
                    .filter(squad -> squad != undersized && squad.fighters.size() > 3)
                    .sorted((left, right) -> {
                        int byTarget = Boolean.compare(!java.util.Objects.equals(left.target, undersized.target),
                                !java.util.Objects.equals(right.target, undersized.target));
                        if (byTarget != 0) return byTarget;
                        int bySize = Integer.compare(right.fighters.size(), left.fighters.size());
                        return bySize != 0 ? bySize : Integer.compare(left.roleOrdinal, right.roleOrdinal);
                    }).toList();
            for (SquadState donor : donors) {
                while (deficit > 0 && donor.fighters.size() > 3) {
                    undersized.fighters.add(donor.fighters.remove(donor.fighters.size() - 1));
                    deficit--;
                }
                if (deficit == 0) break;
            }
            if (deficit > 0) return;
        }
    }

    private void addNewSquads(CohortState state, List<UUID> fighters,
                              Map<UUID, Position> fighterPositions) {
        int squadCount = Math.max(1, (fighters.size() + 4) / 5);
        int baseSize = fighters.size() / squadCount;
        int largerSquads = fighters.size() % squadCount;
        List<UUID> remaining = new ArrayList<>(fighters.stream().distinct().sorted().toList());
        Map<UUID, Position> stablePositions = fighterPositions == null
                || remaining.stream().anyMatch(fighter -> fighterPositions.get(fighter) == null)
                ? Map.of() : fighterPositions;
        for (int index = 0; index < squadCount; index++) {
            int size = baseSize + (index < largerSquads ? 1 : 0);
            List<UUID> members = takeSpatialSquad(remaining, size, stablePositions);
            state.squads.add(new SquadState(++squadIdentityNonce, state.nextRoleOrdinal++, members));
        }
    }

    /**
     * Start each new pack from a deterministic spatial seed and fill it with
     * its nearest remaining fighters. UUID ordering remains the fallback when
     * a complete position snapshot is unavailable.
     */
    private static List<UUID> takeSpatialSquad(List<UUID> remaining, int size,
                                                Map<UUID, Position> fighterPositions) {
        if (size <= 0 || remaining.isEmpty()) return List.of();
        if (fighterPositions == null
                || remaining.stream().anyMatch(fighter -> fighterPositions.get(fighter) == null)) {
            List<UUID> members = new ArrayList<>(remaining.subList(0, Math.min(size, remaining.size())));
            remaining.removeAll(members);
            return members;
        }

        UUID seed = remaining.stream().min((left, right) -> {
            Position a = fighterPositions.get(left);
            Position b = fighterPositions.get(right);
            int byX = Double.compare(a.x(), b.x());
            if (byX != 0) return byX;
            int byY = Double.compare(a.y(), b.y());
            if (byY != 0) return byY;
            int byZ = Double.compare(a.z(), b.z());
            return byZ != 0 ? byZ : left.compareTo(right);
        }).orElseThrow();
        List<UUID> members = new ArrayList<>(size);
        members.add(seed);
        remaining.remove(seed);
        while (members.size() < size && !remaining.isEmpty()) {
            Position center = centroid(members, fighterPositions);
            UUID nearest = remaining.stream().min((left, right) -> {
                int byDistance = Double.compare(
                        distanceSquared(center, fighterPositions.get(left)),
                        distanceSquared(center, fighterPositions.get(right)));
                return byDistance != 0 ? byDistance : left.compareTo(right);
            }).orElseThrow();
            members.add(nearest);
            remaining.remove(nearest);
        }
        return members;
    }

    private static List<UUID> rolePool(List<UUID> players, int wave, UUID marked, int roleOrdinal) {
        boolean hasMark = wave == 2 && marked != null && players.contains(marked);
        boolean focusMark = hasMark && Math.floorMod(roleOrdinal, 3) == 0;
        if (focusMark) return List.of(marked);
        if (hasMark && players.size() > 1)
            return players.stream().filter(player -> !player.equals(marked)).toList();
        return players;
    }

    private void trimSquadCohorts() {
        while (squadCohorts.size() > MAX_SQUAD_COHORTS) {
            CohortKey eldest = squadCohorts.keySet().iterator().next();
            squadCohorts.remove(eldest);
        }
    }

    private record CohortKey(long generation, String cohort) { }

    private static final class CohortState {
        private final List<SquadState> squads = new ArrayList<>();
        private int nextRoleOrdinal;
    }

    private static final class SquadState {
        private final long identity;
        private final int roleOrdinal;
        private final List<UUID> fighters;
        private UUID target;

        private SquadState(long identity, int roleOrdinal, List<UUID> fighters) {
            this.identity = identity;
            this.roleOrdinal = roleOrdinal;
            this.fighters = fighters;
        }
    }

    private static UUID mostCommonEligibleTarget(List<UUID> fighters,
                                                  Map<UUID, UUID> currentTargets,
                                                  List<UUID> eligible) {
        if (currentTargets == null || currentTargets.isEmpty()) return null;
        Map<UUID, Integer> votes = new java.util.HashMap<>();
        for (UUID fighter : fighters) {
            UUID current = currentTargets.get(fighter);
            if (current != null && eligible.contains(current)) votes.merge(current, 1, Integer::sum);
        }
        int highest = votes.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        if (highest == 0) return null;
        return eligible.stream().filter(id -> votes.getOrDefault(id, 0) == highest)
                .min(UUID::compareTo).orElse(null);
    }

    private static UUID leastLoadedPlayer(List<UUID> eligible, Map<UUID, Integer> loads,
                                          int squadIndex) {
        int lowest = eligible.stream().mapToInt(id -> loads.getOrDefault(id, 0)).min().orElse(0);
        List<UUID> leastBusy = eligible.stream()
                .filter(id -> loads.getOrDefault(id, 0) == lowest).toList();
        return leastBusy.get(Math.floorMod(squadIndex, leastBusy.size()));
    }

    private static UUID nearestBalancedPlayer(List<UUID> fighters, List<UUID> eligible,
                                              Map<UUID, Integer> loads,
                                              Map<UUID, Integer> clusterLoads, int squadIndex,
                                              Map<UUID, Position> fighterPositions,
                                              Map<UUID, Position> playerPositions,
                                              List<PlayerCluster> clusters) {
        Position center = centroid(fighters, fighterPositions);
        if (center == null || playerPositions.isEmpty()) return null;
        if (!clusters.isEmpty()) {
            PlayerCluster selectedCluster = clusters.stream().min((left, right) -> {
                double leftCost = clusterCost(left, center, clusterLoads);
                double rightCost = clusterCost(right, center, clusterLoads);
                int byCost = Double.compare(leftCost, rightCost);
                return byCost != 0 ? byCost : left.identity().compareTo(right.identity());
            }).orElse(null);
            if (selectedCluster != null) {
                return selectedCluster.players().stream().min((left, right) -> {
                    int byCost = Double.compare(
                            targetCost(left, fighters, loads, fighterPositions, playerPositions),
                            targetCost(right, fighters, loads, fighterPositions, playerPositions));
                    return byCost != 0 ? byCost : left.compareTo(right);
                }).orElse(null);
            }
        }
        return eligible.stream().min((left, right) -> {
            int byCost = Double.compare(targetCost(left, fighters, loads, fighterPositions, playerPositions),
                    targetCost(right, fighters, loads, fighterPositions, playerPositions));
            return byCost != 0 ? byCost : Integer.compare(
                    Math.floorMod(left.hashCode() + squadIndex, eligible.size()),
                    Math.floorMod(right.hashCode() + squadIndex, eligible.size()));
        }).orElse(null);
    }

    private static List<PlayerCluster> clusterPlayers(List<UUID> players,
                                                       Map<UUID, Position> positions) {
        if (players == null || players.size() < 2 || positions == null
                || players.stream().anyMatch(player -> positions.get(player) == null)) return List.of();
        int[] parents = new int[players.size()];
        for (int index = 0; index < parents.length; index++) parents[index] = index;
        for (int left = 0; left < players.size(); left++) {
            Position a = positions.get(players.get(left));
            for (int right = left + 1; right < players.size(); right++) {
                Position b = positions.get(players.get(right));
                if (distanceSquared(a, b) <= PLAYER_CLUSTER_RANGE_SQUARED)
                    union(parents, left, right);
            }
        }
        Map<Integer, List<UUID>> members = new LinkedHashMap<>();
        for (int index = 0; index < players.size(); index++)
            members.computeIfAbsent(find(parents, index), ignored -> new ArrayList<>())
                    .add(players.get(index));
        List<PlayerCluster> clusters = new ArrayList<>(members.size());
        for (List<UUID> group : members.values()) {
            double x = 0.0D, y = 0.0D, z = 0.0D;
            for (UUID player : group) {
                Position position = positions.get(player);
                x += position.x(); y += position.y(); z += position.z();
            }
            clusters.add(new PlayerCluster(group.get(0), group,
                    new Position(x / group.size(), y / group.size(), z / group.size())));
        }
        return List.copyOf(clusters);
    }

    private static PlayerCluster clusterForPlayer(List<PlayerCluster> clusters, UUID player) {
        if (player == null || clusters == null) return null;
        return clusters.stream().filter(cluster -> cluster.players().contains(player)).findFirst().orElse(null);
    }

    private static double clusterCost(PlayerCluster cluster, Position fighterCenter,
                                      Map<UUID, Integer> clusterLoads) {
        double distance = distanceSquared(fighterCenter, cluster.center());
        double normalizedLoad = clusterLoads.getOrDefault(cluster.identity(), 0)
                / (double) Math.max(1, cluster.players().size());
        return distance + normalizedLoad * 36.0D;
    }

    private static double distanceSquared(Position left, Position right) {
        return square(left.x() - right.x()) + square(left.y() - right.y())
                + square(left.z() - right.z());
    }

    private static int find(int[] parents, int value) {
        if (parents[value] != value) parents[value] = find(parents, parents[value]);
        return parents[value];
    }

    private static void union(int[] parents, int left, int right) {
        int a = find(parents, left), b = find(parents, right);
        if (a != b) parents[Math.max(a, b)] = Math.min(a, b);
    }

    private static Position centroid(List<UUID> fighters, Map<UUID, Position> positions) {
        if (positions == null || positions.isEmpty()) return null;
        double x = 0.0D;
        double y = 0.0D;
        double z = 0.0D;
        int count = 0;
        for (UUID fighter : fighters) {
            Position position = positions.get(fighter);
            if (position == null) continue;
            x += position.x();
            y += position.y();
            z += position.z();
            count++;
        }
        return count == 0 ? null : new Position(x / count, y / count, z / count);
    }

    private static double targetCost(UUID target, List<UUID> fighters, Map<UUID, Integer> loads,
                                     Map<UUID, Position> fighterPositions,
                                     Map<UUID, Position> playerPositions) {
        Position center = centroid(fighters, fighterPositions);
        Position player = playerPositions.get(target);
        double distance = center == null || player == null ? 0.0D
                : square(center.x() - player.x()) + square(center.y() - player.y())
                + square(center.z() - player.z());
        return distance + loads.getOrDefault(target, 0) * 36.0D;
    }

    private static double square(double value) { return value * value; }

    public static boolean refreshPath(long now, long next, double destinationDeltaSquared,
                                       boolean intentionChanged, boolean pathActive) {
        if (!Double.isFinite(destinationDeltaSquared) || destinationDeltaSquared < 0) return false;
        return intentionChanged || destinationDeltaSquared >= 6.25D
                || now >= next && !pathActive;
    }
}
