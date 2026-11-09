"""Exercise real Wave 7 controllers across the small/large roster boundary."""
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "copimine-end-event/src/me/copimine/endevent"


def test_one_sequential_route_for_every_roster_size(tmp_path):
    probe = tmp_path / "RosterRoutesProbe.java"
    probe.write_text('''
import java.util.*;
import me.copimine.endevent.domain.*;
import me.copimine.endevent.runtime.*;
public class RosterRoutesProbe {
  static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
  static List<UUID> roster(int count){var p=new ArrayList<UUID>();for(int i=1;i<=count;i++)p.add(new UUID(0,i));return p;}
  public static void main(String[] args){
    UUID event=new UUID(17,7);
    // Wave 7 is ONE sequential route at every party size. The party size only
    // changes how many actors the single room must hold, never how many rooms
    // there are: a four-sector split produced the parallel route the
    // specification forbids.
    for(int size:new int[]{1,2,3,4,5,10,11,20}){
      var rooms=new RealitySplitChamberController();rooms.beginWave7(7,roster(size));
      check(rooms.assignment().chamberCount()==1,"every party size shares one arena");
      var trials=new RealitySplitTrialController();
      trials.beginNamed(event,7,rooms.assignment(),RealitySplitTrialController.sequentialRoute());
      check(trials.namedSnapshot().trials().size()==1,"one room means exactly one live trial");
      check(trials.presentableNamedTrials().size()==1,"only the current stage is presentable");
      var owners=trials.namedTrial(0).owners();
      check(owners.size()==size,"every admitted player owns the current stage");
      UUID actor=new UUID(10,1);
      check(rooms.assignEntity(7,actor,0),"assign actor");
      for(UUID player:roster(size))
        check(rooms.allowsMobTarget(7,actor,player)==owners.contains(player),"the single room holds its targets");
      if(trials.namedTrial(0).trial()==RealitySplitTrialController.NamedTrial.PERSONAL_ECHO){
        for(var cell:trials.echoDuelCells(0))
          check(ChamberIsolationPolicy.containsPoint(0,
             10*Math.cos(cell.centerAngleRadians()),10*Math.sin(cell.centerAngleRadians()),4),
             "Echo starts stay inside the one room");
      }
    }
    // The four trials still run in order, and a parallel room may never advance.
    {
      var rooms=new RealitySplitChamberController();rooms.beginWave7(7,roster(6));
      var trials=new RealitySplitTrialController();
      trials.beginNamed(event,7,rooms.assignment(),RealitySplitTrialController.sequentialRoute());
      check(trials.namedSnapshot().trials().size()==1,"a six-player party is still one room");
      check(rooms.allowsMobTarget(7,new UUID(10,0),roster(6).get(5))==false,
            "an unassigned actor is not yet a legal target");
      check(trials.completeNamedActor(event,7,0,RealitySplitTrialController.NamedTrial.WARDEN),"real Warden death");
      check(!trials.allComplete(7),"one Warden must not complete all four sequential trials");
      check(trials.advanceSequentialTrial(event,7,RealitySplitTrialController.NamedTrial.WARDEN),
            "advance completed stage after cleanup");
      check(!trials.advanceSequentialTrial(event,7,RealitySplitTrialController.NamedTrial.WARDEN),
            "duplicate callback cannot advance twice");
    }
  }
}''', encoding="utf-8")
    paths = [SOURCE / path for path in (
        "domain/ChamberIsolationPolicy.java", "domain/RealitySplitBarrierPolicy.java",
        "domain/Wave7AdmissionPolicy.java", "domain/Wave7EchoDuelCellPolicy.java",
        "domain/Wave7NamedTrialActorPolicy.java", "domain/Wave7MinibossScalingPolicy.java",
        "domain/Wave7RoomLayoutPolicy.java", "domain/Wave7TrialRoutePolicy.java",
        "domain/wave7/EchoBehaviorStrategy.java", "domain/wave7/EchoLoadoutState.java",
        "runtime/RealitySplitEchoDuelState.java", "runtime/RealitySplitChamberController.java",
        "runtime/RealitySplitTrialController.java", "runtime/RealitySplitTrialSnapshot.java",
    )]
    built = subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(tmp_path),
                            *map(str, paths), str(probe)], capture_output=True, text=True)
    assert built.returncode == 0, built.stdout + built.stderr
    ran = subprocess.run(["java", "-cp", str(tmp_path), "RosterRoutesProbe"], capture_output=True, text=True)
    assert ran.returncode == 0, ran.stdout + ran.stderr
