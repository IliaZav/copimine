"""Execute the real teleport boundary for bed-pending and admitted room owners."""
from pathlib import Path
import subprocess

from tests.test_end_rift_death_item_integrations import extract

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"

STUBS = r'''
import java.util.*;
import me.copimine.endevent.domain.ChamberIsolationPolicy;
import me.copimine.endevent.domain.RealitySplitPlayerTeleportPolicy;
import me.copimine.endevent.runtime.AttemptLifecycleController;
public class ReturnTeleportProbe {
    static final UUID OWNER=new UUID(0,1);
    static final String WORLD="configured-arena";
    static class Location implements Cloneable {
        String world;double x,y,z;
        Location(String world,double x,double y,double z){this.world=world;this.x=x;this.y=y;this.z=z;}
        String getWorld(){return world;} double getX(){return x;} double getY(){return y;} double getZ(){return z;}
        public Location clone(){return new Location(world,x,y,z);}
    }
    static class Player {
        ReturnTeleportProbe main;boolean reject,fail,nested;int calls;
        Player(ReturnTeleportProbe main){this.main=main;}UUID getUniqueId(){return OWNER;}
        boolean teleport(Location to){
            calls++;
            if(fail)throw new IllegalStateException("synthetic other-plugin failure");
            if(nested){var foreign=new PlayerTeleportEvent(this,new Location(WORLD,0,68,10));
                main.onRealitySplitPlayerTeleport(foreign);
                require(foreign.cancelled,"exact return permit allowed a different nested room teleport");}
            var event=new PlayerTeleportEvent(this,to);main.onRealitySplitPlayerTeleport(event);
            return !reject&&!event.cancelled;
        }
    }
    static class PlayerTeleportEvent {
        Player player;Location to;boolean cancelled;
        PlayerTeleportEvent(Player player,Location to){this.player=player;this.to=to;}
        Player getPlayer(){return player;}Location getTo(){return to;}String getCause(){return "PLUGIN";}
        void setCancelled(boolean value){cancelled=value;}
    }
    static class Chambers {
        ChamberIsolationPolicy.Assignment assignment(){return new ChamberIsolationPolicy.Assignment(2,Map.of(OWNER,0));}
        boolean allChambersComplete(long generation){return false;}
    }
    long generation=43;String eventId="synthetic-event";boolean official=true;
    AttemptLifecycleController attemptLifecycle=new AttemptLifecycleController();
    Map<UUID,Location> realitySplitPlayerTeleportPermits=new HashMap<>();
    Chambers realitySplitChamberController=new Chambers();
    boolean isRealitySplitPlayerRuntimeActive(){return true;}
    boolean isOfficialWave7ReturnContext(){return official;}
    Location coreCombatAnchorLocation(){return new Location(WORLD,0,68,0);}
    boolean isArenaLocation(Location location){return WORLD.equals(location.world)&&Math.abs(location.x)<=20&&Math.abs(location.z)<=20;}
    java.util.logging.Logger getLogger(){return java.util.logging.Logger.getAnonymousLogger();}
    static void require(boolean value,String reason){if(!value)throw new AssertionError(reason);}
    ReturnTeleportProbe(){attemptLifecycle.begin(43,Set.of(OWNER));attemptLifecycle.enableWave7Returns(43);}
    void pending(){attemptLifecycle.markDead(OWNER,43);attemptLifecycle.markAlive(OWNER,43);}
'''


