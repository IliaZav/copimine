"""A final pack death must not create new Wave 1 debt after three deliveries."""
from pathlib import Path
import subprocess
from tests.test_wave_navigation_adapter import extract

ROOT=Path(__file__).resolve().parents[1]
SRC=ROOT/'copimine-end-event/src'


def test_terminal_delivery_refuses_every_late_reinforcement_callback(tmp_path):
    source=(SRC/'me/copimine/endevent/CopiMineEndEvent.java').read_text('utf-8')
    # Extract BOTH overloads. The one-argument form became a delegate to the
    # persist-aware form when delivery moved behind a durable checkpoint, so
    # extracting only the entry point leaves a call to a method the probe does
    # not define.
    method=extract(source,'void commitWaveOneReinforcement(String reasonCode)')
    method+="\n"+extract(source,'void commitWaveOneReinforcement(String reasonCode, boolean persist)')
    java='''import java.util.logging.Logger;
import me.copimine.endevent.domain.RiftCarrierPolicy;
public class Wave1TerminalDebtProbe {
 int activeWave=1,currentCarrierCharges,wave1ReinforcementOwed,wave1ReinforcementSpawned,wave1ClearReinforcementMask,saves;
 RiftCarrierPolicy.State currentCarrierState;String eventId="event";long generation=7;
 static final int WAVE_ONE_MAX_REINFORCEMENT_PACKS=5;
 Logger getLogger(){return Logger.getLogger("w1-terminal");}void saveStateAsync(){saves++;}
 public static void main(String[] args){
 var p=new Wave1TerminalDebtProbe();p.currentCarrierCharges=2;p.commitWaveOneReinforcement("delivery-two");
 if(p.wave1ReinforcementOwed!=1)throw new AssertionError("valid finite debt lost");
 p.currentCarrierCharges=3;for(int i=0;i<40;i++)p.commitWaveOneReinforcement("wave-one-pack-cleared");
 if(p.wave1ReinforcementOwed!=1||p.saves!=1)throw new AssertionError("terminal deaths create endless debt");
 p.currentCarrierCharges=0;p.activeWave=2;p.commitWaveOneReinforcement("stale-death");
 if(p.wave1ReinforcementOwed!=1)throw new AssertionError("foreign wave creates stale debt");
 p.activeWave=1;
 for(int stage=0;stage<3;stage++){
  p.currentCarrierCharges=stage;
  for(int i=0;i<40;i++)p.commitWaveOneReinforcement("wave-one-pack-cleared");
  if(p.wave1ReinforcementOwed!=stage+2)throw new AssertionError("pack clears create unbounded debt within one delivery stage");
 }
 }
'''
    path=tmp_path/'Wave1TerminalDebtProbe.java';path.write_text(java+method+'}',encoding='utf-8')
    built=subprocess.run(['javac','-encoding','UTF-8','-sourcepath',str(SRC),'-d',str(tmp_path),str(path)],capture_output=True,text=True)
    assert built.returncode==0,built.stderr
    ran=subprocess.run(['java','-cp',str(tmp_path),'Wave1TerminalDebtProbe'],capture_output=True,text=True)
    assert ran.returncode==0,ran.stderr
