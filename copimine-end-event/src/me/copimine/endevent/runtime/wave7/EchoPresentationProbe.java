package me.copimine.endevent.runtime.wave7;

import java.util.UUID;
import java.util.function.BiConsumer;
import me.copimine.endevent.domain.wave7.EchoPresentationProbeState;
import org.bukkit.Location;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Husk;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

/** Disposable native carrier driven by the existing five-tick encounter loop. */
public final class EchoPresentationProbe {
    private final Husk carrier;
    private final UUID owner;
    private final Location anchor;
    private final EchoPresentationProbeState state;
    private final BiConsumer<String, EchoPresentationProbeState.Frame> send;
    private long nextPathTick;
    private boolean closed;

    public EchoPresentationProbe(Husk carrier, Player player, UUID event, long generation,
                                  long epoch, long tick,
                                  BiConsumer<String, EchoPresentationProbeState.Frame> sender) {
        this.carrier = carrier; owner = player.getUniqueId(); anchor = carrier.getLocation().clone(); send = sender;
        state = new EchoPresentationProbeState(event, generation, epoch, UUID.randomUUID(),
                carrier.getUniqueId(), owner, carrier.getWorld().getKey().toString(), tick);
        carrier.setAdult(); carrier.setShouldBurnInDay(false); carrier.setCanPickupItems(false);
        carrier.setRemoveWhenFarAway(false); carrier.setPersistent(false); carrier.setSilent(true);
        // Purpur/Paper returns before navigation.tick() when aware=false.
        // Remove only this actor's goals while retaining native path physics.
        Bukkit.getMobGoals().removeAllGoals(carrier);
        carrier.setAI(true); carrier.setAware(true); carrier.setTarget(null);
        EntityEquipment gear = carrier.getEquipment();
        gear.setHelmetDropChance(0); gear.setChestplateDropChance(0);
        gear.setLeggingsDropChance(0); gear.setBootsDropChance(0);
        gear.setItemInMainHandDropChance(0); gear.setItemInOffHandDropChance(0);
        gear.setHelmet(new ItemStack(Material.IRON_HELMET)); gear.setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
        gear.setLeggings(new ItemStack(Material.IRON_LEGGINGS)); gear.setBoots(new ItemStack(Material.IRON_BOOTS));
        equip(EchoPresentationProbeState.Action.IDLE);
        send.accept("END_ECHO_BIND", state.nextFrame(tick));
    }

    public UUID owner() { return owner; }
    public Husk carrier() { return carrier; }

    public boolean action(EchoPresentationProbeState.Action action, long tick) {
        if (closed || !carrier.isValid() || carrier.isDead() || !state.begin(action, tick)) return false;
        carrier.getPathfinder().stopPathfinding(); nextPathTick = tick;
        carrier.setPose(action == EchoPresentationProbeState.Action.CROUCH ? Pose.SNEAKING : Pose.STANDING, true);
        equip(action);
        switch (action) {
            case JUMP -> { if (carrier.isOnGround()) carrier.setVelocity(carrier.getVelocity().setY(0.42)); }
            case SWING -> carrier.swingMainHand();
            case HURT -> {
                double before = carrier.getHealth(); carrier.damage(2.0);
                if (carrier.getHealth() < before) state.acceptedHurt();
            }
            case DEATH -> { carrier.setHealth(0); state.died(tick); }
            default -> { }
        }
        send.accept("END_ECHO_STATE", state.nextFrame(tick));
        return true;
    }

    public boolean tick(Player player, UUID event, long generation, long tick, boolean capable) {
        boolean sameWorld = player != null && player.getWorld().equals(carrier.getWorld());
        if (!state.active(event, generation, tick, player != null && player.isOnline(),
                player != null && !player.isDead(), sameWorld, capable)) { close(tick); return false; }
        if (carrier.isDead()) state.died(tick);
        else if (!carrier.isValid()) { close(tick); return false; }
        if (!carrier.isDead()) {
            // No pursuit teleport and no path work every tick. The local lane
            // remains within four blocks of the validated initial floor.
            if (carrier.getLocation().distanceSquared(anchor) > 36.0) { close(tick); return false; }
            if (state.action() == EchoPresentationProbeState.Action.WALK
                    || state.action() == EchoPresentationProbeState.Action.SPRINT) {
                if (tick >= nextPathTick) {
                    double side = (tick / 80 % 2 == 0) ? 3 : -3;
                    carrier.getPathfinder().moveTo(anchor.clone().add(side, 0, 0),
                            state.action() == EchoPresentationProbeState.Action.SPRINT ? 1.3 : 0.8);
                    nextPathTick = tick + 40;
                }
            } else if (player != null) {
                Vector facing = player.getEyeLocation().toVector().subtract(carrier.getEyeLocation().toVector());
                if (facing.lengthSquared() > 0.001) {
                    Location rotation = carrier.getLocation().setDirection(facing);
                    carrier.setRotation(rotation.getYaw(), rotation.getPitch());
                }
            }
        }
        send.accept("END_ECHO_STATE", state.nextFrame(tick));
        return true;
    }

    /** Keep the carrier reference if a native cleanup throws so the caller can retry. */
    public void close(long tick) {
        if (closed) return;
        send.accept("END_ECHO_REMOVE", state.nextFrame(tick));
        carrier.remove(); state.close(); closed = true;
    }

    private void equip(EchoPresentationProbeState.Action action) {
        Material item = switch (action) {
            case BOW -> Material.BOW;
            case CROSSBOW -> Material.CROSSBOW;
            case EAT -> Material.GOLDEN_APPLE;
            default -> Material.IRON_SWORD;
        };
        carrier.getEquipment().setItemInMainHand(new ItemStack(item));
        carrier.getEquipment().setItemInOffHand(new ItemStack(Material.SHIELD));
    }
}
