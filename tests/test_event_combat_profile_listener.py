"""Execute the production observer, including the cancelled real-health route."""
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]


def test_real_hooks_count_receipts_once_and_exclude_forced_movement(tmp_path):
    observer = ROOT / "copimine-end-event/src/me/copimine/endevent/runtime/EventCombatProfileListener.java"
    assert observer.exists(), "The official damage/consumption/movement path has no profile observer"
    files = {
        "org/bukkit/event/Listener.java": "package org.bukkit.event; public interface Listener {}",
        "org/bukkit/event/EventPriority.java": "package org.bukkit.event; public enum EventPriority { MONITOR }",
        "org/bukkit/event/EventHandler.java": "package org.bukkit.event; public @interface EventHandler { EventPriority priority(); boolean ignoreCancelled() default false; }",
        "org/bukkit/Material.java": "package org.bukkit; public enum Material { AIR,DIAMOND_SWORD,IRON_AXE,BOW,CROSSBOW,TRIDENT,GOLDEN_APPLE,ENCHANTED_GOLDEN_APPLE,POTION,BREAD }",
        "org/bukkit/World.java": "package org.bukkit; public class World {}",
        "org/bukkit/Location.java": "package org.bukkit; public record Location(World world,double x,double y,double z,float yaw) {public World getWorld(){return world;} public double getX(){return x;} public double getY(){return y;} public double getZ(){return z;} public float getYaw(){return yaw;}}",
        "org/bukkit/util/Vector.java": "package org.bukkit.util; public record Vector(double x,double y,double z){public double getX(){return x;} public double getY(){return y;} public double getZ(){return z;}}",
        "org/bukkit/inventory/EquipmentSlot.java": "package org.bukkit.inventory; public enum EquipmentSlot { HAND,OFF_HAND }",
        "org/bukkit/inventory/ItemStack.java": "package org.bukkit.inventory; import org.bukkit.Material; import org.bukkit.inventory.meta.*; public record ItemStack(Material type,ItemMeta meta) {public ItemStack(Material t){this(t,null);} public Material getType(){return type;} public ItemMeta getItemMeta(){return meta;}}",
        "org/bukkit/inventory/PlayerInventory.java": "package org.bukkit.inventory; import org.bukkit.Material; public class PlayerInventory {public ItemStack main=new ItemStack(Material.DIAMOND_SWORD); public ItemStack getItemInMainHand(){return main;}}",
        "org/bukkit/inventory/meta/ItemMeta.java": "package org.bukkit.inventory.meta; public interface ItemMeta {}",
        "org/bukkit/inventory/meta/PotionMeta.java": "package org.bukkit.inventory.meta; import org.bukkit.potion.PotionType; public interface PotionMeta extends ItemMeta {PotionType getBasePotionType();}",
        "org/bukkit/potion/PotionType.java": "package org.bukkit.potion; public enum PotionType { HEALING,STRONG_HEALING,REGENERATION,LONG_REGENERATION,STRONG_REGENERATION,WATER }",
        "org/bukkit/entity/Entity.java": "package org.bukkit.entity; import java.util.UUID; import org.bukkit.*; public interface Entity {UUID getUniqueId(); Location getLocation(); World getWorld();}",
        "org/bukkit/entity/LivingEntity.java": "package org.bukkit.entity; public interface LivingEntity extends Entity {}",
        "org/bukkit/entity/Projectile.java": "package org.bukkit.entity; public interface Projectile extends Entity {Object getShooter();}",
        "org/bukkit/entity/AbstractArrow.java": "package org.bukkit.entity; public interface AbstractArrow extends Projectile {boolean isShotFromCrossbow();}",
        "org/bukkit/entity/Trident.java": "package org.bukkit.entity; public interface Trident extends AbstractArrow {}",
        "org/bukkit/entity/Player.java": "package org.bukkit.entity; import org.bukkit.inventory.*; import org.bukkit.util.Vector; public interface Player extends LivingEntity {PlayerInventory getInventory(); double getHealth(); double getMaxHealth(); boolean isSprinting(); boolean isBlocking(); boolean isOnGround(); float getFallDistance(); Vector getVelocity(); int getActiveItemUsedTime();}",
        "org/bukkit/event/entity/EntityDamageByEntityEvent.java": "package org.bukkit.event.entity; import org.bukkit.entity.*; public record EntityDamageByEntityEvent(Entity entity,Entity damager,double damage,boolean critical,boolean cancelled) {public Entity getEntity(){return entity;} public Entity getDamager(){return damager;} public double getFinalDamage(){return damage;} public boolean isCritical(){return critical;} public boolean isCancelled(){return cancelled;}}",
        "org/bukkit/event/entity/EntityShootBowEvent.java": "package org.bukkit.event.entity; import org.bukkit.entity.*; import org.bukkit.inventory.*; public record EntityShootBowEvent(LivingEntity entity,ItemStack bow,EquipmentSlot hand,boolean cancelled) {public LivingEntity getEntity(){return entity;} public ItemStack getBow(){return bow;} public EquipmentSlot getHand(){return hand;} public boolean isCancelled(){return cancelled;}}",
        "io/papermc/paper/event/entity/EntityLoadCrossbowEvent.java": "package io.papermc.paper.event.entity; import org.bukkit.entity.*; public record EntityLoadCrossbowEvent(LivingEntity entity,boolean cancelled) {public LivingEntity getEntity(){return entity;}public boolean isCancelled(){return cancelled;}}",
        "org/bukkit/event/player/PlayerAnimationType.java": "package org.bukkit.event.player; public enum PlayerAnimationType { ARM_SWING,OFF_ARM_SWING }",
        "org/bukkit/event/player/PlayerAnimationEvent.java": "package org.bukkit.event.player; import org.bukkit.entity.Player; public record PlayerAnimationEvent(Player player,PlayerAnimationType type,boolean cancelled) {public Player getPlayer(){return player;} public PlayerAnimationType getAnimationType(){return type;} public boolean isCancelled(){return cancelled;}}",
        "org/bukkit/event/player/PlayerItemConsumeEvent.java": "package org.bukkit.event.player; import org.bukkit.entity.Player; import org.bukkit.inventory.ItemStack; public record PlayerItemConsumeEvent(Player player,ItemStack item,boolean cancelled) {public Player getPlayer(){return player;} public ItemStack getItem(){return item;} public boolean isCancelled(){return cancelled;}}",
    }
    for event in ("PlayerItemHeldEvent", "PlayerTeleportEvent", "PlayerVelocityEvent"):
        files[f"org/bukkit/event/player/{event}.java"] = f"package org.bukkit.event.player; import org.bukkit.entity.Player; public record {event}(Player player,boolean cancelled) {{public Player getPlayer(){{return player;}} public boolean isCancelled(){{return cancelled;}}}}"
    files["ProfileListenerProbe.java"] = r'''
import java.util.*;
import org.bukkit.*;import org.bukkit.entity.*;import org.bukkit.inventory.*;
import org.bukkit.util.Vector;import org.bukkit.event.entity.*;import org.bukkit.event.player.*;
import me.copimine.endevent.runtime.*;import me.copimine.endevent.runtime.EventCombatProfileService.*;
public class ProfileListenerProbe {
    static final World WORLD=new World();
    static class P implements Player {
        final UUID id=new UUID(0,1); final PlayerInventory inv=new PlayerInventory();
        double x,z; boolean sprint,block,ground=true; float fall; int use=20;
        public UUID getUniqueId(){return id;}public PlayerInventory getInventory(){return inv;}
        public Location getLocation(){return new Location(WORLD,x,68,z,0);}public World getWorld(){return WORLD;}
        public double getHealth(){return 6;}public double getMaxHealth(){return 20;}
        public boolean isSprinting(){return sprint;}public boolean isBlocking(){return block;}
        public boolean isOnGround(){return ground;}public float getFallDistance(){return fall;}
        public Vector getVelocity(){return new Vector(0,ground?0:-.2,0);}public int getActiveItemUsedTime(){return use;}
    }
    static class Mob implements LivingEntity {
        final UUID id=new UUID(0,2); public UUID getUniqueId(){return id;}
        public Location getLocation(){return new Location(WORLD,0,68,3,0);}public World getWorld(){return WORLD;}
    }
    static class Shot implements AbstractArrow {
        final P p;final boolean cross;Shot(P p,boolean c){this.p=p;cross=c;}
        public Object getShooter(){return p;}public boolean isShotFromCrossbow(){return cross;}
        public UUID getUniqueId(){return new UUID(0,3);}public Location getLocation(){return p.getLocation();}public World getWorld(){return WORLD;}
    }
    static class Spear extends Shot implements Trident {Spear(P p){super(p,false);}}
    static void require(boolean b,String m){if(!b)throw new AssertionError(m);}
    public static void main(String[] args){
        var p=new P();var mob=new Mob();var service=new EventCombatProfileService();
        service.beginAttempt("event",71,71,Set.of(p.id),true);
        long[] tick={5}; boolean[] active={true};
        var observer=new EventCombatProfileListener(service, player -> new Context("event",71,player.getUniqueId(),tick[0],1,active[0],false,false,false,false), e -> e==mob);
        var ordinary=new EntityDamageByEntityEvent(mob,p,3,false,false);
        observer.onDamage(ordinary);observer.onDamage(ordinary);
        require(service.snapshot(p.id).count(Stat.MELEE_HITS)==1,"duplicate callbacks cannot count twice");
        var shield=new EntityDamageByEntityEvent(mob,p,3,false,true);observer.onDamage(shield);
        require(service.snapshot(p.id).count(Stat.MELEE_HITS)==1,"cancelled shield hits are not accepted");
        p.ground=false;p.fall=1;tick[0]=10;
        var authoritative=new EntityDamageByEntityEvent(mob,p,4,true,true);
        observer.recordAcceptedHit(authoritative,p,new Context("event",71,p.id,10,1,true,false,false,false,false));
        observer.onDamage(authoritative);
        require(service.snapshot(p.id).count(Stat.MELEE_HITS)==2 && service.snapshot(p.id).count(Stat.JUMP_CRITICAL_HITS)==1,"real health transaction counts despite deliberate vanilla cancellation");
        observer.onDamage(new EntityDamageByEntityEvent(mob,new Shot(p,true),3,true,false));
        require(service.snapshot(p.id).count(Stat.CROSSBOW_HITS)==1 && service.snapshot(p.id).count(Stat.JUMP_CRITICAL_HITS)==1,"critical arrows never count as jumping melee");
        p.inv.main=new ItemStack(Material.BOW);p.ground=true;tick[0]=15;
        observer.onDamage(new EntityDamageByEntityEvent(mob,p,2,false,false));
        require(service.snapshot(p.id).count(Stat.MELEE_HITS)==3 && service.snapshot(p.id).count(Stat.RANGED_HITS)==1,"punching with a held bow is melee; weapon in hand cannot imply projectile provenance");
        observer.onDamage(new EntityDamageByEntityEvent(p,p,3,true,false));
        require(service.snapshot(p.id).count(Stat.MELEE_HITS)==3,"foreign/non-owned target excluded");
        observer.onDamage(new EntityDamageByEntityEvent(mob,new Spear(p),2,false,false));
        require(service.snapshot(p.id).count(Stat.RANGED_HITS)==2 && service.snapshot(p.id).count(Stat.BOW_HITS)==0,"Trident also inherits AbstractArrow but is not a bow observation");
        observer.onShoot(new EntityShootBowEvent(p,new ItemStack(Material.CROSSBOW),EquipmentSlot.HAND,false));
        observer.onShoot(new EntityShootBowEvent(p,new ItemStack(Material.CROSSBOW),EquipmentSlot.HAND,false));
        observer.onShoot(new EntityShootBowEvent(p,new ItemStack(Material.CROSSBOW),EquipmentSlot.HAND,false));
        require(service.snapshot(p.id).count(Stat.CROSSBOW_RELEASES)==1,"multishot is one release, not three invented uses");
        observer.onCrossbowLoad(new io.papermc.paper.event.entity.EntityLoadCrossbowEvent(p,false));
        observer.onCrossbowLoad(new io.papermc.paper.event.entity.EntityLoadCrossbowEvent(p,true));
        require(service.snapshot(p.id).count(Stat.CROSSBOW_LOADS)==1 && service.snapshot(p.id).count(Stat.CROSSBOW_CHARGE_TICKS)==20 && service.snapshot(p.id).count(Stat.BOW_CHARGE_TICKS)==0,"crossbow charge is observed at successful load, independently of multishot release");
        observer.onConsume(new PlayerItemConsumeEvent(p,new ItemStack(Material.GOLDEN_APPLE),false));
        observer.onConsume(new PlayerItemConsumeEvent(p,new ItemStack(Material.BREAD),false));
        observer.onConsume(new PlayerItemConsumeEvent(p,new ItemStack(Material.GOLDEN_APPLE),true));
        require(service.snapshot(p.id).count(Stat.HEAL_USES)==1 && service.snapshot(p.id).count(Stat.HEAL_HEALTH_PERMILLE)==300,"only accepted healing completion records real low-health threshold");
        observer.onSwing(new PlayerAnimationEvent(p,PlayerAnimationType.ARM_SWING,false));
        observer.onSwitch(new PlayerItemHeldEvent(p,false));
        require(service.snapshot(p.id).count(Stat.ATTEMPTED_SWINGS)==1 && service.snapshot(p.id).count(Stat.ITEM_SWITCHES)==1,"attempts remain separate from accepted hits");
        p.ground=true;tick[0]=15;observer.sample(p,mob);
        long before=service.snapshot(p.id).count(Stat.MOVEMENT_SAMPLES);
        observer.onTeleport(new PlayerTeleportEvent(p,false));p.x=20;tick[0]=20;observer.sample(p,mob);
        require(service.snapshot(p.id).count(Stat.MOVEMENT_SAMPLES)==before,"forced teleport is excluded from movement preference");
        tick[0]=40;observer.sample(p,mob);p.x=19;tick[0]=45;observer.sample(p,mob);
        require(service.snapshot(p.id).count(Stat.MOVEMENT_SAMPLES)==before+2 && service.snapshot(p.id).count(Stat.PURSUIT_SAMPLES)==1,"natural movement resumes without counting teleport distance as pursuit");
        observer.onVelocity(new PlayerVelocityEvent(p,false));tick[0]=50;observer.sample(p,mob);
        require(service.snapshot(p.id).count(Stat.MOVEMENT_SAMPLES)==before+2,"server knockback excluded");
        observer.clear();tick[0]=55;observer.sample(p,mob);
        require(service.snapshot(p.id).count(Stat.MOVEMENT_SAMPLES)==before+3,"runtime cleanup releases transient suppression");
        observer.onDamage(new EntityDamageByEntityEvent(mob,p,2,false,false));
        require(service.snapshot(p.id).count(Stat.MELEE_HITS)==4,"same-generation observer cleanup cannot reuse a receipt and suppress a new accepted hit");
        active[0]=false;observer.onConsume(new PlayerItemConsumeEvent(p,new ItemStack(Material.GOLDEN_APPLE),false));
        require(service.snapshot(p.id).count(Stat.HEAL_USES)==1,"inactive phase cannot contaminate persona");
    }
}
'''
    for name, source in files.items():
        path = tmp_path / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(source, encoding="utf-8")
    production = observer.with_name("EventCombatProfileService.java")
    result = subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(tmp_path), str(production), str(observer), *map(str, tmp_path.rglob("*.java"))], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
    result = subprocess.run(["java", "-cp", str(tmp_path), "ProfileListenerProbe"], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
