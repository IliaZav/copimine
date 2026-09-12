package me.copimine.endevent.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Canonical wire catalogue for the End Rift guardian animation state.
 *
 * The server and the client are separate projects, so the client contains the
 * same catalogue.  Keeping aliases here is intentional: the original artist
 * files use names such as {@code Running2} and {@code udar_po_zemle2}, while
 * the network protocol uses stable, upper-case identifiers.
 */
public enum BossAnimationId {
    IDLE_BREATH("IDLE_BREATH", "IDLE", "IDLE_BREATH"),
    RUN("RUN", "RUNNING", "RUNNING2", "WALK"),
    MELEE_SWIPE("MELEE_SWIPE", "SWIPE", "SWIPE2", "MELEE"),
    CHEST_STRIKE("CHEST_STRIKE", "CHEST_PROJECTILE", "UDAR_IZ_GRUDI"),
    GROUND_SLAM("GROUND_SLAM", "UDAR_PO_ZEMLE", "UDAR_PO_ZEMLE2"),
    MARK_CONTROL("MARK_CONTROL", "MARK", "CONTROL_MARK"),
    SUMMON_CHANNEL("SUMMON_CHANNEL", "SUMMON_CHANNELING"),
    HURT("HURT", "HURT2", "DAMAGED_FLINCH"),
    PHASE_TRANSITION("PHASE_TRANSITION", "PHASE_SHIFT"),
    FINAL_STRIKE("FINAL_STRIKE"),
    DYING("DYING", "DYING2", "DEFEAT_COLLAPSE"),
    TELEPORT_RIP("TELEPORT_RIP", "TELEPORT"),
    CAST_CHARGE("CAST_CHARGE", "TELEGRAPHING"),
    CAST_RELEASE("CAST_RELEASE", "EXECUTING"),
    CAST_IMPACT("CAST_IMPACT", "SPELL_IMPACT"),
    RECOVERY("RECOVERY"),
    SPELL_VOID_BLAST("SPELL_VOID_BLAST"),
    SPELL_RIFT_PROJECTILE("SPELL_RIFT_PROJECTILE"),
    SPELL_RIFT_ARROWS("SPELL_RIFT_ARROWS"),
    SPELL_ARROW_SALVO("SPELL_ARROW_SALVO"),
    SPELL_VOID_MARK("SPELL_VOID_MARK"),
    SPELL_SUMMON_SERVANTS("SPELL_SUMMON_SERVANTS"),
    SPELL_SUMMON("SPELL_SUMMON"),
    SPELL_RIFT_OBELISKS("SPELL_RIFT_OBELISKS"),
    SPELL_ARENA_INFERNO("SPELL_ARENA_INFERNO"),
    UNKNOWN("UNKNOWN"),
    NONE("NONE");

    private static final Map<String, BossAnimationId> BY_ALIAS = buildAliasIndex();

    private final String wireId;
    private final String[] aliases;

    BossAnimationId(String wireId, String... aliases) {
        this.wireId = wireId;
        this.aliases = aliases;
    }

    public String wireId() {
        return wireId;
    }

    /** Returns the canonical identifier, or UNKNOWN for a malformed value. */
    public static BossAnimationId fromWire(String value) {
        if (value == null || value.isBlank()) {
            return IDLE_BREATH;
        }
        BossAnimationId result = BY_ALIAS.get(normalize(value));
        return result == null ? UNKNOWN : result;
    }

    public static String canonicalWireId(String value) {
        return fromWire(value).wireId();
    }

    public static boolean isKnown(String value) {
        return value != null && !value.isBlank() && BY_ALIAS.containsKey(normalize(value));
    }

    public static Map<String, BossAnimationId> aliases() {
        return BY_ALIAS;
    }

    private static Map<String, BossAnimationId> buildAliasIndex() {
        LinkedHashMap<String, BossAnimationId> result = new LinkedHashMap<>();
        for (BossAnimationId id : values()) {
            putAlias(result, id.wireId, id);
            for (String alias : id.aliases) {
                putAlias(result, alias, id);
            }
        }
        return Collections.unmodifiableMap(result);
    }

    private static void putAlias(Map<String, BossAnimationId> result, String alias,
                                 BossAnimationId id) {
        String normalized = normalize(alias);
        BossAnimationId previous = result.put(normalized, id);
        if (previous != null && previous != id) {
            throw new IllegalStateException("duplicate boss animation alias: " + normalized);
        }
    }

    private static String normalize(String value) {
        return value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }
}
