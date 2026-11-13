"""Execute real tactical decisions and the shared animation clock."""
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]


def test_contact_pressure_and_fury_are_not_replaced_by_orbit_beats(tmp_path):
    java = tmp_path / "ContactPursuitProbe.java"
    java.write_text('''
import me.copimine.endevent.domain.*;
public class ContactPursuitProbe {
    static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
    public static void main(String[] args){
        for(var phase:BossPhase.values())for(int beat=-8;beat<12;beat++) {
            for(double distance:new double[]{0,2,3.5,4.5}) {
                var p=CombatTacticsPolicy.bossPlan(phase,beat,distance,false,phase==BossPhase.LAST_SEAL);
                check(p.tactic()==CombatTacticsPolicy.BossTactic.MELEE_PRESSURE,"lost contact: "+phase+"/"+beat);
                check(!p.preferOuterRing()&&p.preferredDistance()<BossContactStrikePolicy.MELEE_SWIPE_RANGE,"unreachable swipe");
            }
            for(double distance:new double[]{5,10,30}) {
                var p=CombatTacticsPolicy.bossPlan(phase,beat,distance,false,true);
                if(phase==BossPhase.LAST_SEAL)check(p.tactic()==CombatTacticsPolicy.BossTactic.MELEE_PRESSURE,"fury stopped pursuit");
            }
            var core=CombatTacticsPolicy.bossPlan(phase,beat,2,true,true);
            check(core.preferOuterRing()&&core.tactic()==CombatTacticsPolicy.BossTactic.CREATE_SPACE,"Core exclusion lost");
            for(double bad:new double[]{-1,Double.NaN,Double.POSITIVE_INFINITY}) {
                var p=CombatTacticsPolicy.bossPlan(phase,1,bad,false,false);
                check(p.tactic()!=CombatTacticsPolicy.BossTactic.MELEE_PRESSURE,"invalid distance invented contact");
            }
        }
    }
}
''', encoding="utf-8")
    _run(java, tmp_path)


def test_faster_clips_keep_contact_and_follow_through_on_one_clock(tmp_path):
    java = tmp_path / "ContactPlaybackProbe.java"
    java.write_text('''
import me.copimine.protocol.BossAnimationPlayback;
public class ContactPlaybackProbe {
    static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
    public static void main(String[] args){
        String[] names={"MELEE_SWIPE","CHEST_STRIKE","GROUND_SLAM"};
        int[] contact={26,40,110},length={60,81,181},expected={13,23,55};
        for(int i=0;i<names.length;i++){
            var n=names[i];int c=BossAnimationPlayback.combatTicks(n,contact[i]);
            check(c==expected[i],"contact tuning drift: "+n);
            check(BossAnimationPlayback.authoredTicks(n,c)>=contact[i],"damage before pose");
            check(BossAnimationPlayback.authoredTicks(n,c-1)<contact[i],"missed earliest safe frame");
            check(BossAnimationPlayback.combatTicks(n,length[i])>c,"lost visible recovery");
        }
        check(BossAnimationPlayback.combatTicks("DYING",40)==40,"death cinematic accidentally accelerated");
        check(BossAnimationPlayback.authoredTicks("MELEE_SWIPE",Double.NaN)==0,"nonfinite pose");
    }
}
''', encoding="utf-8")
    _run(java, tmp_path, ROOT / "tools/minecraft-26.3/protocol/src/main/java/me/copimine/protocol/BossAnimationPlayback.java")


def _run(java, output, *sources):
    compiled = subprocess.run(["javac", "-encoding", "UTF-8", "-sourcepath",
        str(ROOT / "copimine-end-event/src"), "-d", str(output), str(java),
        *map(str, sources)], capture_output=True, text=True)
    assert compiled.returncode == 0, compiled.stdout + compiled.stderr
    executed = subprocess.run(["java", "-cp", str(output), java.stem], capture_output=True, text=True)
    assert executed.returncode == 0, executed.stdout + executed.stderr
