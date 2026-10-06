package me.copimine.endevent.runtime.wave7;

import java.util.UUID;
import java.util.function.BiConsumer;
import me.copimine.endevent.domain.wave7.EchoPresentationProbeState;
import me.copimine.endevent.domain.wave7.EchoLoadoutState.Kind;
import org.bukkit.Location;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Pillager;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/** Disposable native carrier driven by the existing five-tick encounter loop. */
public final class EchoPresentationProbe {
    private final Pillager carrier;
    private final UUID owner;
    private final Location anchor;
    private final EchoPresentationProbeState state;
    private final BiConsumer<String, EchoPresentationProbeState.Frame> send;
    private final EchoReplicaInventory replica;
    private int pendingFoodSlot = -1;
    private long pendingFoodTick, pendingFoodRevision;
    private long nextPathTick;
    private boolean closed;

    public EchoPresentationProbe(Pillager carrier, Player player, UUID event, long generation,
                                  long epoch, long tick,
                                  BiConsumer<String, EchoPresentationProbeState.Frame> sender) {
        this(carrier,player,event,generation,epoch,tick,sender,false);
    }

    public EchoPresentationProbe(Pillager carrier, Player player, UUID event, long generation,
                                  long epoch, long tick,
                                  BiConsumer<String, EchoPresentationProbeState.Frame> sender, boolean copiedLoadout) {
        this.carrier = carrier; owner = player.getUniqueId(); anchor = carrier.getLocation().clone(); send = sender;
        UUID duel = UUID.randomUUID();
        state = new EchoPresentationProbeState(event, generation, epoch, duel,
                carrier.getUniqueId(), owner, carrier.getWorld().getKey().toString(), tick);
        replica = copiedLoadout ? EchoReplicaInventory.capture(player.getInventory(),event,generation,duel,owner) : null;
        carrier.setCanPickupItems(false);
        carrier.setRemoveWhenFarAway(false); carrier.setPersistent(false); carrier.setSilent(true);
        // Purpur/Paper returns before navigation.tick() when aware=false.
        // Remove only this actor's goals while retaining native path physics.
        Bukkit.getMobGoals().removeAllGoals(carrier);
        carrier.setAI(true); carrier.setAware(true); carrier.setTarget(null);
        EntityEquipment gear = carrier.getEquipment();
        gear.setHelmetDropChance(0); gear.setChestplateDropChance(0);
        gear.setLeggingsDropChance(0); gear.setBootsDropChance(0);
        gear.setItemInMainHandDropChance(0); gear.setItemInOffHandDropChance(0);
        if (replica == null) {
            gear.setHelmet(new ItemStack(Material.IRON_HELMET)); gear.setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
            gear.setLeggings(new ItemStack(Material.IRON_LEGGINGS)); gear.setBoots(new ItemStack(Material.IRON_BOOTS));
        }
        equip(EchoPresentationProbeState.Action.IDLE);
        send.accept("END_ECHO_BIND", state.nextFrame(tick));
    }

    public UUID owner() { return owner; }
    public Pillager carrier() { return carrier; }

    public boolean action(EchoPresentationProbeState.Action action, long tick) {
        String shieldHand = replica != null && replica.state().item(40).kind() != Kind.SHIELD ? "MAIN" : "OFF";
        if (closed || !carrier.isValid() || carrier.isDead() || action == null || !available(action)
                || !state.begin(action, tick, shieldHand)) return false;
        pendingFoodSlot = -1;
        carrier.getPathfinder().stopPathfinding(); nextPathTick = tick;
        carrier.setPose(action == EchoPresentationProbeState.Action.CROUCH ? Pose.SNEAKING : Pose.STANDING, true);
        equip(action);
        if (replica != null && action == EchoPresentationProbeState.Action.EAT) {
            pendingFoodSlot = replica.state().find(Kind.GOLDEN_APPLE);
            pendingFoodTick = tick; pendingFoodRevision = replica.state().revision();
            carrier.getWorld().playSound(carrier.getLocation(), Sound.ENTITY_GENERIC_EAT, 0.55f, 1.0f);
        }
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
            if (pendingFoodSlot >= 0 && state.action() == EchoPresentationProbeState.Action.EAT
                    && tick - pendingFoodTick >= 32) {
                int slot = pendingFoodSlot; pendingFoodSlot = -1;
                if (replica.state().consume(slot, 1, pendingFoodRevision)) {
                    // Ordinary golden apple: vanilla 1.21.1 effects, not instant direct health.
                    carrier.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 1));
                    carrier.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 2400, 0));
                    carrier.getWorld().playSound(carrier.getLocation(), Sound.ENTITY_PLAYER_BURP, 0.55f, 1.0f);
                }
                state.begin(EchoPresentationProbeState.Action.IDLE, tick); equip(EchoPresentationProbeState.Action.IDLE);
            }
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
        pendingFoodSlot = -1;
        send.accept("END_ECHO_REMOVE", state.nextFrame(tick));
        carrier.remove(); state.close(); closed = true;
    }

    private void equip(EchoPresentationProbeState.Action action) {
        if (replica != null) {
            int main = replica.state().selectedSlot(), off = 40;
            switch (action) {
                case BOW -> main = replica.state().find(Kind.BOW);
                case CROSSBOW -> main = replica.state().find(Kind.CROSSBOW);
                case EAT -> main = replica.state().find(Kind.GOLDEN_APPLE);
                case SWING -> main = replica.state().find(Kind.MELEE);
                case SHIELD -> { if (replica.state().item(40).kind() != Kind.SHIELD) main = replica.state().find(Kind.SHIELD); }
                default -> { }
            }
            if (main == off) off = -1;
            replica.equip(carrier.getEquipment(), main, off);
            return;
        }
        Material item = switch (action) {
            case BOW -> Material.BOW;
            case CROSSBOW -> Material.CROSSBOW;
            case EAT -> Material.GOLDEN_APPLE;
            default -> Material.IRON_SWORD;
        };
        carrier.getEquipment().setItemInMainHand(new ItemStack(item));
        carrier.getEquipment().setItemInOffHand(new ItemStack(Material.SHIELD));
    }

    private boolean available(EchoPresentationProbeState.Action action) {
        if (replica == null) return true;
        Kind required = switch (action) {
            case BOW -> Kind.BOW;
            case CROSSBOW -> Kind.CROSSBOW;
            case EAT -> Kind.GOLDEN_APPLE;
            case SHIELD -> Kind.SHIELD;
            case SWING -> Kind.MELEE;
            default -> null;
        };
        return required == null || replica.state().find(required) >= 0;
    }
}
