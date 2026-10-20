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
        ARCHMAGE_BEAM
    }

    public record Profile(NamedTrial trial, String entityType, String displayName,
                          double maxHealth, double contactDamage, List<Attack> attacks) {
        public Profile {
            if (trial == null || entityType == null || entityType.isBlank()
                    || displayName == null || displayName.isBlank()
                    || !Double.isFinite(maxHealth) || maxHealth <= 0.0D
                    || !Double.isFinite(contactDamage) || contactDamage < 0.0D
                    || attacks == null || attacks.isEmpty()) {
                throw new IllegalArgumentException("invalid named Wave 7 actor profile");
            }
            attacks = List.copyOf(attacks);
        }
    }

    /** A room-bound actor identity and attack checkpoint stored on the persistent Paper entity. */
    public record Checkpoint(UUID eventId, long generation, int chamber, NamedTrial trial,
                             UUID actorId, long attackIndex, Attack attack,
                             long phaseDeadlineMillis, long cooldownUntilMillis,
                             UUID targetId, boolean supportUsed) {
        public Checkpoint {
            Profile profile = profile(trial);
            if (eventId == null || generation <= 0L || chamber < 0 || chamber >= 4
                    || profile == null || actorId == null || attackIndex < 0L || attackIndex > 1_000_000L
                    || attack == null || attack != Attack.NONE && !profile.attacks().contains(attack)
                    && !(trial == NamedTrial.MOBILE_MARKSMAN && attack == Attack.MARKSMAN_SUPPORT)
                    || phaseDeadlineMillis < 0L || cooldownUntilMillis < 0L
                    || attack != Attack.NONE && phaseDeadlineMillis == 0L) {
                throw new IllegalArgumentException("invalid named Wave 7 actor checkpoint");
            }
        }
    }

    private static final List<Attack> MARKSMAN_ATTACKS = List.of(
            Attack.MARKSMAN_QUICK_SHOT, Attack.MARKSMAN_HEAVY_SHOT, Attack.MARKSMAN_SPREAD_SHOT);
    private static final List<Attack> ARCHMAGE_ATTACKS = List.of(
            Attack.ARCHMAGE_FANGS, Attack.ARCHMAGE_SPEAR,
            Attack.ARCHMAGE_STEP, Attack.ARCHMAGE_BEAM);

    private Wave7NamedTrialActorPolicy() { }

    public static Profile profile(NamedTrial trial) {
        if (trial == NamedTrial.MOBILE_MARKSMAN) {
            return new Profile(trial, "SKELETON", "Подвижный Стрелок Разлома",
                    120.0D, 4.0D, MARKSMAN_ATTACKS);
        }
        if (trial == NamedTrial.RIFT_ARCHMAGE) {
            return new Profile(trial, "EVOKER", "Архимаг Разлома",
                    200.0D, 5.0D, ARCHMAGE_ATTACKS);
        }
        return null;
    }

    public static boolean hasActorAdapter(NamedTrial trial) {
        return profile(trial) != null;
    }

    public static Attack attackFor(NamedTrial trial, long attackIndex) {
        Profile profile = profile(trial);
        if (profile == null || attackIndex < 0L || attackIndex > 1_000_000L) return Attack.NONE;
        return profile.attacks().get((int) (attackIndex % profile.attacks().size()));
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
            case NONE -> 0;
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
            case NONE -> 1;
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
        return String.join("|", "1", checkpoint.eventId().toString(),
                Long.toString(checkpoint.generation()), Integer.toString(checkpoint.chamber()),
                checkpoint.trial().name(), checkpoint.actorId().toString(),
                Long.toString(checkpoint.attackIndex()), checkpoint.attack().name(),
                Long.toString(checkpoint.phaseDeadlineMillis()),
                Long.toString(checkpoint.cooldownUntilMillis()),
                checkpoint.targetId() == null ? "-" : checkpoint.targetId().toString(),
                checkpoint.supportUsed() ? "1" : "0");
    }

    public static Checkpoint decode(String encoded, UUID expectedEvent, long expectedGeneration,
                                    int expectedChamber, NamedTrial expectedTrial, UUID expectedActor) {
        if (encoded == null || encoded.length() > 512) {
            throw new IllegalArgumentException("missing or oversized named actor checkpoint");
        }
        String[] parts = encoded.split("\\|", -1);
        if (parts.length != 12 || !"1".equals(parts[0])) {
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
            Checkpoint checkpoint = new Checkpoint(event, generation, chamber, trial, actor,
                    attackIndex, attack, phaseDeadline, cooldown, target, supportUsed);
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
