"""Run the actual official obelisk journal gate across the first applied stage."""
import subprocess
from pathlib import Path
from tests.test_wave_navigation_adapter import extract

ROOT = Path(__file__).resolve().parents[1]


def test_emergence_continues_only_in_its_own_live_journal(tmp_path):
    source = (ROOT/'copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java').read_text('utf-8')
    method = extract(source, 'boolean journalAllowsLiveObeliskStageMutation()')
    java = tmp_path/'JournalProbe.java'
    java.write_text('''
public class JournalProbe {
 String eventId="event"; long generation=9; String worldName="arena"; boolean official=true;
 boolean isOfficialCurrentAttempt(){return official;}
 HazardMutationJournal hazardJournal=new HazardMutationJournal();
 static class HazardMutationJournal {
  enum Status {PREPARED,APPLIED,RESTORED,EMPTY}
  record Snapshot(boolean valid,String eventId,long generation,String world,Status status){}
  Snapshot current=new Snapshot(true,"event",9,"arena",Status.PREPARED);
  Snapshot load(){return current;}
 }
 void check(boolean value){if(journalAllowsLiveObeliskStageMutation()!=value)throw new AssertionError(hazardJournal.current);}
 public static void main(String[] args){
  var p=new JournalProbe(); p.check(true);
  p.hazardJournal.current=new HazardMutationJournal.Snapshot(true,"event",9,"arena",HazardMutationJournal.Status.APPLIED);p.check(true);
  p.hazardJournal.current=new HazardMutationJournal.Snapshot(true,"other",9,"arena",HazardMutationJournal.Status.APPLIED);p.check(false);
  p.hazardJournal.current=new HazardMutationJournal.Snapshot(true,"event",8,"arena",HazardMutationJournal.Status.PREPARED);p.check(false);
  p.hazardJournal.current=new HazardMutationJournal.Snapshot(true,"event",9,"elsewhere",HazardMutationJournal.Status.PREPARED);p.check(false);
  p.hazardJournal.current=new HazardMutationJournal.Snapshot(true,"event",9,"arena",HazardMutationJournal.Status.RESTORED);p.check(false);
  p.hazardJournal.current=new HazardMutationJournal.Snapshot(false,"event",9,"arena",HazardMutationJournal.Status.APPLIED);p.check(false);
  p.official=false;p.check(false);
  p.hazardJournal.current=new HazardMutationJournal.Snapshot(true,"event",9,"arena",HazardMutationJournal.Status.APPLIED);p.check(true);
  p.hazardJournal.current=new HazardMutationJournal.Snapshot(true,"event",8,"arena",HazardMutationJournal.Status.APPLIED);p.check(false);
 }
''' + method + '\n}', encoding='utf-8')
    subprocess.run(['javac','-d',str(tmp_path),str(java)],check=True,capture_output=True)
    subprocess.run(['java','-cp',str(tmp_path),'JournalProbe'],check=True,capture_output=True)
