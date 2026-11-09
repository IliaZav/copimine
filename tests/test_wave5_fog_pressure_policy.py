"""Finite resurrection/elite pressure; executes the actual Java policy."""
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]


def test_resurrection_budget_and_elite_rolls(tmp_path):
    source = tmp_path / 'FogPressureProbe.java'
    source.write_text('''
import me.copimine.endevent.domain.Wave5FogPressurePolicy;
import me.copimine.endevent.domain.Wave5PostFogPackPolicy;
public class FogPressureProbe {
 static void check(boolean b){if(!b)throw new AssertionError();}
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 public static void main(String[] args){
  for(int p:new int[]{2,5,10,11,20}) for(int c=0;c<10;c++) {
   int n=Wave5FogPressurePolicy.resurrectionBudget(p,c,200);
   check(n>=2 && n<=6);
   check(Wave5FogPressurePolicy.resurrectionBudget(p,c,1)==1);
   check(Wave5FogPressurePolicy.resurrectionBudget(p,c,0)==0);
   check(Wave5FogPressurePolicy.packSize(p,false)<Wave5FogPressurePolicy.packSize(p,true));
  }
  int ordinary=0, cleared=0;
  for(int seed=0;seed<10000;seed++) {
   if(Wave5FogPressurePolicy.eliteKind(seed,3,false)>=0) ordinary++;
   if(Wave5FogPressurePolicy.eliteKind(seed,3,true)>=0) cleared++;
   check(Wave5FogPressurePolicy.eliteKind(seed,3,true)==Wave5FogPressurePolicy.eliteKind(seed,3,true));
  }
  check(ordinary>300 && ordinary<2000 && cleared>ordinary && cleared<4000);
  packReceiptsSurviveRetries();
 }
 static void packReceiptsSurviveRetries(){
  // Point 12: a one-shot decision must not execute twice on a retry, and a
  // receipt from another fight must not settle this fight's debt.
  var receipt=new Wave5PostFogPackPolicy.Receipt("event",4L,5,3);
  check(receipt.matches("event",4L,5),"a receipt must match its own fight");
  check(!receipt.matches("event",5L,5),"another generation must not spend this receipt");
  check(!receipt.matches("other",4L,5),"another event must not spend this receipt");
  check(!receipt.matches("event",4L,10),"a different cycle target is a different fight");
  check(Wave5PostFogPackPolicy.packsOwed(5,5)==0,"a fully paid fight owes nothing");
  // Clamping: a checkpoint frozen for ten cycles opened by a five-cycle fight
  // may only claim the packs that fight could have run.
  check(Wave5PostFogPackPolicy.schedulablePacks(10,5,10)==5,
        "a ten-cycle receipt cannot settle a five-cycle fight past its target");
  check(Wave5PostFogPackPolicy.schedulablePacks(5,10,3)==3,
        "delivered packs keep their value when the target grows");
  check(Wave5PostFogPackPolicy.schedulablePacks(10,10,4)==4,"identical targets pass through");
  check(Wave5PostFogPackPolicy.schedulablePacks(10,10,99)==10,
        "an absurd stored count is clamped to the protocol ceiling");
  check(Wave5PostFogPackPolicy.schedulablePacks(5,5,-1)==0,"negative counts clamp to zero");
  check(Wave5PostFogPackPolicy.schedulablePacks(0,5,3)==0,"an unset target delivers nothing");
  expectRejected(()->new Wave5PostFogPackPolicy.Receipt("",4L,5,1));
  expectRejected(()->new Wave5PostFogPackPolicy.Receipt("event",0L,5,1));
  expectRejected(()->new Wave5PostFogPackPolicy.Receipt("event",4L,4,1));
  expectRejected(()->new Wave5PostFogPackPolicy.Receipt("event",4L,11,1));
  expectRejected(()->new Wave5PostFogPackPolicy.Receipt("event",4L,5,0));
  expectRejected(()->new Wave5PostFogPackPolicy.Receipt("event",4L,5,6));
 }
 static void expectRejected(Runnable r){
  try{ r.run(); throw new AssertionError("receipt must fail closed"); }
  catch(IllegalArgumentException expected){ }
 }
}
''',encoding='utf-8')
    domain = ROOT/'copimine-end-event/src/me/copimine/endevent/domain'
    # Wave5PostFogPackPolicy now carries the pack RECEIPT, and it reads the
    # cycle bounds from BlackFogTimingPolicy. Java probes list their sources by
    # hand, so both files have to be named here or the probe cannot compile.
    result = subprocess.run(['javac','-d',str(tmp_path),
                             str(domain/'Wave5FogPressurePolicy.java'),
                             str(domain/'Wave5PostFogPackPolicy.java'),
                             str(domain/'BlackFogTimingPolicy.java'),
                             str(domain/'Wave5PreFogRefillPolicy.java'),
                             str(domain/'WaveMechanicsPolicy.java'),
                             str(domain/'WaveScalingPolicy.java'),
                             str(domain/'WavePressurePolicy.java'),
                             str(domain/'RiftCarrierPolicy.java'),
                             str(domain/'PressureBudgetController.java'),
                             str(source)],capture_output=True,text=True)
    assert result.returncode == 0, result.stderr
    result = subprocess.run(['java','-cp',str(tmp_path),'FogPressureProbe'],capture_output=True,text=True)
    assert result.returncode == 0, result.stderr
