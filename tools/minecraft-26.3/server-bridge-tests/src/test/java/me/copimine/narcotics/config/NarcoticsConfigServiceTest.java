package me.copimine.narcotics.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class NarcoticsConfigServiceTest {
    @Test
    void reloadReadsSavedExactClientVersionPinsWithoutMigratingThem() throws Exception {
        YamlConfiguration persisted = new YamlConfiguration();
        persisted.set("client_bridge.required_client_mod_version", "0.1.2+26.3");

        for (int reload = 0; reload < 2; reload++) {
            YamlConfiguration loaded = new YamlConfiguration();
            loaded.loadFromString(persisted.saveToString());

            assertEquals("0.1.2+26.3", NarcoticsConfigService.readRequiredClientModVersion(loaded));
            persisted = loaded;
        }
    }
}
