package me.copimine.endevent.runtime.wave7;

import java.util.List;
import java.util.function.BiConsumer;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.util.Vector;
import me.copimine.endevent.domain.Wave7AbilityEffectPolicy;
import me.copimine.endevent.domain.Wave7AbilityEffectPolicy.Pass;
import me.copimine.endevent.domain.Wave7AbilityEffectPolicy.Shape;

/**
 * Executes the authored Wave 7 ability signatures with vanilla particles.
 *
 * <p>Pure geometry: this class never decides who is hit, never spawns entities
 * and never touches state. The caller hands it a shape and an emitter, so the
 * shapes can be rendered on the server for event audience and unit-tested
 * without a world. Every position is validated first, so a dead actor or a
 * coincident target produces no NaN coordinates in the wire packet.</p>
 */
public final class Wave7AbilityEffectRuntime {
    /** Sends {@code count} particles of {@code particle} with per-point spread. */
    @FunctionalInterface
    public interface Emitter {
        void emit(Particle particle, Location point, int count, double spreadSquared,
                  double extra, Color color);
    }

    private static final double RING_STEP_RADIANS = Math.toRadians(15.0D);

    private Wave7AbilityEffectRuntime() {
    }

    public static boolean render(String abilityId, Location actorEye, Location actorFeet,
                                 Vector aim, Location impact) {
        List<Pass> passes = Wave7AbilityEffectPolicy.passes(abilityId);
        if (passes.isEmpty() || actorFeet == null || actorFeet.getWorld() == null) return false;
        Emitter emitter = (particle, point, count, spread, extra, color) -> {
            try {
                if (color == null) {
                    actorFeet.getWorld().spawnParticle(particle, point, count,
                            0.0D, 0.0D, 0.0D, extra);
                } else {
                    actorFeet.getWorld().spawnParticle(particle, point, count,
                            0.0D, 0.0D, 0.0D, extra, new Particle.DustOptions(color, 1.1F));
                }
            } catch (RuntimeException rejected) {
                // A particle pass must never abort the ability that authored it.
            }
        };
        return render(abilityId, actorEye, actorFeet, aim, impact, emitter);
    }

    /** Render every pass of {@code abilityId}; false when the ability has no authored shape. */
    public static boolean render(String abilityId, Location actorEye, Location actorFeet,
                                 Vector aim, Location impact, Emitter emitter) {
        List<Pass> passes = Wave7AbilityEffectPolicy.passes(abilityId);
        if (passes.isEmpty() || actorFeet == null || actorFeet.getWorld() == null
                || emitter == null || !finite(actorFeet)) return false;
        Vector direction = safeDirection(aim);
        for (Pass pass : passes) {
            Particle primary = particle(pass.particle());
            if (primary == null) continue;
            Color colour = colour(pass.accentParticle());
            renderPass(pass, primary, colour, actorEye == null ? actorFeet : actorEye,
                    actorFeet, direction, impact, emitter);
        }
        return true;
    }

