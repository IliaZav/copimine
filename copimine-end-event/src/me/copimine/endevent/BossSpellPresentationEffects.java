package me.copimine.endevent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import me.copimine.endevent.domain.BossSpellGeometryPolicy;
import me.copimine.endevent.domain.BossSpellParticlePalette;
import me.copimine.endevent.domain.BossSpellVisualCompositionPolicy;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

/**
 * Presentation only. One frame is transformed once, then shared by bounded
 * viewers.
 *
 * <p>The frame carries two reads at once. The body of every silhouette is
 * drawn in the spell's own dust colour, so the shape stays legible and can
 * never hide the attack; the sparse bright nodes carry the authored particle
 * family, so a bone volley, a void rupture and a Kagune eruption are distinct
 * at a glance instead of sharing one grey dust. Nothing here owns damage,
 * collision or a gameplay volume.</p>
 */
final class BossSpellPresentationEffects {
    /** Hard ceiling on admitted viewers per frame; the budget, not a hint. */
    private static final int MAX_VIEWERS = 32;
    private static final double TAU = Math.PI * 2.0D;

    private BossSpellPresentationEffects() { }

    /** Dust-only frame, used by callers with no authored composition. */
    static void frame(Location anchor, Vector facing, List<BossSpellGeometryPolicy.Point> points,
            int rgb, List<Player> viewers, BiPredicate<Player, Location> allowed) {
        frame(anchor, facing, points, rgb, null, 0.0D, viewers, allowed);
    }

    /**
     * One authored frame.
     *
     * @param composition authored spell signature; null keeps the dust body
     * @param progress  0..1 charge progress, drives the authored vortex twist
     */
    static void frame(Location anchor, Vector facing, List<BossSpellGeometryPolicy.Point> points,
            int rgb, BossSpellVisualCompositionPolicy.Composition composition, double progress,
            List<Player> viewers, BiPredicate<Player, Location> allowed) {
        if (anchor == null || anchor.getWorld() == null || points == null || points.isEmpty()) return;
        Vector forward = facing == null ? new Vector(0, 0, 1) : facing.clone().setY(0);
        if (!Double.isFinite(forward.lengthSquared()) || forward.lengthSquared() < 0.0001)
            forward = new Vector(0, 0, 1);
        forward.normalize();
        Vector side = new Vector(forward.getZ(), 0, -forward.getX());
        // The authored vortex: a converging tell literally spirals, and its
        // number of turns is the number the author chose for this spell.
        double twist = 0.0D;
        if (composition != null && composition.vortexTurns() > 0.0F && Double.isFinite(progress)) {
            twist = Math.clamp(progress, 0.0D, 1.0D) * composition.vortexTurns() * TAU;
        }
        double cos = Math.cos(twist), sin = Math.sin(twist);
        List<Location> positions = new ArrayList<>(points.size());
        for (var point : points) {
            // Rotate the local (side, forward) pair about the vertical axis so
            // the whole silhouette turns as one rigid body, not point by point.
            double localSide = point.side() * cos - point.forward() * sin;
            double localForward = point.side() * sin + point.forward() * cos;
            positions.add(anchor.clone().add(
                    side.getX() * localSide + forward.getX() * localForward, point.up(),
                    side.getZ() * localSide + forward.getZ() * localForward));
        }
        Color color = Color.fromRGB(rgb & 0xFFFFFF);
        Particle.DustOptions bodyDust = new Particle.DustOptions(color, 0.85F);
        Particle.DustOptions innerDust = new Particle.DustOptions(Color.fromRGB(
                (color.getRed() + 224) / 2, (color.getGreen() + 224) / 2,
                (color.getBlue() + 255) / 2), 0.65F);
        // The bright-node carrier: the authored particle family where the
        // spell names a real one, otherwise the old END_ROD highlight. This
        // replaces the previous END_ROD slot one-for-one, so the budget is
        // unchanged while the signature becomes spell-specific.
        BossSpellParticlePalette.Signature nodeSignature = nodeSignature(composition, rgb);
        int admitted = 0;
        for (Player viewer : viewers) {
            if (viewer == null || !viewer.isOnline() || !viewer.getWorld().equals(anchor.getWorld())
                    || !allowed.test(viewer, anchor)) continue;
            int stride = BossSpellGeometryPolicy.stride(viewer.getLocation().distanceSquared(anchor), viewers.size());
            if (stride == 0) continue;
            if (++admitted > MAX_VIEWERS) break;
            for (int i = 0; i < points.size(); i += stride) {
                var point = points.get(i);
                // Bright nodes stay sparse; a 96-point all-signature cage hides the attack.
                if (point.layer() == 2 && i % 4 == 0) {
                    spawn(viewer, positions.get(i), nodeSignature, bodyDust);
                } else {
                    viewer.spawnParticle(Particle.DUST, positions.get(i), 1, 0, 0, 0, 0,
                            point.layer() == 0 ? bodyDust : innerDust);
                }
            }
        }
    }

