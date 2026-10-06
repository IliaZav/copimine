"""Execute the real bootstrap routing after a validated Wave 7 return checkpoint."""
from pathlib import Path
import subprocess

from tests.test_wave_navigation_adapter import extract

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "copimine-end-event/src/me/copimine/endevent"


def test_bootstrap_preserves_validated_return_generation_and_original_deadline(tmp_path):
    source = (SOURCE / "CopiMineEndEvent.java").read_text(encoding="utf-8")
    adapters = extract(source, "void tryBootstrap()")
    adapters += extract(source, "boolean isOfficialWave7ReturnContext()")
    signature = "boolean canResumeWave7ParticipationAfterRestart()"
    if "    private " + signature in source:
        adapters += extract(source, signature)
    probe = tmp_path / "Wave7ReturnBootstrapProbe.java"
    probe.write_text(r'''
import java.util.*;
import java.util.logging.*;
import me.copimine.endevent.domain.*;
import me.copimine.endevent.runtime.*;
public class Wave7ReturnBootstrapProbe {
    static final UUID OWNER=new UUID(0,1),OTHER=new UUID(0,2);
    static final String VICTORY_COMPLETE="complete";
    boolean bootstrapped,testWaveFrontVisualMode,testCombatAiMode,configured=true;
    long generation=43;int activeWave=7,recoveries,restores;
    String eventId="synthetic-event",victoryStep="",recoveryReason="";
    boolean endUnlocked;
    EventPhase phase=EventPhase.WAVE_7,restoredPhase;
    BukkitTask bootstrapTask,tickTask,waveContainmentTask;
    WorldAccessService worldAccessService;
    EventArtifactRewardService rewardService;
    Config config=new Config();
    EventLayoutState layoutState=new EventLayoutState(null,null,null,null,Map.of(),"UNSET",null);
    AttemptLifecycleController attemptLifecycle=new AttemptLifecycleController();
    RealitySplitChamberController realitySplitChamberController=new RealitySplitChamberController();
    RealitySplitTrialController realitySplitTrialController=new RealitySplitTrialController();
    static class Config {String bridgeChannel(){return "synthetic:bridge";}}
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
    static class Bukkit {
        static PluginManager getPluginManager(){return new PluginManager();}
        static Services getServicesManager(){return new Services();}
        static Scheduler getScheduler(){return new Scheduler();}
    }
    static class Server {
        Messenger getMessenger(){return new Messenger();}
        PluginManager getPluginManager(){return new PluginManager();}
    }
    static class DepositJournal {static class JournalCorruptionException extends RuntimeException {}}
    boolean isEnabled(){return true;}boolean isConfigured(){return configured;}
    boolean isOfficialAttemptActive(){return true;}
    Logger getLogger(){return Logger.getAnonymousLogger();}Server getServer(){return new Server();}
    void resetEncounterTaskRegistry(long gen){}
    void recoverHazardJournal(){}void restorePersistedGateIfNeeded(){}
    void recoverTransientSession(){recoveries++;generation++;phase=EventPhase.READY_FOR_PLAYERS;attemptLifecycle.clear();}
    void forcePhase(EventPhase value,String reason){phase=value;}
    boolean saveStateSync(){return true;}
    void rebuildPersistedVisuals(){}
    void restorePersistedCombatRuntime(){restores++;restoredPhase=phase;}
    void recoverUnresolvedDeposits(){}void resumeVictorySaga(){}
    void tick(){}void tickWaveMobContainment(){}
    void playEventMusic(Object value){}Object musicForPhase(){return null;}
''' + adapters + r'''
    static void require(boolean value,String reason){if(!value)throw new AssertionError(reason);}
    Map<String,String> prepareRestoredReturns(){
        var roster=Set.of(OWNER,OTHER);
        realitySplitChamberController.begin(generation,new ArrayList<>(roster));
        var claims=realitySplitChamberController.assignment();
        realitySplitTrialController.begin(generation,claims);
        attemptLifecycle.begin(generation,roster);attemptLifecycle.enableWave7Returns(generation);
        attemptLifecycle.markDead(OWNER,generation);attemptLifecycle.markDead(OTHER,generation);
        attemptLifecycle.observeWave7ReturnWindow(generation,0,10000,120000);
        var saved=attemptLifecycle.encodeWave7Returns(eventId,claims.chamberByPlayer(),1000000,10001,120000);
        require(attemptLifecycle.restoreWave7Returns(saved,eventId,generation,claims.chamberByPlayer(),roster,2000000,11000,120000),
                "actual strict return receipt must restore before bootstrap");
        return saved;
    }
    public static void main(String[] args){
        var main=new Wave7ReturnBootstrapProbe();var saved=main.prepareRestoredReturns();
        main.tryBootstrap();
        require(main.recoveries==0,"validated official Wave 7 reached destructive transient recovery after its checkpoint was already restored");
        require(main.bootstrapped&&main.restores==1&&main.restoredPhase==EventPhase.WAVE_7&&main.generation==43,
                "normal bootstrap must resume the same official phase and generation");
        require(main.attemptLifecycle.hasWave7Returns(43)&&main.attemptLifecycle.isReturnPending(OWNER,43)
                &&main.attemptLifecycle.isReturnPending(OTHER,43)&&main.attemptLifecycle.activeLivingOnlineRoster().isEmpty(),
                "cold boot must retain pending participation without automatically admitting owners");
        require(main.attemptLifecycle.incarnation(OWNER,43)==3,"restart must retain the strictly advanced incarnation");
        var after=main.attemptLifecycle.encodeWave7Returns(main.eventId,main.realitySplitChamberController.assignment().chamberByPlayer(),3000000,11001,120000);
        require(saved.get("wave7-return.deadline-millis").equals(after.get("wave7-return.deadline-millis")),
                "bootstrap must never reset or extend the original all-dead deadline");
        main.tryBootstrap();require(main.restores==1&&main.recoveries==0,"duplicate bootstrap cannot reconstruct another session");
        var preBoss=new Wave7ReturnBootstrapProbe();preBoss.prepareRestoredReturns();preBoss.phase=EventPhase.PRE_BOSS_COOLDOWN;
        preBoss.tryBootstrap();require(preBoss.recoveries==0&&preBoss.restoredPhase==EventPhase.PRE_BOSS_COOLDOWN&&preBoss.generation==43,
                "the return window must survive the existing post-wave handoff too");
        var missing=new Wave7ReturnBootstrapProbe();missing.realitySplitChamberController.begin(43,List.of(OWNER,OTHER));
        missing.tryBootstrap();require(missing.recoveries==1,"room ownership alone cannot bypass ordinary startup recovery without participation receipts");
        var stale=new Wave7ReturnBootstrapProbe();stale.prepareRestoredReturns();stale.generation=44;
        stale.tryBootstrap();require(stale.recoveries==1,"old generation cannot resume participation");
        var badRoom=new Wave7ReturnBootstrapProbe();badRoom.prepareRestoredReturns();badRoom.realitySplitChamberController.clear();
        badRoom.tryBootstrap();require(badRoom.recoveries==1,"missing validated room assignment cannot grant a restart exception");
        var badTrial=new Wave7ReturnBootstrapProbe();badTrial.prepareRestoredReturns();badTrial.realitySplitTrialController.clear();
        badTrial.tryBootstrap();require(badTrial.recoveries==1,"missing validated trial receipts cannot grant a restart exception");
        var changedRoster=new Wave7ReturnBootstrapProbe();changedRoster.prepareRestoredReturns();
        changedRoster.realitySplitChamberController.begin(43,List.of(OWNER));
        changedRoster.tryBootstrap();require(changedRoster.recoveries==1,"mismatching original claims cannot grant a restart exception");
        var refused=new Wave7ReturnBootstrapProbe();refused.prepareRestoredReturns();refused.phase=EventPhase.RECOVERY_REQUIRED;
        refused.tryBootstrap();require(refused.recoveries==0&&refused.phase==EventPhase.RECOVERY_REQUIRED,
                "malformed checkpoint recovery must remain explicit and usable");
        var otherWave=new Wave7ReturnBootstrapProbe();otherWave.prepareRestoredReturns();otherWave.phase=EventPhase.WAVE_1;otherWave.activeWave=1;
        otherWave.tryBootstrap();require(otherWave.recoveries==1,"Wave 1 must keep its current transient recovery policy");
        var disposable=new Wave7ReturnBootstrapProbe();disposable.testWaveFrontVisualMode=true;
        disposable.tryBootstrap();require(disposable.recoveries==0,"existing disposable Wave 7 continuation must be preserved");
        var boss=new Wave7ReturnBootstrapProbe();boss.prepareRestoredReturns();boss.phase=EventPhase.BOSS_ACTIVE;
        boss.tryBootstrap();require(boss.recoveries==1,"the Wave 7 return exception must not redesign boss restart behavior");
    }
}
''', encoding="utf-8")
    paths = (
        "domain/EventPhase.java", "domain/EndEventStateMachine.java",
        "domain/EndRiftObjective.java", "domain/ChamberIsolationPolicy.java",
        "domain/RealitySplitBarrierPolicy.java", "runtime/RealitySplitChamberController.java",
        "runtime/RealitySplitTrialController.java", "runtime/AttemptLifecycleController.java",
    )
    compile_result = subprocess.run(
        ["javac", "-encoding", "UTF-8", "-d", str(tmp_path), *(str(SOURCE / path) for path in paths), str(probe)],
        capture_output=True, text=True,
    )
    assert compile_result.returncode == 0, compile_result.stdout + compile_result.stderr
    result = subprocess.run(["java", "-cp", str(tmp_path), "Wave7ReturnBootstrapProbe"], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
