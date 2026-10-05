"""Execute the shared containment predicate and real projectile incarnation boundary."""
from pathlib import Path
import subprocess

from tests.test_end_rift_death_item_integrations import extract

ROOT = Path(__file__).resolve().parents[1]


def test_pending_respawn_and_old_projectiles_cannot_bypass_return(tmp_path):
    source = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(encoding="utf-8")
    signatures = (
        "boolean isRealitySplitPlayerCandidate(",
        "void rememberWave7ProjectileIncarnations(Entity entity, int wave)",
        "boolean wave7ProjectileMayHit(Entity projectile, Player player)",
    )
    adapters = "\n".join(extract(source, signature) for signature in signatures)
    probe = tmp_path / "ReturnAdmissionProbe.java"
    probe.write_text(r'''
import java.util.*;
import me.copimine.endevent.runtime.AttemptLifecycleController;
public class ReturnAdmissionProbe {
    static final UUID OWNER=new UUID(0,1);
    enum GameMode { SURVIVAL,SPECTATOR,CREATIVE }
    static class Entity {UUID id=UUID.randomUUID();int wave=7;boolean official=true;UUID getUniqueId(){return id;}}
    static class Projectile extends Entity {}
    static class Player extends Entity {
        Player(){id=OWNER;} boolean isOnline(){return true;} boolean isDead(){return false;}
        double getHealth(){return 20D;}GameMode getGameMode(){return GameMode.SURVIVAL;}
    }
    static class ChamberIsolationPolicy {record Assignment(Map<UUID,Integer> chamberByPlayer){}}
    AttemptLifecycleController attemptLifecycle=new AttemptLifecycleController();
    Set<UUID> officialRewardRoster=new HashSet<>(Set.of(OWNER));
    Map<UUID,Map<UUID,Long>> wave7ProjectileIncarnations=new LinkedHashMap<>();
    long generation=43;String eventId="synthetic-event";boolean testWaveFrontVisualMode;
    Object keyWave=new Object();
    boolean isOfficialWave7ReturnContext(){return true;}
    boolean isOfficialEntity(Entity entity){return entity.official;}
    boolean ownedBySession(Entity entity,String event,long gen){return gen==43;}
    int readInt(Entity entity,Object key,int fallback){return entity.wave;}
''' + adapters + r'''
    static void require(boolean value,String reason){if(!value)throw new AssertionError(reason);}
    public static void main(String[] args){
        var main=new ReturnAdmissionProbe();var player=new Player();
        var assignment=new ChamberIsolationPolicy.Assignment(Map.of(OWNER,0));
        main.attemptLifecycle.begin(43,Set.of(OWNER));main.attemptLifecycle.enableWave7Returns(43);
        require(main.isRealitySplitPlayerCandidate(player,assignment),"initial owner participates in containment");
        var old=new Projectile();main.rememberWave7ProjectileIncarnations(old,7);
        require(main.wave7ProjectileMayHit(old,player),"current launched attack may affect current owner");
        main.attemptLifecycle.markDead(OWNER,43);main.attemptLifecycle.markAlive(OWNER,43);
        require(!main.isRealitySplitPlayerCandidate(player,assignment),"watchdog, move, teleport, join and world-change must not force bed return");
        var token=main.attemptLifecycle.beginReturn(OWNER,43,0,40);
        require(!main.isRealitySplitPlayerCandidate(player,assignment),"staging does not become room admission");
        main.attemptLifecycle.completeReturn(token,40,0);
        require(main.isRealitySplitPlayerCandidate(player,assignment),"only completed explicit return restores containment");
        require(!main.wave7ProjectileMayHit(old,player),"old lethal projectile cannot affect a returned incarnation");
        var fresh=new Projectile();main.rememberWave7ProjectileIncarnations(fresh,7);
        require(main.wave7ProjectileMayHit(fresh,player),"new attacks still work after re-entry");
        var untracked=new Projectile();require(!main.wave7ProjectileMayHit(untracked,player),"missing official launch provenance fails closed");
        var natural=new Projectile();natural.official=false;
        require(main.wave7ProjectileMayHit(natural,player),"ordinary projectiles are unaffected");
        for(int i=0;i<600;i++)main.rememberWave7ProjectileIncarnations(new Projectile(),7);
        require(main.wave7ProjectileIncarnations.size()<=256,"tracked provenance remains bounded across projectile bursts");
        main.generation=44;require(!main.wave7ProjectileMayHit(fresh,player),"generation change invalidates launch receipt");
    }
}
''', encoding="utf-8")
    lifecycle = ROOT / "copimine-end-event/src/me/copimine/endevent/runtime/AttemptLifecycleController.java"
    result = subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(tmp_path), str(lifecycle), str(probe)], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
    result = subprocess.run(["java", "-cp", str(tmp_path), "ReturnAdmissionProbe"], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
