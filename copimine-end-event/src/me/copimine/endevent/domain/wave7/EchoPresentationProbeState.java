package me.copimine.endevent.domain.wave7;

import java.util.Objects;
import java.util.UUID;

/** Local presentation clock only. It owns no duel outcome, inventory or healing. */
public final class EchoPresentationProbeState {
    public enum Action { IDLE, WALK, SPRINT, CROUCH, JUMP, SWING, BOW, CROSSBOW, SHIELD, EAT, HURT, DEATH }
    public record Frame(UUID event, long generation, long epoch, UUID duel, UUID actor, UUID owner,
                        String dimension, long sequence, String pose, boolean sprinting, String hand,
                        int useElapsed, int useDuration, long swingSerial, long hurtSerial,
                        int deathTicks, int equipmentVersion) {
        public String fields() {
            return owner + "|" + epoch + "|" + sequence + "|" + pose + "|" + (sprinting ? "1" : "0")
                    + "|" + hand + "|" + useElapsed + "|" + useDuration + "|" + swingSerial
                    + "|" + hurtSerial + "|" + deathTicks + "|" + equipmentVersion;
        }
    }
    private final UUID event, duel, actor, owner;
    private final long generation, epoch, startedTick;
    private final long lifetimeTicks;
    private final String dimension;
    private Action action = Action.IDLE;
    private long actionTick, sequence, swingSerial, hurtSerial, deathTick = -1;
    private int equipmentVersion;
    /**
     * E-05: how long the current item use is actually meant to last.
     *
     * <p>A bow draw is a real item use: the client keeps the bow pulled exactly
     * while {@code useDuration > 0}, so the fixed 32/25-tick presentation
     * windows would make a heavy draw (up to 60 ticks) visibly finish and lower
     * the bow before the shot. The action still begins once; only its window is
     * the operator's own recorded charge.</p>
     */
    private int useDurationOverride;
    private String shieldHand = "OFF";
    private boolean closed;

    public EchoPresentationProbeState(UUID event, long generation, long epoch, UUID duel, UUID actor,
                                      UUID owner, String dimension, long tick) {
        this(event, generation, epoch, duel, actor, owner, dimension, tick, 6_000L);
    }

    /** Official persisted duels use a bounded long lifetime; local probes retain their short timeout. */
    public EchoPresentationProbeState(UUID event, long generation, long epoch, UUID duel, UUID actor,
                                      UUID owner, String dimension, long tick, long lifetimeTicks) {
        this.event = Objects.requireNonNull(event); this.duel = Objects.requireNonNull(duel);
        this.actor = Objects.requireNonNull(actor); this.owner = Objects.requireNonNull(owner);
        if (generation <= 0 || epoch <= 0 || actor.equals(owner) || tick < 0
                || lifetimeTicks <= 0 || lifetimeTicks > 1_000_000_000L
                || dimension == null || dimension.length() > 64
                || !dimension.matches("[a-z0-9_.-]+:[a-z0-9_/.-]+"))
            throw new IllegalArgumentException("Invalid local Echo presentation identity");
        this.generation = generation; this.epoch = epoch; this.dimension = dimension;
        this.lifetimeTicks = lifetimeTicks;
        startedTick = actionTick = tick;
    }

    public boolean begin(Action requested, long tick) {
        return begin(requested, tick, "OFF", 0);
    }
    public boolean begin(Action requested, long tick, String requestedShieldHand) {
        return begin(requested, tick, requestedShieldHand, 0);
    }
    /**
     * Starts a pose and, for a real item use, records how long that use lasts.
     *
     * @param requestedUseDurationTicks the charge window the pose must cover, or
     *                                  {@code 0} for the action's own default
     */
    public boolean begin(Action requested, long tick, String requestedShieldHand,
                         int requestedUseDurationTicks) {
        if (closed) return false;
        if (!canProduceFrame() || equipmentVersion == Integer.MAX_VALUE
                || requested == Action.SWING && swingSerial == Long.MAX_VALUE) {
            closed = true;
            return false;
        }
        if (deathTick >= 0 || requested == null || tick < actionTick
                || !"MAIN".equals(requestedShieldHand) && !"OFF".equals(requestedShieldHand)
                || requestedUseDurationTicks < 0 || requestedUseDurationTicks > 72_000) return false;
        action = requested; actionTick = tick; equipmentVersion++;
        shieldHand = requestedShieldHand;
        useDurationOverride = requestedUseDurationTicks;
        if (requested == Action.SWING) swingSerial++;
        return true;
    }
    public Action action() { return action; }
    public UUID duel() { return duel; }
    public long epoch() { return epoch; }
    public boolean closed() { return closed; }
    public boolean canProduceFrame() { return !closed && sequence < Long.MAX_VALUE; }
    public boolean hasEquipmentCounterCapacity() { return equipmentVersion < Integer.MAX_VALUE; }
    public boolean hasHurtCounterCapacity() { return hurtSerial < Long.MAX_VALUE; }
    public boolean acceptedHurt() {
        if (closed || deathTick >= 0) return false;
        if (hurtSerial == Long.MAX_VALUE) {
            closed = true;
            return false;
        }
        hurtSerial++;
        return true;
    }
    public void died(long tick) { if (deathTick < 0) { deathTick = tick; action = Action.DEATH; } }
    public void close() { closed = true; }

    public boolean active(UUID currentEvent, long currentGeneration, long tick,
                          boolean online, boolean alive, boolean sameWorld, boolean capable) {
        return !closed && event.equals(currentEvent) && generation == currentGeneration
                && tick >= startedTick && tick - startedTick < lifetimeTicks && online && alive && sameWorld && capable
                && (deathTick < 0 || tick - deathTick < 20);
    }

    public Frame nextFrame(long tick) {
        if (closed || sequence == Long.MAX_VALUE) {
            closed = true;
            return null;
        }
        int duration = deathTick >= 0 ? 0 : switch (action) {
            case BOW, EAT -> 32;
            case CROSSBOW -> 25;
            case SHIELD -> 72_000;
            default -> 0;
        };
        // E-05: a bow/crossbow draw keeps the item-use window for its real
        // charge instead of the fixed presentation default.
        if (deathTick < 0 && useDurationOverride > 0) duration = useDurationOverride;
        long elapsed = Math.max(0, tick - actionTick);
        if (duration != 0 && elapsed >= duration) duration = 0;
        String hand = duration == 0 ? "NONE" : action == Action.SHIELD ? shieldHand : "MAIN";
        return new Frame(event, generation, epoch, duel, actor, owner, dimension, ++sequence,
                action == Action.CROUCH && deathTick < 0 ? "CROUCHING" : "STANDING",
                action == Action.SPRINT && deathTick < 0, hand,
                duration == 0 ? 0 : (int) elapsed, duration, swingSerial, hurtSerial,
                deathTick < 0 ? 0 : (int) Math.min(20, Math.max(0, tick - deathTick)), equipmentVersion);
    }
}
