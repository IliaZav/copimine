"""Execute the production spell-command branch, including its admission result."""
from pathlib import Path
import subprocess
from tests.test_wave_navigation_adapter import extract

ROOT = Path(__file__).resolve().parents[1]


def test_last_seal_commands_reach_the_real_dispatcher_and_report_refusal(tmp_path):
    source = (ROOT/'copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java').read_text('utf-8')
    handle = extract(source, 'void handleBoss(CommandSender sender, String[] args)')
    start = handle.index('case "spell" ->')
    end = handle.index('{', start) + 1
    depth = 1
    while depth:
        depth += (handle[end] == '{') - (handle[end] == '}')
        end += 1
    branch = handle[start:end]
    code = '''import java.util.*;
public class SpellCommandProbe {
 static class LivingEntity{} static class CommandSender{}
 static class EndRiftAiPolicy { enum BossSpell {
  VOID_BLAST,RIFT_PROJECTILE,RIFT_ARROWS,VOID_MARK,ARENA_INFERNO,FINAL_STRIKE,BOSS_FIREBALL,
  MELEE_SWIPE,CHEST_STRIKE,GROUND_SLAM,AMBUSH_GRAB,SMALL_KAGUNE,SUMMON_SERVANTS;
  String id(){return name().toLowerCase(Locale.ROOT);}
 }}
 int calls;boolean accepted=true;String lastMessage="",bossPhase="LAST_SEAL";EndRiftAiPolicy.BossSpell last;
 LivingEntity liveBoss(){return new LivingEntity();}
 void message(CommandSender sender,String text){lastMessage=text;}
 boolean castCurrentBossSpell(LivingEntity b,EndRiftAiPolicy.BossSpell s,boolean forced){
  if(!forced)throw new AssertionError();calls++;last=s;return accepted;
 }
 void command(String name){String[] args={"boss","spell",name};CommandSender sender=new CommandSender();
  switch(args[1]){''' + branch + ''' default->{} }
 }
 public static void main(String[] args){
  var p=new SpellCommandProbe();
  p.command("ambush_grab");if(p.calls!=1 || p.last!=EndRiftAiPolicy.BossSpell.AMBUSH_GRAB)throw new AssertionError("ambush is unreachable");
  p.command("small_kagune");if(p.calls!=2 || p.last!=EndRiftAiPolicy.BossSpell.SMALL_KAGUNE)throw new AssertionError("small summon is unreachable");
  p.accepted=false;p.command("ground_slam");
  if(!p.lastMessage.contains("не запущен"))throw new AssertionError("a refused cast reported success: "+p.lastMessage);
 }
}'''
    java = tmp_path/'SpellCommandProbe.java'
    java.write_text(code, encoding='utf-8')
    compiled = subprocess.run(['javac','-encoding','UTF-8','-d',str(tmp_path),str(java)],capture_output=True,text=True)
    assert compiled.returncode == 0, compiled.stderr
    result = subprocess.run(['java','-cp',str(tmp_path),'SpellCommandProbe'],capture_output=True,text=True)
    assert result.returncode == 0, result.stderr
