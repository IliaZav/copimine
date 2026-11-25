package me.copimine.dungeon.store;

import me.copimine.dungeon.domain.DungeonRoomDefinition;
import me.copimine.dungeon.domain.GateDefinition;
import me.copimine.dungeon.domain.SecretDefinition;
import me.copimine.dungeon.domain.SpawnAnchor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * In-memory dungeon definition catalogue. Rooms, gates, secrets and spawn
 * anchors live in stable insertion-ordered maps so exports are deterministic
 * and versioned diffs stay small.
 */
public final class DungeonCatalog {

    private final Map<String, DungeonRoomDefinition> rooms = new LinkedHashMap<>();
    private final Map<String, GateDefinition> gates = new LinkedHashMap<>();
    private final Map<String, SecretDefinition> secrets = new LinkedHashMap<>();
    private final Map<String, SpawnAnchor> spawns = new LinkedHashMap<>();

    public Map<String, DungeonRoomDefinition> rooms() {
        return Collections.unmodifiableMap(rooms);
    }

    public Map<String, GateDefinition> gates() {
        return Collections.unmodifiableMap(gates);
    }

    public Map<String, SecretDefinition> secrets() {
        return Collections.unmodifiableMap(secrets);
    }

    public Map<String, SpawnAnchor> spawns() {
        return Collections.unmodifiableMap(spawns);
    }

    public DungeonRoomDefinition room(String id) {
        return rooms.get(id);
    }

    public GateDefinition gate(String id) {
        return gates.get(id);
    }

    public SecretDefinition secret(String id) {
        return secrets.get(id);
    }

    public SpawnAnchor spawn(String id) {
        return spawns.get(id);
    }

    public boolean putRoom(DungeonRoomDefinition room) {
        return rooms.put(room.id(), room) == null;
    }

    public boolean putGate(GateDefinition gate) {
        return gates.put(gate.id(), gate) == null;
    }

    public boolean putSecret(SecretDefinition secret) {
        return secrets.put(secret.id(), secret) == null;
    }

    public boolean putSpawn(SpawnAnchor spawn) {
        return spawns.put(spawn.id(), spawn) == null;
    }

    public DungeonRoomDefinition removeRoom(String id) {
        return rooms.remove(id);
    }

    public GateDefinition removeGate(String id) {
        return gates.remove(id);
    }

    public SecretDefinition removeSecret(String id) {
        return secrets.remove(id);
    }

    public SpawnAnchor removeSpawn(String id) {
        return spawns.remove(id);
    }

    public List<String> roomIds() {
        return new ArrayList<>(rooms.keySet());
    }

    public List<String> gateIds() {
        return new ArrayList<>(gates.keySet());
    }

    public List<String> secretIds() {
        return new ArrayList<>(secrets.keySet());
    }

    public List<String> spawnIds() {
        return new ArrayList<>(spawns.keySet());
    }

    public void rebuildRoomLinks() {
        for (Map.Entry<String, DungeonRoomDefinition> entry : rooms.entrySet()) {
            String roomId = entry.getKey();
            entry.setValue(entry.getValue().toBuilder()
                    .gateIds(gates.values().stream().filter(gate -> gate.roomId().equals(roomId))
                            .map(GateDefinition::id).toList())
                    .secretIds(secrets.values().stream().filter(secret -> secret.roomId().equals(roomId))
                            .map(SecretDefinition::id).toList())
                    .spawnAnchorIds(spawns.values().stream().filter(spawn -> spawn.roomId().equals(roomId))
                            .map(SpawnAnchor::id).toList())
                    .build());
        }
    }

    public void clear() {
        rooms.clear();
        gates.clear();
        secrets.clear();
        spawns.clear();
    }

    public int totalDefinitions() {
        return rooms.size() + gates.size() + secrets.size() + spawns.size();
    }
}
