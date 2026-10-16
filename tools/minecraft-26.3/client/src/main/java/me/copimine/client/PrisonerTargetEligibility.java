package me.copimine.client;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** Server-authored target allowlist used only to drive the local prisoner HUD. */
public final class PrisonerTargetEligibility {
    private static final String PREFIX = "PRISONER_TARGETS_V1|";
    private static final int MAX_ENCODED_LENGTH = 8_192;
    private static final int MAX_TARGETS_PER_GROUP = 64;
    private static final PrisonerTargetEligibility EMPTY = new PrisonerTargetEligibility(Set.of(), Set.of());

    private final Set<UUID> supportTargets;
    private final Set<UUID> turncoatTargets;

    private PrisonerTargetEligibility(Set<UUID> supportTargets, Set<UUID> turncoatTargets) {
        this.supportTargets = Set.copyOf(supportTargets);
        this.turncoatTargets = Set.copyOf(turncoatTargets);
    }

    /** Invalid or absent target data fails closed while leaving cooldown state usable. */
    public static PrisonerTargetEligibility parse(String encoded) {
        if (encoded == null || encoded.isBlank() || encoded.length() > MAX_ENCODED_LENGTH
                || !encoded.startsWith(PREFIX)) {
            return EMPTY;
        }
        String[] fields = encoded.substring(PREFIX.length()).split("\\|", -1);
        if (fields.length != 2) return EMPTY;
        try {
            return new PrisonerTargetEligibility(parseTargets(fields[0]), parseTargets(fields[1]));
        } catch (IllegalArgumentException error) {
            return EMPTY;
        }
    }

    public static PrisonerTargetEligibility empty() {
        return EMPTY;
    }

    public boolean allows(PrisonerHudController.Ability ability, UUID target) {
        if (ability == null || target == null) return false;
        return switch (ability) {
            case HEAL, BATTLE_SURGE, GUARDIAN_LINK -> supportTargets.contains(target);
            case TURNCOAT -> turncoatTargets.contains(target);
        };
    }

    private static Set<UUID> parseTargets(String field) {
        if (field.isEmpty()) return Set.of();
        String[] values = field.split(",", -1);
        if (values.length > MAX_TARGETS_PER_GROUP) throw new IllegalArgumentException("too many targets");
        Set<UUID> result = new LinkedHashSet<>();
        for (String value : values) {
            UUID target = UUID.fromString(value);
            if (value.length() != 36 || !target.toString().equalsIgnoreCase(value)) {
                throw new IllegalArgumentException("noncanonical target UUID");
            }
            result.add(target);
        }
        return Set.copyOf(result);
    }
}
