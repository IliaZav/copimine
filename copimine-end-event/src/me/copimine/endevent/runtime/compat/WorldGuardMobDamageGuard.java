package me.copimine.endevent.runtime.compat;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

/**
 * Optional WorldGuard query for direct player effects that do not emit a Bukkit damage event.
 * This class deliberately uses reflection for WorldGuard so the plugin still loads without it.
 */
public final class WorldGuardMobDamageGuard {
    private static final String WORLD_GUARD_PLUGIN = "WorldGuard";
    private static final AtomicBoolean FAILURE_WARNING_EMITTED = new AtomicBoolean();

    private WorldGuardMobDamageGuard() {
    }

    /**
     * Returns whether an End Rift mob effect may affect the player under WorldGuard's MOB_DAMAGE flag.
     *
     * <p>If WorldGuard is not installed, the existing event behavior is preserved. Once the plugin is
     * present, a disabled plugin, missing API, failed query, or malformed response blocks the effect.</p>
     */
    public static boolean isMobDamageAllowed(
            Player player,
            PluginManager pluginManager,
            Consumer<String> warningSink
    ) {
        if (pluginManager == null) {
            return failClosed(warningSink, "Bukkit PluginManager is unavailable", null);
        }

        final Plugin worldGuard;
        try {
            worldGuard = pluginManager.getPlugin(WORLD_GUARD_PLUGIN);
        } catch (RuntimeException exception) {
            return failClosed(warningSink, "could not inspect installed WorldGuard", exception);
        }
        if (worldGuard == null) {
            return true;
        }
        try {
            if (player == null || !worldGuard.isEnabled()) {
                return failClosed(warningSink, "WorldGuard is present but unavailable for a region query", null);
            }
            return queryMobDamage(worldGuard, player);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            return failClosed(warningSink, "WorldGuard MOB_DAMAGE query failed", exception);
        }
    }

    private static boolean queryMobDamage(Plugin worldGuard, Player player) throws ReflectiveOperationException {
        ClassLoader worldGuardLoader = worldGuard.getClass().getClassLoader();
        Class<?> worldGuardType = Class.forName("com.sk89q.worldguard.WorldGuard", true, worldGuardLoader);
        Object instance = worldGuardType.getMethod("getInstance").invoke(null);
        Object platform = worldGuardType.getMethod("getPlatform").invoke(instance);
        Class<?> platformType = Class.forName(
                "com.sk89q.worldguard.internal.platform.WorldGuardPlatform", true, worldGuardLoader);
        Object container = platformType.getMethod("getRegionContainer").invoke(platform);
        Class<?> containerType = Class.forName(
                "com.sk89q.worldguard.protection.regions.RegionContainer", true, worldGuardLoader);
        Object query = containerType.getMethod("createQuery").invoke(container);

        Class<?> adapterType = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter", true, worldGuardLoader);
        Location bukkitLocation = player.getLocation();
        Object worldGuardLocation = adapterType.getMethod("adapt", Location.class).invoke(null, bukkitLocation);
        Class<?> worldGuardPluginType = Class.forName(
                "com.sk89q.worldguard.bukkit.WorldGuardPlugin", true, worldGuardLoader);
        Object localPlayer = worldGuardPluginType.getMethod("wrapPlayer", Player.class).invoke(worldGuard, player);

        Class<?> flagsType = Class.forName(
                "com.sk89q.worldguard.protection.flags.Flags", true, worldGuardLoader);
        Object mobDamageFlag = flagsType.getField("MOB_DAMAGE").get(null);
        Class<?> regionQueryType = Class.forName(
                "com.sk89q.worldguard.protection.regions.RegionQuery", true, worldGuardLoader);
        Class<?> locationType = Class.forName("com.sk89q.worldedit.util.Location", true, worldGuardLoader);
        Class<?> regionAssociableType = Class.forName(
                "com.sk89q.worldguard.protection.association.RegionAssociable", true, worldGuardLoader);
        Class<?> stateFlagType = Class.forName(
                "com.sk89q.worldguard.protection.flags.StateFlag", true, worldGuardLoader);
        Class<?> flagArrayType = Array.newInstance(stateFlagType, 0).getClass();
        Method testState = regionQueryType.getMethod(
                "testState", locationType, regionAssociableType, flagArrayType);
        Object flags = Array.newInstance(stateFlagType, 1);
        Array.set(flags, 0, mobDamageFlag);

        Object result = testState.invoke(query, worldGuardLocation, localPlayer, flags);
        if (!(result instanceof Boolean allowed)) {
            throw new IllegalStateException("WorldGuard RegionQuery.testState returned a non-boolean result");
        }
        return allowed;
    }

    private static boolean failClosed(Consumer<String> warningSink, String reason, Throwable failure) {
        if (FAILURE_WARNING_EMITTED.compareAndSet(false, true) && warningSink != null) {
            String details = failure == null || failure.getMessage() == null
                    ? ""
                    : ": " + failure.getMessage();
            try {
                warningSink.accept(reason + details + "; blocking the elite Web Shot effect");
            } catch (RuntimeException ignored) {
                // Logging must not let a protected effect through or break the projectile listener.
            }
        }
        return false;
    }
}
