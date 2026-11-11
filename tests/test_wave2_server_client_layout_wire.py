"""Exercise server-authored positions through the real codec and client cache."""
import subprocess
from pathlib import Path

from tests.test_end_rift_phantom_layout_manager import _jdk_bin

ROOT = Path(__file__).resolve().parents[1]
CLIENT = ROOT / "tools/minecraft-26.3/client/src/main/java/me/copimine/client"
PROTOCOL = ROOT / "tools/minecraft-26.3/protocol/src/main/java/me/copimine/protocol"
SERVER = ROOT / "copimine-end-event/src/me/copimine/endevent/domain"


def test_server_layout_survives_the_actual_client_admission_boundary(tmp_path):
    probe = tmp_path / "LayoutWireProbe.java"
    probe.write_text(r"""package me.copimine.client;
import java.util.UUID;
import me.copimine.endevent.domain.WaveTwoPhantomLayoutPolicy;
import me.copimine.protocol.EndEventBridgeBodyCodec;
public final class LayoutWireProbe {
 static final UUID EVENT=UUID.fromString("11111111-2222-3333-4444-555555555555");
 static void check(boolean ok,String message) { if(!ok) throw new AssertionError(message); }
 static EndEventBridgeBodyCodec.Body packet(int cycle,long timestamp,String id,String coords,String status) {
  return new EndEventBridgeBodyCodec.Body(EndEventBridgeBodyCodec.Type.END_PLAYER_STATE,
   1L,timestamp,EVENT,"wave2-hunt-mark",coords,"WAVE2_HUNT_MARK_V1","ACTIVE".equals(status)?8000L:0L,"ACTIVE".equals(status)?.25F:0F,
   0L,0L,"overworld|wave2|"+cycle,"ACTIVE".equals(status)?"intro-age=10000":"",id,"",status);
 }
 static boolean deliver(WaveTwoPhantomLayoutManager cache, EndEventBridgeBodyCodec.Body body) {
  return cache.apply(EndEventBridgeBodyCodec.decode(EndEventBridgeBodyCodec.encode(body)),5000000L);
 }
 public static void main(String[] args) {
  var anchor=new WaveTwoPhantomLayoutPolicy.Position(0,64,0);
  var cache=new WaveTwoPhantomLayoutManager();
  for(int cycle=1;cycle<=3;cycle++) {
   var layout=WaveTwoPhantomLayoutPolicy.selectSafe(anchor,740L+cycle,17,p->p);
   check(layout.size()==70,"arena supports seventy observers; server emitted "+layout.size());
   check(deliver(cache,packet(cycle,cycle*1000,"","","ACTIVE")),"mark admitted");
   for(var phantom:layout) {
    var p=phantom.position();
    check(deliver(cache,packet(cycle,cycle*1000+1,phantom.id(),p.x()+","+p.y()+","+p.z(),"ACTIVE")),"position admitted");
   }
   check(cache.isComplete("overworld",EVENT.toString(),1,cycle),"server field must be renderable");
   check(cache.layout("overworld",EVENT.toString(),1,cycle).size()==70,"all server observers retained");
   check(deliver(cache,packet(cycle,cycle*1000+2,"","","CLEAR")),"group cleanup");
   check(cache.layout("overworld",EVENT.toString(),1,cycle).isEmpty(),"no old group figures");
   check(!deliver(cache,packet(cycle,cycle*1000+1,"phantom-00","0,64,0","ACTIVE")),"late layout cannot resurrect group");
  }
  check(WaveTwoPhantomLayoutPolicy.MIN_PHANTOM_COUNT==WaveTwoPhantomLayout.MIN_PHANTOM_COUNT,
   "server and client fallback floors must agree");
  check(WaveTwoPhantomLayoutPolicy.selectSafe(anchor,1,17,p->null).isEmpty(),"blocked arena never emits unsafe figures");
  cache.reset();
  check(deliver(cache,packet(1,100,"","","ACTIVE")),"fallback mark");
  var valid=WaveTwoPhantomLayoutPolicy.selectSafe(anchor,1,17,p->p).subList(0,50);
  var attempts=new java.util.concurrent.atomic.AtomicInteger();
  var fallback=WaveTwoPhantomLayoutPolicy.selectSafe(anchor,1,17,p->{int i=attempts.getAndIncrement();return i<50?valid.get(i).position():null;});
  check(fallback.size()==50,"server produces exactly the supported blocked-layout fallback");
  for(int i=0;i<fallback.size();i++) {
   var f=fallback.get(i); var p=f.position();
   check(deliver(cache,packet(1,101,f.id(),p.x()+","+p.y()+","+p.z(),"ACTIVE")),"fallback item");
   check(cache.isComplete("overworld",EVENT.toString(),1,1)==(i==49),"fallback ready only at fifty");
  }
  System.out.println("LayoutWireProbe OK");
 }
} """, encoding="utf-8")
    sources = [probe, SERVER / "WaveTwoPhantomLayoutPolicy.java",
               *[CLIENT / (name + ".java") for name in
                 ("WaveTwoPhantomLayout", "WaveTwoPhantomLayoutManager", "WaveTwoCinematicTimeline")],
               *[PROTOCOL / (name + ".java") for name in
                 ("EndEventBridgeBodyCodec", "BridgeSigningPolicy", "BridgeEnvelope")]]
    result = subprocess.run([str(_jdk_bin("javac")), "-encoding", "UTF-8", "-d", str(tmp_path),
                             *map(str, sources)], capture_output=True, text=True, encoding="utf-8", errors="replace")
    assert result.returncode == 0, result.stdout + result.stderr
    result = subprocess.run([str(_jdk_bin("java")), "-cp", str(tmp_path), "me.copimine.client.LayoutWireProbe"],
                            capture_output=True, text=True, encoding="utf-8", errors="replace")
    assert result.returncode == 0, result.stdout + result.stderr
