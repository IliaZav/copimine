"""Execute the real respawn callback: bed respawn must not re-enter a live trial."""
from pathlib import Path
import subprocess

from tests.test_end_rift_death_item_integrations import extract

ROOT = Path(__file__).resolve().parents[1]


def test_wave7_respawn_preserves_normal_spawn_until_explicit_return(tmp_path):
    source = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(encoding="utf-8")
    callback = extract(source, "void onPlayerRespawn(PlayerRespawnEvent event)")
    probe = tmp_path / "BedRespawnProbe.java"
    probe.write_text(r'''
import java.util.*;
import me.copimine.endevent.runtime.AttemptLifecycleController;
public class BedRespawnProbe {
    static final UUID OWNER = new UUID(0, 1);
    static class Player { UUID getUniqueId(){return OWNER;} boolean isOnline(){return true;} boolean isDead(){return false;} }
    record PlayerRespawnEvent(Player player) { Player getPlayer(){return player;} }
    static class BukkitTask {}
    static class Scheduler { static boolean defer;static Runnable pending;BukkitTask runTaskLater(Object plugin, Runnable task, long delay){if(defer)pending=task;else task.run();return new BukkitTask();} }
    static class Bukkit { static Scheduler getScheduler(){return new Scheduler();} }
    static class Registry { boolean owns(long generation){return generation==43;} }
    static class Assignment { Map<UUID,Integer> chamberByPlayer(){return Map.of(OWNER,0);} }
    static class Chambers { boolean owns(long generation){return generation==43;} Assignment assignment(){return new Assignment();} }
    AttemptLifecycleController attemptLifecycle = new AttemptLifecycleController();
    Set<UUID> officialRewardRoster = new HashSet<>(Set.of(OWNER));
    Set<UUID> clientBindingReadyPlayers = new HashSet<>();
    Map<UUID,Long> offlineRosterGraceUntilMillis = new HashMap<>();
    long generation=43; int activeWave=7, teleports, bindings;
    boolean testCombatAiMode;
    Registry taskRegistry = new Registry(); Chambers realitySplitChamberController = new Chambers();
    boolean isOfficialAttemptActive(){return true;} boolean isCombatPhase(){return true;}
    boolean isOfficialWave7ReturnContext(){return activeWave==7;}
    boolean isEventMusicPhase(){return false;} Object liveBoss(){return null;}
    int chamberWaveNumber(){return 7;}
    void containRealitySplitParticipant(Player player,long generation,String reason){teleports++;}
    void teleportRespawnedOfficialParticipant(Player player,long generation){teleports++;}
    void refreshClientBindingsForPlayer(Player player){bindings++;}
    void offerWave7Return(Player player){}
    void registerEncounterTask(BukkitTask task){}
''' + callback + r'''
    public static void main(String[] args){
        var main=new BedRespawnProbe(); main.attemptLifecycle.begin(43,Set.of(OWNER));
        main.attemptLifecycle.enableWave7Returns(43);main.attemptLifecycle.markDead(OWNER,43);
        main.onPlayerRespawn(new PlayerRespawnEvent(new Player()));
        if(main.teleports!=0)throw new AssertionError("Wave 7 bed/fallback respawn was silently teleported into combat after two ticks");
        if(main.bindings!=1)throw new AssertionError("normal respawn must still rebind the client state");
        if(main.attemptLifecycle.isObjectiveEligible(OWNER,43))throw new AssertionError("bed respawn alone cannot authorize combat re-entry");
        var other=new BedRespawnProbe();other.activeWave=6;other.attemptLifecycle.begin(43,Set.of(OWNER));other.attemptLifecycle.markDead(OWNER,43);
        other.onPlayerRespawn(new PlayerRespawnEvent(new Player()));
        if(other.teleports!=1||other.bindings!=1)throw new AssertionError("scoped Wave 7 bed fix changed the existing Wave 6 respawn path");
        var stale=new BedRespawnProbe();stale.attemptLifecycle.begin(43,Set.of(OWNER));stale.attemptLifecycle.enableWave7Returns(43);stale.attemptLifecycle.markDead(OWNER,43);
        Scheduler.defer=true;stale.onPlayerRespawn(new PlayerRespawnEvent(new Player()));stale.generation=44;Scheduler.pending.run();
        if(stale.teleports!=0||stale.bindings!=0)throw new AssertionError("old respawn callback sent gameplay/client state after generation changed");
    }
}
''', encoding="utf-8")
    lifecycle = ROOT / "copimine-end-event/src/me/copimine/endevent/runtime/AttemptLifecycleController.java"
    result = subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(tmp_path), str(lifecycle), str(probe)], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
    result = subprocess.run(["java", "-cp", str(tmp_path), "BedRespawnProbe"], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
