"""Execute actual phase music routing without rewriting a disposable boss's phase."""
from pathlib import Path
import subprocess
from tests.test_end_rift_death_item_integrations import extract

ROOT = Path(__file__).resolve().parents[1]


def test_manual_boss_uses_its_own_phase_music_while_durable_phase_stays_collecting(tmp_path):
    source = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(encoding="utf-8")
    methods = "\n".join(extract(source, signature) for signature in (
        "EventConfig.MusicTrack musicForPhase(", "boolean isEventMusicPhase("))
    java = tmp_path / "ManualBossMusicProbe.java"
    java.write_text("""import me.copimine.endevent.domain.*;
public class ManualBossMusicProbe {
 static class EventConfig {
   record MusicTrack(String soundId){}
   MusicTrack phaseMusic(String key){return new MusicTrack(key);}
   MusicTrack ritualWaitMusic(){return new MusicTrack("wait");}
 }
 EventConfig config=new EventConfig();EventPhase phase=EventPhase.COLLECTING;
 int activeWave;boolean testWaveFrontVisualMode,manual,live;long waveTwoIntroStartedMillis;
 boolean isBossHarnessMusicActive(){return manual&&live;}
 boolean isRitualMusicPhase(){return phase==EventPhase.READY_FOR_PLAYERS||phase==EventPhase.START_RITUAL;}
 EventConfig.MusicTrack currentBossMusic(){return config.phaseMusic("boss-awakening");}
""" + methods + """
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 public static void main(String[] args){
   var p=new ManualBossMusicProbe();p.manual=true;p.live=true;
   check(p.isEventMusicPhase(),"manual boss is silent outside official BOSS_ACTIVE");
   check(p.musicForPhase().soundId().equals("boss-awakening"),"manual boss uses unrelated durable phase music");
   check(p.phase==EventPhase.COLLECTING,"music routing changed durable phase");
   p.live=false;check(!p.isEventMusicPhase()&&p.musicForPhase()==null,"stale manual flag retains boss music");
   p.manual=false;p.phase=EventPhase.BOSS_ACTIVE;
   check(p.isEventMusicPhase()&&p.musicForPhase().soundId().equals("boss-awakening"),"official boss music regressed");
 }
} """, encoding="utf-8")
    result = subprocess.run(["javac", "-encoding", "UTF-8", "-sourcepath", str(ROOT / "copimine-end-event/src"),
                             "-d", str(tmp_path), str(java)], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
    result = subprocess.run(["java", "-cp", str(tmp_path), "ManualBossMusicProbe"], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
