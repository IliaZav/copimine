"""Execute the production Wave 2 attempt roster restoration at restart."""
from pathlib import Path
import subprocess

from tests.test_wave_navigation_adapter import extract

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "copimine-end-event/src/me/copimine/endevent"


def test_wave2_restart_restores_generation_roster_and_live_presence(tmp_path):
    source = (SOURCE / "CopiMineEndEvent.java").read_text(encoding="utf-8")
    signature = "void restoreOfficialWave2AttemptLifecycle()"
    adapter = extract(source, signature) if "    private " + signature in source else "private " + signature + " {}"
    restore_participation = extract(source, "void restoreWave7ReturnParticipation(EventSnapshot snapshot)")
    can_resume = extract(source, "boolean canResumeWave2AfterRestart()")
    probe = tmp_path / "Wave2AttemptLifecycleRestoreProbe.java"
    probe.write_text(r'''
import java.util.*;
import java.util.logging.*;
import me.copimine.endevent.runtime.AttemptLifecycleController;
public class Wave2AttemptLifecycleRestoreProbe {
    long generation=44;
    EventPhase phase=EventPhase.WAVE_2;
    String eventId="event";
    Set<UUID> officialRewardRoster=new LinkedHashSet<>(List.of(new UUID(0,1),new UUID(0,2),new UUID(0,3)));
    AttemptLifecycleController attemptLifecycle=new AttemptLifecycleController();
    static class Player {
        boolean online, dead;
        Player(boolean online,boolean dead){this.online=online;this.dead=dead;}
        boolean isOnline(){return online;} boolean isDead(){return dead;}
    }
    static class Bukkit {
        static Map<UUID,Player> players=new HashMap<>();
        static Player getPlayer(UUID id){return players.get(id);}
    }
    enum EventPhase { WAVE_2, WAVE_7, RECOVERY_REQUIRED }
    static class EventSnapshot { Map<String,String> objectiveProgress(){return Map.of();} }
    boolean official=true;
    boolean isOfficialCurrentAttempt(){return official;}
    Logger getLogger(){return Logger.getAnonymousLogger();}
''' + adapter + '''
    static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        UUID online=new UUID(0,1),offline=new UUID(0,2),dead=new UUID(0,3);
        Bukkit.players.put(online,new Player(true,false));
        Bukkit.players.put(dead,new Player(true,true));
        var probe=new Wave2AttemptLifecycleRestoreProbe();
        probe.restoreOfficialWave2AttemptLifecycle();
        require(probe.attemptLifecycle.owns(probe.generation),"restored Wave 2 lifecycle must own the durable generation");
        require(probe.attemptLifecycle.roster().equals(probe.officialRewardRoster),"restored lifecycle must retain the frozen reward roster");
        require(probe.attemptLifecycle.status(online).online()&&probe.attemptLifecycle.status(online).alive(),
                "online living owner remains active for resumed combat");
        require(!probe.attemptLifecycle.status(offline).online()&&probe.attemptLifecycle.status(offline).alive(),
                "offline owner remains registered but is not treated as online");
        require(probe.attemptLifecycle.status(dead).online()&&!probe.attemptLifecycle.status(dead).alive(),
                "online dead owner remains registered without being treated as alive");
    }
}
''', encoding="utf-8")
    result = subprocess.run(
        ["javac", "-encoding", "UTF-8", "-d", str(tmp_path),
         str(SOURCE / "runtime/AttemptLifecycleController.java"), str(probe)],
        capture_output=True, text=True,
    )
    assert result.returncode == 0, result.stdout + result.stderr
    run = subprocess.run(["java", "-cp", str(tmp_path), "Wave2AttemptLifecycleRestoreProbe"],
                         capture_output=True, text=True)
    assert run.returncode == 0, run.stdout + run.stderr
    assert "restoreOfficialWave2AttemptLifecycle();" in restore_participation
    assert "attemptLifecycle.owns(generation)" in can_resume
    assert "attemptLifecycle.roster().equals(officialRewardRoster)" in can_resume
