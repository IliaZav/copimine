package me.copimine.dungeon.command;

import me.copimine.dungeon.domain.DungeonRoomDefinition;
import me.copimine.dungeon.domain.GateDefinition;
import me.copimine.dungeon.domain.SecretDefinition;
import me.copimine.dungeon.domain.SpawnAnchor;
import me.copimine.dungeon.store.DungeonCatalog;
import me.copimine.dungeon.store.DungeonDefinitionStore;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * {@link IdSource} backed by the live durable catalogue plus the externally
 * registered loot-table ids and known player names. This is what tab completion
 * reads so the builder only ever sees real registered ids.
 */
public final class CatalogIdSource implements IdSource {

    private final DungeonDefinitionStore store;
    private final Supplier<List<String>> lootTables;
    private final Supplier<List<String>> playerNames;

    public CatalogIdSource(DungeonDefinitionStore store,
                           Supplier<List<String>> lootTables,
                           Supplier<List<String>> playerNames) {
        this.store = store;
        this.lootTables = lootTables == null ? List::of : lootTables;
        this.playerNames = playerNames == null ? List::of : playerNames;
    }

    private DungeonCatalog catalog() {
        return store.catalog();
    }

    @Override
    public List<String> roomIds() {
        return catalog().roomIds();
    }

    @Override
    public List<String> gateIds() {
        return catalog().gateIds();
    }

    @Override
    public List<String> secretIds() {
        return catalog().secretIds();
    }

    @Override
    public List<String> spawnIds() {
        return catalog().spawnIds();
    }

    @Override
    public List<String> mobTemplateIds() {
        return me.copimine.dungeon.domain.MobTemplate.allIds();
    }

    @Override
    public List<String> lootTableIds() {
        List<String> ids = new ArrayList<>();
        for (DungeonRoomDefinition room : catalog().rooms().values()) {
            String table = room.reward().lootTableId();
            if (table != null && !table.isBlank() && !ids.contains(table)) {
                ids.add(table);
            }
        }
        for (String table : lootTables.get()) {
            if (table != null && !table.isBlank() && !ids.contains(table)) {
                ids.add(table);
            }
        }
        return ids;
    }

    @Override
    public List<String> playerNames() {
        return playerNames.get();
    }
}