def run_probe(tmp_path, signatures, body):
    source = SOURCE.read_text(encoding="utf-8")
    adapters = "\n".join(extract(source, signature) for signature in signatures)
    probe = tmp_path / "ReturnTeleportProbe.java"
    probe.write_text(STUBS + adapters + body + "\n}\n", encoding="utf-8")
    files = [ROOT / "copimine-end-event/src/me/copimine/endevent" / path for path in (
        "runtime/AttemptLifecycleController.java", "domain/ChamberIsolationPolicy.java",
        "domain/RealitySplitPlayerTeleportPolicy.java")]
    result = subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(tmp_path),
                             *map(str, files), str(probe)], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
    result = subprocess.run(["java", "-cp", str(tmp_path), "ReturnTeleportProbe"], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr


BOUNDARY = (
    "void onRealitySplitPlayerTeleport(PlayerTeleportEvent event)",
    "boolean isRealitySplitPlayerTeleportPermitted(PlayerTeleportEvent event)",
    "boolean sameTeleportDestination(Location expected, Location actual)",
    "void issueRealitySplitPlayerTeleportPermit(UUID playerId, Location destination)",
    "void clearRealitySplitPlayerTeleportPermit(UUID playerId, Location destination)",
)


def test_pending_owner_can_remain_at_bed_but_cannot_teleport_into_a_trial(tmp_path):
    run_probe(tmp_path, BOUNDARY, r'''
    public static void main(String[] args){
        var main=new ReturnTeleportProbe();var player=new Player(main);main.pending();
        var bed=new PlayerTeleportEvent(player,new Location("bed-world",100,70,100));
        main.onRealitySplitPlayerTeleport(bed);
        require(!bed.cancelled,"Wave 7 room guard intercepted a pending owner's ordinary bed-world teleport");
        var entrance=new PlayerTeleportEvent(player,new Location(WORLD,0,68,-22));
        main.onRealitySplitPlayerTeleport(entrance);
        require(!entrance.cancelled,"pending owner cannot reach the arena-derived outside entrance");
        var bypass=new PlayerTeleportEvent(player,new Location(WORLD,0,68,-10));
        main.onRealitySplitPlayerTeleport(bypass);
        require(bypass.cancelled,"pending owner entered a room without explicit return admission");
        var token=main.attemptLifecycle.beginReturn(OWNER,43,0,40);
        require(token!=null,"staging starts");
        var staging=new PlayerTeleportEvent(player,new Location(WORLD,0,68,-10));
        main.onRealitySplitPlayerTeleport(staging);
        require(staging.cancelled,"staging alone must not authorize an arena teleport");
        require(main.attemptLifecycle.completeReturn(token,40,0),"return admits");
        var own=new PlayerTeleportEvent(player,new Location(WORLD,0,68,-10));main.onRealitySplitPlayerTeleport(own);
        require(!own.cancelled,"admitted owner must keep the existing own-room teleport policy");
        var foreign=new PlayerTeleportEvent(player,new Location(WORLD,0,68,10));main.onRealitySplitPlayerTeleport(foreign);
        require(foreign.cancelled,"admitted owner escaped into another unfinished room");
        main.onRealitySplitPlayerTeleport(bed);require(bed.cancelled,"admitted combat owner escaped arena containment");
        var test=new ReturnTeleportProbe();test.official=false;test.pending();
        var sandbox=new PlayerTeleportEvent(new Player(test),new Location(WORLD,0,68,-10));test.onRealitySplitPlayerTeleport(sandbox);
        require(!sandbox.cancelled,"official return policy changed the existing test-wave teleport path");
    }
''')


def test_owned_return_teleport_permit_is_exact_and_always_cleared(tmp_path):
    run_probe(tmp_path, BOUNDARY + ("boolean teleportWave7Return(Player player, Location destination)",), r'''
    public static void main(String[] args){
        var main=new ReturnTeleportProbe();var player=new Player(main);main.pending();
        var entrance=new Location(WORLD,0,68,-22);var own=new Location(WORLD,0,68,-10);
        require(main.teleportWave7Return(player,entrance),"internal entrance transfer failed");
        require(main.realitySplitPlayerTeleportPermits.isEmpty(),"entrance permit leaked");
        player.nested=true;require(main.teleportWave7Return(player,own),"permitted pre-admission own-claim transfer failed");
        require(main.realitySplitPlayerTeleportPermits.isEmpty(),"room permit leaked after nested event");
        require(!main.attemptLifecycle.isCombatAdmitted(OWNER,43),"moving with permit itself granted combat admission");
        player.reject=true;require(!main.teleportWave7Return(player,own),"another plugin's teleport cancellation was ignored");
        require(main.realitySplitPlayerTeleportPermits.isEmpty(),"permit leaked after cancelled teleport");
        player.fail=true;try{main.teleportWave7Return(player,own);throw new AssertionError("teleport failure swallowed");}
        catch(IllegalStateException expected){}
        require(main.realitySplitPlayerTeleportPermits.isEmpty(),"permit leaked after throwing teleport");
        require(!main.teleportWave7Return(null,own)&&!main.teleportWave7Return(player,null),"missing destination/player accepted");
    }
''')
