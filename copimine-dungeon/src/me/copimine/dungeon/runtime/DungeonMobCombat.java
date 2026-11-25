package me.copimine.dungeon.runtime;

import me.copimine.dungeon.domain.DungeonMobRegistry;
import me.copimine.dungeon.domain.MobTemplate;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.UUID;

final class DungeonMobCombat {
    private final Mob mob;
    private final DungeonMobRegistry.Profile profile;
    private long nextAttack;
    private long releaseAt;
    private long contactUntil;
    private Location lockedAim;
    private UUID lockedTarget;

    DungeonMobCombat(Mob mob, MobTemplate template, long tick) {
        this.mob = mob;
        this.profile = DungeonMobRegistry.profile(template);
        nextAttack = tick + 20 + (mob.getUniqueId().hashCode() & 15);
    }

    void advance(long tick, List<Player> players) {
        if (!mob.isValid() || mob.isDead()) {
            return;
        }
        if (contactUntil > 0) {
            resolveLeap(tick, players);
            return;
        }
        if (lockedAim != null) {
            tickWindup(tick, players);
            return;
        }
        Player target = nearest(players);
        mob.setTarget(target);
        if (target == null || tick < nextAttack || !profile.template().isDungeonExtension()
                || !mob.hasLineOfSight(target)) {
            return;
        }
        double distance = mob.getLocation().distanceSquared(target.getLocation());
        switch (profile.template()) {
            case RIFT_SILVERFISH -> {
                if (distance >= 2.25 && distance <= 25 && mob.isOnGround()) {
                    begin(tick, target, 10, Particle.PORTAL, Sound.ENTITY_SILVERFISH_AMBIENT);
                }
            }
            case RIFT_BREEZE -> {
                if (distance <= 144 && mob.isOnGround()) {
                    begin(tick, target, 20, Particle.CLOUD, Sound.ENTITY_BREEZE_CHARGE);
                }
            }
            case RIFT_BOGGED -> {
                if (distance >= 16 && distance <= 225) {
                    begin(tick, target, 25, Particle.SPORE_BLOSSOM_AIR, Sound.ITEM_CROSSBOW_LOADING_START);
                }
            }
            default -> {
                // Other templates retain their existing native attack goals.
            }
        }
    }

    private Player nearest(List<Player> players) {
        Location origin = mob.getLocation();
        Player nearest = null;
        double best = profile.aggroRange() * profile.aggroRange();
        for (Player player : players) {
            double distance = origin.distanceSquared(player.getLocation());
            if (distance < best) {
                nearest = player;
                best = distance;
            }
        }
        return nearest;
    }

    private void begin(long tick, Player player, long windup, Particle tell, Sound sound) {
        lockedTarget = player.getUniqueId();
        lockedAim = player.getLocation().add(0, player.getHeight() * 0.55, 0);
        releaseAt = tick + windup;
        mob.getPathfinder().stopPathfinding();
        mob.setAware(false);
        Location origin = mob.getEyeLocation();
        Vector facing = lockedAim.toVector().subtract(origin.toVector());
        if (facing.lengthSquared() > 1.0e-6) {
            origin.setDirection(facing);
            mob.setRotation(origin.getYaw(), origin.getPitch());
        }
        mob.getWorld().spawnParticle(tell, origin, 14, 0.22, 0.23, 0.22, 0.015);
        mob.getWorld().playSound(origin, sound, 0.85f, 0.85f);
    }

    private void tickWindup(long tick, List<Player> players) {
        if (players.stream().noneMatch(p -> p.getUniqueId().equals(lockedTarget))) {
            finish(tick, 20);
            return;
        }
        if (tick < releaseAt) {
            return;
        }
        Location origin = mob.getEyeLocation();
        Vector direction = lockedAim.toVector().subtract(origin.toVector());
        if (direction.lengthSquared() < 1.0e-6) {
            finish(tick, 20);
            return;
        }
        direction.normalize();
        switch (profile.template()) {
            case RIFT_SILVERFISH -> {
                direction.setY(0);
                if (direction.lengthSquared() < 1.0e-6) {
                    finish(tick, 20);
                    return;
                }
                mob.setVelocity(direction.normalize().multiply(0.92).setY(0.32));
                contactUntil = tick + 15;
                mob.getWorld().playSound(origin, Sound.ENTITY_SILVERFISH_HURT, 0.8f, 1.2f);
            }
            case RIFT_BREEZE -> {
                Snowball charge = mob.launchProjectile(Snowball.class, direction.multiply(1.0));
                charge.setGravity(false);
                mob.getWorld().spawnParticle(Particle.CLOUD, origin, 16, 0.32, 0.22, 0.32, 0.08);
                mob.getWorld().playSound(origin, Sound.ENTITY_BREEZE_SHOOT, 0.9f, 0.8f);
                finish(tick, 60);
            }
            case RIFT_BOGGED -> {
                Arrow bolt = mob.launchProjectile(Arrow.class, direction.multiply(1.95));
                bolt.setDamage(3.0);
                bolt.addCustomEffect(new PotionEffect(PotionEffectType.POISON, 80, 0), true);
                mob.getWorld().spawnParticle(Particle.SPORE_BLOSSOM_AIR, origin, 12, 0.2, 0.2, 0.2, 0);
                mob.getWorld().playSound(origin, Sound.ENTITY_ARROW_SHOOT, 0.9f, 0.75f);
                finish(tick, 100);
            }
            default -> finish(tick, 20);
        }
    }

    private void resolveLeap(long tick, List<Player> players) {
        for (Player player : players) {
            if (mob.getBoundingBox().expand(0.35).overlaps(player.getBoundingBox())
                    && mob.hasLineOfSight(player)) {
                player.damage(4.0, mob);
                finish(tick, 35);
                return;
            }
        }
        if (tick >= contactUntil || mob.isOnGround()) {
            finish(tick, 25);
        }
    }

    private void finish(long tick, long recovery) {
        lockedAim = null;
        lockedTarget = null;
        contactUntil = 0;
        nextAttack = tick + recovery;
        mob.setAware(true);
    }
}
