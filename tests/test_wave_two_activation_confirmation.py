"""Execute real client reservation/confirmation state without Minecraft or Gradle."""
import subprocess
from pathlib import Path

from tests.test_end_rift_phantom_session import _jdk_bin

ROOT = Path(__file__).resolve().parents[1]


def test_only_confirmed_exact_reserved_phantom_retires(tmp_path):
    probe = tmp_path / 'ConfirmationProbe.java'
    probe.write_text('''package me.copimine.client;
import java.util.UUID;
import me.copimine.protocol.EndEventBridgeBodyCodec;
public class ConfirmationProbe {
 static final UUID EVENT=UUID.fromString("11111111-2222-3333-4444-555555555555");
 static void check(boolean value,String message) { if(!value) throw new AssertionError(message); }
 static EndEventBridgeBodyCodec.Body body(long time,int cycle,String id,String position,String status) {
  return new EndEventBridgeBodyCodec.Body(EndEventBridgeBodyCodec.Type.END_PLAYER_STATE,4,time,EVENT,
    "wave2-phantom-activation",position,"WAVE2_PHANTOM_ACTIVATION_V1",
    status.equals("ACTIVE")?1000:0,0F,0,0,"the_end|wave2|"+cycle,"",id,"",status);
 }
 static boolean used(WaveTwoPhantomActivationManager m,String id) {
  return m.usedPhantomIds("the_end",EVENT.toString(),4,1).contains(id);
 }
 public static void main(String[] args) {
  var m=new WaveTwoPhantomActivationManager();
  check(!m.apply(body(90,1,"unknown","1,64,2","CLEAR"),100),"unknown clear cannot reserve or poison");
  check(m.apply(body(100,1,"phantom-00","1,64,2","ACTIVE"),100),"reserve");
  check(!used(m,"phantom-00"),"reservation is not materialization");
  check(!m.apply(body(110,1,"phantom-00","2,64,2","ACTIVE"),110),"refresh cannot move reserved position");
  check(m.active("the_end",1100)==null,"warning times out");
  check(!used(m,"phantom-00"),"timeout cannot retire phantom");
  check(!m.apply(body(120,1,"phantom-00","2,64,2","CLEAR"),1101),"wrong coordinate clear refused");
  check(m.apply(body(130,1,"phantom-00","1,64,2","CLEAR"),1102),"late exact confirmation accepted");
  check(used(m,"phantom-00"),"confirmed phantom retires");
  check(!m.apply(body(140,1,"phantom-00","1,64,2","ACTIVE"),1103),"confirmed cannot reactivate");
  check(m.apply(body(150,1,"phantom-01","3,64,4","ACTIVE"),1200),"second reserve");
  check(m.apply(body(160,1,"phantom-01","3,64,4","CANCEL"),1250),"cancel warning");
  check(!used(m,"phantom-01") && m.active("the_end",1251)==null,"cancel leaves phantom intact");
  check(!m.apply(body(165,1,"phantom-01","3,64,4","CLEAR"),1252),"cancelled reservation cannot late confirm");
  check(m.apply(body(170,1,"phantom-01","3,64,4","ACTIVE"),1300),"cancelled phantom can retry");
  check(m.apply(body(180,1,"phantom-01","3,64,4","CANCEL"),1350),"retry cancelled");
  check(m.apply(body(190,1,"phantom-02","5,64,6","ACTIVE"),1400),"third reserve");
  check(m.apply(body(200,1,"phantom-02","5,64,6","ACTIVE"),2500),"expired reservation retries without active() call");
  check(m.apply(body(210,2,"phantom-03","7,64,8","ACTIVE"),2600),"new cycle");
  check(!m.apply(body(220,1,"phantom-02","5,64,6","CLEAR"),2700),"old cycle confirmation fenced");
 }
}''', encoding='utf-8')
    client = ROOT / 'tools/minecraft-26.3/client/src/main/java/me/copimine/client'
    protocol = ROOT / 'tools/minecraft-26.3/protocol/src/main/java/me/copimine/protocol'
    sources = [probe, client/'WaveTwoPhantomActivationManager.java', client/'WaveTwoPhantomLayout.java',
               *(protocol/name for name in ('EndEventBridgeBodyCodec.java','BridgeSigningPolicy.java','BridgeEnvelope.java'))]
    result = subprocess.run([str(_jdk_bin('javac')), '-d', str(tmp_path), *map(str,sources)],
                            capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
    result = subprocess.run([str(_jdk_bin('java')), '-cp', str(tmp_path), 'me.copimine.client.ConfirmationProbe'],
                            capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
