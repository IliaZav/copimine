package me.copimine.endevent.diagnostics;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Collections;

/** Canonical semantic encounter snapshot used for restart/recovery comparison. */
public record EndRiftDiagnosticSnapshot(
        String eventId,
        long generation,
        String phase,
        int activeWave,
        String objective,
        UUID bossId,
        double bossHealth,
        double bossMaxHealth,
        UUID ritualPrisonerId,
        int ritualCasterCount,
        int ritualGuardCount,
        int ritualProjectileCount,
        int ritualZoneCount,
        int ritualControlCount,
        int ownedEntityCount,
        int activeTaskCount,
        int bossHitboxProxyCount,
        int wave7TemporaryBlockCount,
        int wave7JournalEntryCount
) {
    public Map<String, Object> toFields() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("eventId", eventId == null ? "" : eventId);
        fields.put("generation", generation);
        fields.put("phase", phase == null ? "" : phase);
        fields.put("activeWave", activeWave);
        fields.put("objective", objective == null ? "" : objective);
        fields.put("bossId", bossId);
        fields.put("bossHealth", bossHealth);
        fields.put("bossMaxHealth", bossMaxHealth);
        fields.put("ritualPrisonerId", ritualPrisonerId);
        fields.put("ritualCasterCount", ritualCasterCount);
        fields.put("ritualGuardCount", ritualGuardCount);
        fields.put("ritualProjectileCount", ritualProjectileCount);
        fields.put("ritualZoneCount", ritualZoneCount);
        fields.put("ritualControlCount", ritualControlCount);
        fields.put("ownedEntityCount", ownedEntityCount);
        fields.put("activeTaskCount", activeTaskCount);
        fields.put("bossHitboxProxyCount", bossHitboxProxyCount);
        fields.put("wave7TemporaryBlockCount", wave7TemporaryBlockCount);
        fields.put("wave7JournalEntryCount", wave7JournalEntryCount);
        // A recovery snapshot legitimately has no boss or prisoner.  Keep
        // those null values visible in JSON instead of using Map.copyOf,
        // which rejects null entries and hides the pre-recovery state.
        return Collections.unmodifiableMap(fields);
    }

    public String toJson() {
        return EndRiftDiagnosticJson.toJson(toFields());
    }

    public String semanticHash() {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(toJson().getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                result.append(String.format(java.util.Locale.ROOT, "%02x", value));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("JRE does not provide SHA-256", impossible);
        }
    }
}
