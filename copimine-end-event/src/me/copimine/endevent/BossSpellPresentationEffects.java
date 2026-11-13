package me.copimine.endevent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import me.copimine.endevent.domain.BossSpellGeometryPolicy;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

/** Presentation only. One frame is transformed once, then shared by bounded viewers. */
final class BossSpellPresentationEffects {
    private BossSpellPresentationEffects() { }

    static void frame(Location anchor, Vector facing, List<BossSpellGeometryPolicy.Point> points,
            int rgb, List<Player> viewers, BiPredicate<Player, Location> allowed) {
        if (anchor == null || anchor.getWorld() == null || points == null || points.isEmpty()) return;
        Vector forward = facing == null ? new Vector(0, 0, 1) : facing.clone().setY(0);
        if (!Double.isFinite(forward.lengthSquared()) || forward.lengthSquared() < 0.0001)
            forward = new Vector(0, 0, 1);
        forward.normalize();
        Vector side = new Vector(forward.getZ(), 0, -forward.getX());
        List<Location> positions = new ArrayList<>(points.size());
        for (var point : points) positions.add(anchor.clone().add(
                side.getX() * point.side() + forward.getX() * point.forward(), point.up(),
                side.getZ() * point.side() + forward.getZ() * point.forward()));
        Color color = Color.fromRGB(rgb);
        Particle.DustOptions primary = new Particle.DustOptions(color, 0.85F);
        Particle.DustOptions accent = new Particle.DustOptions(Color.fromRGB(
                (color.getRed() + 224) / 2, (color.getGreen() + 224) / 2,
                (color.getBlue() + 255) / 2), 0.65F);
        int admitted = 0;
        for (Player viewer : viewers) {
            if (viewer == null || !viewer.isOnline() || !viewer.getWorld().equals(anchor.getWorld())
                    || !allowed.test(viewer, anchor)) continue;
            int stride = BossSpellGeometryPolicy.stride(viewer.getLocation().distanceSquared(anchor), viewers.size());
            if (stride == 0) continue;
            if (++admitted > 32) break;
            for (int i = 0; i < points.size(); i += stride) {
                var point = points.get(i);
                // Bright nodes are sparse; a 96-point all-END_ROD cage hides the attack.
                if (point.layer() == 2 && i % 4 == 0)
                    viewer.spawnParticle(Particle.END_ROD, positions.get(i), 1, 0, 0, 0, 0);
                else viewer.spawnParticle(Particle.DUST, positions.get(i), 1, 0, 0, 0, 0,
                        point.layer() == 0 ? primary : accent);
            }
        }
    }

    static void impact(JavaPlugin plugin, LivingEntity boss, BossSpellGeometryPolicy.Shape shape,
            Location origin, Vector facing, double radius, int rgb, BooleanSupplier owned,
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
                frame(anchor, direction, BossSpellGeometryPolicy.sample(shape,
                        BossSpellGeometryPolicy.Beat.IMPACT, step / 5.0, radius), rgb, audience.get(), allowed);
                if (++step >= 6) cancel();
            }
        }.runTaskTimer(plugin, 0, 2);
        register.accept(task);
    }
}