    /**
     * Pick the bright-node particle.
     *
     * <p>Prefer a DENSE authored particle over a SPARSE one: a soul wisp or a
     * flame reads as a body, while a sweep arc, a sonic boom or a flash is a
     * single one-shot read that must not be stamped on every node. Dust
     * accents fall through to the primary, and two dust accents fall back to
     * END_ROD, which is exactly the pre-existing behaviour for a spell with no
     * distinct particle.</p>
     */
    private static BossSpellParticlePalette.Signature nodeSignature(
            BossSpellVisualCompositionPolicy.Composition composition, int rgb) {
        if (composition != null) {
            BossSpellParticlePalette.Signature primary =
                    BossSpellParticlePalette.resolve(composition.primaryParticle(), rgb);
            BossSpellParticlePalette.Signature accent =
                    BossSpellParticlePalette.resolve(composition.accentParticle(), rgb);
            BossSpellParticlePalette.Signature best = null;
            int bestRank = 0;
            for (BossSpellParticlePalette.Signature candidate : new BossSpellParticlePalette.Signature[]{
                    primary, accent}) {
                int rank = BossSpellParticlePalette.nodeRank(candidate);
                if (rank > bestRank) {
                    bestRank = rank;
                    best = candidate;
                }
            }
            if (best != null) return best;
        }
        return BossSpellParticlePalette.resolve("END_ROD", rgb);
    }

    /** Spawn one resolved signature, supplying the payload Bukkit demands. */
    private static void spawn(Player viewer, Location at,
            BossSpellParticlePalette.Signature signature, Particle.DustOptions dust) {
        Particle particle = particle(signature.particle());
        if (particle == null) {
            viewer.spawnParticle(Particle.DUST, at, 1, 0, 0, 0, 0, dust);
            return;
        }
        switch (signature.payload()) {
            case NONE -> viewer.spawnParticle(particle, at, 1, 0, 0, 0, 0);
            case FLOAT -> viewer.spawnParticle(particle, at, 1, 0, 0, 0, 0,
                    Float.valueOf(signature.scale()));
            case INTEGER -> viewer.spawnParticle(particle, at, 1, 0, 0, 0, 0, Integer.valueOf(0));
            case BLOCK -> viewer.spawnParticle(particle, at, 1, 0, 0, 0, 0,
                    Material.STONE.createBlockData());
            // A signature with a dust payload paints the authored colour, not
            // the shape's body colour, so an authored accent keeps its own hue.
            case DUST -> viewer.spawnParticle(particle, at, 1, 0, 0, 0, 0,
                    new Particle.DustOptions(Color.fromRGB(signature.rgb()), signature.scale()));
            // A colour-payload particle (e.g. FLASH) must be handed its Colour;
            // spawning it bare throws "missing required data class".
            case COLOR -> viewer.spawnParticle(particle, at, 1, 0, 0, 0, 0,
                    Color.fromRGB(signature.rgb()));
        }
    }

    /** Resolve a palette name to a real Bukkit particle, or null to keep dust. */
    private static Particle particle(String name) {
        if (name == null || name.isBlank()) return null;
        try {
            return Particle.valueOf(name.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }

    static void impact(JavaPlugin plugin, LivingEntity boss, BossSpellGeometryPolicy.Shape shape,
            Location origin, Vector facing, double radius, int rgb,
            BossSpellVisualCompositionPolicy.Composition composition, BooleanSupplier owned,
            Supplier<List<Player>> audience, BiPredicate<Player, Location> allowed,
            Consumer<BukkitTask> register) {
        if (boss == null || origin == null || origin.getWorld() == null || shape == null) return;
        Location anchor = origin.clone();
        Vector direction = facing.clone();
        BukkitTask task = new BukkitRunnable() {
            int step;
            @Override public void run() {
                if (!plugin.isEnabled() || !owned.getAsBoolean() || !boss.isValid() || boss.isDead()
                        || !boss.getWorld().equals(anchor.getWorld()) || step >= 6) {cancel(); return;}
                double progress = step / 5.0;
                frame(anchor, direction, BossSpellGeometryPolicy.sample(shape,
                        BossSpellGeometryPolicy.Beat.IMPACT, progress, radius), rgb,
                        composition, progress, audience.get(), allowed);
                // The release needs a footprint, not just a body: a bounded
                // expanding ground ring tells the group exactly how far the hit
                // reached and where it already stopped, which is the read players
                // use to step back in. Three frames only, fixed point count.
                if (step <= 2) {
                    shockRing(anchor, rgb, progress, audience.get(), allowed);
                }
                if (++step >= 6) cancel();
            }
        }.runTaskTimer(plugin, 0, 2);
        register.accept(task);
    }

    /**
     * Bounded expanding ground ring for the release moment.
     *
     * <p>Fixed 18-point circumference and the same {@link #MAX_VIEWERS} ceiling
     * as every other layer, so the shockwave costs three frames of one ring and
     * never scales with the party size. The ring uses the composition's hot
     * impact colour, so a bone volley and a Kagune eruption release in their own
     * hue rather than the shared white flash this replaces.</p>
     */
    private static void shockRing(Location center, int rgb, double progress,
            List<Player> viewers, BiPredicate<Player, Location> allowed) {
        if (center == null || center.getWorld() == null || viewers == null) return;
        double radius = 0.5D + Math.clamp(progress, 0.0D, 1.0D) * 3.5D;
        int points = 18;
        Color hot = Color.fromRGB(BossSpellVisualCompositionPolicy.impactColor(rgb));
        Particle.DustOptions dust = new Particle.DustOptions(hot, 1.05F);
        int admitted = 0;
        for (Player viewer : viewers) {
            if (viewer == null || !viewer.isOnline() || !viewer.getWorld().equals(center.getWorld())
                    || !allowed.test(viewer, center)) continue;
            if (++admitted > MAX_VIEWERS) break;
            for (int i = 0; i < points; i++) {
                double a = i * TAU / points;
                Location at = center.clone().add(Math.cos(a) * radius, 0.12D, Math.sin(a) * radius);
                viewer.spawnParticle(Particle.DUST, at, 1, 0, 0, 0, 0, dust);
            }
        }
    }
}
