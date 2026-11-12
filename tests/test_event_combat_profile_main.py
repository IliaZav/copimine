"""Execute Main's actual admission/checkpoint methods with the real lifecycle."""
from pathlib import Path
import subprocess
from tests.test_end_rift_death_item_integrations import extract

ROOT = Path(__file__).resolve().parents[1]


def test_profile_main_binding_preserves_attempt_and_filters_diagnostic_players(tmp_path):
    source = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(encoding="utf-8")
    assert "combatProfileContext(Player player)" in source, "Official roster has no combat-profile admission or checkpoint binding"
    methods = "\n".join(extract(source, signature) for signature in (
        "Context combatProfileContext(Player player)",
        # Wave 7 miniboss combat is recorded too, so admission consults the stage gate.
        "boolean isCombatProfileWave7TrainingStage()",
        "void restoreCombatProfiles(Map<String, String> progress)",
        "Map<String, String> objectiveProgressSnapshot()",
        "boolean isOfficialAttemptActive()",
        "void encodeTentacleGuardianState(Map<String, String> encoded)",
        "boolean tentacleGuardianRegenerationDisabled()",
    ))
    probe = tmp_path / "CombatProfileMainProbe.java"
    probe.write_text(r'''
import java.util.*;import java.util.logging.*;
import me.copimine.endevent.domain.EventPhase;
import me.copimine.endevent.domain.CocoonFallPolicy;
import me.copimine.endevent.domain.TentacleGuardianPolicy;
import me.copimine.endevent.domain.WaveTwoEntityRecoveryPolicy;
import me.copimine.endevent.runtime.*;
import me.copimine.endevent.runtime.EventCombatProfileService.Context;
public class CombatProfileMainProbe {
    void encodeSmallKaguneState(Map<String,String> encoded){
        encoded.putAll(me.copimine.endevent.domain.SmallKaguneRosterSnapshot.encode(eventId,generation,List.of()));
    }

    long cocoonFallGeneration=Long.MIN_VALUE;
    boolean cocoonFallStarted,cocoonLandingPlayed;
    int cocoonFallElapsedTicks=-1,cocoonFallMoveFailures;
    me.copimine.endevent.domain.CocoonFallPolicy.Stage cocoonFallStage=
            me.copimine.endevent.domain.CocoonFallPolicy.Stage.COCOONED;
    // encodeTentacleGuardianState persists the small-Kagune clock,
    // so the probe must own the real field the production method reads.
    final me.copimine.endevent.domain.SmallKaguneCadence smallKaguneCadence=
            new me.copimine.endevent.domain.SmallKaguneCadence();

    static final String WAVE_TWO_CLEANUP_UUIDS_PROGRESS_KEY = "wave2-cleanup-uuids";
    static final String WAVE_TWO_OUTSTANDING_UUIDS_PROGRESS_KEY = "wave2-outstanding-uuids";
    static final String WAVE_TWO_CLEANUP_EPOCH_PROGRESS_KEY = "wave2-cleanup-epoch";
    enum GameMode { SURVIVAL,ADVENTURE,CREATIVE,SPECTATOR }
    enum BlackFogPhase { COMBAT,SAFE,FOG }
    record Player(UUID id,String name,GameMode mode,boolean online,boolean dead,double max) {
        UUID getUniqueId(){return id;}String getName(){return name;}GameMode getGameMode(){return mode;}
        boolean isOnline(){return online;}boolean isDead(){return dead;}double getMaxHealth(){return max;}
    }
    static class Bukkit {static int getCurrentTick(){return 80;}}
    static class PreBoss {long elapsedServerTicks(long tick){return tick;}}
    boolean bootstrapped=true,testWaveFrontVisualMode,testCombatAiMode,testWaveInspectionMode,preBossHandoffCommitted,bossHalfRepelCommitted;
    String eventId="event";long generation=71,eventTickCounter=80,restoredPreBossElapsedTicks,lastCombatProfileSaveTick,waveTwoCleanupEpoch,nextBossEdgeVolleyMillis;
    int activeWave=1;EventPhase phase=EventPhase.WAVE_1;BlackFogPhase blackFogPhase=BlackFogPhase.COMBAT;
    EventCombatProfileService combatProfiles=new EventCombatProfileService();
    EventCombatProfileListener combatProfileListener;
    AttemptLifecycleController attemptLifecycle=new AttemptLifecycleController();
    Set<UUID> officialRewardRoster=new LinkedHashSet<>();UUID prisoner;
    Set<UUID> waveTwoCleanupEntityUuids=new LinkedHashSet<>(),waveTwoOutstandingEntityUuids=new LinkedHashSet<>();
    me.copimine.endevent.domain.GuardianDeathLedger guardianDeaths=new me.copimine.endevent.domain.GuardianDeathLedger(71L);
    Map<Integer,Double> tentacleGuardianHealth=new HashMap<>();Map<Integer,Long> tentacleGuardianRespawnDeadlineMillis=new HashMap<>();
    final Set<Integer> tentacleGuardianWarnedSlots=new HashSet<>();
    double tentacleGuardianRegenerationGranted;
    long tentacleDamageWindowEndsMillis=-1L,tentacleActiveMillis=0L;
    long tentacleGuardianRegenerationDisabledGeneration=Long.MIN_VALUE;
    PreBoss preBossTransitionController=new PreBoss();
    static class Chambers {Assignment assignment(){return new Assignment();}}
    static class Assignment {Map<UUID,Integer> chamberByPlayer(){return Map.of();}}
    Chambers realitySplitChamberController=new Chambers();
    boolean isOfficialWave7ReturnContext(){return false;} long wave7ReturnGraceMillis(){return 120_000L;}
    boolean isOfficialCurrentAttempt(){return isOfficialAttemptActive()&&!testWaveFrontVisualMode&&!testCombatAiMode;}
    boolean isCombatTarget(Player player){return player!=null&&player.isOnline()&&!player.isDead();}
    UUID ritualPrisonerId(){return prisoner;}
    // Wave 2 ownership is now server-owned and persisted: the roster of squad
    // owners, the recovery reason recorded when it cannot be restored, and the
    // progress key it is saved under. The probe mirrors the real fields so the
    // persisted-state code compiles against it unchanged.
    List<UUID> waveTwoAssignmentRoster = List.of();
    String recoveryReason = "";
    static final String WAVE_TWO_OWNER_ROSTER_PROGRESS_KEY = "wave2-squad-owner-roster";
    static class WaveTwoOwnershipPolicy {
        static String encodeRoster(java.util.Collection<UUID> roster){return String.join(",", roster.stream().map(UUID::toString).toList());}
        static List<UUID> decodeRoster(String text){
            return text == null || text.isBlank() ? List.of()
                    : java.util.Arrays.stream(text.split(",")).map(UUID::fromString).toList();
        }
    }
    Logger getLogger(){return Logger.getLogger("synthetic-profile-test");}
    Map<String,String> waveObjectiveProgressSnapshot(){return Map.of("other-objective","preserved");}
    // Wave 7 admission consults the sequential trial stage; this probe records
    // waves 1-6, so no trial is active here.
    me.copimine.endevent.runtime.RealitySplitTrialController realitySplitTrialController =
            new me.copimine.endevent.runtime.RealitySplitTrialController();

''' + methods + r'''
    static void require(boolean b,String m){if(!b)throw new AssertionError(m);}
    public static void main(String[] args){
        var main=new CombatProfileMainProbe();UUID id=new UUID(0,1);
        var player=new Player(id,"ordinary-owner",GameMode.SURVIVAL,true,false,20);
        main.officialRewardRoster.add(id);main.attemptLifecycle.begin(71,Set.of(id));
        main.combatProfiles.beginAttempt("event",71,71,Set.of(id),true);
        var context=main.combatProfileContext(player);
        require(context!=null&&context.activeCombat()&&context.tick()==80,"actual official context uses server tick and committed lifecycle");
        main.combatProfiles.acceptedHit(context,"first",EventCombatProfileService.Weapon.SWORD,false,false);
        UUID cleanupId=new UUID(0,2);
        main.waveTwoCleanupEntityUuids.add(cleanupId);
        main.tentacleActiveMillis=89_000L;
        main.tentacleGuardianHealth.put(0,0.0D);
        main.tentacleGuardianRespawnDeadlineMillis.put(0,90_000L);
        main.tentacleGuardianWarnedSlots.add(0);
        var checkpoint=main.objectiveProgressSnapshot();
        require("0".equals(checkpoint.get("boss-small-roster.count")),"living-roster codec coexists with combat profile");
        require(checkpoint.containsKey("combat-profile.schema")&&checkpoint.containsKey("other-objective"),"profile and wave objective checkpoint coexist");
        require("DONE".equals(checkpoint.get("boss-guardian-respawn-warned.0")),
                "guardian warning receipt coexists with combat profile and objective checkpoint");
        require("0".equals(checkpoint.get(WAVE_TWO_CLEANUP_EPOCH_PROGRESS_KEY)),
                "Wave 2 cleanup epoch is always represented in the durable objective checkpoint");
        require(cleanupId.toString().equals(checkpoint.get(WAVE_TWO_CLEANUP_UUIDS_PROGRESS_KEY)),
                "Wave 2 cleanup tombstones remain in the objective checkpoint");
        UUID outstandingId=new UUID(0,3);
        main.phase=EventPhase.RECOVERY_REQUIRED;main.waveTwoOutstandingEntityUuids.add(outstandingId);
        var recoveryCheckpoint=main.objectiveProgressSnapshot();
        require(outstandingId.toString().equals(recoveryCheckpoint.get(WAVE_TWO_OUTSTANDING_UUIDS_PROGRESS_KEY))
                        && cleanupId.toString().equals(recoveryCheckpoint.get(WAVE_TWO_CLEANUP_UUIDS_PROGRESS_KEY)),
                "recovery checkpoint retains both outstanding fighters and cleanup tombstones");
        main.phase=EventPhase.WAVE_6;main.activeWave=6;
        require(main.combatProfileContext(player)!=null,"same attempt carries observations through numbered wave transitions");
        main.restoreCombatProfiles(checkpoint);
        require(main.combatProfiles.snapshot(id).count(EventCombatProfileService.Stat.SWORD_HITS)==1,"actual Main restore retains persona aggregates");
        main.blackFogPhase=BlackFogPhase.FOG;main.phase=EventPhase.WAVE_5;main.activeWave=5;
        require(main.combatProfileContext(player).frozenFog(),"intentional fog freeze identified");
        main.phase=EventPhase.WAVE_6;main.activeWave=6;main.prisoner=id;
        require(main.combatProfileContext(player).confined(),"sphere prisoner identified");main.prisoner=null;
        for(GameMode mode:new GameMode[]{GameMode.CREATIVE,GameMode.SPECTATOR})
            require(main.combatProfileContext(new Player(id,"ordinary-owner",mode,true,false,20))==null,"noncombat mode excluded");
        require(main.combatProfileContext(new Player(id,"EndRiftTarget1",GameMode.SURVIVAL,true,false,1000))==null,"dedicated diagnostic attributes cannot become real persona");
        require(main.combatProfileContext(new Player(id,"ordinary-owner",GameMode.SURVIVAL,true,false,1000))==null,"artificial 1000-HP overrides excluded independently of name");
        main.phase=EventPhase.INTERMISSION_6;
        require(main.combatProfileContext(player)==null,"intermission excluded");main.phase=EventPhase.WAVE_6;
        main.attemptLifecycle.markDead(id,71);require(main.combatProfileContext(player)==null,"death invalidates observation entitlement");
        main.attemptLifecycle.markAlive(id,71);main.attemptLifecycle.markOffline(id,71);
        require(main.combatProfileContext(player)==null,"disconnect invalidates observation entitlement");
        main.attemptLifecycle.markAlive(id,71);main.generation=72;
        require(main.combatProfileContext(player)==null,"stale lifecycle cannot bind to new generation");main.generation=71;
        main.restoreCombatProfiles(Map.of());
        require(!main.combatProfiles.snapshot(id).fromEventStart()&&main.combatProfiles.snapshot(id).confidence()==0,"old event without instrumentation declares missing coverage");
        main.officialRewardRoster.clear();main.restoreCombatProfiles(checkpoint);
        require(main.combatProfiles.encode().isEmpty(),"foreign/restored UUID data cannot survive absent official roster");
    }
}
''', encoding="utf-8")
    listener_stub = tmp_path / "me/copimine/endevent/runtime/EventCombatProfileListener.java"
    listener_stub.parent.mkdir(parents=True, exist_ok=True)
    listener_stub.write_text("package me.copimine.endevent.runtime; public class EventCombatProfileListener {public void clear(){}}", encoding="utf-8")
    production = ROOT / "copimine-end-event/src/me/copimine/endevent"
    sources = [production / "domain/SmallKaguneRosterSnapshot.java",production / "runtime/EventCombatProfileService.java", production / "runtime/CombatSequence.java",
               production / "runtime/AttemptLifecycleController.java",
               production / "domain/wave7/EchoBehaviorStrategy.java",
               production / "domain/wave7/EchoLoadoutState.java",
               production / "domain/EventPhase.java", production / "domain/PreBossTickSnapshotPolicy.java",
               production / "domain/PressureBudgetController.java", production / "domain/WaveMechanicsPolicy.java", production / "domain/RiftCarrierPolicy.java",
               production / "domain/WaveTwoStagePolicy.java", production / "domain/WaveTwoHuntPolicy.java",
               production / "domain/WaveTwoEntityRecoveryPolicy.java",
               production / "domain/GuardianDeathLedger.java", production / "domain/CocoonFallPolicy.java", production / "domain/EndRiftAiPolicy.java",
               production / "domain/TentacleGuardianPolicy.java", production / "domain/TentacleScalingPolicy.java",
               production / "domain/SmallKaguneSpawnPolicy.java", production / "domain/SmallKaguneCadence.java",
               production / "domain/TentacleAnimationPolicy.java", production / "domain/BossPhase.java",
               production / "runtime/RealitySplitTrialController.java",
               production / "domain/Wave7TrialRoutePolicy.java",
               production / "domain/ChamberIsolationPolicy.java",
               production / "domain/Wave7RoomLayoutPolicy.java",
               production / "domain/Wave7EchoDuelCellPolicy.java",
               production / "runtime/RealitySplitEchoDuelState.java",
               listener_stub, probe]
    result = subprocess.run(["javac", str(ROOT / "tools/minecraft-26.3/protocol/src/main/java/me/copimine/protocol/EncounterGenerationPolicy.java"), "-encoding", "UTF-8", "-d", str(tmp_path), *map(str, sources)], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
    result = subprocess.run(["java", "-cp", str(tmp_path), "CombatProfileMainProbe"], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
