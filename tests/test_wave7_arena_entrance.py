"""Arena-derived return entrance geometry and bounded, fail-closed site probing."""
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]


def test_return_entrance_follows_arena_and_requires_a_safe_site(tmp_path):
    production = ROOT / "copimine-end-event/src/me/copimine/endevent/domain/ArenaEntranceLocator.java"
    assert production.exists(), "No arena-derived return entrance: the portal-room test coordinates cannot be a return destination"
    probe = tmp_path / "ArenaEntranceProbe.java"
    probe.write_text(r'''
import java.util.*;
import me.copimine.endevent.domain.ArenaEntranceLocator;
import me.copimine.endevent.domain.ArenaEntranceLocator.*;
public class ArenaEntranceProbe {
    static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    public static void main(String[] args){
        var arena=new Bounds("arena-A",-20,55,-40,20,75,0);
        var first=ArenaEntranceLocator.find(arena,65,(world,site)->true).orElseThrow();
        var moved=new Bounds("arena-B",980,105,2960,1020,125,3000);
        var second=ArenaEntranceLocator.find(moved,115,(world,site)->{
            require(world.equals("arena-B"),"probe must use the configured arena world");return true;
        }).orElseThrow();
        require(second.x()-first.x()==1000 && second.y()-first.y()==50 && second.z()-first.z()==3000,
            "moving the configured arena must move its entrance; no test-world coordinate literals");
        require(first.z()<arena.minZ()-1 && first.x()>=arena.minX() && first.x()<=arena.maxX(),
            "return vestibule must be outside the combat cuboid with player clearance");
        Set<Site> visited=new HashSet<>();int[] probes={0};
        require(ArenaEntranceLocator.find(arena,65,(world,site)->{
            probes[0]++;require(visited.add(site),"do not repeat physical collision probes");return false;
        }).isEmpty(),"no valid floor/collision must refuse return, never substitute the Core or world spawn");
        require(probes[0]>0 && probes[0]<=180,"local search must have a fixed per-resolution work bound");
        int[] hazards={0};
        var safe=ArenaEntranceLocator.find(arena,65,(world,site)->++hazards[0]==3).orElseThrow();
        require(hazards[0]==3 && !safe.equals(first),"reject unsafe candidates and stop immediately at the accepted safe site");
        require(ArenaEntranceLocator.find(null,65,(world,site)->true).isEmpty(),"missing layout cannot create a fabricated destination");
        require(ArenaEntranceLocator.find(arena,65,null).isEmpty(),"geometry alone is not collision proof");
        boolean invalid=false;try{new Bounds("",1,2,3,0,4,5);}catch(IllegalArgumentException expected){invalid=true;}
        require(invalid,"invalid arena input must be rejected before probing");
    }
}
''', encoding="utf-8")
    result = subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(tmp_path), str(production), str(probe)], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
    result = subprocess.run(["java", "-cp", str(tmp_path), "ArenaEntranceProbe"], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
