package me.copimine.endevent.domain;

import java.util.List;
import java.util.UUID;
import me.copimine.endevent.runtime.RealitySplitTrialController.NamedTrial;

/** Bounded role profiles, attack cadence, target admission, and durable actor checkpoints for named Wave 7 actors. */
public final class Wave7NamedTrialActorPolicy {
    public enum Attack {
        NONE,
        MARKSMAN_QUICK_SHOT,
        MARKSMAN_HEAVY_SHOT,
        MARKSMAN_SPREAD_SHOT,
        MARKSMAN_SUPPORT,
        ARCHMAGE_FANGS,
        ARCHMAGE_SPEAR,
        ARCHMAGE_STEP,
        ARCHMAGE_BEAM,
        /** Plaque sentinelle: the Warden uses its native combat loop, not a scripted spell. */
        NONE_INTERNAL
    }

    public record Profile(NamedTrial trial, String entityType, String displayName,
                          double maxHealth, double contactDamage, List<Attack> attacks,
                          double bodyScale) {
        public Profile {
            if (trial == null || entityType == null || entityType.isBlank()
                    || displayName == null || displayName.isBlank()
                    || !Double.isFinite(maxHealth) || maxHealth <= 0.0D
                    || !Double.isFinite(contactDamage) || contactDamage < 0.0D
                    || attacks == null || attacks.isEmpty()
                    || !Double.isFinite(bodyScale) || bodyScale <= 0.0D) {
                throw new IllegalArgumentException("invalid named Wave 7 actor profile");
            }
            attacks = List.copyOf(attacks);
        }
    }

    /** A room-bound actor identity and attack checkpoint stored on the persistent Paper entity. */
    public record Checkpoint(UUID eventId, long generation, int chamber, NamedTrial trial,
                             UUID actorId, long attackIndex, Attack attack,
                             long phaseDeadlineMillis, long cooldownUntilMillis,
                             UUID targetId, boolean supportUsed, long vanishUntilMillis) {
        /** Schema 1: no invisibility window recorded yet. */
        public Checkpoint(UUID eventId, long generation, int chamber, NamedTrial trial,
                          UUID actorId, long attackIndex, Attack attack,
                          long phaseDeadlineMillis, long cooldownUntilMillis,
                          UUID targetId, boolean supportUsed) {
            this(eventId, generation, chamber, trial, actorId, attackIndex, attack,
                    phaseDeadlineMillis, cooldownUntilMillis, targetId, supportUsed, 0L);
        }

        public Checkpoint {
            Profile profile = profile(trial);
            if (eventId == null || generation <= 0L || chamber < 0 || chamber >= 4
                    || profile == null || actorId == null || attackIndex < 0L || attackIndex > 1_000_000L
                    || attack == null || attack != Attack.NONE && !profile.attacks().contains(attack)
                    && !(trial == NamedTrial.MOBILE_MARKSMAN && attack == Attack.MARKSMAN_SUPPORT)
                    || phaseDeadlineMillis < 0L || cooldownUntilMillis < 0L
                    || vanishUntilMillis < 0L
                    || vanishUntilMillis > 0L && trial != NamedTrial.MOBILE_MARKSMAN
                    || attack != Attack.NONE && phaseDeadlineMillis == 0L) {
                throw new IllegalArgumentException("invalid named Wave 7 actor checkpoint");
            }
        }

        /**
         * MARK-005: the actor is hidden until this deadline.
         *
         * <p>The deadline is absolute, so a restart never refreshes the vanish,
         * and {@link #mayAttackNow(long)} keeps a hidden Marksman from shooting
         * from cover.
         */
        public boolean hiddenNow(long nowMillis) {
            return vanishUntilMillis > 0L && nowMillis < vanishUntilMillis;
        }

        /** MARK-006: hidden is not invulnerable, and it is never a legal firing window. */
        public boolean mayAttackNow(long nowMillis) {
            return !hiddenNow(nowMillis);
        }
    }

