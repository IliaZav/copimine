package me.copimine.dungeon.editor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Owns live editor sessions and the durable builder grant list (spec OV4.3).
 *
 * <p>Session lifecycle is explicit: dropping the wand, unequipping, death,
 * logout, world change or a permission revoke must tear the session down so no
 * overlay or subscription survives. The grant list is the authoritative build
 * permission record that {@code editor grant|revoke|list} manipulates.</p>
 */
public final class EditorSessionManager {

    private final Map<UUID, EditorSession> sessions = new LinkedHashMap<>();
    private final Map<String, String> grants = new LinkedHashMap<>();

    public EditorSession open(UUID actorId, String actorName) {
        Objects.requireNonNull(actorId, "actorId");
        return sessions.computeIfAbsent(actorId, id -> new EditorSession(id, actorName));
    }

    public EditorSession session(UUID actorId) {
        return sessions.get(actorId);
    }

    public boolean hasSession(UUID actorId) {
        return sessions.containsKey(actorId);
    }

    /** Tears the session down; returns the removed session or {@code null}. */
    public EditorSession close(UUID actorId) {
        return sessions.remove(actorId);
    }

    public void closeAll() {
        sessions.clear();
    }

    public Collection<EditorSession> sessions() {
        return List.copyOf(sessions.values());
    }

    public void grant(String actorKey, String scope) {
        Objects.requireNonNull(actorKey, "actorKey");
        grants.put(actorKey, scope == null || scope.isBlank() ? "global" : scope.trim());
    }

    /** Revokes the grant and tears down any matching live session. */
    public boolean revoke(UUID actorId, String actorKey) {
        if (actorId != null) {
            close(actorId);
        }
        return actorKey != null && grants.remove(actorKey) != null;
    }

    public boolean isGranted(String actorKey) {
        return actorKey != null && grants.containsKey(actorKey);
    }

    public String scopeOf(String actorKey) {
        return grants.get(actorKey);
    }

    public List<String> grantLines() {
        List<String> lines = new ArrayList<>(grants.size());
        for (Map.Entry<String, String> entry : grants.entrySet()) {
            lines.add(entry.getKey() + " -> " + entry.getValue());
        }
        return lines;
    }

    public int grantCount() {
        return grants.size();
    }
}
