package me.copimine.dungeon.runtime;

import me.copimine.dungeon.domain.SecretTrigger.Action;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Switch;

public final class BukkitSecretTrigger {
    private BukkitSecretTrigger() {}

    public static boolean compatible(Action action, Block block) {
        return switch (action) {
            case AUTO -> true;
            case RUNE, HIT -> block.getType().isSolid();
            case BUTTON -> Tag.BUTTONS.isTagged(block.getType());
            case LEVER -> block.getType() == Material.LEVER && block.getBlockData() instanceof Switch;
            case PLATE -> Tag.PRESSURE_PLATES.isTagged(block.getType());
            case TARGET -> block.getType() == Material.TARGET;
        };
    }
}
