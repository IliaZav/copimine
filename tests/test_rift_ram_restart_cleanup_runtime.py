"""The event-level restart cleanup must not delete the saved marksman."""
from pathlib import Path
import subprocess
import pytest
from tests.test_wave_navigation_adapter import extract

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"
DOMAIN = ROOT / "copimine-end-event/src/me/copimine/endevent/domain"


@pytest.mark.parametrize("scenario", ["alive", "corrupt"])
def test_restart_cleanup_preserves_required_actor_ids(tmp_path, scenario):
    source = SOURCE.read_text("utf-8")
    if "private boolean clearRiftRamRestartPresentation()" in source:
        method = extract(source, "boolean clearRiftRamRestartPresentation()")
    else:
        start = source.index("            // Transient displays and adds are rebuilt from the minimum checkpoint.")
        end = source.index("            riftRam.restore(riftRamCheckpoint);", start)
        method = "boolean clearRiftRamRestartPresentation(){" + source[start:end] + "return true;}"
    java = tmp_path / "RamRestartCleanupProbe.java"
    java.write_text(r'''
import java.util.*;
public class RamRestartCleanupProbe {
    static class Entity {UUID id=UUID.randomUUID();boolean valid=true;int wave=6;
        UUID getUniqueId(){return id;}boolean isValid(){return valid;}void remove(){valid=false;}}
    static class Ravager extends Entity {}
    Map<UUID,Entity> ownedEntities=new LinkedHashMap<>();Map<String,String> riftRamCheckpoint=new HashMap<>();
    Object keyWave=new Object();boolean recovery;
    int readInt(Entity e,Object key,int fallback){return e.wave;}
    Entity unregisterOwnedEntity(UUID id,String reason){return ownedEntities.remove(id);}
    void markWave6RecoveryRequired(String reason){recovery=true;}
''' + method + r'''
    static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
    public static void main(String[] args){
        var p=new RamRestartCleanupProbe();var ram=new Ravager();var guard=new Entity();
        var stale=new Entity();var otherWave=new Entity();otherWave.wave=3;
        for(Entity e:List.of(ram,guard,stale,otherWave))p.ownedEntities.put(e.id,e);
        p.riftRamCheckpoint.putAll(Map.of("ram.schema","2","ram.impacts","5",
            "ram.uuid",ram.id.toString(),"ram.skeleton",guard.id.toString(),
            "ram.pillar-id",UUID.randomUUID().toString(),"ram.pillar-impacts","1","ram.complete","false"));
        if(args[0].equals("corrupt"))p.riftRamCheckpoint.put("ram.skeleton","invalid-uuid");
        boolean accepted=p.clearRiftRamRestartPresentation();
        check(ram.valid&&guard.valid&&otherWave.valid,"restart cleanup removed a required/scope-foreign actor");
        if(args[0].equals("alive"))check(accepted&&!stale.valid,"stale presentation was not removed");
        else check(!accepted&&p.recovery&&stale.valid,"corrupt ownership mutated actors instead of recovering");
    }
}
''', encoding="utf-8")
    built = subprocess.run(["javac", "-encoding", "UTF-8", "-sourcepath", str(DOMAIN.parents[3]),
                            "-d", str(tmp_path), str(java)], capture_output=True, text=True)
    assert built.returncode == 0, built.stdout + built.stderr
    ran = subprocess.run(["java", "-cp", str(tmp_path), "RamRestartCleanupProbe", scenario],
                         capture_output=True, text=True)
    assert ran.returncode == 0, ran.stdout + ran.stderr
