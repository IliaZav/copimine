package me.copimine.endevent;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;

/** Presentation for one already-warned Inferno eruption; never owns damage or timing. */
public final class ArenaInfernoGeyserEffects {
    private static final NativeGeyser NATIVE = resolveNativeGeyser();

    private ArenaInfernoGeyserEffects() {}

    /**
     * Sends one finite burst and one positional cue to a previously selected event
     * viewer. The caller owns event/proximity filtering and calls this only in ERUPT.
     * Returns the actual component count for event particle accounting.
     */
    public static int erupt(Player viewer, Location point) {
        if (viewer == null || point == null || point.getWorld() == null
                || !viewer.isOnline() || !viewer.getWorld().equals(point.getWorld())) {
            return 0;
        }
        if (NATIVE != null) {
            // The GEYSER emitter creates roughly 120 children over 20 client ticks.
            // Direct components keep this one-shot burst at five particles instead.
            viewer.spawnParticle(NATIVE.plume(), point, 3, 0.0D, 0.0D, 0.0D, 0.0D,
                    NATIVE.plumeData());
            viewer.spawnParticle(NATIVE.base(), point, 2, 0.0D, 0.0D, 0.0D, 0.0D,
                    NATIVE.baseData());
            viewer.playSound(point, "minecraft:block.potent_sulfur.geyser_eruption",
                    SoundCategory.HOSTILE, 0.8F, 0.7F);
            return 5;
        }
        viewer.spawnParticle(Particle.CLOUD, point, 24, 0.4D, 0.6D, 0.4D, 0.05D);
        viewer.playSound(point, "minecraft:block.fire.extinguish",
                SoundCategory.HOSTILE, 0.8F, 0.7F);
        return 24;
    }

    private static NativeGeyser resolveNativeGeyser() {
        Particle plume;
        Particle base;
        try {
            // This shared source also compiles/runs with the pinned 1.21.1 API.
            plume = Particle.valueOf("GEYSER_PLUME");
            base = Particle.valueOf("GEYSER_BASE");
        } catch (IllegalArgumentException unavailableOnLegacyApi) {
            return null;
        }
        try {
            // Paper 26.3 requires Geyser(int) and GeyserBase(int,float) payloads.
            // Resolve them once through the particle metadata, without linking new
            // API classes into the legacy build. One water block gives a 5-block plume.
            Object plumeData = plume.getDataType().getConstructor(int.class).newInstance(1);
            Object baseData = base.getDataType().getConstructor(int.class, float.class)
                    .newInstance(1, 1.5F);
            return new NativeGeyser(plume, plumeData, base, baseData);
        } catch (ReflectiveOperationException | SecurityException incompatibleApi) {
            Logger.getLogger(ArenaInfernoGeyserEffects.class.getName()).log(Level.WARNING,
                    "Native Inferno geyser particle data is incompatible; using the bounded cloud fallback",
                    incompatibleApi);
            return null;
        }
    }

    private record NativeGeyser(Particle plume, Object plumeData, Particle base, Object baseData) {}
}
