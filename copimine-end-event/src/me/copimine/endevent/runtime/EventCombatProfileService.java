package me.copimine.endevent.runtime;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Plain server-thread observations; no world, player, skin or item objects. */
public final class EventCombatProfileService {
    private static final String PREFIX = "combat-profile.";
    private static final int MAX_PARTICIPANTS = 128;
    private static final long MAX_COUNTER = 1_000_000_000L;
    public enum Weapon { NONE, SWORD, AXE, OTHER_MELEE, BOW, CROSSBOW, OTHER_RANGED }
    public enum Stat {
        MOVEMENT_SAMPLES, SPRINT_SAMPLES, STRAFE_LEFT, STRAFE_RIGHT, SHIELD_SAMPLES,
        PURSUIT_SAMPLES, RETREAT_SAMPLES, DISTANCE_NEAR, DISTANCE_MID, DISTANCE_FAR,
        MELEE_HITS, RANGED_HITS, JUMP_CRITICAL_HITS, SWORD_HITS, AXE_HITS,
        BOW_HITS, CROSSBOW_HITS, ATTACK_INTERVAL_TICKS, ATTACK_INTERVAL_SAMPLES,
        SHORT_ATTACK_INTERVALS, ATTEMPTED_SWINGS, ITEM_SWITCHES, BOW_RELEASES,
        CROSSBOW_RELEASES, BOW_CHARGE_TICKS, HEAL_USES, HEAL_HEALTH_PERMILLE,
        CROSSBOW_LOADS, CROSSBOW_CHARGE_TICKS
    }
    public record Context(String eventId, long runtimeGeneration, UUID player, long tick,
                          int wave, boolean activeCombat, boolean diagnostic,
                          boolean forcedMotion, boolean frozenFog, boolean confined) { }
    public record Movement(double distance, boolean sprinting, double strafe,
                           boolean blocking, double approach, double healthRatio) { }
    public record Observation(long tick, int wave, String action, int value) { }
    public record Snapshot(Map<Stat, Long> stats, int observedWaves, boolean fromEventStart,
                           List<Observation> recent) {
        public Snapshot { stats = Map.copyOf(stats); recent = List.copyOf(recent); }
        public long count(Stat stat) { return stats.getOrDefault(stat, 0L); }
        public double confidence() {
            double evidence = Math.min(1D, (count(Stat.MOVEMENT_SAMPLES)
                    + 2D * (count(Stat.MELEE_HITS) + count(Stat.RANGED_HITS))) / 120D);
            return evidence * Integer.bitCount(observedWaves) / 6D * (fromEventStart ? 1D : .5D);
        }
        public double confidence(Stat feature) {
            double evidence = switch (feature) {
                case MELEE_HITS, RANGED_HITS, JUMP_CRITICAL_HITS, SWORD_HITS, AXE_HITS,
                        BOW_HITS, CROSSBOW_HITS -> (count(Stat.MELEE_HITS) + count(Stat.RANGED_HITS)) / 60D;
                case ATTACK_INTERVAL_TICKS, ATTACK_INTERVAL_SAMPLES, SHORT_ATTACK_INTERVALS -> count(Stat.ATTACK_INTERVAL_SAMPLES) / 30D;
                case BOW_RELEASES, BOW_CHARGE_TICKS -> count(Stat.BOW_RELEASES) / 12D;
                case CROSSBOW_RELEASES -> count(Stat.CROSSBOW_RELEASES) / 12D;
                case CROSSBOW_LOADS, CROSSBOW_CHARGE_TICKS -> count(Stat.CROSSBOW_LOADS) / 12D;
                case HEAL_USES, HEAL_HEALTH_PERMILLE -> count(Stat.HEAL_USES) / 8D;
                case ITEM_SWITCHES -> count(Stat.ITEM_SWITCHES) / 20D;
                case ATTEMPTED_SWINGS -> count(Stat.ATTEMPTED_SWINGS) / 60D;
                default -> count(Stat.MOVEMENT_SAMPLES) / 120D;
            };
            return Math.min(1D, evidence) * Integer.bitCount(observedWaves) / 6D * (fromEventStart ? 1D : .5D);
        }
    }
    private static final class Profile {
        final EnumMap<Stat, Long> stats = new EnumMap<>(Stat.class);
        final ArrayDeque<Observation> recent = new ArrayDeque<>();
        final LinkedHashSet<String> acceptedReceipts = new LinkedHashSet<>();
        int waves;
        long lastMovementTick = Long.MIN_VALUE, lastAttackTick = Long.MIN_VALUE;
        void add(Stat stat, long value) {
            stats.put(stat, Math.min(MAX_COUNTER, stats.getOrDefault(stat, 0L) + value));
        }
        void observe(Context context, String action, int value) {
            waves |= 1 << (context.wave() - 1);
            if (recent.size() == 32) recent.removeFirst();
            recent.addLast(new Observation(context.tick(), context.wave(), action, value));
        }
        void resetTransient() {
            recent.clear(); acceptedReceipts.clear();
            lastMovementTick = lastAttackTick = Long.MIN_VALUE;
        }
    }
    private String eventId = "";
    private long attemptGeneration, runtimeGeneration;
    private boolean fromEventStart;
    private long revision;
    public long revision() { return revision; }
    public boolean hasProfiles() { return !profiles.isEmpty(); }
    private final Map<UUID, Profile> profiles = new LinkedHashMap<>();

