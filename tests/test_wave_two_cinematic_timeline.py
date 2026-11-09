"""Execute the production cinematic clock and rotation math without Minecraft/Gradle."""
import subprocess
import json
from pathlib import Path

from tests.test_end_rift_phantom_session import _jdk_bin

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / 'tools/minecraft-26.3/client/src/main/java/me/copimine/client'


def test_intro_clock_and_bounded_shortest_angle_turns(tmp_path):
    probe = tmp_path / 'CinematicProbe.java'
    probe.write_text('''package me.copimine.client;
public class CinematicProbe {
 static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
 public static void main(String[] args) {
  check(WaveTwoCinematicTimeline.visibleCount(0,70)==0,"field hidden initially");
  check(WaveTwoCinematicTimeline.firstObserverVisible(0),"one opening observer");
  check(!WaveTwoCinematicTimeline.firstObserverVisible(3500),"opening observer retires");
  check(WaveTwoCinematicTimeline.visibleCount(6000,70)>0 && WaveTwoCinematicTimeline.visibleCount(6000,70)<70,"progressive reveal");
  check(WaveTwoCinematicTimeline.visibleCount(9000,70)==70,"full stare before combat");
  check(!WaveTwoCinematicTimeline.combatReady(9999) && WaveTwoCinematicTimeline.combatReady(10000),"ten second boundary");
  check(WaveTwoCinematicTimeline.shaderStrength(1500)==0 && WaveTwoCinematicTimeline.shaderStrength(3000)>0,"effect follows scream");
  check(Math.abs(WaveTwoCinematicTimeline.shaderStrength(4500)-.8F)<.00001,"fade ends at vanilla 26.3 invert amount");
  check(WaveTwoCinematicTimeline.darknessStrength(3000)>0 && WaveTwoCinematicTimeline.darknessStrength(10000)==0,"temporary darkness");
  check(WaveTwoCinematicTimeline.parseIntroAge("intro-age=10000")==10000,"late join finished");
  check(WaveTwoCinematicTimeline.parseIntroAge("intro-age=10001")==-1,"invalid age rejected");
  check(WaveTwoCinematicTimeline.parseIntroAge("intro-age=-1")==-1,"negative rejected");
  check(WaveTwoCinematicTimeline.firstAudibleAppearance(0,70)==68,"lag catch-up bounded to two cues");
  check(WaveTwoCinematicTimeline.firstAudibleAppearance(20,21)==20,"normal appearance has one cue");
  check(WaveTwoCinematicTimeline.screamCue(1499,1500),"one opening scream boundary");
  check(!WaveTwoCinematicTimeline.screamCue(1500,1501),"no repeated scream");
  check(!WaveTwoCinematicTimeline.screamCue(0,10000),"no delayed scream after intro");
  check(Math.abs(WaveTwoPhantomLayout.shortestAngle(179,-179)-2)<.001,"wrap shortest arc");
  var origin=new WaveTwoPhantomLayout.Position(0,64,0);
  for (int angle=-180;angle<=180;angle++) {
   var target=new WaveTwoPhantomLayout.Position(Math.sin(Math.toRadians(angle))*5,65,Math.cos(Math.toRadians(angle))*5);
   var pose=WaveTwoPhantomLayout.turnToward(origin,target,179,10,8);
   check(Math.abs(WaveTwoPhantomLayout.shortestAngle(179,pose.bodyYawDegrees()))<=8.001,"bounded body rate");
   check(Math.abs(pose.headYawDegrees())<=55.001,"head bounded relative to body");
  }
  System.out.println("OK");
 }
}''', encoding='utf-8')
    sources=[SRC/'WaveTwoPhantomLayout.java', SRC/'WaveTwoCinematicTimeline.java']
    result=subprocess.run([str(_jdk_bin('javac')), '-d', str(tmp_path), str(probe), *map(str,sources)],capture_output=True,text=True)
    assert result.returncode==0,result.stdout+result.stderr
    result=subprocess.run([str(_jdk_bin('java')), '-cp', str(tmp_path), 'me.copimine.client.CinematicProbe'],capture_output=True,text=True)
    assert result.returncode==0,result.stdout+result.stderr


def test_intro_post_chain_has_bounded_real_fade_and_world_only_darkness():
    resources = ROOT / 'tools/minecraft-26.3/client/src/main/resources/assets/copimineclient'
    levels = []
    for stage in range(24):
        chain = json.loads((resources / f'post_effect/wave2_intro_{stage}.json').read_text())
        first = chain['passes'][0]
        assert first['fragment_shader'] == 'copimineclient:post/wave2_intro'
        assert first['inputs'][0]['target'] == 'minecraft:main'
        config = {item['name']: item['value'] for item in first['uniforms']['IntroConfig']}
        assert 0 <= config['InvertStrength'] <= 1
        assert 0 <= config['DarknessStrength'] <= .65
        levels.append(config)
    assert levels[0]['InvertStrength'] < levels[8]['InvertStrength']
    assert max(stage['DarknessStrength'] for stage in levels) > .6
    # Pinned minecraft-client.jar post_effect/invert.json uses InverseAmount=.8.
    assert levels[-1]['InvertStrength'] == .8
    assert levels[-1]['DarknessStrength'] < .05


