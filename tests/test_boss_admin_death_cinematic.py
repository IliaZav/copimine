"""The actual admin HP adapter must use the disposable death scene, not vanilla death drops."""
from pathlib import Path
import subprocess
from tests.test_end_rift_portal_layer_runtime import declaration

ROOT = Path(__file__).resolve().parents[1]


def test_test_command_lethal_damage_preserves_the_actor_for_its_death_clip(tmp_path):
    source = (ROOT / 'copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java').read_text(encoding='utf-8')
    adapter = declaration(source, 'private void applyCurrentAdministrativeDamage(')
    java = tmp_path / 'AdminDeathProbe.java'
    java.write_text('''
import java.util.*;
class AdminDeathProbe {
    boolean bossDefeatCinematicStarted, testCombatAiMode=true, testBoss=true;
    String eventId="test"; UUID bossKillerUuid; int scenes, vanillaDeaths, phases;
    class Entity { UUID id=UUID.randomUUID(); UUID getUniqueId(){return id;} }
    class LivingEntity extends Entity {
        double health=100; boolean isValid(){return true;} boolean isDead(){return health<=0;}
        double getHealth(){return health;} void setHealth(double value){health=value;if(value<=0)vanillaDeaths++;}
    }
    class Player extends Entity {}
    boolean isTestBoss(LivingEntity boss){return testBoss;}
    void commitDisposableBossDefeat(LivingEntity boss){scenes++;bossDefeatCinematicStarted=true;}
    void synchronizeBossPhase(LivingEntity boss){phases++;}
    java.util.logging.Logger getLogger(){return java.util.logging.Logger.getLogger("test");}
    static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
    public static void main(String[] args){
        var p=new AdminDeathProbe();var boss=p.new LivingEntity();
        p.applyCurrentAdministrativeDamage(boss,40,null);
        check(boss.health==60&&p.phases==1,"ordinary phase-probe damage remains real HP damage");
        p.applyCurrentAdministrativeDamage(boss,1000,null);
        check(p.scenes==1&&boss.health==60&&p.vanillaDeaths==0,"lethal test command must retain the actor for the death scene");
        p.applyCurrentAdministrativeDamage(boss,1000,null);
        check(p.scenes==1&&p.vanillaDeaths==0,"repeating command cannot restart scene or produce pearls");
        var official=new AdminDeathProbe();official.testBoss=false;official.testCombatAiMode=false;
        var real=official.new LivingEntity();official.applyCurrentAdministrativeDamage(real,1000,null);
        check(real.health==100&&official.scenes==0&&official.vanillaDeaths==0,"admin command cannot commit official victory");
    }
''' + adapter + '\n}', encoding='utf-8')
    built = subprocess.run(['javac', '-encoding', 'UTF-8', '-d', str(tmp_path), str(java)], capture_output=True, text=True)
    assert built.returncode == 0, built.stdout + built.stderr
    tested = subprocess.run(['java', '-cp', str(tmp_path), 'AdminDeathProbe'], capture_output=True, text=True)
    assert tested.returncode == 0, tested.stdout + tested.stderr
