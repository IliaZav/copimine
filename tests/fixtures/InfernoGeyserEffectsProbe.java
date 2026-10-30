package me.copimine.endevent;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Player;

/** Captures the real adapter's Bukkit packet calls; usable with both pinned APIs. */
public final class InfernoGeyserEffectsProbe {
    private record Emission(Particle particle, Location point, int count,
                            double x, double y, double z, double extra, Object data) {}
    private record Cue(Location point, String id, SoundCategory category, float volume, float pitch) {}

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static World world() {
        return (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[]{World.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" -> "GeyserProbeWorld";
                    default -> throw new AssertionError("Unexpected world mutation/query: " + method);
                });
    }

    private static final class Sink {
        private final World world;
        private final List<Emission> particles = new ArrayList<>();
        private final List<Cue> sounds = new ArrayList<>();
        private boolean online = true;
        private boolean rejectParticle;
        private final Player player;

        private Sink(World world) {
            this.world = world;
            this.player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(),
                    new Class<?>[]{Player.class}, (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "isOnline": return online;
                            case "getWorld": return this.world;
                            case "equals": return proxy == args[0];
                            case "hashCode": return System.identityHashCode(proxy);
                            case "toString": return "GeyserProbePlayer";
                            case "spawnParticle":
                                if (rejectParticle) throw new IllegalStateException("packet-send-rejected");
                                Particle particle = (Particle) args[0];
                                Object data = args.length == 8 ? args[7] : null;
                                require(particle.getDataType() == Void.class
                                                || data != null && particle.getDataType().isInstance(data),
                                        "Particle packet has missing/wrong payload for " + particle);
                                particles.add(new Emission(particle, (Location) args[1], (Integer) args[2],
                                        (Double) args[3], (Double) args[4], (Double) args[5],
                                        (Double) args[6], data));
                                return null;
                            case "playSound":
                                require(args[1] instanceof String, "Sound must use a legacy-safe namespaced key");
                                sounds.add(new Cue((Location) args[0], (String) args[1],
                                        (SoundCategory) args[2], (Float) args[3], (Float) args[4]));
                                return null;
                            default: throw new AssertionError("Effect changed gameplay or made an unbounded call: " + method);
                        }
                    });
        }
    }

    private static int erupt(Method method, Player viewer, Location point) throws Exception {
        try {
            return (Integer) method.invoke(null, viewer, point);
        } catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof Exception cause) throw cause;
            if (failure.getCause() instanceof Error cause) throw cause;
            throw failure;
        }
    }

    private static void verifyEruption(Method method, boolean nativeApi) throws Exception {
        World world = world();
        Location point = new Location(world, 10.5, 70, -3.5);
        Sink sink = new Sink(world);
        int count = erupt(method, sink.player, point);
        require(count == (nativeApi ? 5 : 24), "Eruption did not respect its bounded particle count");
        require(sink.particles.size() == (nativeApi ? 2 : 1), "Unexpected particle packets or duplicate fallback");
        require(sink.sounds.size() == 1, "Eruption must play exactly one sound");
        Cue cue = sink.sounds.get(0);
        require(cue.point == point && cue.category == SoundCategory.HOSTILE && cue.volume > 0 && cue.pitch > 0,
                "Eruption sound changed location/category or is inaudible");
        require(cue.id.equals(nativeApi ? "minecraft:block.potent_sulfur.geyser_eruption"
                : "minecraft:block.fire.extinguish"), "Wrong eruption sound for the available API");
        int emitted = 0;
        for (Emission emission : sink.particles) {
            require(emission.point == point, "Particle origin moved away from the fixed warned vent");
            emitted += emission.count;
            if (nativeApi) {
                require(emission.x == 0 && emission.y == 0 && emission.z == 0 && emission.extra == 0,
                        "Native geyser component gained random spread or extra velocity");
                require((Integer) emission.data.getClass().getMethod("getWaterBlocks").invoke(emission.data) == 1,
                        "Native plume is oversized");
                if (emission.particle.name().equals("GEYSER_PLUME")) {
                    require(emission.count == 3, "Wrong bounded plume count");
                } else {
                    require(emission.particle.name().equals("GEYSER_BASE") && emission.count == 2,
                            "A cascading geyser emitter or wrong component was sent");
                    require((Float) emission.data.getClass().getMethod("getBurstImpulse").invoke(emission.data) == 1.5F,
                            "Wrong native base impulse payload");
                }
            } else {
                require(emission.particle == Particle.CLOUD && emission.data == null,
                        "Legacy API did not retain the existing cloud fallback");
                require(emission.x == 0.4D && emission.y == 0.6D && emission.z == 0.4D && emission.extra == 0.05D,
                        "Legacy cloud appearance changed");
            }
        }
        require(emitted == count, "Particle accounting differs from actual component emissions");
        erupt(method, sink.player, point);
        require(sink.sounds.size() == 2, "A repeated distinct eruption lost/duplicated its cue");
    }

    private static void verifyInvalidViewers(Method method) throws Exception {
        World world = world();
        Location point = new Location(world, 0, 70, 0);
        Sink sink = new Sink(world);
        require(erupt(method, null, point) == 0, "Null viewer emitted effects");
        require(erupt(method, sink.player, null) == 0, "Missing vent emitted effects");
        require(erupt(method, sink.player, new Location(null, 0, 0, 0)) == 0, "Missing world emitted effects");
        sink.online = false;
        require(erupt(method, sink.player, point) == 0, "Offline viewer received effects");
        sink.online = true;
        require(erupt(method, sink.player, new Location(world(), 0, 70, 0)) == 0,
                "Other-world viewer received effects");
        require(sink.particles.isEmpty() && sink.sounds.isEmpty(), "Rejected audience leaked packets/audio");
    }

    private static void verifyUnexpectedSendFailureIsVisible(Method method) throws Exception {
        World world = world();
        Sink sink = new Sink(world);
        sink.rejectParticle = true;
        try {
            erupt(method, sink.player, new Location(world, 0, 70, 0));
            throw new AssertionError("Unexpected packet failure was silently treated as legacy capability absence");
        } catch (IllegalStateException expected) {
            require(expected.getMessage().equals("packet-send-rejected"), "Different failure was substituted");
        }
        require(sink.particles.isEmpty() && sink.sounds.isEmpty(), "Failed send emitted duplicate fallback/audio");
    }

    public static void main(String[] args) throws Exception {
        Class<?> adapter;
        try {
            adapter = Class.forName("me.copimine.endevent.ArenaInfernoGeyserEffects");
        } catch (ClassNotFoundException missing) {
            throw new AssertionError("Inferno eruption cannot emit typed native geyser components: adapter is missing", missing);
        }
        Method method = adapter.getMethod("erupt", Player.class, Location.class);
        verifyEruption(method, args[0].equals("native"));
        verifyInvalidViewers(method);
        verifyUnexpectedSendFailureIsVisible(method);
        System.out.println("PASS " + args[0] + ": typed bounded eruption, one sound, audience guards and visible send failure");
    }
}
