package me.copimine.endevent.runtime.wave7;

import java.util.UUID;
import java.util.IdentityHashMap;
import java.util.function.BiConsumer;
import me.copimine.endevent.domain.wave7.EchoPresentationProbeState;
import me.copimine.endevent.domain.wave7.EchoLoadoutState.Kind;
import me.copimine.endevent.domain.wave7.EchoLoadoutState.Item;
import org.bukkit.Location;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.Pillager;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
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
    private long pendingFoodTick;
    private Item pendingFoodItem;
    private long nextPathTick;
    private int activeShieldSlot = -1;
    private EquipmentSlot activeShieldHand;
    private long shieldDisabledUntil, shieldReceiptTick = -1, shieldRaisedTick = -1;
    private final IdentityHashMap<Object, Boolean> shieldReceipts = new IdentityHashMap<>();
    private final IdentityHashMap<Object, Boolean> armorReceipts = new IdentityHashMap<>();
    private long armorReceiptTick = -1;
    private EchoNativeArmorWear nativeArmor;
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
    public UUID duel() { return state.duel(); }
    public long epoch() { return state.epoch(); }

    public boolean activeForCombat(Player player, UUID event, long generation, long tick, boolean capable) {
        return !closed && player != null && owner.equals(player.getUniqueId())
                && carrier.isValid() && !carrier.isDead()
                && state.active(event, generation, tick, player.isOnline(), !player.isDead(),
                        player.getWorld().equals(carrier.getWorld()), capable);
    }

    public boolean action(EchoPresentationProbeState.Action action, long tick) {
        String shieldHand = replica != null && replica.state().item(40).kind() != Kind.SHIELD ? "MAIN" : "OFF";
        if (closed || !carrier.isValid() || carrier.isDead() || action == null || !available(action)
                || action == EchoPresentationProbeState.Action.SHIELD && tick < shieldDisabledUntil
                || !state.begin(action, tick, shieldHand)) return false;
        pendingFoodSlot = -1;
        carrier.clearActiveItem();
        activeShieldSlot = -1; activeShieldHand = null;
        carrier.getPathfinder().stopPathfinding(); nextPathTick = tick;
        carrier.setPose(action == EchoPresentationProbeState.Action.CROUCH ? Pose.SNEAKING : Pose.STANDING, true);
        equip(action);
        if (action == EchoPresentationProbeState.Action.SHIELD) {
            // Direction, projectile piercing and the native raise delay belong
            // to Minecraft's blocking path, not the presentation packet.
            activeShieldHand = "MAIN".equals(shieldHand) ? EquipmentSlot.HAND : EquipmentSlot.OFF_HAND;
            activeShieldSlot = replica == null ? -1 : activeShieldHand == EquipmentSlot.HAND ? replica.state().find(Kind.SHIELD) : 40;
            shieldRaisedTick = tick;
            carrier.startUsingItem(activeShieldHand);
        }
        if (replica != null && action == EchoPresentationProbeState.Action.EAT) {
            pendingFoodSlot = replica.state().find(Kind.GOLDEN_APPLE);
            pendingFoodTick = tick; pendingFoodItem = replica.state().item(pendingFoodSlot);
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
        if (!owner.equals(player == null ? null : player.getUniqueId())
                || !state.active(event, generation, tick, player != null && player.isOnline(),
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
                // Unrelated native armor/shield wear must not cancel eating.
                // Quantities only decrease, so a changed food slot invalidates
                // this use while another slot's revision is harmless.
                if (replica.state().item(slot).equals(pendingFoodItem)
                        && replica.state().consume(slot, 1, replica.state().revision())) {
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

    /** Observe one accepted native block. Minecraft alone decides direction and HP mitigation. */
    public boolean acceptedShieldBlock(Object receipt, Player player, UUID event, long generation,
                                        long tick, boolean capable, double blockedAmount, boolean axe) {
        if (closed || replica == null || receipt == null || !Double.isFinite(blockedAmount)
                || blockedAmount <= 0 || blockedAmount > 1_000_000 || tick < shieldReceiptTick || tick < shieldRaisedTick
                || player == null || !owner.equals(player.getUniqueId()) || carrier.isDead() || !carrier.isValid()
                || !state.active(event, generation, tick, player.isOnline(), !player.isDead(),
                        player.getWorld().equals(carrier.getWorld()), capable)
                || state.action() != EchoPresentationProbeState.Action.SHIELD || activeShieldSlot < 0
                || !carrier.hasActiveItem() || carrier.getActiveItemHand() != activeShieldHand) return false;
        if (tick != shieldReceiptTick) { shieldReceipts.clear(); shieldReceiptTick = tick; }
        if (shieldReceipts.containsKey(receipt)) return false;
        if (shieldReceipts.size() >= 32) { close(tick); return false; }
        shieldReceipts.put(receipt, Boolean.TRUE);
        if (blockedAmount >= 3.0) {
            long revision = replica.state().revision();
            // Carrier mobs have no native Player.hurtCurrentlyUsedShield override.
            // Damage the equipped replica through Paper: native Unbreaking and
            // break notifications run once; record its outcome, not the raw request.
            carrier.damageItemStack(activeShieldHand, 1 + (int) Math.floor(blockedAmount));
            ItemStack outcome = activeShieldHand == EquipmentSlot.HAND
                    ? carrier.getEquipment().getItemInMainHand() : carrier.getEquipment().getItemInOffHand();
            if (!replica.recordNativeWear(activeShieldSlot, outcome, revision)) {
                close(tick); return false;
            }
        }
        boolean broken = replica.state().item(activeShieldSlot).kind() != Kind.SHIELD;
        carrier.getWorld().playSound(carrier.getLocation(), broken ? Sound.ITEM_SHIELD_BREAK : Sound.ITEM_SHIELD_BLOCK, 0.65f, 1.0f);
        if (axe) shieldDisabledUntil = tick + 100;
        if (broken || axe) {
            carrier.clearActiveItem(); activeShieldSlot = -1; activeShieldHand = null;
            state.begin(EchoPresentationProbeState.Action.IDLE, tick); equip(EchoPresentationProbeState.Action.IDLE);
            send.accept("END_ECHO_STATE", state.nextFrame(tick));
        }
        return true;
    }

    /** Supply the Player-only native equipment dispatch omitted by this carrier. */
    public boolean acceptedArmorDamage(Object receipt, Player player, UUID event, long generation,
                                       long tick, boolean capable, DamageSource source,
                                       double originalAmount, double armorAmount) {
        if (closed || replica == null || receipt == null || source == null || tick < armorReceiptTick
                || !Double.isFinite(originalAmount) || originalAmount < 0 || originalAmount > 1_000_000
                || !Double.isFinite(armorAmount) || armorAmount < 0 || armorAmount > 1_000_000
                || player == null || !owner.equals(player.getUniqueId()) || carrier.isDead() || !carrier.isValid()
                || !state.active(event, generation, tick, player.isOnline(), !player.isDead(),
                        player.getWorld().equals(carrier.getWorld()), capable)) return false;
        if (tick != armorReceiptTick) { armorReceipts.clear(); armorReceiptTick = tick; }
        if (armorReceipts.containsKey(receipt)) return false;
        if (armorReceipts.size() >= 32) { close(tick); return false; }
        armorReceipts.put(receipt, Boolean.TRUE);
        if (nativeArmor == null) nativeArmor = new EchoNativeArmorWear(carrier);
        nativeArmor.wear(source, originalAmount, armorAmount);
        EntityEquipment gear = carrier.getEquipment();
        ItemStack[] outcomes = {gear.getBoots(), gear.getLeggings(), gear.getChestplate(), gear.getHelmet()};
        for (int index = 0; index < outcomes.length; index++) {
            int slot = 36 + index;
            if (replica.state().item(slot).kind() == Kind.ARMOR
                    && !replica.recordNativeWear(slot, outcomes[index], replica.state().revision())) {
                close(tick); return false;
            }
        }
        return true;
    }

    /** Keep the carrier reference if a native cleanup throws so the caller can retry. */
    public void close(long tick) {
        if (closed) return;
        pendingFoodSlot = -1;
        carrier.clearActiveItem();
        activeShieldSlot = -1; activeShieldHand = null; shieldReceipts.clear(); armorReceipts.clear();
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