    private static final List<Attack> MARKSMAN_ATTACKS = List.of(
            Attack.MARKSMAN_QUICK_SHOT, Attack.MARKSMAN_HEAVY_SHOT, Attack.MARKSMAN_SPREAD_SHOT);
    private static final List<Attack> ARCHMAGE_ATTACKS = List.of(
            Attack.ARCHMAGE_FANGS, Attack.ARCHMAGE_SPEAR,
            Attack.ARCHMAGE_STEP, Attack.ARCHMAGE_BEAM);

    /**
     * Contractual floor for every Wave 7 miniboss, in real health points.
     *
     * <p>A miniboss is not an elite. The shared elite rule (3x the ordinary
     * role) is deliberately not reused here: 3x a skeleton is still a mob, and
     * the approved contract requires a real fight under party scaling.</p>
     */
    public static final double MIN_MINIBOSS_HEALTH = 1000.0D;

    /** Party scaling step, applied once per admitted player above the minimum. */
    public static final double HEALTH_PER_EXTRA_PLAYER = 0.25D;

    /** Smallest supported party. */
    public static final int MIN_PARTICIPANTS = 2;

    /** Largest supported party; also the bound of the scaling basis. */
    public static final int MAX_PARTICIPANTS = 20;

    private Wave7NamedTrialActorPolicy() { }

    /**
     * Miniboss health for one trial under a frozen roster.
     *
     * <p>{@code base * (1 + 0.25 * (admitted - 2))}, floored at
     * {@link #MIN_MINIBOSS_HEALTH}. The basis is the roster frozen at
     * admission, so a relog never rescales or heals a living actor.</p>
     */
    public static double minibossHealth(NamedTrial trial, int admittedPlayers) {
        if (trial == null) {
            throw new IllegalArgumentException("trial is required");
        }
        if (admittedPlayers < MIN_PARTICIPANTS || admittedPlayers > MAX_PARTICIPANTS) {
            throw new IllegalArgumentException(
                    "admitted players must be in [" + MIN_PARTICIPANTS + ", "
                            + MAX_PARTICIPANTS + "]: " + admittedPlayers);
        }
        double scaled = trialBaseHealth(trial)
                * (1.0D + HEALTH_PER_EXTRA_PLAYER * (admittedPlayers - MIN_PARTICIPANTS));
        return Math.max(MIN_MINIBOSS_HEALTH, scaled);
    }

    /** Per-trial base health; every trial sits at or above the contractual floor. */
    public static double trialBaseHealth(NamedTrial trial) {
        if (trial == NamedTrial.MOBILE_MARKSMAN) {
            return MIN_MINIBOSS_HEALTH;
        }
        if (trial == NamedTrial.RIFT_ARCHMAGE) {
            return 1200.0D;
        }
        return MIN_MINIBOSS_HEALTH;
    }

    /**
     * Profile for a trial under the default roster.
     *
     * <p>The profile carries display identity and attack set; health in a live
     * encounter comes from {@link #minibossHealth(NamedTrial, int)}.</p>
     */
    public static Profile profile(NamedTrial trial) {
        return profile(trial, MIN_PARTICIPANTS);
    }

    /**
     * Warden body scale relative to a vanilla Warden.
     *
     * <p>The Wave 7 Warden is a real boss-tier trial, so it towers over the
     * vanilla mob rather than matching it.</p>
     */
    public static final double WARDEN_BODY_SCALE = 1.5D;

    /**
     * Marksman body scale relative to a vanilla Skeleton.
     *
     * <p>Deliberately just above an elite: it reads as an upgraded archer, not
     * as a second boss.</p>
     */
    public static final double MARKSMAN_BODY_SCALE = 1.15D;

    /** Archmage body scale relative to a vanilla Evoker. */
    public static final double ARCHMAGE_BODY_SCALE = 1.5D;

    /** Profile for a trial under an explicit frozen roster. */
    public static Profile profile(NamedTrial trial, int admittedPlayers) {
        if (trial == NamedTrial.MOBILE_MARKSMAN) {
            return new Profile(trial, "SKELETON", "Подвижный Стрелок Разлома",
                    minibossHealth(trial, admittedPlayers), 4.0D, MARKSMAN_ATTACKS,
                    MARKSMAN_BODY_SCALE);
        }
        if (trial == NamedTrial.RIFT_ARCHMAGE) {
            return new Profile(trial, "EVOKER", "Архимаг Разлома",
                    minibossHealth(trial, admittedPlayers), 5.0D, ARCHMAGE_ATTACKS,
                    ARCHMAGE_BODY_SCALE);
        }
        return null;
    }