    private static void renderPass(Pass pass, Particle particle, Color colour,
                                   Location eye, Location feet, Vector direction,
                                   Location impact, Emitter emitter) {
        World world = feet.getWorld();
        double extra = pass.spread();
        switch (pass.shape()) {
            case GROUND_RING -> {
                double radius = Math.max(0.25D, pass.radius());
                int steps = Math.max(6, (int) Math.round((Math.PI * 2.0D) / RING_STEP_RADIANS));
                for (int i = 0; i < steps; i++) {
                    double angle = i * (Math.PI * 2.0D / steps);
                    Location point = feet.clone().add(Math.cos(angle) * radius, 0.12D,
                            Math.sin(angle) * radius);
                    if (finite(point)) emitter.emit(particle, point, 1, 0.0D, extra, colour);
                }
            }
            case TARGET_OUTLINE -> {
                Location centre = impact == null || !finite(impact) ? feet : impact;
                double radius = Math.max(0.5D, pass.radius());
                int steps = Math.max(8, (int) Math.round((Math.PI * 2.0D) / RING_STEP_RADIANS));
                for (int i = 0; i < steps; i++) {
                    double angle = i * (Math.PI * 2.0D / steps);
                    Location point = centre.clone().add(Math.cos(angle) * radius, 0.05D,
                            Math.sin(angle) * radius);
                    if (finite(point)) emitter.emit(particle, point, 1, 0.0D, extra, colour);
                }
            }
            case AIM_LANCE -> {
                double length = Math.max(0.5D, pass.extent());
                double radius = Math.max(0.0D, pass.radius());
                int points = Math.max(2, pass.count());
                for (int i = 0; i < points; i++) {
                    double t = length * i / (points - 1.0D);
                    double wobble = radius * Math.sin(t * 2.4D);
                    Location point = eye.clone().add(direction.clone().multiply(t))
                            .add(new Vector(-direction.getZ() * wobble, wobble * 0.4D,
                                    direction.getX() * wobble));
                    if (finite(point)) emitter.emit(particle, point, 1, 0.0D, extra, colour);
                }
            }
            case HELIX -> {
                double radius = Math.max(0.1D, pass.radius());
                double height = Math.max(0.4D, pass.height());
                int points = Math.max(4, pass.count());
                for (int i = 0; i < points; i++) {
                    double t = i / (double) points;
                    double angle = t * Math.PI * 4.0D;
                    Location point = feet.clone()
                            .add(Math.cos(angle) * radius, 0.2D + t * height, Math.sin(angle) * radius);
                    if (finite(point)) emitter.emit(particle, point, 1, 0.0D, extra, colour);
                }
            }
            case IMPACT_BURST -> {
                Location centre = impact == null || !finite(impact) ? feet : impact;
                double radius = Math.max(0.2D, pass.radius());
                double height = Math.max(0.0D, pass.height());
                int points = Math.max(6, pass.count());
                for (int i = 0; i < points; i++) {
                    double t = i / (double) points;
                    double angle = t * Math.PI * 2.0D;
                    Location point = centre.clone().add(Math.cos(angle) * radius, 0.1D + height * t,
                            Math.sin(angle) * radius);
                    if (finite(point)) emitter.emit(particle, point, 1, 0.0D, extra, colour);
                }
            }
            case SELF_AURA -> {
                int points = Math.max(2, pass.count());
                double radius = Math.max(0.05D, pass.radius());
                double height = Math.max(0.2D, pass.height());
                for (int i = 0; i < points; i++) {
                    double angle = i * (Math.PI * 2.0D / points);
                    Location point = feet.clone()
                            .add(Math.cos(angle) * radius, height, Math.sin(angle) * radius);
                    if (finite(point)) emitter.emit(particle, point, 1, 0.0D, extra, colour);
                }
            }
            case PROJECTILE_TRAIL -> {
                Location from = eye.clone();
                int points = Math.max(2, pass.count());
                double length = Math.max(1.0D, pass.extent());
                for (int i = 0; i < points; i++) {
                    Location point = from.clone().add(direction.clone().multiply(length * i / points));
                    if (finite(point)) emitter.emit(particle, point, 1, 0.0D, extra, colour);
                }
            }
        }
    }

    /** Play the authored release sound for one ability. */
    public static void playRelease(World world, Location at, String abilityId, float volume, float pitch) {
        play(world, at, Wave7AbilityEffectPolicy.releaseSound(abilityId), volume, pitch);
    }

    /** Play the authored telegraph cue for one ability. */
    public static void playTelegraph(World world, Location at, String abilityId) {
        play(world, at, Wave7AbilityEffectPolicy.telegraphSound(abilityId), 0.45F, 1.0F);
    }

    private static void play(World world, Location at, String soundName, float volume, float pitch) {
        if (world == null || at == null || soundName == null || soundName.isBlank()) return;
        try {
            Sound sound = Sound.valueOf(soundName);
            world.playSound(at, sound, volume, pitch);
        } catch (IllegalArgumentException unknownSound) {
            // An unresolved key is a silent ability, never a crash.
        }
    }

    /** A shape executor for a caller that only has a level emitter. */
    public static Emitter adapt(BiConsumer<Particle, Location> emit) {
        return (particle, point, count, spreadSquared, extra, color) -> {
            for (int i = 0; i < count; i++) emit.accept(particle, point);
        };
    }

    private static Particle particle(String name) {
        if (name == null || name.isBlank()) return null;
        String trimmed = name.trim().toUpperCase(java.util.Locale.ROOT);
        int underscore = trimmed.lastIndexOf('_');
        if (trimmed.startsWith("DUST_") && underscore > 0) return Particle.DUST;
        try {
            return Particle.valueOf(trimmed);
        } catch (IllegalArgumentException unknownParticle) {
            return null;
        }
    }

    private static Color colour(String accent) {
        if (accent == null) return null;
        return switch (accent.trim().toUpperCase(java.util.Locale.ROOT)) {
            case "DUST_PURPLE" -> Color.fromRGB(148, 96, 226);
            case "DUST_CYAN" -> Color.fromRGB(96, 226, 232);
            case "DUST_RED" -> Color.fromRGB(226, 72, 66);
            case "DUST_WHITE" -> Color.fromRGB(236, 240, 255);
            case "DUST_GREEN" -> Color.fromRGB(110, 236, 150);
            case "DUST_MAGENTA" -> Color.fromRGB(232, 96, 214);
            default -> null;
        };
    }

    private static Vector safeDirection(Vector aim) {
        if (aim == null || !finite(aim) || aim.lengthSquared() < 1.0E-6D) {
            return new Vector(0.0D, 0.0D, 1.0D);
        }
        return aim.clone().normalize();
    }

    private static boolean finite(Location location) {
        return location != null && location.getWorld() != null
                && Double.isFinite(location.getX()) && Double.isFinite(location.getY())
                && Double.isFinite(location.getZ());
    }

    private static boolean finite(Vector vector) {
        return Double.isFinite(vector.getX()) && Double.isFinite(vector.getY())
                && Double.isFinite(vector.getZ());
    }
}
