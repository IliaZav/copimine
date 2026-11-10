"""Behavioral regressions for the Guardian's actual pure combat policies."""
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "copimine-end-event/src"


def run_probe(tmp_path, body):
    java = tmp_path / "BossAiPolishProbe.java"
    java.write_text("""import java.util.*;
import me.copimine.endevent.domain.*;
public class BossAiPolishProbe {
 static void check(boolean b,String message){if(!b)throw new AssertionError(message);}
 public static void main(String[] args){
""" + body + "\n}}", encoding="utf-8")
    compile = subprocess.run(["javac", "-encoding", "UTF-8", "-sourcepath", str(SRC),
                              "-d", str(tmp_path), str(java)], capture_output=True, text=True)
    assert compile.returncode == 0, compile.stdout + compile.stderr
    run = subprocess.run(["java", "-cp", str(tmp_path), "BossAiPolishProbe"],
                         capture_output=True, text=True)
    assert run.returncode == 0, run.stdout + run.stderr


def test_final_seal_roles_are_reachable_by_the_real_brain(tmp_path):
    run_probe(tmp_path, """
      for(var spell:BossPhasePolicy.spellPool(BossPhase.LAST_SEAL))
        check(BossAbilityId.fromSpell(spell)!=null,"brain drops final-phase spell: "+spell);
      var grab=BossAbilityId.fromSpell(EndRiftAiPolicy.BossSpell.AMBUSH_GRAB);
      var small=BossAbilityId.fromSpell(EndRiftAiPolicy.BossSpell.SMALL_KAGUNE);
      check(grab.hardControl(),"ambush must reserve hard control");
      check(!small.hardControl(),"small melee summons cannot grab");
      check(grab.mechanic()!=small.mechanic(),"distinct Kagune roles share a hazard reservation");
    """)


def test_contact_moves_do_not_waste_casts_on_distant_targets(tmp_path):
    run_probe(tmp_path, """
      var id=new UUID(0,1);
      var player=BossPerceptionSnapshot.PlayerObservation.at(id,18,0,0,1,18,true);
      var scene=new BossPerceptionSnapshot(BossPhase.RIFT,null,List.of(player),
          new BossPerceptionSnapshot.Position(0,0,0),null,"",1,false);
      for(var ability:List.of(BossAbilityId.MELEE_SWIPE,BossAbilityId.GROUND_SLAM,BossAbilityId.CHEST_STRIKE)){
        var result=BossAbilitySelector.choose(scene,BossIntent.PRESSURE,id,null,
            List.of(ability),null,1,0);
        check(result.ability()==null,"out-of-range contact action selected: "+ability);
      }
    """)


def test_simultaneous_group_hits_do_not_break_a_valid_focus_lock(tmp_path):
    run_probe(tmp_path, """
      var focus=new UUID(0,1);var attacker=new UUID(0,2);
      long start=100_000,deadline=start+BossTargetPolicy.DEFAULT_LOCK_MILLIS;
      for(long now=start;now<deadline;now+=50){
        var result=BossGroupAggroPolicy.evaluate(List.of(
            new BossGroupAggroPolicy.Candidate(focus,true,true,now-20),
            new BossGroupAggroPolicy.Candidate(attacker,true,true,now)),
            focus,now,0,deadline,start);
        check(result.decision()==BossGroupAggroPolicy.Decision.HOLD,
            "group damage caused focus thrashing at elapsed "+(now-start));
        check(focus.equals(result.target()),"live locked focus changed");
        check(result.newLockUntilMillis()==deadline,"focus lock was extended");
      }
    """)


def test_health_perception_uses_player_maximum_and_clamps_invalid_values(tmp_path):
    run_probe(tmp_path, """
      check(BossPerceptionSnapshot.normalizedHealth(20,20)==1,"healthy player appears weak");
      check(BossPerceptionSnapshot.normalizedHealth(10,20)==0.5,"half-health player is misclassified");
      check(BossPerceptionSnapshot.normalizedHealth(50,40)==1,"overheal exceeds normalized range");
      check(BossPerceptionSnapshot.normalizedHealth(Double.NaN,20)==1,"invalid health creates a focus exploit");
      check(BossPerceptionSnapshot.normalizedHealth(10,0)==1,"invalid max creates a focus exploit");
    """)


def test_manual_spawn_alias_preserves_safety_arguments_without_mutating_input(tmp_path):
    run_probe(tmp_path, """
      String[] input={"SpAwN","BoSs","official","confirm"};
      var output=BossCommandPolicy.normalize(input);
      check(Arrays.equals(output,new String[]{"boss","spawn","official","confirm"}),"alias lost confirmation");
      check(input[0].equals("SpAwN"),"alias rewrote caller's input");
      check(Arrays.equals(BossCommandPolicy.normalize(new String[]{"boss","spawn"}),
          new String[]{"boss","spawn"}),"existing command changed");
      check(Arrays.equals(BossCommandPolicy.normalize(new String[]{"spawn","other"}),
          new String[]{"spawn","other"}),"unrelated command became boss spawn");
    """)


def test_final_phase_does_not_starve_small_kagune_during_a_long_group_fight(tmp_path):
    run_probe(tmp_path, """
      var a=new UUID(0,1);var b=new UUID(0,2);
      var scene=new BossPerceptionSnapshot(BossPhase.LAST_SEAL,null,List.of(
          BossPerceptionSnapshot.PlayerObservation.at(a,7,0,0,1,7,true),
          BossPerceptionSnapshot.PlayerObservation.at(b,8,0,1,1,8,true)),
          new BossPerceptionSnapshot.Position(0,0,0),null,"",1,false);
      var choices=BossPhasePolicy.spellPool(BossPhase.LAST_SEAL).stream().map(BossAbilityId::fromSpell).toList();
      var brain=new BossBrain();var seen=new HashSet<BossAbilityId>();BossAbilityId previous=null;
      for(int cursor=0;cursor<24;cursor++){
        var decision=brain.decide(scene,choices,previous,a,null,1,cursor);
        previous=decision.ability().ability();seen.add(previous);
        brain.recordAccepted(previous,1);
      }
      check(seen.contains(BossAbilityId.fromSpell(EndRiftAiPolicy.BossSpell.SMALL_KAGUNE)),
          "small Kagune is registered but starved forever by top-three cursor rotation: "+seen);
      check(seen.contains(BossAbilityId.fromSpell(EndRiftAiPolicy.BossSpell.AMBUSH_GRAB)),"ambush starved");
    """)