    /** Warden trial profile; the Warden is spawned by its own branch, not by {@link #profile}. */
    public static Profile wardenProfile(int admittedPlayers) {
        return new Profile(NamedTrial.WARDEN, "WARDEN", "Страж Разлома",
                minibossHealth(NamedTrial.WARDEN, admittedPlayers), 8.0D,
                List.of(Attack.NONE_INTERNAL), WARDEN_BODY_SCALE);
    }

    /** Body scale of one trial's miniboss; 1.0 means "vanilla size". */
    public static double bodyScale(NamedTrial trial) {
        if (trial == NamedTrial.MOBILE_MARKSMAN) return MARKSMAN_BODY_SCALE;
        if (trial == NamedTrial.RIFT_ARCHMAGE) return ARCHMAGE_BODY_SCALE;
        return WARDEN_BODY_SCALE;
    }

    public static boolean hasActorAdapter(NamedTrial trial) {
        return profile(trial) != null;
    }

    public static Attack attackFor(NamedTrial trial, long attackIndex) {
        Profile profile = profile(trial);
        if (profile == null || attackIndex < 0L || attackIndex > 1_000_000L) return Attack.NONE;
        return profile.attacks().get((int) (attackIndex % profile.attacks().size()));
    }

    /**
     * Fans in one Marksman spread burst.
     *
     * <p>The contract asks for an enhanced successive fan: three fans with
     * readable gaps, not one wide spray. Each fan is fired as its own
     * sub-volley so a target can read the sequence and take bounded hits.</p>
     */
    public static final int MARKSMAN_FAN_COUNT = 3;

    /** Arrows in one Marksman fan. */
    public static final int MARKSMAN_ARROWS_PER_FAN = 8;

    /** Total arrows in one spread burst; the event-wide cap is 24. */
    public static final int MARKSMAN_ARROWS_PER_BURST = MARKSMAN_FAN_COUNT * MARKSMAN_ARROWS_PER_FAN;

    /** Ticks between two fans of one burst, long enough for a readable tell. */
    public static final int MARKSMAN_FAN_GAP_TICKS = 6;

    /** Angular step between adjacent arrows of one fan, in degrees. */
    public static final double MARKSMAN_FAN_SPREAD_DEGREES = 2.2D;

    /**
     * Angled offsets of one fan, centred on the target line.
     *
     * <p>Odd arm counts stay symmetric; even counts offset by one half step so
     * no arrow is exactly on the axis and the middle is never double-hit.</p>
     */
    public static java.util.List<Double> fanAngles() {
        java.util.List<Double> angles = new java.util.ArrayList<>(MARKSMAN_ARROWS_PER_FAN);
        double half = (MARKSMAN_ARROWS_PER_FAN - 1) / 2.0D;
        for (int index = 0; index < MARKSMAN_ARROWS_PER_FAN; index++) {
            angles.add((index - half) * MARKSMAN_FAN_SPREAD_DEGREES);
        }
        return java.util.List.copyOf(angles);
    }

    /** Number of visible tell ticks before a real attack resolves. */
    public static int telegraphTicks(Attack attack) {
        return switch (attack) {
            case MARKSMAN_QUICK_SHOT -> 12;
            case MARKSMAN_HEAVY_SHOT -> 22;
            case MARKSMAN_SPREAD_SHOT -> 18;
            case MARKSMAN_SUPPORT -> 20;
            case ARCHMAGE_FANGS -> 20;
            case ARCHMAGE_SPEAR -> 24;
            case ARCHMAGE_STEP -> 18;
            case ARCHMAGE_BEAM -> 26;
            case NONE, NONE_INTERNAL -> 0;
        };
    }

