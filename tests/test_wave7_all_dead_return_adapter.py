"""The actual wipe boundary must retain Wave 7 claims during return grace."""
from pathlib import Path
import subprocess

from tests.test_end_rift_death_item_integrations import extract

ROOT = Path(__file__).resolve().parents[1]


def test_last_wave7_death_does_not_immediately_destroy_the_attempt(tmp_path):
    source = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(encoding="utf-8")
    wipe = extract(source, "void wipeOfficialAttemptIfAllDead(String reason)")
    probe = tmp_path / "Wave7ReturnGraceProbe.java"
    probe.write_text(r'''
import java.util.*;import java.util.logging.*;
import me.copimine.endevent.runtime.AttemptLifecycleController;
public class Wave7ReturnGraceProbe {
    enum EventPhase { WAVE_7, READY_FOR_PLAYERS, COLLECTING, RECOVERY_REQUIRED }
    static class World {}
    static class Bukkit {static World getWorld(String world){return null;}}
    static class Controller {void clear(){} void restore(String event,long gen,EventPhase phase,Object unused){}}
    static class Profiles {void clear(){}}
    static class Listener {void clear(){}}
    AttemptLifecycleController attemptLifecycle=new AttemptLifecycleController();
    long generation=43,phaseDeadlineMillis,transitionRuneDeadlineMillis;
    String eventId="synthetic-attempt",worldName="configured-arena",recoveryReason,bossRewardStatus,
        returnStoneStatus,victoryStep;
    static final String BOSS_REWARDS_PENDING="pending";
    EventPhase phase=EventPhase.WAVE_7;int activeWave=7,requiredPlayers=1,transitionRuneWave,cleanups;
    boolean coreCharged,officialBossDeathCommitted,bossLootCommitted;
    UUID bossUuid,bossKillerUuid,bossRewardRecipientUuid;
    Set<UUID> officialRewardRoster=new HashSet<>(),rewardRequestsInFlight=new HashSet<>(),
        nightCloakRequestsInFlight=new HashSet<>(),lootIssuedEntityUuids=new HashSet<>();
    Map<UUID,Long> offlineRosterGraceUntilMillis=new HashMap<>();
    Map<String,Object> rewardStatuses=new HashMap<>(),nightCloakRolls=new HashMap<>(),
        padOccupants=new HashMap<>(),runeVisualOccupants=new HashMap<>();
    List<Object> pads=new ArrayList<>();
    Controller encounterController=new Controller(),realitySplitChamberController=new Controller(),
        realitySplitTrialController=new Controller();
    Profiles combatProfiles=new Profiles();Listener combatProfileListener=new Listener();
    boolean isOfficialAttemptActive(){return true;}
    boolean isOfficialWave7ReturnContext(){return activeWave==7&&phase==EventPhase.WAVE_7;}
    long wave7ReturnGraceMillis(){return 120_000L;} void saveStateAsync(){}
    Logger getLogger(){return Logger.getLogger("synthetic-return-grace");}
    boolean cancelSessionTasks(){cleanups++;return true;}
    boolean saveStateSync(){return true;}
    void removeTransitionRuneVisuals(){}void cleanupOwnedEntities(String event,long gen){}
    void clearClientEffects(){}void clearBossOnly(){}void clearCombatAiState(){}
    void resetEncounterTaskRegistry(long gen){}void calculateAndPlacePads(World world){}
    void forcePhase(EventPhase next,String reason){phase=next;}void rebuildPersistedVisuals(){}
''' + wipe + r'''
    public static void main(String[] args){
        var main=new Wave7ReturnGraceProbe();UUID owner=new UUID(0,1);
        main.officialRewardRoster.add(owner);main.attemptLifecycle.begin(43,Set.of(owner));
        main.attemptLifecycle.enableWave7Returns(43);
        main.attemptLifecycle.markDead(owner,43);
        main.wipeOfficialAttemptIfAllDead("last participant committed death");
        if(main.generation!=43||main.cleanups!=0||!main.officialRewardRoster.contains(owner)
            ||!main.attemptLifecycle.owns(43)||main.phase!=EventPhase.WAVE_7)
            throw new AssertionError("last Wave 7 death immediately wiped claims and return entitlement instead of opening the 120-second window");
        main.attemptLifecycle.clear();main.attemptLifecycle.begin(43,Set.of(owner));main.attemptLifecycle.enableWave7Returns(43);
        main.attemptLifecycle.markDead(owner,43);
        main.attemptLifecycle.observeWave7ReturnWindow(43,System.nanoTime()-121_000_000_000L,System.currentTimeMillis()-121_000L,120_000L);
        main.wipeOfficialAttemptIfAllDead("original grace expired");
        if(main.generation!=44||main.cleanups!=1||!main.officialRewardRoster.isEmpty()
            ||main.attemptLifecycle.owns(43)||main.phase!=EventPhase.RECOVERY_REQUIRED)
            throw new AssertionError("expired return grace must clean once and enter explicit recovery without granting success");
        main.wipeOfficialAttemptIfAllDead("repeated expiry");
        if(main.cleanups!=1)throw new AssertionError("expired attempt cleanup repeated");
    }
}
''', encoding="utf-8")
    lifecycle = ROOT / "copimine-end-event/src/me/copimine/endevent/runtime/AttemptLifecycleController.java"
    result = subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(tmp_path), str(lifecycle), str(probe)], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
    result = subprocess.run(["java", "-cp", str(tmp_path), "Wave7ReturnGraceProbe"], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
