"""Run the authoritative wave clock and signed metadata validation."""
from pathlib import Path
import os
import subprocess

ROOT=Path(__file__).resolve().parents[1]


def test_wave_intro_clock_and_signed_metadata_are_bounded(tmp_path):
    source='''import java.util.*;
import me.copimine.endevent.domain.WaveTwoCinematicPolicy;
import me.copimine.protocol.EndEventBridgeBodyCodec;
public class WaveIntroProbe {
 static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
 static EndEventBridgeBodyCodec.Body packet(String visual,String metadata,String status){
  return new EndEventBridgeBodyCodec.Body(EndEventBridgeBodyCodec.Type.END_PLAYER_STATE,7,1234,UUID.randomUUID(),
   visual.equals("WAVE1_CARRIER_V1")?"wave1-carrier-charge":"wave2-hunt-mark","",visual,
   status.equals("ACTIVE")?8000:0,.25f,0,0,visual.equals("WAVE1_CARRIER_V1")?"overworld|wave1|1":"overworld|wave2|1",metadata,"","",status);
 }
 static boolean accepted(String visual,String metadata,String status){
  try{var body=packet(visual,metadata,status);return EndEventBridgeBodyCodec.decode(EndEventBridgeBodyCodec.encode(body)).equals(body);}
  catch(IllegalArgumentException expected){return false;}
 }
 public static void main(String[] args){
  check(WaveTwoCinematicPolicy.INTRO_MILLIS==10000,"one ten-second opening");
  check(!WaveTwoCinematicPolicy.combatReady(1000,10999),"combat cannot start before full opening");
  check(WaveTwoCinematicPolicy.combatReady(1000,11000),"combat starts at deadline");
  check(!WaveTwoCinematicPolicy.combatReady(0,11000),"missing clock is not ready");
  check(WaveTwoCinematicPolicy.age(1000,999)==0,"clock rollback is bounded");
  check(WaveTwoCinematicPolicy.age(1000,Long.MAX_VALUE)==10000,"late join skips opening");
  check(WaveTwoCinematicPolicy.restoreStart(null,50000,1)==40000,"legacy live group never replays opening");
  check(WaveTwoCinematicPolicy.restoreStart("1000",5000,0)==1000,"restart retains opening clock");
  for(String value:new String[]{"intro-age=0","intro-age=1500","intro-age=10000"})check(accepted("WAVE2_HUNT_MARK_V1",value,"ACTIVE"),"valid signed age "+value);
  for(String value:new String[]{"intro-age=-1","intro-age=10001","intro-age=NaN","intro-age=9999999999999","unknown=1"})check(!accepted("WAVE2_HUNT_MARK_V1",value,"ACTIVE"),"reject malformed age "+value);
  check(!accepted("WAVE1_CARRIER_V1","intro-age=1","ACTIVE"),"age cannot leak into other waves");
  check(!accepted("WAVE2_HUNT_MARK_V1","intro-age=1","CLEAR"),"clear is not an opening command");
  check(accepted("WAVE2_HUNT_MARK_V1","","CLEAR"),"ordinary clear remains valid");
 }
}'''
    path=tmp_path/'WaveIntroProbe.java';path.write_text(source,encoding='utf-8')
    roots=[ROOT/'copimine-end-event/src',ROOT/'tools/minecraft-26.3/protocol/src/main/java']
    built=subprocess.run(['javac','-encoding','UTF-8','-sourcepath',os.pathsep.join(map(str,roots)),'-d',str(tmp_path),str(path)],capture_output=True,text=True)
    assert built.returncode==0,built.stderr
    ran=subprocess.run(['java','-cp',str(tmp_path),'WaveIntroProbe'],capture_output=True,text=True)
    assert ran.returncode==0,ran.stderr
