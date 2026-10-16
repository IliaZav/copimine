"""W1 inventory, owner presentation, and movement lease at detached Bukkit boundaries.

The production pickup, carried-state tick, and beam dispatch are executed;
world, network, inventory and attribute API calls are recorded by the harness.
"""
from pathlib import Path
import subprocess

import pytest

from tests.test_end_rift_portal_layer_runtime import declaration

ROOT = Path(__file__).resolve().parents[1]
DOMAIN = ROOT / "copimine-end-event/src/me/copimine/endevent/domain"
EVENT = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"


@pytest.fixture(scope="module")
def inventory_probe(tmp_path_factory):
    source = EVENT.read_text(encoding="utf-8")
    pickup = declaration(source, "private boolean pickUpCurrentCarrierCharge(Player player)")
    has_charge = declaration(source, "private boolean hasCurrentCarrierInventoryCharge(Player holder)")
    matches_charge = declaration(source, "private boolean isCurrentCarrierInventoryChargeItem(Player holder, ItemStack stack)")
    remove_charge = declaration(source, "private void removeCurrentCarrierInventoryCharge(Player holder)")
    tick = declaration(source, "private void tickCurrentCarrierObjective(long now)")
    carried = declaration(tick, "if (currentCarrierState.phase() == RiftCarrierPolicy.Phase.CHARGE_CARRIED)")
    beam = declaration(tick[tick.rindex("        if ((currentCarrierState.phase()"):], "if ((currentCarrierState.phase()")
    slowness = declaration(source, "private void updateCurrentCarrierSlowness(Player player, boolean carryingCharge)")
    folder = tmp_path_factory.mktemp("wave1-inventory-acceptance")
    java = folder / "CarrierInventoryAcceptanceProbe.java"
    java.write_text(r'''
import java.util.*;
import me.copimine.endevent.domain.RiftCarrierPolicy;
import me.copimine.endevent.domain.CarrierSlownessLeasePolicy;
public class CarrierInventoryAcceptanceProbe {
    static final int MODEL_CARRIER_CHARGE=1;
    static class World {}
    static final World WORLD=new World();
    static class Location {
        double x,y,z; Location(double x,double y,double z){this.x=x;this.y=y;this.z=z;}
        public Location clone(){return new Location(x,y,z);}
        Location add(double x,double y,double z){this.x+=x;this.y+=y;this.z+=z;return this;}
        double distanceSquared(Location p){return Math.pow(x-p.x,2)+Math.pow(y-p.y,2)+Math.pow(z-p.z,2);}
    }
    static class Entity {
        final UUID id=UUID.randomUUID(); boolean valid=true;
        Location pos=new Location(10,64,0); UUID getUniqueId(){return id;}
        World getWorld(){return WORLD;} Location getLocation(){return pos.clone();}
        boolean isValid(){return valid;} double getHeight(){return 1.8;}
    }
    static class LivingEntity extends Entity {}
    static class Item extends Entity {
        int pickupDelay; boolean gravity=true;
        void setPickupDelay(int ticks){pickupDelay=ticks;} void setGravity(boolean on){gravity=on;}
    }
    static class Inventory {
        List<ItemStack> contents=new ArrayList<>(); boolean full;
        ItemStack offHand;
        int firstEmpty(){return full?-1:contents.size();}
        Map<Integer,ItemStack> addItem(ItemStack item){if(full)return Map.of(0,item);contents.add(item);return Map.of();}
        ItemStack[] getStorageContents(){return contents.toArray(ItemStack[]::new);}
        void setStorageContents(ItemStack[] storage){contents=new ArrayList<>(Arrays.asList(storage));}
        ItemStack getItemInOffHand(){return offHand;}
        void setItemInOffHand(ItemStack item){offHand=item;}
    }
    static class Material {static final Material AIR=new Material(), AMETHYST_SHARD=new Material(); boolean isAir(){return this==AIR;}}
    static class PersistentDataType<T> {
        static final PersistentDataType<Byte> BYTE=new PersistentDataType<>();
        static final PersistentDataType<String> STRING=new PersistentDataType<>();
        static final PersistentDataType<Long> LONG=new PersistentDataType<>();
    }
    static class PersistentDataContainer {
        Map<Object,Object> values=new HashMap<>();
        <T> void set(Object key,PersistentDataType<T> type,T value){values.put(key,value);}
        @SuppressWarnings("unchecked") <T> T get(Object key,PersistentDataType<T> type){return (T)values.get(key);}
        @SuppressWarnings("unchecked") <T> T getOrDefault(Object key,PersistentDataType<T> type,T fallback){return (T)values.getOrDefault(key,fallback);}
    }
    static class ItemMeta {
        PersistentDataContainer data=new PersistentDataContainer();
        PersistentDataContainer getPersistentDataContainer(){return data;}
    }
    static class ItemStack {
        Material type; int amount; ItemMeta meta=new ItemMeta();
        ItemStack(Material type,int amount){this.type=type;this.amount=amount;}
        Material getType(){return type;} int getAmount(){return amount;}
        boolean hasItemMeta(){return meta!=null;} ItemMeta getItemMeta(){return meta;}
        void setItemMeta(ItemMeta value){meta=value;}
    }
    enum PotionEffectType {SLOWNESS}
    record PotionEffect(PotionEffectType type,int duration,int amplifier,boolean ambient,boolean particles,boolean icon) {}
    static class Player extends LivingEntity {
        Inventory inventory=new Inventory();Map<PotionEffectType,PotionEffect> effects=new HashMap<>();
        AttributeInstance movementSpeed=new AttributeInstance();
        Inventory getInventory(){return inventory;} String getName(){return "test-player";}
        AttributeInstance getAttribute(Attribute attribute){return movementSpeed;}
        void playSound(Object... args){} void sendActionBar(Object component){}
        void spawnParticle(Object... args){}
        boolean addPotionEffect(PotionEffect effect){effects.put(effect.type(),effect);return true;}
        PotionEffect getPotionEffect(PotionEffectType type){return effects.get(type);}
    }
    enum Attribute {GENERIC_MOVEMENT_SPEED}
    static final class AttributeModifier {
        enum Operation {MULTIPLY_SCALAR_1}
        final Object key; final double amount; final Operation operation;
        AttributeModifier(Object key,double amount,Operation operation){this.key=key;this.amount=amount;this.operation=operation;}
    }
    static final class AttributeInstance {
        Map<Object,AttributeModifier> modifiers=new HashMap<>();
        AttributeModifier getModifier(Object key){return modifiers.get(key);}
        void addTransientModifier(AttributeModifier modifier){modifiers.put(modifier.key,modifier);}
        void removeModifier(AttributeModifier modifier){modifiers.remove(modifier.key,modifier);}
    }
    Object keyCurrentCarrierCharge=new Object(),keyEventId=new Object(),keyGeneration=new Object(),keyCarrierChargeOwner=new Object();
    Object keyCarrierSlownessModifier=new Object();
    static class Sound {static final String BLOCK_RESPAWN_ANCHOR_CHARGE="charge";}
    static class NamedTextColor {static final String AQUA="aqua";}
    static class Component {static Object text(String text,Object color){return text;}}
    static class Particle {static final String END_ROD="rod",DUST="dust";record DustOptions(Object color,float size){}}
    static class Color {static Object fromRGB(int r,int g,int b){return r;}}
    static class Bukkit {static Map<UUID,Player> players=new HashMap<>();static Player getPlayer(UUID id){return players.get(id);}}
    static class Refresh {boolean shouldRefresh(long now){return true;}}
    record Beam(UUID viewer,Location from,Location to) {}
    Player holder=new Player(),other=new Player();Item charge=new Item();
    RiftCarrierPolicy.State currentCarrierState;
    Map<UUID,Entity> ownedEntities=new HashMap<>();List<Beam> beams=new ArrayList<>();
    Refresh carrierChargeRefresh=new Refresh(),carrierBeamRefresh=new Refresh();
    UUID currentCarrierVisualHolder;int currentCarrierVisualCycle;
    long generation=7,eventTickCounter=10,currentCarrierVisualRefreshMillis;
    String eventId="event";Location currentCarrierChargeLocation;
    CarrierInventoryAcceptanceProbe(){
        Bukkit.players.put(holder.id,holder);Bukkit.players.put(other.id,other);
        ownedEntities.put(charge.id,charge);
        currentCarrierState=new RiftCarrierPolicy.State(generation,0,null,charge.id,null,200,
                RiftCarrierPolicy.Phase.CHARGE_DROPPED);
    }
    Entity currentCarrierChargeEntity(){return charge;}
    boolean isActiveArenaParticipant(Player p){return p!=null;}
    boolean isCurrentDroppedCarrierCharge(Entity e){return e==charge&&currentCarrierState.phase()==RiftCarrierPolicy.Phase.CHARGE_DROPPED;}
    boolean sameWorld(Player p,Entity e){return p.getWorld()==e.getWorld();}
    void syncCurrentCarrierFields(){}
    void saveStateAsync(){}
    java.util.logging.Logger getLogger(){return java.util.logging.Logger.getLogger("carrier-test");}
    void spawnCurrentCarrierCharge(Location position){}
    void renderCurrentCarrierCharge(Entity entity){}
    boolean teleportCombatEntity(Entity e,Location pos){e.pos=pos;return true;}
    void sendCurrentCarrierPlayerVisual(Player p,boolean active,int cycle){}
    void releaseCurrentCarrierHolder(UUID holder,String reason){}
    Location coreCombatAnchorLocation(){return new Location(0,64,0);}
    void updateCurrentCarrierMarker(Entity e){}
    void removeCurrentCarrierMarker(){}
    List<Player> eventAudience(){return List.of(holder,other);}
    boolean isEventParticleViewer(Player p,Location core){return true;}
    void sendWorldBeamPacket(Player p,String key,Location from,Location to,int color,float width){beams.add(new Beam(p.id,from.clone(),to.clone()));}
    ItemStack overlayItem(int model,String id){return new ItemStack(Material.AMETHYST_SHARD,1);}
''' + pickup + r'''
''' + has_charge + r'''
''' + matches_charge + r'''
''' + remove_charge + r'''
    void tickCarried(long now){
''' + carried + r'''
    }
    void tickBeam(long now){
''' + beam + r'''
    }
''' + slowness + r'''
    void carry(){currentCarrierState=RiftCarrierPolicy.pickUp(currentCarrierState,generation,holder.id,charge.id,eventTickCounter);}
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args){
        var p=new CarrierInventoryAcceptanceProbe();
        switch(args[0]) {
            case "inventory-pickup" -> {
                check(p.pickUpCurrentCarrierCharge(p.holder),"eligible holder must pick up a legal charge");
                check(p.holder.inventory.contents.size()==1,"successful pickup must put exactly one actual charge item in holder inventory");
                check(p.holder.inventory.contents.get(0) instanceof ItemStack
                                && p.hasCurrentCarrierInventoryCharge(p.holder),
                        "the inventory item must be marked for this event, generation and owner");
            }
            case "full-inventory" -> {
                p.holder.inventory.full=true;
                check(!p.pickUpCurrentCarrierCharge(p.holder),"full inventory must not acquire an abstract carried charge");
                check(p.currentCarrierState.phase()==RiftCarrierPolicy.Phase.CHARGE_DROPPED,
                        "failed inventory insertion must preserve one available dropped charge");
            }
            case "offhand-carrier-charge" -> {
                check(p.pickUpCurrentCarrierCharge(p.holder),"eligible holder must pick up a legal charge");
                ItemStack carried=p.holder.inventory.contents.get(0);
                p.holder.inventory.contents.set(0,null);
                p.holder.inventory.setItemInOffHand(carried);
                check(p.hasCurrentCarrierInventoryCharge(p.holder),
                        "moving the owner's tagged charge into the offhand must preserve carried state");
                p.removeCurrentCarrierInventoryCharge(p.holder);
                check(p.holder.inventory.getItemInOffHand()==null
                                &&!p.hasCurrentCarrierInventoryCharge(p.holder),
                        "ending the carrier objective must remove its tagged offhand item");
            }
            case "owner-only-beam", "core-to-holder-beam" -> {
                check(p.pickUpCurrentCarrierCharge(p.holder),"carrier pickup must create an inventory charge before presentation");
                p.tickBeam(1000);
                if(args[0].equals("owner-only-beam"))check(p.beams.size()==1&&p.beams.get(0).viewer().equals(p.holder.id),
                        "charge beam must be sent only to the inventory owner");
                else check(!p.beams.isEmpty()&&p.beams.stream().allMatch(b->b.from().x==0&&b.to().x==10),
                        "charge beam must start at core and end at current holder");
            }
            case "carrier-speed-lease" -> {
                check(p.pickUpCurrentCarrierCharge(p.holder),"carrier pickup must create a legal charge item");
                Object unrelatedHolderKey="other-plugin:slow", unrelatedOtherKey="another-plugin:slow";
                p.holder.movementSpeed.modifiers.put(unrelatedHolderKey,
                        new AttributeModifier(unrelatedHolderKey,-0.20D,AttributeModifier.Operation.MULTIPLY_SCALAR_1));
                p.other.movementSpeed.modifiers.put(unrelatedOtherKey,
                        new AttributeModifier(unrelatedOtherKey,-0.10D,AttributeModifier.Operation.MULTIPLY_SCALAR_1));
                p.tickCarried(1000);
                var owned=p.holder.movementSpeed.getModifier(p.keyCarrierSlownessModifier);
                check(owned!=null&&owned.amount==CarrierSlownessLeasePolicy.MOVEMENT_SPEED_MULTIPLIER
                                &&owned.operation==AttributeModifier.Operation.MULTIPLY_SCALAR_1,
                        "legal inventory charge must apply the keyed 30 percent slowdown lease");
                check(p.holder.movementSpeed.modifiers.size()==2
                                &&p.holder.movementSpeed.getModifier(unrelatedHolderKey)!=null,
                        "carrier slowdown must preserve the holder's unrelated movement modifiers");
                check(p.other.movementSpeed.modifiers.size()==1
                                &&p.other.movementSpeed.getModifier(unrelatedOtherKey)!=null
                                &&p.other.movementSpeed.getModifier(p.keyCarrierSlownessModifier)==null,
                        "only the inventory owner receives charge slowdown; unrelated participants remain untouched");
            }
            default -> throw new AssertionError("unknown scenario");
        }
    }
}
''', encoding="utf-8")
    result = subprocess.run(["javac", "-J-Xmx128m", "-encoding", "UTF-8", "-d", str(folder),
                            str(DOMAIN / "RiftCarrierPolicy.java"),
                            str(DOMAIN / "CarrierSlownessLeasePolicy.java"), str(java)],
                            capture_output=True, text=True, timeout=30)
    assert result.returncode == 0, result.stdout + result.stderr
    return folder


@pytest.mark.parametrize("scenario", ["inventory-pickup", "full-inventory", "owner-only-beam",
                                      "core-to-holder-beam", "carrier-speed-lease", "offhand-carrier-charge"])
def test_charge_inventory_and_personal_presentation(inventory_probe, scenario):
    result = subprocess.run(["java", "-Xmx64m", "-cp", str(inventory_probe),
                             "CarrierInventoryAcceptanceProbe", scenario], capture_output=True, text=True, timeout=15)
    assert result.returncode == 0, result.stdout + result.stderr
