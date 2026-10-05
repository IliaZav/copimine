"""Run the production per-death listener with detached Bukkit boundaries."""
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]


def test_current_slots_are_retained_without_drops_reinsertion_or_xp_change(tmp_path):
    listener = ROOT / "copimine-end-event/src/me/copimine/endevent/runtime/EventDeathProtectionListener.java"
    assert listener.is_file(), "Active participants have no production inventory-retention listener"
    files = {
        "org/bukkit/Material.java": "package org.bukkit; public enum Material { AIR, STONE }",
        "org/bukkit/event/Listener.java": "package org.bukkit.event; public interface Listener {}",
        "org/bukkit/event/EventPriority.java": "package org.bukkit.event; public enum EventPriority { LOWEST, HIGHEST, MONITOR }",
        "org/bukkit/event/EventHandler.java": "package org.bukkit.event; public @interface EventHandler { EventPriority priority(); boolean ignoreCancelled() default false; }",
        "org/bukkit/inventory/ItemStack.java": r'''
package org.bukkit.inventory;
import org.bukkit.Material;
public class ItemStack implements Cloneable {
    public final String data; private int amount;
    public ItemStack(String data,int amount){this.data=data;this.amount=amount;}
    public Material getType(){return Material.STONE;}
    public int getAmount(){return amount;}
    public void setAmount(int value){amount=value;}
    public boolean isSimilar(ItemStack other){return other!=null && data.equals(other.data);}
    public ItemStack clone(){return new ItemStack(data,amount);}
}
''',
        "org/bukkit/inventory/PlayerInventory.java": r'''
package org.bukkit.inventory;
public class PlayerInventory {
    public ItemStack[] storage,armor,extra;
    public ItemStack[] getStorageContents(){return storage;}
    public ItemStack[] getArmorContents(){return armor;}
    public ItemStack[] getExtraContents(){return extra;}
}
''',
        "org/bukkit/entity/Player.java": r'''
package org.bukkit.entity;
import java.util.UUID;
import org.bukkit.inventory.PlayerInventory;
public class Player {
    public final PlayerInventory inventory=new PlayerInventory();
    public PlayerInventory getInventory(){return inventory;}
    public UUID getUniqueId(){return new UUID(0,7);}
}
''',
        "org/bukkit/event/entity/PlayerDeathEvent.java": r'''
package org.bukkit.event.entity;
import java.util.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
public class PlayerDeathEvent {
    public final Player player=new Player();
    public final List<ItemStack> drops=new ArrayList<>(),itemsToKeep=new ArrayList<>();
    public boolean cancelled,keepInventory,keepLevel;
    public int droppedExp=7,newExp=3,newLevel=2;
    public Player getEntity(){return player;}
    public List<ItemStack> getDrops(){return drops;}
    public List<ItemStack> getItemsToKeep(){return itemsToKeep;}
    public boolean isCancelled(){return cancelled;}
    public boolean getKeepInventory(){return keepInventory;}
    public void setKeepInventory(boolean value){keepInventory=value;}
}
''',
        "InventoryDeathProbe.java": r'''
import java.util.*;
import java.util.logging.Logger;
import org.bukkit.inventory.*;
import org.bukkit.event.entity.PlayerDeathEvent;
import me.copimine.endevent.runtime.EventDeathProtectionListener;
public class InventoryDeathProbe {
    static boolean eligible=true;
    static void require(boolean v,String m){if(!v)throw new AssertionError(m);}
    static PlayerDeathEvent event() {
        var event=new PlayerDeathEvent();
        event.player.inventory.storage=new ItemStack[36];
        for(int i=0;i<36;i++)event.player.inventory.storage[i]=new ItemStack("named-enchanted-slot-"+i,1);
        event.player.inventory.storage[0]=new ItemStack("apples-left-after-use",1);
        event.player.inventory.armor=new ItemStack[]{new ItemStack("binding-armor-damage-73",1),new ItemStack("vanishing-armor-damage-99",1)};
        event.player.inventory.extra=new ItemStack[]{new ItemStack("named-offhand-shield-damage-42",1)};
        // Vanilla drops may share a handle with live slots. Never setAmount on
        // those handles while subtracting inventory-origin death drops.
        event.drops.addAll(Arrays.asList(event.player.inventory.storage));
        event.drops.add(event.player.inventory.armor[0]);
        event.drops.add(event.player.inventory.extra[0]);
        event.itemsToKeep.add(event.player.inventory.extra[0].clone());
        return event;
    }
    public static void main(String[] args) {
        var listener=new EventDeathProtectionListener(e->eligible,Logger.getAnonymousLogger());
        var event=event();
        listener.capture(event);
        require(!event.keepInventory,"LOWEST only captures ownership, it does not mutate event policy");
        eligible=false; // last-player cleanup can end the attempt after capture
        var independentReward=event.player.inventory.storage[0].clone();
        event.drops.add(independentReward); // even same metadata is a different added drop
        listener.retain(event);
        require(event.keepInventory,"eligible lethal event must retain actual current slots");
        require(event.drops.size()==1 && event.drops.get(0)==independentReward,"remove inventory-origin drops and preserve independent reward identity");
        require(event.player.inventory.storage[0].getAmount()==1,"spent apples are not replenished or removed from real slots");
        require(event.player.inventory.armor[0].data.endsWith("73") && event.player.inventory.extra[0].data.endsWith("42"),"real durability, armor and offhand remain unchanged");
        require(event.itemsToKeep.size()==1,"pinned keepInventory path skips processKeep; do not erase other plugins' list");
        require(!event.keepLevel && event.droppedExp==7 && event.newExp==3 && event.newLevel==2,"XP policy is untouched");
        listener.retain(event);
        require(event.drops.size()==1,"duplicate retention callback cannot consume the independent reward");
        listener.finish(event);
        require(!listener.protects(event),"event entitlement receipt ends with the callback");

        var normal=event();listener.capture(normal);listener.retain(normal);
        require(!normal.keepInventory && normal.drops.size()==38,"nonparticipants keep vanilla drop behavior");
        eligible=true;
        var cancelled=event();listener.capture(cancelled);cancelled.cancelled=true;listener.retain(cancelled);listener.finish(cancelled);
        require(!cancelled.keepInventory && cancelled.drops.size()==38,"cancelled death cannot mutate retention or drops");
        var second=event();listener.capture(second);listener.retain(second);listener.finish(second);
        require(second.keepInventory && second.drops.isEmpty() && second.player.inventory.storage[0].getAmount()==1,"next death captures its current supplies independently");

        // Installed ClearLag HIGH copies drops, clears the event, and emits
        // that copy one tick later. HIGHEST alone cannot remove that copy.
        var forwarded=event();listener.capture(forwarded);
        var reward=forwarded.player.inventory.storage[0].clone();forwarded.drops.add(reward);
        var queued=new ArrayList<ItemStack>();
        Runnable emitter=()->{queued.addAll(forwarded.drops);forwarded.drops.clear();};
        forward(listener,forwarded,emitter);
        listener.retain(forwarded);
        require(queued.size()==1 && queued.get(0)==reward,"delayed drop forwarder must not queue retained inventory copies");
        require(forwarded.drops.isEmpty() && forwarded.keepInventory,"only the independent reward reaches the existing delayed emitter");
        listener.finish(forwarded);

        var partial=event();
        partial.drops.set(0,new ItemStack(partial.player.inventory.storage[0].data,3));
        listener.capture(partial);
        var partialQueue=new ArrayList<ItemStack>();
        forward(listener,partial,()->{partialQueue.addAll(partial.drops);partial.drops.clear();});
        listener.retain(partial);listener.finish(partial);
        require(partialQueue.size()==1 && partialQueue.get(0).getAmount()==2 && partial.drops.isEmpty(),"a merged independent remainder is emitted once while owned quantity is retained");
        require(partial.player.inventory.storage[0].getAmount()==1,"filtering a merged drop never changes the actual inventory handle");

        var failing=event();listener.capture(failing);
        try {forward(listener,failing,()->{throw new IllegalStateException("forwarder failure");});throw new AssertionError("expected forwarder failure");}
        catch(IllegalStateException expected){require(expected.getMessage().equals("forwarder failure"),"preserve original delegate failure");}
        require(failing.drops.size()==38,"delegate failure restores original owned drop view");
        listener.retain(failing);listener.finish(failing);
        var ignored=event();listener.capture(ignored);ignored.cancelled=true;
        final boolean[] called={false};forward(listener,ignored,()->called[0]=true);
        require(!called[0] && ignored.drops.size()==38 && !ignored.keepInventory,"cancelled death cannot reach the known delayed item emitter");
        listener.retain(ignored);listener.finish(ignored);

        eligible=false;
        var outside=event();listener.capture(outside);var ordinary=new ArrayList<ItemStack>();
        forward(listener,outside,()->{ordinary.addAll(outside.drops);outside.drops.clear();});
        require(ordinary.size()==38 && !outside.keepInventory,"ordinary death retains the installed forwarder's drop behavior");

        eligible=true;
        var disabled=event();listener.capture(disabled);listener.clear();eligible=false;listener.retain(disabled);
        require(!disabled.keepInventory,"terminal cleanup revokes pending protection without an orphan flag");
    }
    static void forward(EventDeathProtectionListener listener,PlayerDeathEvent event,Runnable emitter) {
        try {listener.getClass().getMethod("withRetainedDropsHidden",PlayerDeathEvent.class,Runnable.class).invoke(listener,event,emitter);}
        catch(NoSuchMethodException baseline){emitter.run();}
        catch(java.lang.reflect.InvocationTargetException failure){
            if(failure.getCause() instanceof RuntimeException cause)throw cause;
            throw new AssertionError(failure.getCause());
        }
        catch(ReflectiveOperationException failure){throw new AssertionError(failure);}
    }
}
''',
    }
    for relative, contents in files.items():
        path = tmp_path / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(contents, encoding="utf-8")
    generated = list(tmp_path.rglob("*.java"))
    subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(tmp_path), str(listener), *map(str, generated)], check=True, capture_output=True, text=True)
    result = subprocess.run(["java", "-cp", str(tmp_path), "InventoryDeathProbe"], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
