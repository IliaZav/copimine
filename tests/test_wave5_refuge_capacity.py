"""No late cycle may silently strand most of the frozen party."""
from pathlib import Path
import subprocess
ROOT=Path(__file__).resolve().parents[1]


def test_actual_cycle_footprints_fit_every_supported_party(tmp_path):
    probe=tmp_path/'RefugeCapacity.java'
    probe.write_text(r"""import me.copimine.endevent.domain.*;
public class RefugeCapacity {
 public static void main(String[] args){
  for(int players:new int[]{1,2,5,10,11,20}){
   int previousSide=3,previousCount=20;
   int cycles=BlackFogTimingPolicy.cycleCount(players);
   for(int cycle=0;cycle<cycles;cycle++){
    int side=BlackFogSafeZonePolicy.sideForCycleAndPlayers(cycle,players);
    int zones=BlackFogTimingPolicy.zoneCount(players,cycle);
    if(side<1||side>3||side>previousSide||zones>previousCount||zones*side*side<players)
     throw new AssertionError("party="+players+" cycle="+cycle+" side="+side+" zones="+zones+" capacity="+(zones*side*side));
    previousSide=side;previousCount=zones;
   }
  }
 }
}""",encoding='utf-8')
    compiled=subprocess.run(['javac','-sourcepath',str(ROOT/'copimine-end-event/src'),'-d',str(tmp_path),str(probe)],capture_output=True,text=True)
    assert compiled.returncode==0,compiled.stdout+compiled.stderr
    run=subprocess.run(['java','-cp',str(tmp_path),'RefugeCapacity'],capture_output=True,text=True)
    assert run.returncode==0,run.stdout+run.stderr


def test_walkable_routes_distinct_capacity_and_bounded_warning(tmp_path):
    probe=tmp_path/'RefugeRoutes.java'
    probe.write_text(r"""import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import me.copimine.endevent.domain.BlackFogRefugeAccessPolicy;
import me.copimine.endevent.domain.BlackFogRefugeAccessPolicy.Cell;
public class RefugeRoutes {
 static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
 public static void main(String[] args){
  var start=List.of(new Cell(1,1));var target=Set.of(new Cell(3,1));
  var wall=BlackFogRefugeAccessPolicy.plan(0,0,10,10,start,target,p->p.x()!=2);
  check(!wall.admitted(),"nearby refuge behind an unbroken wall is unreachable");
  var detour=BlackFogRefugeAccessPolicy.plan(0,0,10,10,start,target,p->p.x()!=2||p.z()==8);
  check(detour.admitted()&&detour.longestPathBlocks()==16,"actual detour replaces Euclidean distance");
  check(detour.requiredLeadMillis()>=6900&&detour.requiredLeadMillis()<7000,"slowed walking plus reaction margin");
  var starts=new ArrayList<Cell>();var targets=new HashSet<Cell>();
  for(int players:new int[]{1,2,5,10,11,20}){
   starts.clear();targets.clear();for(int i=0;i<players;i++){starts.add(new Cell(0,i));targets.add(new Cell(5,i));}
   var reads=new AtomicInteger();
   var plan=BlackFogRefugeAccessPolicy.plan(0,0,6,20,starts,targets,p->{reads.incrementAndGet();return true;});
   check(plan.admitted()&&plan.longestPathBlocks()==5,"one distinct reachable slot per player");
   check(reads.get()==147,"terrain sampled once per admission, not once per player");
   if(players>1){targets.remove(new Cell(5,0));check(!BlackFogRefugeAccessPolicy.plan(0,0,6,20,starts,targets,p->true).admitted(),"insufficient capacity rejected");}
  }
  check(!BlackFogRefugeAccessPolicy.plan(0,0,4,0,List.of(new Cell(0,0),new Cell(0,0)),
   Set.of(new Cell(0,0),new Cell(4,0)),p->p.x()!=2).admitted(),"two owners cannot share the only reachable slot");
  check(!BlackFogRefugeAccessPolicy.plan(0,0,1000,1000,start,target,p->{throw new AssertionError("unbounded terrain read");}).admitted(),"oversized grid fails before reads");
  check(!BlackFogRefugeAccessPolicy.plan(0,0,60,0,List.of(new Cell(0,0)),Set.of(new Cell(60,0)),p->true).admitted(),"unbounded lead times refused");
 }
}""",encoding='utf-8')
    compiled=subprocess.run(['javac','-sourcepath',str(ROOT/'copimine-end-event/src'),'-d',str(tmp_path),str(probe)],capture_output=True,text=True)
    assert compiled.returncode==0,compiled.stdout+compiled.stderr
    run=subprocess.run(['java','-cp',str(tmp_path),'RefugeRoutes'],capture_output=True,text=True)
    assert run.returncode==0,run.stdout+run.stderr