    public void beginAttempt(String eventId, long attemptGeneration, long runtimeGeneration,
                             Set<UUID> roster, boolean fromEventStart) {
        if (eventId == null || eventId.isBlank() || attemptGeneration <= 0
                || runtimeGeneration < attemptGeneration || roster == null || roster.isEmpty()
                || roster.size() > MAX_PARTICIPANTS || roster.stream().anyMatch(owner -> owner == null)) {
            throw new IllegalArgumentException("valid bounded attempt roster required");
        }
        clear(); this.eventId = eventId; this.attemptGeneration = attemptGeneration;
        this.runtimeGeneration = runtimeGeneration; this.fromEventStart = fromEventStart;
        roster.stream().sorted().forEach(owner -> profiles.put(owner, new Profile()));
    }
    public boolean rotateRuntime(String eventId, long runtimeGeneration) {
        if (!this.eventId.equals(eventId) || profiles.isEmpty()
                || runtimeGeneration < this.runtimeGeneration) return false;
        if (runtimeGeneration != this.runtimeGeneration) profiles.values().forEach(Profile::resetTransient);
        this.runtimeGeneration = runtimeGeneration;
        return true;
    }
    private Profile eligible(Context context, boolean movement) {
        if (context == null || !eventId.equals(context.eventId())
                || runtimeGeneration != context.runtimeGeneration() || context.tick() < 0
                || context.wave() < 1 || context.wave() > 6 || !context.activeCombat()
                || context.diagnostic() || context.frozenFog() || context.confined()
                || movement && context.forcedMotion()) return null;
        return profiles.get(context.player());
    }
    public boolean accepts(Context context) { return eligible(context, false) != null; }
    public boolean sample(Context context, Movement movement) {
        Profile p = eligible(context, true);
        if (p == null || movement == null || !Double.isFinite(movement.distance())
                || movement.distance() < 0 || !Double.isFinite(movement.strafe())
                || !Double.isFinite(movement.approach()) || !Double.isFinite(movement.healthRatio())
                || movement.healthRatio() < 0 || movement.healthRatio() > 1
                || p.lastMovementTick != Long.MIN_VALUE
                && (context.tick() < p.lastMovementTick || context.tick() - p.lastMovementTick < 5)) return false;
        p.lastMovementTick = context.tick();
        p.add(Stat.MOVEMENT_SAMPLES, 1);
        if (movement.sprinting()) p.add(Stat.SPRINT_SAMPLES, 1);
        if (movement.strafe() < -.08) p.add(Stat.STRAFE_LEFT, 1);
        if (movement.strafe() > .08) p.add(Stat.STRAFE_RIGHT, 1);
        if (movement.blocking()) p.add(Stat.SHIELD_SAMPLES, 1);
        if (movement.approach() > .1) p.add(Stat.PURSUIT_SAMPLES, 1);
        if (movement.approach() < -.1) p.add(Stat.RETREAT_SAMPLES, 1);
        p.add(movement.distance() <= 4 ? Stat.DISTANCE_NEAR
                : movement.distance() <= 10 ? Stat.DISTANCE_MID : Stat.DISTANCE_FAR, 1);
        revision++;
        p.observe(context, "movement", (int) Math.min(64, movement.distance()));
        return true;
    }
    public boolean acceptedHit(Context context, String receipt, Weapon weapon,
                               boolean nativeCritical, boolean eligibleDescendingMelee) {
        Profile p = eligible(context, false);
        if (p == null || receipt == null || receipt.isBlank() || receipt.length() > 128
                || weapon == null || !p.acceptedReceipts.add(receipt)) return false;
        if (p.acceptedReceipts.size() > 64) p.acceptedReceipts.remove(p.acceptedReceipts.iterator().next());
        boolean ranged = weapon == Weapon.BOW || weapon == Weapon.CROSSBOW || weapon == Weapon.OTHER_RANGED;
        p.add(ranged ? Stat.RANGED_HITS : Stat.MELEE_HITS, 1);
        if (!ranged && nativeCritical && eligibleDescendingMelee) p.add(Stat.JUMP_CRITICAL_HITS, 1);
        switch (weapon) {
            case SWORD -> p.add(Stat.SWORD_HITS, 1);
            case AXE -> p.add(Stat.AXE_HITS, 1);
            case BOW -> p.add(Stat.BOW_HITS, 1);
            case CROSSBOW -> p.add(Stat.CROSSBOW_HITS, 1);
            default -> { }
        }
        if (p.lastAttackTick != Long.MIN_VALUE && context.tick() > p.lastAttackTick) {
            long interval = context.tick() - p.lastAttackTick;
            if (interval <= 200) {
                p.add(Stat.ATTACK_INTERVAL_TICKS, interval); p.add(Stat.ATTACK_INTERVAL_SAMPLES, 1);
                if (interval <= 12) p.add(Stat.SHORT_ATTACK_INTERVALS, 1);
            }
        }
        revision++;
        p.lastAttackTick = context.tick(); p.observe(context, "accepted-hit", weapon.ordinal());
        return true;
    }
    public void swing(Context context) { action(context, Stat.ATTEMPTED_SWINGS, "swing"); }
    public void switchItem(Context context) { action(context, Stat.ITEM_SWITCHES, "switch"); }
    private void action(Context context, Stat stat, String action) {
        Profile p = eligible(context, false);
        if (p != null) { revision++; p.add(stat, 1); p.observe(context, action, 0); }
    }
    public void bowRelease(Context context, Weapon weapon, int chargeTicks) {
        Profile p = eligible(context, false);
        if (p == null || weapon != Weapon.BOW && weapon != Weapon.CROSSBOW || chargeTicks < 0) return;
        p.add(weapon == Weapon.BOW ? Stat.BOW_RELEASES : Stat.CROSSBOW_RELEASES, 1);
        if (weapon == Weapon.BOW) p.add(Stat.BOW_CHARGE_TICKS, Math.min(60, chargeTicks));
        revision++;
        p.observe(context, "ranged-release", Math.min(60, chargeTicks));
    }
    public void crossbowLoad(Context context, int chargeTicks) {
        Profile p = eligible(context, false);
        if (p == null || chargeTicks < 0) return;
        p.add(Stat.CROSSBOW_LOADS, 1); p.add(Stat.CROSSBOW_CHARGE_TICKS, Math.min(60, chargeTicks));
        revision++;
        p.observe(context, "crossbow-load", Math.min(60, chargeTicks));
    }
    public void healUse(Context context, double healthRatio) {
        Profile p = eligible(context, false);
        if (p == null || !Double.isFinite(healthRatio) || healthRatio < 0 || healthRatio > 1) return;
        p.add(Stat.HEAL_USES, 1); p.add(Stat.HEAL_HEALTH_PERMILLE, Math.round(healthRatio * 1000));
        revision++;
        p.observe(context, "healing-use", (int) Math.round(healthRatio * 1000));
    }
    public Snapshot snapshot(UUID owner) {
        Profile p = profiles.get(owner);
        return p == null ? null : new Snapshot(p.stats, p.waves, fromEventStart, new ArrayList<>(p.recent));
    }
    public Map<String, String> encode() {
        if (profiles.isEmpty()) return Map.of();
        Map<String, String> encoded = new LinkedHashMap<>();
        encoded.put(PREFIX + "schema", "1"); encoded.put(PREFIX + "event", eventId);
        encoded.put(PREFIX + "attempt", Long.toString(attemptGeneration));
        encoded.put(PREFIX + "runtime", Long.toString(runtimeGeneration));
        encoded.put(PREFIX + "from-start", Boolean.toString(fromEventStart));
        encoded.put(PREFIX + "roster", rosterText(profiles.keySet()));
        profiles.forEach((owner, p) -> {
            String key = PREFIX + owner;
            List<String> values = new ArrayList<>();
            for (Stat stat : Stat.values()) values.add(Long.toString(p.stats.getOrDefault(stat, 0L)));
            encoded.put(key + ".stats", String.join(",", values));
            encoded.put(key + ".waves", Integer.toString(p.waves));
        });
        return Map.copyOf(encoded);
    }
    public boolean restore(Map<String, String> encoded, String eventId, long runtimeGeneration,
                           Set<UUID> roster) {
        try {
            if (encoded == null || !"1".equals(encoded.get(PREFIX + "schema"))
                    || eventId == null || eventId.isBlank() || !eventId.equals(encoded.get(PREFIX + "event"))
                    || roster == null || roster.isEmpty() || roster.size() > MAX_PARTICIPANTS
                    || roster.stream().anyMatch(owner -> owner == null)
                    || !rosterText(roster).equals(encoded.get(PREFIX + "roster"))
                    || Long.parseLong(encoded.get(PREFIX + "runtime")) != runtimeGeneration) return false;
            long attempt = Long.parseLong(encoded.get(PREFIX + "attempt"));
            if (attempt <= 0 || runtimeGeneration < attempt) return false;
            String fromStart = encoded.get(PREFIX + "from-start");
            if (!"true".equals(fromStart) && !"false".equals(fromStart)) return false;
            Map<UUID, Profile> restored = new LinkedHashMap<>();
            for (UUID owner : roster.stream().sorted().toList()) {
                String key = PREFIX + owner;
                String[] values = encoded.get(key + ".stats").split(",", -1);
                if (values.length != Stat.values().length) return false;
                Profile p = new Profile();
                for (int i = 0; i < values.length; i++) {
                    long value = Long.parseLong(values[i]);
                    if (value < 0 || value > MAX_COUNTER) return false;
                    p.stats.put(Stat.values()[i], value);
                }
                p.waves = Integer.parseInt(encoded.get(key + ".waves"));
                if (p.waves < 0 || p.waves > 63) return false;
                restored.put(owner, p);
            }
            this.eventId = eventId; this.attemptGeneration = attempt;
            this.runtimeGeneration = runtimeGeneration; this.fromEventStart = Boolean.parseBoolean(fromStart);
            profiles.clear(); profiles.putAll(restored); revision++; return true;
        } catch (IllegalArgumentException | NullPointerException invalid) { return false; }
    }
    private static String rosterText(Set<UUID> roster) {
        return String.join(",", roster.stream().sorted().map(UUID::toString).toList());
    }
    public void clear() {
        eventId = ""; attemptGeneration = runtimeGeneration = 0; fromEventStart = false; profiles.clear(); revision++;
    }
}
