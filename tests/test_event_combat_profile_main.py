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
        "void restoreCombatProfiles(Map<String, String> progress)",
        "Map<String, String> objectiveProgressSnapshot()",
        "boolean isOfficialAttemptActive()",
    ))
    probe = tmp_path / "CombatProfileMainProbe.java"
    probe.write_text(r'''
import java.util.*;import java.util.logging.*;
import me.copimine.endevent.domain.EventPhase;
import me.copimine.endevent.runtime.*;
import me.copimine.endevent.runtime.EventCombatProfileService.Context;
public class CombatProfileMainProbe {
    enum GameMode { SURVIVAL,ADVENTURE,CREATIVE,SPECTATOR }
    enum BlackFogPhase { COMBAT,SAFE,FOG }
    record Player(UUID id,String name,GameMode mode,boolean online,boolean dead,double max) {
        UUID getUniqueId(){return id;}String getName(){return name;}GameMode getGameMode(){return mode;}
        boolean isOnline(){return online;}boolean isDead(){return dead;}double getMaxHealth(){return max;}
    }
    static class Bukkit {static int getCurrentTick(){return 80;}}
    static class PreBoss {long elapsedServerTicks(long tick){return tick;}}
    boolean bootstrapped=true,testWaveFrontVisualMode,testCombatAiMode,testWaveInspectionMode,preBossHandoffCommitted;
    String eventId="event";long generation=71,eventTickCounter=80,restoredPreBossElapsedTicks,lastCombatProfileSaveTick;
    int activeWave=1;EventPhase phase=EventPhase.WAVE_1;BlackFogPhase blackFogPhase=BlackFogPhase.COMBAT;
    EventCombatProfileService combatProfiles=new EventCombatProfileService();
    EventCombatProfileListener combatProfileListener;
    AttemptLifecycleController attemptLifecycle=new AttemptLifecycleController();
    Set<UUID> officialRewardRoster=new LinkedHashSet<>();UUID prisoner;
    PreBoss preBossTransitionController=new PreBoss();
    static class Chambers {Assignment assignment(){return new Assignment();}}
    static class Assignment {Map<UUID,Integer> chamberByPlayer(){return Map.of();}}
    Chambers realitySplitChamberController=new Chambers();
    boolean isOfficialWave7ReturnContext(){return false;} long wave7ReturnGraceMillis(){return 120_000L;}
    boolean isOfficialCurrentAttempt(){return isOfficialAttemptActive()&&!testWaveFrontVisualMode&&!testCombatAiMode;}
    boolean isCombatTarget(Player player){return player!=null&&player.isOnline()&&!player.isDead();}
    UUID ritualPrisonerId(){return prisoner;}
    Logger getLogger(){return Logger.getLogger("synthetic-profile-test");}
    Map<String,String> waveObjectiveProgressSnapshot(){return Map.of("other-objective","preserved");}
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
        var checkpoint=main.objectiveProgressSnapshot();
        require(checkpoint.containsKey("combat-profile.schema")&&checkpoint.containsKey("other-objective"),"profile and wave objective checkpoint coexist");
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
    sources = [production / "runtime/EventCombatProfileService.java", production / "runtime/AttemptLifecycleController.java",
               production / "domain/EventPhase.java", production / "domain/PreBossTickSnapshotPolicy.java", listener_stub, probe]
    result = subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(tmp_path), *map(str, sources)], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
    result = subprocess.run(["java", "-cp", str(tmp_path), "CombatProfileMainProbe"], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
