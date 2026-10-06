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
    private final String dimension;
    private Action action = Action.IDLE;
    private long actionTick, sequence, swingSerial, hurtSerial, deathTick = -1;
    private int equipmentVersion;
    private String shieldHand = "OFF";
    private boolean closed;

    public EchoPresentationProbeState(UUID event, long generation, long epoch, UUID duel, UUID actor,
                                      UUID owner, String dimension, long tick) {
        this.event = Objects.requireNonNull(event); this.duel = Objects.requireNonNull(duel);
        this.actor = Objects.requireNonNull(actor); this.owner = Objects.requireNonNull(owner);
        if (generation <= 0 || epoch <= 0 || actor.equals(owner) || tick < 0
                || dimension == null || dimension.length() > 64
                || !dimension.matches("[a-z0-9_.-]+:[a-z0-9_/.-]+"))
            throw new IllegalArgumentException("Invalid local Echo presentation identity");
        this.generation = generation; this.epoch = epoch; this.dimension = dimension;
        startedTick = actionTick = tick;
    }

    public boolean begin(Action requested, long tick) {
        return begin(requested, tick, "OFF");
    }
    public boolean begin(Action requested, long tick, String requestedShieldHand) {
        if (closed || deathTick >= 0 || requested == null || tick < actionTick
                || !"MAIN".equals(requestedShieldHand) && !"OFF".equals(requestedShieldHand)) return false;
        action = requested; actionTick = tick; equipmentVersion++;
        shieldHand = requestedShieldHand;
        if (requested == Action.SWING) swingSerial++;
        return true;
    }
    public Action action() { return action; }
    public void acceptedHurt() { if (!closed && deathTick < 0) hurtSerial++; }
    public void died(long tick) { if (deathTick < 0) { deathTick = tick; action = Action.DEATH; } }
    public void close() { closed = true; }

    public boolean active(UUID currentEvent, long currentGeneration, long tick,
                          boolean online, boolean alive, boolean sameWorld, boolean capable) {
        return !closed && event.equals(currentEvent) && generation == currentGeneration
                && tick >= startedTick && tick - startedTick < 6_000 && online && alive && sameWorld && capable
                && (deathTick < 0 || tick - deathTick < 20);
    }

    public Frame nextFrame(long tick) {
        int duration = deathTick >= 0 ? 0 : switch (action) {
            case BOW, EAT -> 32;
            case CROSSBOW -> 25;
            case SHIELD -> 72_000;
            default -> 0;
        };
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
