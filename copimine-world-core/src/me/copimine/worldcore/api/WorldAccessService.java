package me.copimine.worldcore.api;

import org.bukkit.World;

public interface WorldAccessService {
    WorldAccessResult setEndEnabled(boolean enabled, String actor, String idempotencyKey);

    boolean isEndEnabled();

    boolean isEndWorld(World world);
}
