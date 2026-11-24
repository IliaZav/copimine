package me.copimine.dungeon.command;

import java.util.List;

/**
 * Supplies the real, currently-registered ids used for argument validation and
 * tab completion (spec OV4.3: "tab complete real registered room/gate/enemy/
 * loot IDs and enumerated styles").
 *
 * <p>The production implementation reads the durable {@code DungeonCatalog};
 * tests supply a fixed snapshot.</p>
 */
public interface IdSource {

    List<String> roomIds();

    List<String> gateIds();

    List<String> secretIds();

    List<String> spawnIds();

    List<String> mobTemplateIds();

    List<String> lootTableIds();

    List<String> playerNames();
}