def test_intro_controller_really_requests_fade_stages(tmp_path):
    stub = tmp_path / 'ClientPostProcessController.java'
    stub.write_text('''package me.copimine.client;
public class ClientPostProcessController {
 String requested; int clears;
 public boolean apply(String id,float intensity) { requested=id; return true; }
 public void clear() { requested=null; clears++; }
 public String lastFailureReason() { return ""; }
}''', encoding='utf-8')
    probe = tmp_path / 'FadeProbe.java'
    probe.write_text('''package me.copimine.client;
public class FadeProbe {
 static void check(boolean value,String message) { if(!value) throw new AssertionError(message); }
 public static void main(String[] args) {
  var post=new ClientPostProcessController(); var controller=new EndermanMarkShaderController(post);
  controller.updateIntro(1499); check(post.requested==null,"no premature effect");
  controller.updateIntro(1500); check("WAVE2_INTRO_0".equals(post.requested),"first actual fade stage");
  controller.updateIntro(4499); check("WAVE2_INTRO_23".equals(post.requested),"last actual fade stage");
  controller.updateIntro(4500); check("ENDERMAN".equals(post.requested),"vanilla effect after fade");
  controller.update(false); check(post.requested==null && post.clears==1,"withdraw on clear");
 }
}''', encoding='utf-8')
    result = subprocess.run([str(_jdk_bin('javac')), '-d', str(tmp_path), str(stub), str(probe),
                             str(SRC/'WaveTwoCinematicTimeline.java'),
                             str(SRC/'EndermanMarkShaderController.java')], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
    result = subprocess.run([str(_jdk_bin('java')), '-cp', str(tmp_path), 'me.copimine.client.FadeProbe'],
                            capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr


def test_authoritative_wave_age_survives_refresh_group_clear_and_relog(tmp_path):
    stub = tmp_path / 'BridgePayload.java'
    stub.write_text('''package me.copimine.client;
record BridgePayload(String messageType,int protocol,long seq,long timestampMillis,String sessionId,
 String clientVersion,String shaderpack,int durationMillis,String mode,String clearPolicy,String status) { }
class ClientBridgeProtocol { static final String TYPE_END_EVENT_PREFIX="END_EVENT:";
 static final int PROTOCOL_VERSION=4; }
''', encoding='utf-8')
    probe = tmp_path / 'AgeProbe.java'
    probe.write_text('''package me.copimine.client;
public class AgeProbe {
 static void check(boolean value,String message) { if(!value) throw new AssertionError(message); }
 static BridgePayload packet(long timestamp,int cycle,String age,String status) {
  return new BridgePayload("END_EVENT:END_PLAYER_STATE",4,7,timestamp,"event",
   "wave2-hunt-mark","WAVE2_HUNT_MARK_V1",1500,"the_end|wave2|"+cycle,age,status);
 }
 public static void main(String[] args) {
  var manager=new EndEventPlayerVisualManager();
  check(manager.apply(packet(100,1,"intro-age=3000","ACTIVE"),20000),"initial signed age");
  check(manager.visualState("the_end",20000).startedAtMillis()==17000,"signed intro start");
  check(manager.apply(packet(200,1,"intro-age=3500","ACTIVE"),20500),"refresh");
  check(manager.visualState("the_end",20500).startedAtMillis()==17000,"refresh does not reset");
  check(manager.apply(packet(300,1,"intro-age=2000","ACTIVE"),21000),"lower age refresh");
  check(manager.visualState("the_end",21000).startedAtMillis()==17000,"cannot rewind presentation");
  check(manager.apply(packet(400,1,"server-cleanup","CLEAR"),22000),"clear metadata cannot block withdrawal");
  check(manager.apply(packet(500,2,"intro-age=10000","ACTIVE"),26000),"next group");
  check(WaveTwoCinematicTimeline.combatReady(26000-manager.visualState("the_end",26000).startedAtMillis()),"no per-group replay");
  manager.reset();
  check(manager.apply(packet(600,2,"intro-age=10000","ACTIVE"),30000),"relog");
  check(WaveTwoCinematicTimeline.combatReady(30000-manager.visualState("the_end",30000).startedAtMillis()),"no relog replay");
  check(!manager.apply(packet(700,2,"intro-age=10001","ACTIVE"),30010),"reject malformed authoritative age");
 }
}''', encoding='utf-8')
    result = subprocess.run([str(_jdk_bin('javac')), '-d', str(tmp_path), str(stub), str(probe),
                             str(SRC/'WaveTwoCinematicTimeline.java'),
                             str(SRC/'EndEventPlayerVisualManager.java')], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
    result = subprocess.run([str(_jdk_bin('java')), '-cp', str(tmp_path), 'me.copimine.client.AgeProbe'],
                            capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr

