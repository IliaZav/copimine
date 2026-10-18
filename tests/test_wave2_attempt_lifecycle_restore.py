"""Execute the production Wave 2 attempt roster restoration at restart."""
from pathlib import Path
import subprocess

from tests.test_wave_navigation_adapter import extract

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "copimine-end-event/src/me/copimine/endevent"


def test_wave2_restart_restores_generation_roster_and_live_presence(tmp_path):
    source = (SOURCE / "CopiMineEndEvent.java").read_text(encoding="utf-8")
    signature = "void restoreOfficialWave2AttemptLifecycle(EventSnapshot snapshot)"
    adapter = extract(source, signature) if "    private " + signature in source else "private " + signature + " {}"
    can_resume_adapter = extract(source, "boolean canResumeWave2AfterRestart()")
    adapter += can_resume_adapter
    restore_participation = extract(source, "void restoreWave7ReturnParticipation(EventSnapshot snapshot)")
    can_resume = extract(source, "boolean canResumeWave2AfterRestart()")
    tick_offline_grace = extract(source, "void tickOfflineRosterGrace()")
    quit_start = source.index("public void onPlayerQuit(PlayerQuitEvent event)")
    on_player_quit = source[quit_start:source.index("clientBindingReadyPlayers.remove(uuid)", quit_start)]
    probe = tmp_path / "Wave2AttemptLifecycleRestoreProbe.java"
    probe.write_text(r'''
import java.util.*;
import java.util.logging.*;
import me.copimine.endevent.runtime.AttemptLifecycleController;
import me.copimine.endevent.runtime.WaveTwoAttemptLifecycleSnapshot;
public class Wave2AttemptLifecycleRestoreProbe {
    static final UUID ONLINE=new UUID(0,1),OFFLINE=new UUID(0,2),DEAD=new UUID(0,3),RETIRED=new UUID(0,4);
    static final long OFFLINE_RECONNECT_GRACE_MILLIS=25_000L;
    long generation=44;
    EventPhase phase=EventPhase.WAVE_2;
    String eventId="event";
    Set<UUID> officialRewardRoster=new LinkedHashSet<>(List.of(ONLINE,OFFLINE,DEAD,RETIRED));
    int currentHuntCycles=2,waveTwoGroupsSpawned=2;
    boolean waveTwoGroupCleared;
    AttemptLifecycleController attemptLifecycle=new AttemptLifecycleController();
    Map<UUID,Long> offlineRosterGraceUntilMillis=new HashMap<>();
    EventSnapshot snapshot;
    static class Player {
        boolean online, dead;
        Player(boolean online,boolean dead){this.online=online;this.dead=dead;}
        boolean isOnline(){return online;} boolean isDead(){return dead;}
    }
    static class Bukkit {
        static Map<UUID,Player> players=new HashMap<>();
        static Player getPlayer(UUID id){return players.get(id);}
    }
    enum EventPhase { WAVE_1, WAVE_2, WAVE_7, RECOVERY_REQUIRED }
    static class WaveTwoStagePolicy {static boolean hasConsistentProgress(int cycles,int groups,boolean cleared){return true;}}
    static class EventSnapshot {
        final Map<String,String> objectiveProgress;
        EventSnapshot(Map<String,String> objectiveProgress){this.objectiveProgress=Map.copyOf(objectiveProgress);}
        Map<String,String> objectiveProgress(){return objectiveProgress;}
    }
    boolean official=true;
    boolean wave1CarrierCheckpointRestored;
    boolean isOfficialAttempt(){return official;}
    boolean isOfficialCurrentAttempt(){return official;}
    Logger getLogger(){return Logger.getAnonymousLogger();}
''' + adapter + '''
    static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    Map<String,String> saveAttemptLifecycle(long savedAtMillis){
        return WaveTwoAttemptLifecycleSnapshot.encode(eventId,generation,officialRewardRoster,
                attemptLifecycle,offlineRosterGraceUntilMillis,savedAtMillis,OFFLINE_RECONNECT_GRACE_MILLIS);
    }
    public static void main(String[] args){
        long restartObservedAtMillis=System.currentTimeMillis();
        long originalOfflineDeadline=restartObservedAtMillis+9_000L;
        var beforeRestart=new Wave2AttemptLifecycleRestoreProbe();
        beforeRestart.attemptLifecycle.begin(beforeRestart.generation,beforeRestart.officialRewardRoster);
        beforeRestart.attemptLifecycle.setActive(RETIRED,beforeRestart.generation,false);
        beforeRestart.attemptLifecycle.markOffline(OFFLINE,beforeRestart.generation);
        beforeRestart.attemptLifecycle.markDead(DEAD,beforeRestart.generation);
        beforeRestart.offlineRosterGraceUntilMillis.put(OFFLINE,originalOfflineDeadline);
        var persistedProgress=beforeRestart.saveAttemptLifecycle(restartObservedAtMillis);
        beforeRestart.attemptLifecycle.markOffline(RETIRED,beforeRestart.generation);
        beforeRestart.saveAttemptLifecycle(restartObservedAtMillis);
        require(!WaveTwoAttemptLifecycleSnapshot.reconnectDeadlineExpired(
                        originalOfflineDeadline,originalOfflineDeadline-1L),
                "reconnect remains valid immediately before the saved deadline");
        require(WaveTwoAttemptLifecycleSnapshot.reconnectDeadlineExpired(
                        originalOfflineDeadline,originalOfflineDeadline),
                "reconnect at the saved deadline must not extend the grace window");
        try {
            WaveTwoAttemptLifecycleSnapshot.decode(persistedProgress,"other-event",beforeRestart.generation,
                    beforeRestart.officialRewardRoster,restartObservedAtMillis,OFFLINE_RECONNECT_GRACE_MILLIS);
            throw new AssertionError("lifecycle checkpoint from another event must be rejected");
        } catch (IllegalArgumentException expected) { }
        var tamperedProgress=new LinkedHashMap<>(persistedProgress);
        tamperedProgress.put("wave2-attempt-lifecycle.unexpected","true");
        try {
            WaveTwoAttemptLifecycleSnapshot.decode(tamperedProgress,"event",beforeRestart.generation,
                    beforeRestart.officialRewardRoster,restartObservedAtMillis,OFFLINE_RECONNECT_GRACE_MILLIS);
            throw new AssertionError("unknown lifecycle fields must be rejected");
        } catch (IllegalArgumentException expected) { }
        var overflowTimestampProgress=new LinkedHashMap<>(persistedProgress);
        overflowTimestampProgress.put("wave2-attempt-lifecycle.saved-millis",Long.toString(Long.MAX_VALUE));
        try {
            WaveTwoAttemptLifecycleSnapshot.decode(overflowTimestampProgress,"event",beforeRestart.generation,
                    beforeRestart.officialRewardRoster,Long.MAX_VALUE,OFFLINE_RECONNECT_GRACE_MILLIS);
            throw new AssertionError("timestamp overflow must be rejected as an invalid lifecycle checkpoint");
        } catch (IllegalArgumentException expected) { }
        long expiredSavedAt=restartObservedAtMillis-30_000L;
        var expiredBeforeRestart=new Wave2AttemptLifecycleRestoreProbe();
        expiredBeforeRestart.attemptLifecycle.begin(expiredBeforeRestart.generation,
                expiredBeforeRestart.officialRewardRoster);
        expiredBeforeRestart.attemptLifecycle.markOffline(OFFLINE,expiredBeforeRestart.generation);
        expiredBeforeRestart.offlineRosterGraceUntilMillis.put(OFFLINE,
                expiredSavedAt+OFFLINE_RECONNECT_GRACE_MILLIS);
        var expiredProgress=expiredBeforeRestart.saveAttemptLifecycle(expiredSavedAt);
        var expiredProbe=new Wave2AttemptLifecycleRestoreProbe();
        expiredProbe.snapshot=new EventSnapshot(expiredProgress);
        Bukkit.players.put(OFFLINE,new Player(true,false));
        expiredProbe.restoreOfficialWave2AttemptLifecycle(expiredProbe.snapshot);
        require(expiredProbe.attemptLifecycle.status(OFFLINE).online()
                        &&!expiredProbe.attemptLifecycle.status(OFFLINE).active()
                        &&!expiredProbe.attemptLifecycle.status(OFFLINE).alive(),
                "an offline deadline that expired during restart remains expired even when the owner is online");
        Bukkit.players.remove(OFFLINE);
        var probe=new Wave2AttemptLifecycleRestoreProbe();
        probe.snapshot=new EventSnapshot(persistedProgress);
        Bukkit.players.put(ONLINE,new Player(true,false));
        Bukkit.players.put(DEAD,new Player(true,true));
        Bukkit.players.put(RETIRED,new Player(true,false));
        probe.restoreOfficialWave2AttemptLifecycle(probe.snapshot);
        require(probe.attemptLifecycle.owns(probe.generation),"restored Wave 2 lifecycle must own the durable generation");
        List<String> restoreFailures=new ArrayList<>();
        if(!probe.attemptLifecycle.roster().equals(Set.of(ONLINE,OFFLINE,DEAD)))
            restoreFailures.add("active combat roster must exclude owners retired before restart");
        var retiredStatus=probe.attemptLifecycle.status(RETIRED);
        if(!probe.attemptLifecycle.isRegistered(RETIRED,probe.generation)
                ||retiredStatus==null||retiredStatus.active())
            restoreFailures.add("retired owner must remain registered for rewards but inactive after restart");
        if(!probe.canResumeWave2AfterRestart())
            restoreFailures.add("a valid Wave 2 checkpoint must remain resumable with a retired reward owner");
        var empty=new Wave2AttemptLifecycleRestoreProbe();
        empty.attemptLifecycle.begin(empty.generation,empty.officialRewardRoster);
        for(UUID owner:empty.officialRewardRoster){
            empty.attemptLifecycle.setActive(owner,empty.generation,false);
            empty.attemptLifecycle.markDead(owner,empty.generation);
        }
        if(empty.canResumeWave2AfterRestart())
            restoreFailures.add("Wave 2 must not resume when no registered owner remains alive");
        require(probe.attemptLifecycle.status(ONLINE).online()&&probe.attemptLifecycle.status(ONLINE).alive(),
                "online living owner remains active for resumed combat");
        require(!probe.attemptLifecycle.status(OFFLINE).online()&&probe.attemptLifecycle.status(OFFLINE).alive(),
                "offline owner remains registered but is not treated as online");
        require(probe.attemptLifecycle.status(DEAD).online()&&!probe.attemptLifecycle.status(DEAD).alive(),
                "online dead owner remains registered without being treated as alive");
        long restoredOfflineDeadline=probe.offlineRosterGraceUntilMillis.getOrDefault(OFFLINE,0L);
        if(restoredOfflineDeadline!=originalOfflineDeadline)
            restoreFailures.add("restart must preserve the saved offline grace deadline instead of losing or replacing it");
        if(restoredOfflineDeadline<=restartObservedAtMillis
                ||restoredOfflineDeadline-restartObservedAtMillis>=OFFLINE_RECONNECT_GRACE_MILLIS)
            restoreFailures.add("restarting during offline grace must not issue a fresh full 25-second window");
        require(restoreFailures.isEmpty(),String.join("; ",restoreFailures));
    }
}
''', encoding="utf-8")
    result = subprocess.run(
        ["javac", "-encoding", "UTF-8", "-d", str(tmp_path),
         str(SOURCE / "runtime/AttemptLifecycleController.java"),
         str(SOURCE / "runtime/WaveTwoAttemptLifecycleSnapshot.java"), str(probe)],
        capture_output=True, text=True,
    )
    assert result.returncode == 0, result.stdout + result.stderr
    run = subprocess.run(["java", "-cp", str(tmp_path), "Wave2AttemptLifecycleRestoreProbe"],
                         capture_output=True, text=True)
    assert run.returncode == 0, run.stdout + run.stderr
    assert "restoreOfficialWave2AttemptLifecycle(snapshot);" in restore_participation
    assert "attemptLifecycle.registeredRoster().equals(officialRewardRoster)" in can_resume
    assert "!attemptLifecycle.living().isEmpty()" in can_resume
    assert "WaveTwoStagePolicy.hasConsistentProgress" in can_resume
    assert "offlineRosterGraceUntilMillis.isEmpty()" not in tick_offline_grace
    assert "wipeOfficialAttemptIfAllDead" in tick_offline_grace
    assert "participant != null && participant.active()" in on_player_quit
    assert "offlineRosterGraceUntilMillis.remove(uuid)" in on_player_quit
