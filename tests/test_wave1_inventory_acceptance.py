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
RESET_CARRIER_INVENTORY_HELPER = "private void clearCurrentCarrierInventoryChargeForReset()"


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
    extra = "\n".join(declaration(source, signature) for signature in (
        "private boolean reconcileCurrentCarrierInventoryOwner(",
        "private boolean isCurrentCarrierTokenItem(",
        "private void purgeCurrentCarrierToken(",
        "private boolean isCurrentCarrierResetChargeItem(",
        "private boolean isOwnedCarrierSlowness(",
    ) if signature in source)
    reset_inventory_helper = (declaration(source, RESET_CARRIER_INVENTORY_HELPER)
                              if RESET_CARRIER_INVENTORY_HELPER in source else
                              'void clearCurrentCarrierInventoryChargeForReset(){throw new AssertionError("missing production reset charge purge helper");}')
    delivery = declaration(source, "private boolean deliverCurrentCarrierCharge(")
    release = declaration(source, "private void releaseCurrentCarrierHolder(")
    folder = tmp_path_factory.mktemp("wave1-inventory-acceptance")
    java = folder / "CarrierInventoryAcceptanceProbe.java"
    java.write_text(r'''
import java.util.*;
import me.copimine.endevent.domain.RiftCarrierPolicy;
import me.copimine.endevent.domain.CarrierSlownessLeasePolicy;
public class CarrierInventoryAcceptanceProbe {
    static final int MODEL_CARRIER_CHARGE=1;
    static class World {List<Entity> entities=new ArrayList<>(); List<Entity> getEntities(){return entities;} void playSound(Object... args){}}
    static final World WORLD=new World();
    static class Location {
        double x,y,z; Location(double x,double y,double z){this.x=x;this.y=y;this.z=z;}
        public Location clone(){return new Location(x,y,z);}
        World getWorld(){return WORLD;}
        Location(World world,double x,double y,double z){this(x,y,z);}
        int getBlockX(){return (int)Math.floor(x);}int getBlockY(){return (int)Math.floor(y);}int getBlockZ(){return (int)Math.floor(z);}
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
        ItemStack stack; ItemStack getItemStack(){return stack;} void remove(){valid=false;}
        void setPickupDelay(int ticks){pickupDelay=ticks;} void setGravity(boolean on){gravity=on;}
    }
    static class Inventory {
        List<ItemStack> contents=new ArrayList<>(); boolean full;
        ItemStack offHand;
        int firstEmpty(){return full?-1:contents.size();}
        Map<Integer,ItemStack> addItem(ItemStack item){if(full)return Map.of(0,item);contents.add(item);return Map.of();}
        ItemStack[] getStorageContents(){return contents.toArray(ItemStack[]::new);}
        void setStorageContents(ItemStack[] storage){contents=new ArrayList<>(Arrays.asList(storage));}
        int getSize(){return contents.size();}
        ItemStack getItem(int slot){return contents.get(slot);}
        void setItem(int slot,ItemStack item){contents.set(slot,item);}
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
        Material type; int amount; ItemMeta meta=new ItemMeta();boolean rejectMeta;
        ItemStack(Material type,int amount){this.type=type;this.amount=amount;}
        Material getType(){return type;} int getAmount(){return amount;}
        boolean hasItemMeta(){return meta!=null;} ItemMeta getItemMeta(){var copy=new ItemMeta();copy.data.values.putAll(meta.data.values);return copy;}
        boolean setItemMeta(ItemMeta value){if(rejectMeta)return false;meta=value;return true;}
        ItemStack copy(){var copy=new ItemStack(type,amount);copy.meta.data.values.putAll(meta.data.values);return copy;}
    }
    enum PotionEffectType {SLOWNESS}
    record PotionEffect(PotionEffectType type,int duration,int amplifier,boolean ambient,boolean particles,boolean icon) {
        PotionEffectType getType(){return type;} int getDuration(){return duration;} int getAmplifier(){return amplifier;}
        boolean isAmbient(){return ambient;} boolean hasParticles(){return particles;} boolean hasIcon(){return icon;}
    }
    record CarrierPotionLease(PotionEffect original,long capturedAtTick,long refreshedAtTick) {}
    Map<UUID,CarrierPotionLease> currentCarrierPotionLeases=new HashMap<>();
    Set<UUID> currentCarrierPotionMutations=new HashSet<>();
    UUID currentCarrierInventoryToken;
    boolean currentCarrierRestartReconciliationPending;
    Set<Inventory> currentCarrierObservedInventories=new HashSet<>();
    static class Player extends LivingEntity {
        Inventory inventory=new Inventory();Map<PotionEffectType,PotionEffect> effects=new HashMap<>();
        ItemStack cursor;ItemStack getItemOnCursor(){return cursor;}void setItemOnCursor(ItemStack item){cursor=item;}
        AttributeInstance movementSpeed=new AttributeInstance();
        boolean active=true; boolean isOnline(){return true;} boolean isDead(){return false;}
        Inventory getInventory(){return inventory;} String getName(){return "test-player";}
        AttributeInstance getAttribute(Attribute attribute){return movementSpeed;}
        void playSound(Object... args){} void sendActionBar(Object component){}
        void spawnParticle(Object... args){}
        boolean addPotionEffect(PotionEffect effect){effects.put(effect.type(),effect);return true;}
        PotionEffect getPotionEffect(PotionEffectType type){return effects.get(type);}
        void removePotionEffect(PotionEffectType type){effects.remove(type);}
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
    Object keyCarrierSlownessModifier=new Object(),keyCarrierChargeToken=new Object();
    static class Sound {static final String BLOCK_RESPAWN_ANCHOR_CHARGE="charge",BLOCK_BEACON_POWER_SELECT="insert";}
    static class NamedTextColor {static final String AQUA="aqua",YELLOW="yellow",LIGHT_PURPLE="purple";}
    static class Component {static Object text(String text,Object color){return text;}}
    static class Particle {static final String END_ROD="rod",DUST="dust",REVERSE_PORTAL="reverse";record DustOptions(Object color,float size){}}
    static class Color {static Object fromRGB(int r,int g,int b){return r;}}
    static class Bukkit {
        static Map<UUID,Player> players=new HashMap<>();static Player getPlayer(UUID id){return players.get(id);}
        static int tick=10;static int getCurrentTick(){return tick;}
        static Collection<Player> getOnlinePlayers(){return players.values();}
        static List<World> getWorlds(){return List.of(WORLD);}
    }
    static class Refresh {boolean shouldRefresh(long now){return true;}}
    record Beam(UUID viewer,String key,Location from,Location to) {}
    Player holder=new Player(),other=new Player();Item charge=new Item();
    RiftCarrierPolicy.State currentCarrierState;
    Map<UUID,Entity> ownedEntities=new HashMap<>();List<Beam> beams=new ArrayList<>();
    Refresh carrierChargeRefresh=new Refresh(),carrierBeamRefresh=new Refresh();
    UUID currentCarrierVisualHolder;int currentCarrierVisualCycle;
    long generation=7,eventTickCounter=10,currentCarrierVisualRefreshMillis;
    String eventId="event";Location currentCarrierChargeLocation;
    CarrierInventoryAcceptanceProbe(){
        Bukkit.players.clear();WORLD.entities.clear();Bukkit.tick=10;Bukkit.players.put(holder.id,holder);Bukkit.players.put(other.id,other);
        ownedEntities.put(charge.id,charge);
        currentCarrierState=new RiftCarrierPolicy.State(generation,0,null,charge.id,null,200,
                RiftCarrierPolicy.Phase.CHARGE_DROPPED);
    }
    Entity currentCarrierChargeEntity(){return charge;}
    boolean isActiveArenaParticipant(Player p){return p!=null&&p.active;}
    boolean isCurrentDroppedCarrierCharge(Entity e){return e==charge&&currentCarrierState.phase()==RiftCarrierPolicy.Phase.CHARGE_DROPPED;}
    boolean sameWorld(Player p,Entity e){return p.getWorld()==e.getWorld();}
    List<UUID> syncedHolders=new ArrayList<>();
    void syncCurrentCarrierFields(){syncedHolders.add(currentCarrierState.holder());}
    void saveStateAsync(){}
    java.util.logging.Logger getLogger(){return java.util.logging.Logger.getLogger("carrier-test");}
    void spawnCurrentCarrierCharge(Location position){}
    void renderCurrentCarrierCharge(Entity entity){}
    boolean teleportCombatEntity(Entity e,Location pos){e.pos=pos;return true;}
    void sendCurrentCarrierPlayerVisual(Player p,boolean active,int cycle){}
    void removeCurrentCarrierChargeVisual(){}
    void spawnEventParticle(Object... args){}
    int currentCarrierCharges;
    boolean sameWorld(Player p,Location l){return true;}
    Location coreCombatAnchorLocation(){return new Location(0,64,0);}
    void updateCurrentCarrierMarker(Entity e){}
    void removeCurrentCarrierMarker(){}
    List<Player> eventAudience(){return List.of(holder,other);}
    boolean isEventParticleViewer(Player p,Location core){return true;}
    void sendWorldBeamPacket(Player p,String key,Location from,Location to,int color,float width){beams.add(new Beam(p.id,key,from.clone(),to.clone()));}
    ItemStack overlayItem(int model,String id){return new ItemStack(Material.AMETHYST_SHARD,1);}
''' + pickup + r'''
''' + has_charge + r'''
''' + matches_charge + r'''
''' + remove_charge + r'''
    void markWave1RecoveryRequired(String reason){throw new AssertionError("unexpected recovery requirement: "+reason);}
    void tickCarried(long now){
''' + carried + r'''
    }
    void tickBeam(long now){
''' + beam + r'''
    }
''' + slowness + '\n' + extra + '\n' + reset_inventory_helper + '\n' + delivery + '\n' + release + r'''
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
            case "reset-purge-all-locations" -> {
                check(p.pickUpCurrentCarrierCharge(p.holder),"eligible holder must create a production-tagged charge");
                ItemStack tagged=p.holder.inventory.contents.get(0);
                p.holder.inventory.contents.clear();
                p.holder.inventory.contents.add(tagged.copy());
                p.holder.inventory.offHand=tagged.copy();
                p.holder.cursor=tagged.copy();
                Inventory observed=new Inventory();observed.contents.add(tagged.copy());
                p.currentCarrierObservedInventories.add(observed);
                p.clearCurrentCarrierInventoryChargeForReset();
                check(p.holder.inventory.contents.stream().allMatch(Objects::isNull),"reset must remove charge from storage");
                check(p.holder.inventory.getItemInOffHand()==null,"reset must remove charge from offhand");
                check(p.holder.getItemOnCursor()==null,"reset must remove charge from cursor");
                check(observed.contents.stream().allMatch(Objects::isNull),"reset must remove charge from observed containers");
                check(p.currentCarrierInventoryToken==null,"reset must retire the runtime token after purging every location");
            }
            case "owner-only-beam", "core-to-holder-beam" -> {
                check(p.pickUpCurrentCarrierCharge(p.holder),"carrier pickup must create an inventory charge before presentation");
                p.tickBeam(1000);
                if(args[0].equals("owner-only-beam"))check(!p.beams.isEmpty()&&p.beams.stream().allMatch(b->b.viewer().equals(p.holder.id)),
                        "charge beam must be sent only to the inventory owner");
                else check(p.beams.stream().anyMatch(b->b.key().equals("wave1-carrier-objective"))
                                &&p.beams.stream().filter(b->b.key().equals("wave1-carrier-objective")).allMatch(b->b.from().x==0&&b.to().x==10),
                        "charge beam must start at core and end at current holder");
            }
            case "owner-core-outline" -> {
                p.pickUpCurrentCarrierCharge(p.holder);p.tickBeam(1000);
                var outlines=p.beams.stream().filter(b->b.key().equals("wave1-carrier-core-outline")).toList();
                check(outlines.size()==1&&outlines.get(0).viewer().equals(p.holder.id),"core outline marker must be holder-only");
                var start=outlines.get(0).from();
                check(start.x==0.5&&start.y==64.5&&start.z==0.5,"core outline must be anchored at the core block center");
            }
            case "slowness-storage", "slowness-offhand" -> {
                check(p.pickUpCurrentCarrierCharge(p.holder),"pickup must create one legal charge");
                if(args[0].equals("slowness-offhand")){
                    p.holder.inventory.offHand=p.holder.inventory.contents.remove(0);
                }
                p.tickCarried(1000);
                var effect=p.holder.getPotionEffect(PotionEffectType.SLOWNESS);
                check(effect!=null&&effect.getAmplifier()==1,"legal charge must apply actual Slowness II");
                check(p.other.effects.isEmpty(),"only legal holder is slowed");
                check(p.holder.movementSpeed.modifiers.isEmpty(),"Slowness must not also double-apply a separate speed modifier");
            }
            case "stronger-unrelated" -> {
                var strong=new PotionEffect(PotionEffectType.SLOWNESS,300,3,true,true,true);
                p.holder.effects.put(PotionEffectType.SLOWNESS,strong);
                p.pickUpCurrentCarrierCharge(p.holder);p.tickCarried(1000);
                check(p.holder.getPotionEffect(PotionEffectType.SLOWNESS)==strong,"acquire must preserve stronger unrelated slowness");
                p.releaseCurrentCarrierHolder(p.holder.id,"test");
                check(p.holder.getPotionEffect(PotionEffectType.SLOWNESS)==strong,"release must preserve stronger unrelated slowness");
            }
            case "transfer-once", "transfer-offhand" -> {
                p.pickUpCurrentCarrierCharge(p.holder);p.tickCarried(1000);
                var carried=p.holder.inventory.contents.remove(0);
                if(args[0].equals("transfer-offhand"))p.other.inventory.offHand=carried;else p.other.inventory.contents.add(carried);
                p.tickCarried(1050);
                check(p.other.id.equals(p.currentCarrierState.holder()),"unique token custody must atomically rebind holder");
                check(p.other.id.toString().equals(carried.meta.data.get(p.keyCarrierChargeOwner,PersistentDataType.STRING)),"token owner PDC must follow legal custody");
                check(p.holder.getPotionEffect(PotionEffectType.SLOWNESS)==null&&p.other.getPotionEffect(PotionEffectType.SLOWNESS)!=null,"transfer must move the effect");
                p.other.pos=new Location(0,64,0);
                check(p.deliverCurrentCarrierCharge(p.other),"new holder must deliver once");
                check(!p.deliverCurrentCarrierCharge(p.other)&&p.currentCarrierCharges==1,"repeated delivery must not score twice");
                check(p.other.inventory.contents.stream().allMatch(Objects::isNull)&&p.other.inventory.offHand==null,"delivered token must be consumed");
            }
            case "duplicate-rejected", "stacked-rejected" -> {
                p.pickUpCurrentCarrierCharge(p.holder);
                var carried=p.holder.inventory.contents.get(0);
                if(args[0].equals("duplicate-rejected"))p.other.inventory.contents.add(carried.copy());else carried.amount=2;
                p.tickCarried(1050);
                check(p.currentCarrierState.phase()==RiftCarrierPolicy.Phase.CHARGE_DROPPED,"ambiguous tokens must fail closed without delivery");
                check(p.holder.inventory.contents.stream().allMatch(Objects::isNull)&&p.other.inventory.contents.stream().allMatch(Objects::isNull),"all ambiguous owned copies must be purged");
            }
            case "rebind-pdc-rejected" -> {
                p.pickUpCurrentCarrierCharge(p.holder);
                p.syncedHolders.clear();
                var carried=p.holder.inventory.contents.remove(0);carried.rejectMeta=true;p.other.inventory.contents.add(carried);
                p.tickCarried(1000);
                check(p.currentCarrierState.phase()==RiftCarrierPolicy.Phase.CHARGE_DROPPED,
                        "failed owner PDC write must abort transfer without legal custody");
                check(p.other.inventory.contents.stream().allMatch(Objects::isNull),"failed PDC transfer must purge its own token");
                check(!p.syncedHolders.contains(p.other.id),"failed PDC transfer must never publish partial new ownership");
            }
            case "stronger-replaces-ours" -> {
                p.pickUpCurrentCarrierCharge(p.holder);p.tickCarried(1000);
                var strong=new PotionEffect(PotionEffectType.SLOWNESS,300,4,false,true,true);
                p.holder.effects.put(PotionEffectType.SLOWNESS,strong);
                p.releaseCurrentCarrierHolder(p.holder.id,"test");
                check(p.holder.getPotionEffect(PotionEffectType.SLOWNESS)==strong,"stronger effect installed after acquisition must survive release");
            }
            case "weaker-restored" -> {
                p.holder.effects.put(PotionEffectType.SLOWNESS,new PotionEffect(PotionEffectType.SLOWNESS,100,0,true,true,false));
                p.pickUpCurrentCarrierCharge(p.holder);p.tickCarried(1000);
                Bukkit.tick+=6;p.holder.effects.put(PotionEffectType.SLOWNESS,new PotionEffect(PotionEffectType.SLOWNESS,24,1,false,false,true));
                p.releaseCurrentCarrierHolder(p.holder.id,"test");
                var restored=p.holder.getPotionEffect(PotionEffectType.SLOWNESS);
                check(restored!=null&&restored.getAmplifier()==0&&restored.getDuration()==94&&restored.isAmbient(),"release must restore the remaining unrelated weaker effect");
            }
            case "duplicate-ground", "duplicate-container" -> {
                p.pickUpCurrentCarrierCharge(p.holder);
                var duplicate=p.holder.inventory.contents.get(0).copy();
                if(args[0].equals("duplicate-ground")){var drop=new Item();drop.stack=duplicate;WORLD.entities.add(drop);}
                else {var chest=new Inventory();chest.contents.add(duplicate);p.currentCarrierObservedInventories.add(chest);}
                p.tickCarried(1050);
                check(p.currentCarrierState.phase()==RiftCarrierPolicy.Phase.CHARGE_DROPPED,"owned token outside holder inventory makes custody ambiguous");
                check(p.holder.inventory.contents.stream().allMatch(Objects::isNull),"ambiguous token cannot remain legal after purge");
            }
            case "drop-loss", "container-loss", "cursor-loss", "release-token" -> {
                p.pickUpCurrentCarrierCharge(p.holder);p.tickCarried(1000);
                var carried=p.holder.inventory.contents.get(0);
                var unrelated=new ItemStack(Material.AMETHYST_SHARD,2);p.other.inventory.contents.add(unrelated);
                Inventory container=new Inventory();
                if(args[0].equals("drop-loss")){
                    p.holder.inventory.contents.clear();var drop=new Item();drop.stack=carried;WORLD.entities.add(drop);
                }else if(args[0].equals("cursor-loss")){
                    p.holder.inventory.contents.clear();p.holder.cursor=carried;
                }else if(args[0].equals("container-loss")){
                    p.holder.inventory.contents.clear();container.contents.add(carried);p.currentCarrierObservedInventories.add(container);
                }
                if(args[0].equals("release-token"))p.releaseCurrentCarrierHolder(p.holder.id,"test");else p.tickCarried(1050);
                check(p.currentCarrierState.phase()==RiftCarrierPolicy.Phase.CHARGE_DROPPED,"loss must release custody");
                check(p.holder.inventory.contents.stream().allMatch(Objects::isNull)&&container.contents.stream().allMatch(Objects::isNull)
                        &&p.holder.cursor==null&&WORLD.entities.stream().allMatch(e->!e.valid),"loss/release must leave no stale token in loaded owned locations");
                check(p.other.inventory.contents.get(0)==unrelated,"cleanup must not change another player's unrelated item");
                check(p.holder.getPotionEffect(PotionEffectType.SLOWNESS)==null,"loss/release must remove carrier-owned slowness");
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
                                      "core-to-holder-beam", "owner-core-outline", "offhand-carrier-charge", "slowness-storage", "slowness-offhand",
                                      "stronger-unrelated", "rebind-pdc-rejected", "stronger-replaces-ours", "weaker-restored", "transfer-once", "transfer-offhand",
                                      "duplicate-ground", "duplicate-container", "duplicate-rejected", "stacked-rejected",
                                      "drop-loss", "container-loss", "cursor-loss", "release-token"])
def test_charge_inventory_and_personal_presentation(inventory_probe, scenario):
    result = subprocess.run(["java", "-Xmx64m", "-cp", str(inventory_probe),
                             "CarrierInventoryAcceptanceProbe", scenario], capture_output=True, text=True, timeout=15)
    assert result.returncode == 0, result.stdout + result.stderr


def test_container_tracking_is_wired_to_real_inventory_boundaries():
    source = EVENT.read_text(encoding="utf-8")
    for signature in ("public void onCarrierChargeInventoryOpen(",
                      "public void onCarrierChargeInventoryClick(",
                      "public void onCarrierChargeInventoryDrag("):
        assert signature in source, "container cleanup must observe real inventory events"
        assert "observeCurrentCarrierInventory(" in declaration(source, signature)


def test_reset_charge_purge_helper_removes_tagged_charge_from_all_owned_locations(inventory_probe):
    result = subprocess.run(["java", "-Xmx64m", "-cp", str(inventory_probe),
                             "CarrierInventoryAcceptanceProbe", "reset-purge-all-locations"],
                            capture_output=True, text=True, timeout=15)
    assert result.returncode == 0, result.stdout + result.stderr


def test_objective_reset_purges_wave1_charge_from_all_owned_inventory_locations():
    source = EVENT.read_text(encoding="utf-8")
    reset = declaration(source, "private boolean clearWaveObjectiveState()")

    assert RESET_CARRIER_INVENTORY_HELPER in source, "production reset charge purge helper is missing"
    assert "clearCurrentCarrierInventoryChargeForReset();" in reset, (
        "objective reset must route through the production helper that purges storage, offhand, cursor, and observed containers"
    )


def test_official_wave1_restart_preserves_and_rehydrates_the_same_charge_checkpoint():
    source = EVENT.read_text(encoding="utf-8")
    progress = declaration(source, "private Map<String, String> waveObjectiveProgressSnapshot()")
    restore = declaration(source, "private void restoreWave1CarrierCheckpoint(")
    bootstrap = declaration(source, "private void tryBootstrap()")
    restart_ids = declaration(source, "private Set<UUID> restartPreservedEntityIds()")
    disable = declaration(source, "public void onDisable()")
    tick = declaration(source, "private void tickCurrentCarrierObjective(long now)")

    assert "captureWave1CarrierCheckpoint()" in progress
    assert "RiftCarrierRecoverySnapshot.encode" in declaration(
        source, "private Map<String, String> captureWave1CarrierCheckpoint()"
    )
    assert "RiftCarrierRecoverySnapshot.decode" in restore
    assert "currentCarrierInventoryToken = decoded.inventoryToken();" in restore
    assert "currentCarrierCharges = saved.delivered();" in restore
    assert "canResumeWave1AfterRestart()" in bootstrap
    assert "restorePersistedWave1Objective();" in source
    assert "preserveWave1ForRestart" in disable
    assert "preserveWaveOne" in restart_ids
    assert "currentCarrierRestartReconciliationPending" in tick
    assert "markWave1RecoveryRequired(\"WAVE1_RESTART_CARRIED_CHARGE_NOT_FOUND\")" in tick
