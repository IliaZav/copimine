package me.copimine.endevent;

import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import me.copimine.endevent.domain.BossImpactGeometryPolicy;
import me.copimine.endevent.domain.BossImpactGeometryPolicy.Kind;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

/** Finite generation-owned particles and one sound per cast, never per victim. */
final class BossPresentationEffects {
    private BossPresentationEffects() { }

    static void play(JavaPlugin plugin, LivingEntity boss, Kind kind, Location origin,
            Vector facing, double radius, int rgb, boolean sound,
            BooleanSupplier stillOwned, Supplier<List<Player>> audience,
            BiPredicate<Player, Location> viewerAllowed, Consumer<BukkitTask> register) {
        if (boss == null || kind == null || origin == null || origin.getWorld() == null) return;
        Location anchor = origin.clone();
        Vector forward = facing == null ? new Vector(0, 0, 1) : facing.clone().setY(0);
        if (forward.lengthSquared() < 0.0001) forward = new Vector(0, 0, 1);
        forward.normalize();
        Vector direction = forward;
        Vector side = new Vector(forward.getZ(), 0, -forward.getX());
        int frames = switch (kind) { case PHASE, DEFEAT -> 6; case CHEST -> 1; default -> 3; };
        long period = switch (kind) { case PHASE -> 8; case DEFEAT -> 6; default -> 3; };
        Particle.DustOptions primary = new Particle.DustOptions(Color.fromRGB(rgb), 1.2F);
        Color color = Color.fromRGB(rgb);
        Particle.DustOptions accent = new Particle.DustOptions(Color.fromRGB(
                (color.getRed() + 240) / 2, (color.getGreen() + 224) / 2,
                (color.getBlue() + 255) / 2), 0.9F);
        BukkitTask task = new BukkitRunnable() {
            int frame;
            @Override public void run() {
                if (!plugin.isEnabled() || !stillOwned.getAsBoolean() || !boss.isValid() || boss.isDead()
                        || !boss.getWorld().equals(anchor.getWorld()) || frame >= frames) { cancel(); return; }
                var points = BossImpactGeometryPolicy.sample(kind, radius, frame / (double) Math.max(1, frames - 1));
                for (Player viewer : audience.get().stream().limit(32).toList()) {
                    if (viewer == null || !viewer.isOnline() || !viewer.getWorld().equals(anchor.getWorld())
                            || viewer.getLocation().distanceSquared(anchor) > 48 * 48
                            || !viewerAllowed.test(viewer, anchor)) continue;
                    if (sound && frame == 0) viewer.playSound(anchor, soundFor(kind), SoundCategory.HOSTILE,
                            kind == Kind.DEFEAT ? 0.9F : 0.7F, kind == Kind.SWIPE ? 0.8F : 0.55F);
                    if (kind == Kind.PHASE && frame == 2) {
                        viewer.playSound(anchor, Sound.ENTITY_WARDEN_ROAR, SoundCategory.HOSTILE, 0.45F, 0.7F);
                        viewer.spawnParticle(Particle.END_ROD, anchor.clone().add(0, 2.4, 0),
                                12, 0.6, 0.8, 0.6, 0.04);
                    }
                    for (var point : points) {
                        Location at = anchor.clone().add(side.clone().multiply(point.x()))
                                .add(0, point.y(), 0).add(direction.clone().multiply(point.z()));
                        if (point.layer() == 2) viewer.spawnParticle(Particle.END_ROD, at, 1, 0.025, 0.025, 0.025, 0);
                        else viewer.spawnParticle(Particle.DUST, at, 1, 0.045, 0.03, 0.045, 0,
                                point.layer() == 0 ? primary : accent);
                    }
                }
                if (++frame >= frames) cancel();
            }
        }.runTaskTimer(plugin, 0, period);
        register.accept(task);
    }

    private static Sound soundFor(Kind kind) {
        return switch (kind) {
            case SWIPE -> Sound.ENTITY_PLAYER_ATTACK_SWEEP;
            case SLAM -> Sound.ENTITY_GENERIC_EXPLODE;
            case CHEST -> Sound.ITEM_TRIDENT_THROW;
            case PHASE -> Sound.BLOCK_END_PORTAL_SPAWN;
            case DEFEAT -> Sound.ENTITY_ENDER_DRAGON_DEATH;
        };
    }
}
