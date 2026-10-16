"""Exercise the real bootstrap route for a durable official Wave 2 checkpoint."""
from pathlib import Path
import subprocess

from tests.test_wave_navigation_adapter import extract

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "copimine-end-event/src/me/copimine/endevent"


def test_bootstrap_resumes_only_a_valid_official_wave2_checkpoint(tmp_path):
    source = (SOURCE / "CopiMineEndEvent.java").read_text(encoding="utf-8")
    adapters = extract(source, "void tryBootstrap()")
    for signature in (
        "boolean canResumeWave2AfterRestart()",
        "boolean isOfficialAttempt()",
        "boolean isOfficialCurrentAttempt()",
    ):
        if "    private " + signature in source:
            adapters += extract(source, signature)
        elif signature.startswith("boolean canResumeWave2"):
            adapters += "\nprivate boolean canResumeWave2AfterRestart(){return false;}\n"
    probe = tmp_path / "Wave2ReturnBootstrapProbe.java"
    probe.write_text(r'''
import java.util.*;
import java.util.logging.*;
import me.copimine.endevent.domain.*;
public class Wave2ReturnBootstrapProbe {
    static final String VICTORY_COMPLETE="complete";
    boolean bootstrapped,testWaveFrontVisualMode,testCombatAiMode,configured=true,endUnlocked;
    boolean stateSave=true;
    long generation=18;
    int activeWave,currentHuntCycles,waveTwoGroupsSpawned;
    boolean waveTwoGroupCleared;
    String eventId="synthetic-event",victoryStep="",recoveryReason="";
    EventPhase phase=EventPhase.WAVE_2,restoredPhase;
    EventConfig config=new EventConfig();
    Set<UUID> officialRewardRoster=new LinkedHashSet<>(List.of(new UUID(0,1),new UUID(0,2)));
    static class AttemptLifecycleController {
        long generation=Long.MIN_VALUE; Set<UUID> roster=Set.of();
        void begin(long value,Set<UUID> owners){generation=value;roster=Set.copyOf(owners);}
        boolean owns(long value){return generation==value;}
        Set<UUID> roster(){return roster;}
    }
    AttemptLifecycleController attemptLifecycle=new AttemptLifecycleController();
    BukkitTask bootstrapTask,tickTask,waveContainmentTask;
    WorldAccessService worldAccessService;
    EventArtifactRewardService rewardService;
    EventLayoutState layoutState=new EventLayoutState(null,null,null,null,Map.of(),"UNSET",null);
    static class EventConfig {
        boolean currentFlow=true;
        boolean isCurrentFlow(){return currentFlow;}
        String bridgeChannel(){return "synthetic:bridge";}
    }
    static class EventLayoutState {
        EventLayoutState(Object a,Object b,Object c,Object d,Map<?,?> e,String f,Object g){}
        Object portalRoom(){return null;}
    }
    interface Plugin {boolean isEnabled();}
    static class BukkitTask {void cancel(){}}
    static class PluginManager {
        Plugin getPlugin(String name){return ()->true;}
        void disablePlugin(Object instance){throw new AssertionError("dependencies are available");}
    }
    static class Services {
        <T>T load(Class<T> type){return type.cast(type==WorldAccessService.class?new WorldAccessService():new EventArtifactRewardService());}
    }
    static class WorldAccessService {boolean isEndEnabled(){return false;}}
    static class EventArtifactRewardService {}
    static class Messenger {
        void registerOutgoingPluginChannel(Object plugin,String channel){}
        void registerIncomingPluginChannel(Object plugin,String channel,Object handler){}
    }
    static class Scheduler {BukkitTask runTaskTimer(Object plugin,Runnable task,long delay,long interval){return new BukkitTask();}}
    static class Player {UUID getUniqueId(){return new UUID(0,0);}}
    static class EndEventBridgeV4Access {
        void register(Object plugin){}
        void requestFreshChallenges(Collection<UUID> players){}
    }
    static class EndEventBridgeV4Bootstrap {
        static EndEventBridgeV4Access create(Object plugin){return null;}
    }
    static class Bukkit {
        static PluginManager getPluginManager(){return new PluginManager();}
        static Services getServicesManager(){return new Services();}
        static Scheduler getScheduler(){return new Scheduler();}
        static Collection<Player> getOnlinePlayers(){return List.of();}
    }
    static class Server {
        Messenger getMessenger(){return new Messenger();}
        PluginManager getPluginManager(){return new PluginManager();}
    }
    static class DepositJournal {static class JournalCorruptionException extends RuntimeException {}}
    EndEventBridgeV4Access endEventBridgeV4;
    boolean isEnabled(){return true;}
    boolean isConfigured(){return configured;}
    Logger getLogger(){return Logger.getAnonymousLogger();}
    Server getServer(){return new Server();}
    void resetEncounterTaskRegistry(long gen){}
    void recoverHazardJournal(){}
    void restorePersistedGateIfNeeded(){}
    void recoverTransientSession(){generation++;phase=EventPhase.READY_FOR_PLAYERS;}
    void forcePhase(EventPhase value,String reason){phase=value;recoveryReason=reason;}
    boolean saveStateSync(){return stateSave;}
    void rebuildPersistedVisuals(){}
    void restorePersistedCombatRuntime(){restoredPhase=phase;}
    void recoverUnresolvedDeposits(){}
    void resumeVictorySaga(){}
    void tick(){}
    void tickWaveMobContainment(){}
    void playEventMusic(Object value){}
    Object musicForPhase(){return null;}
    boolean canResumeWave7ParticipationAfterRestart(){return false;}
''' + adapters + r'''
    static void require(boolean value,String reason){if(!value)throw new AssertionError(reason);}
    static Wave2ReturnBootstrapProbe validCheckpoint(){
        var probe=new Wave2ReturnBootstrapProbe();
        probe.currentHuntCycles=1;probe.waveTwoGroupsSpawned=2;probe.waveTwoGroupCleared=false;
        probe.attemptLifecycle.begin(probe.generation,probe.officialRewardRoster);
        return probe;
    }
    public static void main(String[] args){
        var valid=validCheckpoint();valid.tryBootstrap();
        require(valid.bootstrapped&&valid.generation==18&&valid.restoredPhase==EventPhase.WAVE_2,
                "a valid official Wave 2 checkpoint must reach combat rehydration in the same generation");
        var testMode=validCheckpoint();testMode.testCombatAiMode=true;testMode.tryBootstrap();
        require(testMode.generation==19&&testMode.phase==EventPhase.READY_FOR_PLAYERS,
                "test-mode Wave 2 must retain transient startup recovery");
        var oldConfig=validCheckpoint();oldConfig.config.currentFlow=false;oldConfig.tryBootstrap();
        require(oldConfig.generation==19&&oldConfig.phase==EventPhase.READY_FOR_PLAYERS,
                "unsupported legacy configuration must retain transient startup recovery");
        var malformed=validCheckpoint();malformed.waveTwoGroupsSpawned=3;malformed.tryBootstrap();
        require(malformed.generation==19&&malformed.phase==EventPhase.READY_FOR_PLAYERS,
                "inconsistent durable progress must not receive the Wave 2 restart exception");
        var otherWave=validCheckpoint();otherWave.phase=EventPhase.WAVE_1;otherWave.tryBootstrap();
        require(otherWave.generation==19&&otherWave.phase==EventPhase.READY_FOR_PLAYERS,
                "the Wave 2 restart exception must not change Wave 1 recovery");
        var explicitRecovery=validCheckpoint();explicitRecovery.phase=EventPhase.RECOVERY_REQUIRED;
        explicitRecovery.tryBootstrap();
        require(explicitRecovery.generation==18&&explicitRecovery.phase==EventPhase.RECOVERY_REQUIRED,
                "explicit recovery state must remain available for admin repair");
    }
}
''', encoding="utf-8")
    paths = (
        "domain/EventPhase.java", "domain/EndRiftObjective.java",
        "domain/EndEventStateMachine.java", "domain/WaveTwoStagePolicy.java",
        "domain/WaveMechanicsPolicy.java", "domain/PressureBudgetController.java",
    )
    compile_result = subprocess.run(
        ["javac", "-encoding", "UTF-8", "-d", str(tmp_path),
         *(str(SOURCE / path) for path in paths), str(probe)],
        capture_output=True, text=True,
    )
    assert compile_result.returncode == 0, compile_result.stdout + compile_result.stderr
    result = subprocess.run(["java", "-cp", str(tmp_path), "Wave2ReturnBootstrapProbe"],
                            capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
