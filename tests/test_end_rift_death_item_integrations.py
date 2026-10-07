"""Execute first-party death callbacks; event retention cannot enqueue a second item."""
from pathlib import Path
import subprocess

import pytest

ROOT = Path(__file__).resolve().parents[1]
MODULES = {
    "artifacts": ("copimine-artifacts/src/me/copimine/artifacts/CopiMineArtifacts.java", "void onDeath(PlayerDeathEvent var1)"),
    "election": ("copimine-election-core/src/me/copimine/electioncore/CopiMineElectionCore.java", "void onOfficialDeath(PlayerDeathEvent event)"),
    "admin": ("copimine-admin-plugin/src/me/copimine/ultimateplus/CopiMineUltimateAdminPlus.java", "void onOfficialItemDeath(PlayerDeathEvent e)"),
}


def extract(source, signature):
    start = source.index(signature)
    start = source.rfind("\n", 0, start) + 1
    opening = source.index("{", start)
    depth, end = 1, opening + 1
    while depth:
        depth += (source[end] == "{") - (source[end] == "}")
        end += 1
    return source[start:end]


@pytest.mark.parametrize("module", MODULES)
def test_protected_event_death_cannot_journal_or_queue_a_retained_item(tmp_path, module):
    path, signature = MODULES[module]
    source = (ROOT / path).read_text(encoding="utf-8")
    callback = extract(source, signature).replace("CopiMineArtifacts.OfficialDonationRef", "DeathItemsProbe.OfficialDonationRef").replace("CopiMineArtifacts.SessionState", "DeathItemsProbe.SessionState")
    # The production optional-provider method is added with the fix. The RED
    # baseline callbacks do not ask this question at all, even with a provider
    # advertising protection for this exact lethal event.
    provider_signature = "boolean isEndRiftDeathProtected(PlayerDeathEvent event)"
    provider = extract(source, provider_signature) if provider_signature in source else ""
    call = {"artifacts": "onDeath", "election": "onOfficialDeath", "admin": "onOfficialItemDeath"}[module]
    java = r'''
import java.util.*;
import java.util.logging.Logger;
public class DeathItemsProbe {
    int queued,journaled;
    boolean protectedDeath=true,providerEnabled=true,providerMissing=false,providerThrows=false;
    final Map<UUID,List<ItemStack>> pendingOfficialReturns=new HashMap<>();
    final Map<UUID,SessionState> sessions=new HashMap<>();
    final Set<String> lossJournalInFlight=new HashSet<>();
    static DeathItemsProbe active;
    public static class Player {
        UUID id=new UUID(0,3);
        UUID getUniqueId(){return id;}
        String getName(){return "synthetic";}
        void sendMessage(String value){}
    }
    public static class PlayerDeathEvent {
        final Player player=new Player();
        final List<ItemStack> drops=new ArrayList<>(List.of(new ItemStack()));
        boolean cancelled;
        Player getEntity(){return player;}
        List<ItemStack> getDrops(){return drops;}
        boolean getKeepInventory(){return false;} // mutation listener runs later
        boolean isCancelled(){return cancelled;}
    }
    public static class ItemStack implements Cloneable {
        public ItemStack clone(){return new ItemStack();}
        void setAmount(int value){}
    }
    static class SessionState {
        String purchaseInFlightId="",pinBuffer="";
        List<Object> actions=new ArrayList<>();
    }
    record OfficialDonationRef(UUID ownerUuid,String uniqueItemId,String itemId) {}
    record PluginManager(Plugin plugin) {Plugin getPlugin(String name){return plugin;}}
    static class Bukkit {static PluginManager getPluginManager(){return new PluginManager(active.providerMissing?null:new Plugin());}}
    public static class Plugin {
        public boolean isEnabled(){return active.providerEnabled;}
        public boolean protectsParticipantDeath(PlayerDeathEvent event){if(active.providerThrows)throw new IllegalStateException("synthetic provider failure");return active.protectedDeath && !event.isCancelled();}
    }
    void cancelReturnStoneChannel(Player player){}
    boolean customShopItemsAreVanilla(){return false;}
    OfficialDonationRef officialDonationRef(ItemStack stack){return new OfficialDonationRef(new UUID(0,3),"synthetic-item","test");}
    boolean recordDonationLossJournal(UUID player,Map items,String reason){journaled++;return true;}
    void flushPendingDonationLossJournalAsync(){}
    String color(String value){return value;}
    Logger getLogger(){return Logger.getAnonymousLogger();}
    void deduplicatePresidentMandates(Player player){}
    boolean isApplicationBook(ItemStack stack){return false;}
    boolean isProtectedOfficialItem(ItemStack stack){return true;}
    boolean isPresidentMandate(ItemStack stack){return false;}
    void queueOfficialRestore(UUID player,ItemStack stack){queued++;}
    boolean electionCoreOwns(ItemStack stack){return false;}
    boolean artifactsCoreOwns(ItemStack stack){return false;}
    boolean isTemporaryApplicationBook(ItemStack stack){return false;}
    boolean shouldPersistOfficialItem(ItemStack stack){return true;}
    void audit(String name,String action,String detail,boolean success){}
''' + callback + provider + r'''
    static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    void invoke(PlayerDeathEvent event){CALL(event);}
    int copies(){return queued+journaled+pendingOfficialReturns.values().stream().mapToInt(List::size).sum();}
    public static void main(String[] args) {
        active=new DeathItemsProbe();
        var retained=new PlayerDeathEvent();
        active.invoke(retained);
        require(active.copies()==0,"protected participant death must not create a loss-journal or queued restore copy");
        require(retained.getDrops().size()==1,"mutation remains owned by the event retention listener");
        active.protectedDeath=false;
        active.invoke(new PlayerDeathEvent());
        require(active.copies()==1,"ordinary deaths must preserve the previous item recovery integration");
        active=new DeathItemsProbe();
        active.providerEnabled=false;
        active.invoke(new PlayerDeathEvent());
        require(active.copies()==1,"missing or disabled event protection cannot suppress ordinary recovery");
        active=new DeathItemsProbe();active.providerMissing=true;
        active.invoke(new PlayerDeathEvent());
        require(active.copies()==1,"uninstalled provider cannot suppress ordinary recovery");
        active=new DeathItemsProbe();active.providerThrows=true;
        active.invoke(new PlayerDeathEvent());
        require(active.copies()==1,"failing optional provider keeps ordinary recovery behavior");
        active=new DeathItemsProbe();
        var cancelled=new PlayerDeathEvent();cancelled.cancelled=true;
        active.invoke(cancelled);
        require(active.copies()==0,"cancelled deaths must not enqueue recovery copies");
    }
}
'''
    probe = tmp_path / "DeathItemsProbe.java"
    probe.write_text(java.replace("CALL(event)", call + "(event)"), encoding="utf-8")
    subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(tmp_path), str(probe)], check=True, capture_output=True, text=True)
    result = subprocess.run(["java", "-cp", str(tmp_path), "DeathItemsProbe"], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
