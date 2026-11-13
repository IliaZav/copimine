"""Execute automatic dispatch against real phase pools and the production boss brain."""
from pathlib import Path
import subprocess
import pytest
from tests.test_wave_skeleton_protection_runtime import declaration
ROOT=Path(__file__).resolve().parents[1]
SRC=ROOT/'copimine-end-event/src'

@pytest.fixture(scope='module')
def probe(tmp_path_factory):
    out=tmp_path_factory.mktemp('boss-spell-availability')
    source=(SRC/'me/copimine/endevent/CopiMineEndEvent.java').read_text(encoding='utf-8')
    methods='\n'.join(declaration(source,s) for s in (
        'private boolean isCurrentBossMeleeFury(',
        'private List<EndRiftAiPolicy.BossSpell> currentBossSpellPool(',
        'private boolean castCurrentBossSpell(LivingEntity boss, boolean forced)'))
    fixture=out/'BossAvailabilityProbe.java'
    fixture.write_text('''
import java.util.*;
import me.copimine.endevent.domain.*;
public class BossAvailabilityProbe {
    long generation=1,cocoonFallGeneration=1;
    BossPhase bossPhase=BossPhase.LAST_SEAL;
    CocoonFallPolicy.Stage cocoonFallStage=CocoonFallPolicy.Stage.COCOONED;
    boolean bossFinalStrikeUsed;
    enum BossAbilityState {NONE} BossAbilityState bossAbilityState=BossAbilityState.NONE;
    EndRiftAiPolicy.BossSpell previousBossSpell;
    int bossSpellCursor,attemptedFinal,accepted; BossBrain bossBrain=new BossBrain();
    static class LivingEntity {}
    static class Config {boolean enabled; Config(boolean value){enabled=value;} Config finalStrikeTuning(){return this;} boolean enabled(){return enabled;}}
    Config config=new Config(true);
    boolean isBossCombatRuntimeActive(LivingEntity boss){return true;}
    boolean bossAmbushOwnsMajorCast(){return false;}
    EndRiftAiPolicy.BossSpell spellForAbility(BossAbilityId id){
        for(var spell:EndRiftAiPolicy.BossSpell.values())if(BossAbilityId.fromSpell(spell)==id)return spell;
        return null;
    }
    BossBrain.Decision chooseCurrentBossDecision(LivingEntity boss,List<EndRiftAiPolicy.BossSpell> pool){
        UUID player=new UUID(0,1);
        var observation=new BossPerceptionSnapshot.PlayerObservation(player,true,1,3,true,false,0,false,false,false,3,0,0,0,0,0,"");
        var snapshot=new BossPerceptionSnapshot(bossPhase,null,List.of(observation),null,null,"",generation,false);
        return bossBrain.decide(snapshot,pool.stream().map(BossAbilityId::fromSpell).toList(),
            BossAbilityId.fromSpell(previousBossSpell),player,null,generation,bossSpellCursor);
    }
    boolean castCurrentBossSpell(LivingEntity boss,EndRiftAiPolicy.BossSpell spell,boolean forced){
        previousBossSpell=spell;
        if(spell==EndRiftAiPolicy.BossSpell.FINAL_STRIKE){
            attemptedFinal++;
            if(bossFinalStrikeUsed || !config.enabled)return false;
            bossFinalStrikeUsed=true;
        }
        accepted++;return true;
    }
''' + methods + '''
    public static void main(String[] args){
        var p=new BossAvailabilityProbe();
        if(args[0].equals("fury")){
            p.cocoonFallStage=CocoonFallPolicy.Stage.MELEE_FURY;
            if(p.currentBossSpellPool().contains(EndRiftAiPolicy.BossSpell.FINAL_STRIKE))
                throw new AssertionError("remote target explosion leaked into permanent melee fury");
        }else{
            p.bossFinalStrikeUsed=args[0].equals("used");
            p.config.enabled=!args[0].equals("disabled");
            for(int tick=0;tick<20;tick++)
                if(!p.castCurrentBossSpell(new LivingEntity(),false))
                    throw new AssertionError("automatic selector stalled on unavailable spell at attempt "+tick);
            int expected=args[0].equals("fresh")?1:0;
            if(p.attemptedFinal!=expected)throw new AssertionError("one-shot availability was not respected");
            if(p.accepted!=20)throw new AssertionError("valid pressure was lost");
        }
    }
}
''',encoding='utf-8')
    result=subprocess.run(['javac','-encoding','UTF-8','-sourcepath',str(SRC),'-d',str(out),str(fixture)],capture_output=True,text=True)
    assert result.returncode==0,result.stderr
    return out

@pytest.mark.parametrize('mode',['used','disabled','fresh','fury'])
def test_actual_automatic_selector_skips_unavailable_actions(probe,mode):
    result=subprocess.run(['java','-cp',str(probe),'BossAvailabilityProbe',mode],capture_output=True,text=True)
    assert result.returncode==0,result.stderr
