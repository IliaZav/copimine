"""Death/quit must cancel the real trial's locked attack, not only the generic lease."""
from pathlib import Path
import subprocess
from tests.test_end_rift_death_item_integrations import extract

ROOT = Path(__file__).resolve().parents[1]


def test_trial_target_death_cancels_locked_action_without_resetting_actor(tmp_path):
    source = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(encoding="utf-8")
    cancel = extract(source, "void cancelWaveCombatTarget(UUID target)")
    probe = tmp_path / "TrialCancelProbe.java"
    probe.write_text(r'''
import java.util.*;
public class TrialCancelProbe {
    static class Vector {}
    static class Entity {}
    static class Mob extends Entity {int stops;void setVelocity(Vector v){stops++;}void removePotionEffect(Object type){}void setCustomNameVisible(boolean visible){}}
    static class PotionEffectType {static final Object INVISIBILITY=new Object();}
    static class RealitySplitTrialRuntimeState {UUID target;int action;long nextActionTick;}
    static class WaveCombatCoordinator {
        record Lease(UUID target,UUID owner){} Lease currentLease(long gen,long tick){return null;}
    }
    WaveCombatCoordinator waveCombatCoordinator=new WaveCombatCoordinator();
    long generation=43,eventTickCounter=100;
    Map<UUID,RealitySplitTrialRuntimeState> realitySplitTrialRuntimeStates=new HashMap<>();
    Map<UUID,Entity> ownedEntities=new HashMap<>();
    boolean isOfficialWave7ReturnContext(){return true;}
    void cancelWaveCombatAttack(UUID owner,WaveCombatCoordinator.Lease lease,long gen){}
    void finishTrialAttack(RealitySplitTrialRuntimeState state,long delay){state.action=0;state.nextActionTick=eventTickCounter+delay;}
''' + cancel + r'''
    public static void main(String[] args){
        var main=new TrialCancelProbe();var owner=new UUID(0,1);var other=new UUID(0,2);
        var actor=new UUID(0,3);var mob=new Mob();var action=new RealitySplitTrialRuntimeState();action.target=owner;action.action=1;
        main.ownedEntities.put(actor,mob);main.realitySplitTrialRuntimeStates.put(actor,action);
        main.cancelWaveCombatTarget(other);
        if(action.action!=1)throw new AssertionError("unrelated ordinary trial attack was cancelled");
        main.cancelWaveCombatTarget(owner);
        if(action.action!=0||mob.stops!=1||!main.ownedEntities.containsKey(actor))
            throw new AssertionError("death/quit only cancelled generic lease, leaving Wave 7 locked release alive");
        if(action.nextActionTick<=100)throw new AssertionError("cancelled actor needs a recovery interval before reacquiring");
    }
}
''', encoding="utf-8")
    result = subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(tmp_path), str(probe)], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
    result = subprocess.run(["java", "-cp", str(tmp_path), "TrialCancelProbe"], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
