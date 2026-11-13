"""Real projectiles release at the marker, not after a fake impact flight."""
from pathlib import Path
import subprocess
from tests.test_end_rift_death_item_integrations import extract

ROOT = Path(__file__).resolve().parents[1]


def test_production_release_adapter_owns_one_flight(tmp_path):
    source = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(encoding="utf-8")
    method = extract(source, "boolean launchBossSpellDelivery(")
    java = tmp_path / "BossReleaseProbe.java"
    java.write_text('''
import java.util.*;
import me.copimine.endevent.domain.*;
public class BossReleaseProbe {
    static class LivingEntity {UUID getUniqueId(){return new UUID(0,1);}Object getWorld(){return "arena";}}
    static class Player extends LivingEntity {}static class Location {}
    static class Registry {boolean owns(long g){return g==7;}}
    Registry taskRegistry=new Registry();UUID bossUuid=new UUID(0,1);
    int flights,releases;boolean valid=true,active=true;
    boolean isCombatTarget(Player p){return valid;}
    boolean isSpellFlightAllowed(LivingEntity b,boolean forced){return active;}
    boolean launchSpellFlight(LivingEntity b,Location m,String log,String id,UUID target,boolean f,long g,Runnable release){flights++;return true;}
''' + method + '''
    static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
    public static void main(String[] args){
        for(var spell:EndRiftAiPolicy.BossSpell.values()){
            var p=new BossReleaseProbe();var boss=new LivingEntity();var target=new Player();
            boolean physical=BossSpellDeliveryPolicy.physicalProjectile(spell);
            check(p.launchBossSpellDelivery(boss,target,new Location(),spell,false,7,()->p.releases++),"valid release refused");
            check(p.flights==(physical?0:1)&&p.releases==(physical?1:0),"duplicate/fake flight: "+spell);
            for(int failure=0;failure<4;failure++){
                var bad=new BossReleaseProbe();if(failure==0)bad.active=false;
                if(failure==1)bad.bossUuid=new UUID(0,9);
                if(failure==2)bad.valid=false;
                long generation=failure==3?8:7;
                boolean admitted=bad.launchBossSpellDelivery(boss,target,new Location(),spell,false,generation,()->bad.releases++);
                boolean summonException=failure==2&&spell==EndRiftAiPolicy.BossSpell.SUMMON_SERVANTS;
                check(admitted==summonException,"invalid release admitted: "+spell+"/"+failure);
                if(!admitted)check(bad.releases==0&&bad.flights==0,"side effects before admission");
            }
        }
    }
}
''', encoding="utf-8")
    compiled = subprocess.run(["javac", "-encoding", "UTF-8", "-sourcepath",
        str(ROOT / "copimine-end-event/src"), "-d", str(tmp_path), str(java)], capture_output=True, text=True)
    assert compiled.returncode == 0, compiled.stdout + compiled.stderr
    executed = subprocess.run(["java", "-cp", str(tmp_path), java.stem], capture_output=True, text=True)
    assert executed.returncode == 0, executed.stdout + executed.stderr