    /** Minimum rest ticks after resolution; long tells always leave the player a counter window. */
    public static int cooldownTicks(Attack attack) {
        return switch (attack) {
            case MARKSMAN_QUICK_SHOT -> 34;
            case MARKSMAN_HEAVY_SHOT -> 58;
            case MARKSMAN_SPREAD_SHOT -> 48;
            case MARKSMAN_SUPPORT -> 90;
            case ARCHMAGE_FANGS -> 58;
            case ARCHMAGE_SPEAR -> 62;
            case ARCHMAGE_STEP -> 46;
            case ARCHMAGE_BEAM -> 78;
            case NONE, NONE_INTERNAL -> 1;
        };
    }

    /** A target must be active in the exact actor room and event attempt at the last damage boundary. */
    public static boolean admitsTarget(UUID eventId, long generation, int actorChamber,
                                       NamedTrial actorTrial, boolean actorActive,
                                       UUID targetId, int targetChamber, boolean activeParticipant) {
        return eventId != null && generation > 0L && actorChamber >= 0 && actorChamber < 4
                && hasActorAdapter(actorTrial) && actorActive && targetId != null
                && targetChamber == actorChamber && activeParticipant;
    }

    public static String encode(Checkpoint checkpoint) {
        if (checkpoint == null) throw new IllegalArgumentException("missing named actor checkpoint");
        return String.join("|", "2", checkpoint.eventId().toString(),
                Long.toString(checkpoint.generation()), Integer.toString(checkpoint.chamber()),
                checkpoint.trial().name(), checkpoint.actorId().toString(),
                Long.toString(checkpoint.attackIndex()), checkpoint.attack().name(),
                Long.toString(checkpoint.phaseDeadlineMillis()),
                Long.toString(checkpoint.cooldownUntilMillis()),
                checkpoint.targetId() == null ? "-" : checkpoint.targetId().toString(),
                checkpoint.supportUsed() ? "1" : "0",
                Long.toString(checkpoint.vanishUntilMillis()));
    }

    public static Checkpoint decode(String encoded, UUID expectedEvent, long expectedGeneration,
                                    int expectedChamber, NamedTrial expectedTrial, UUID expectedActor) {
        if (encoded == null || encoded.length() > 512) {
            throw new IllegalArgumentException("missing or oversized named actor checkpoint");
        }
        String[] parts = encoded.split("\\|", -1);
        // SUPERSEDED (MARK-005): schema 1 had no invisibility window. It still
        // decodes so a stage in progress across the upgrade is not lost, and a
        // schema 1 record simply has no active vanish.
        if (parts.length != 12 && parts.length != 13
                || !"1".equals(parts[0]) && !"2".equals(parts[0])
                || "2".equals(parts[0]) && parts.length != 13
                || "1".equals(parts[0]) && parts.length != 12) {
            throw new IllegalArgumentException("unsupported named actor checkpoint schema");
        }
        try {
            UUID event = UUID.fromString(parts[1]);
            long generation = Long.parseLong(parts[2]);
            int chamber = Integer.parseInt(parts[3]);
            NamedTrial trial = NamedTrial.valueOf(parts[4]);
            UUID actor = UUID.fromString(parts[5]);
            long attackIndex = Long.parseLong(parts[6]);
            Attack attack = Attack.valueOf(parts[7]);
            long phaseDeadline = Long.parseLong(parts[8]);
            long cooldown = Long.parseLong(parts[9]);
            UUID target = "-".equals(parts[10]) ? null : UUID.fromString(parts[10]);
            boolean supportUsed = switch (parts[11]) {
                case "0" -> false;
                case "1" -> true;
                default -> throw new IllegalArgumentException("non-canonical support flag");
            };
            long vanishUntil = parts.length == 13 ? Long.parseLong(parts[12]) : 0L;
            Checkpoint checkpoint = new Checkpoint(event, generation, chamber, trial, actor,
                    attackIndex, attack, phaseDeadline, cooldown, target, supportUsed, vanishUntil);
            if (!event.equals(expectedEvent) || generation != expectedGeneration
                    || chamber != expectedChamber || trial != expectedTrial
                    || !actor.equals(expectedActor) || !encode(checkpoint).equals(encoded)) {
                throw new IllegalArgumentException("foreign or non-canonical named actor checkpoint");
            }
            return checkpoint;
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("invalid named actor checkpoint", error);
        }
    }
}
