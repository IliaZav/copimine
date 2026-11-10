"""The disposable /cmend boss spawn must use combat without advancing official state."""
from pathlib import Path
import subprocess
from tests.test_wave_navigation_adapter import extract

ROOT=Path(__file__).resolve().parents[1]
SOURCE=ROOT/'copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java'


def test_disposable_boss_combat_is_scoped_and_independent_of_official_phase(tmp_path):
    source=SOURCE.read_text('utf-8')
    method=extract(source,'boolean isBossCombatRuntimeActive(LivingEntity boss)')
    java='''import java.util.*;
public class BossCommandRuntimeProbe {
 enum EventPhase { COLLECTING,WAVE_7,RECOVERY_REQUIRED,BOSS_ACTIVE,BOSS_FINISH,UNLOCKED }
 EventPhase phase=EventPhase.COLLECTING;boolean testCombatAiMode,bossDefeatCinematicStarted;String eventId="event";long generation=1;
 UUID bossUuid=UUID.randomUUID();
 static class LivingEntity {UUID id;boolean valid=true,dead,test,scoped=true;
 LivingEntity(UUID id){this.id=id;}UUID getUniqueId(){return id;}boolean isValid(){return valid;}boolean isDead(){return dead;}}
 boolean isTestBoss(LivingEntity boss){return boss.test;}
 boolean ownedBySession(LivingEntity boss,String event,long generation){return boss.scoped;}
 static void check(boolean b){if(!b)throw new AssertionError();}
 public static void main(String[] args){
 var p=new BossCommandRuntimeProbe();var actor=new LivingEntity(p.bossUuid);
 check(!p.isBossCombatRuntimeActive(actor));actor.test=true;
 check(!p.isBossCombatRuntimeActive(actor));p.testCombatAiMode=true;
 for(var phase:EventPhase.values()){p.phase=phase;check(p.isBossCombatRuntimeActive(actor));}
 actor.scoped=false;check(!p.isBossCombatRuntimeActive(actor));actor.scoped=true;
 actor.id=UUID.randomUUID();check(!p.isBossCombatRuntimeActive(actor));actor.id=p.bossUuid;
 actor.dead=true;check(!p.isBossCombatRuntimeActive(actor));actor.dead=false;
 actor.valid=false;check(!p.isBossCombatRuntimeActive(actor));actor.valid=true;
 p.testCombatAiMode=false;actor.test=false;p.phase=EventPhase.BOSS_ACTIVE;check(p.isBossCombatRuntimeActive(actor));
 p.phase=EventPhase.BOSS_FINISH;check(!p.isBossCombatRuntimeActive(actor));
 p.phase=EventPhase.BOSS_ACTIVE;p.bossDefeatCinematicStarted=true;check(!p.isBossCombatRuntimeActive(actor));
 check(!p.isBossCombatRuntimeActive(null));
 }
'''
    path=tmp_path/'BossCommandRuntimeProbe.java';path.write_text(java+method+'}',encoding='utf-8')
    built=subprocess.run(['javac','-d',str(tmp_path),str(path)],capture_output=True,text=True)
    assert built.returncode==0,built.stderr
    run=subprocess.run(['java','-cp',str(tmp_path),'BossCommandRuntimeProbe'],capture_output=True,text=True)
    assert run.returncode==0,run.stderr
