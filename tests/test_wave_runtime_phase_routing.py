from pathlib import Path
import subprocess
from tests.test_wave_navigation_adapter import extract
ROOT=Path(__file__).resolve().parents[1]
def test_disposable_runtime_survives_saved_recovery_phase_without_stealing_official_ticks(tmp_path):
 s=(ROOT/'copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java').read_text('utf-8')
 helper=extract(s,'boolean isDisposableWaveRuntimeActive(int wave)')
 code="""public class RuntimeRouteProbe {
 long generation=7; int activeWave=4; boolean testWaveFrontVisualMode,testWaveInspectionMode;
 static void check(boolean b){if(!b)throw new AssertionError();}
 """+helper+"""
 public static void main(String[] args){var p=new RuntimeRouteProbe();
 check(!p.isDisposableWaveRuntimeActive(4));
 p.testWaveFrontVisualMode=true;check(p.isDisposableWaveRuntimeActive(4));
 check(!p.isDisposableWaveRuntimeActive(3));check(!p.isDisposableWaveRuntimeActive(0));
 p.generation=0;check(!p.isDisposableWaveRuntimeActive(4));
 p.generation=7;p.testWaveFrontVisualMode=false;p.testWaveInspectionMode=true;check(p.isDisposableWaveRuntimeActive(4));
 }}"""
 path=tmp_path/'RuntimeRouteProbe.java';path.write_text(code,encoding='utf-8')
 r=subprocess.run(['javac','-d',str(tmp_path),str(path)],capture_output=True,text=True);assert r.returncode==0,r.stderr
 r=subprocess.run(['java','-cp',str(tmp_path),'RuntimeRouteProbe'],capture_output=True,text=True);assert r.returncode==0,r.stderr
 def code_only(text):
   # Comments may legitimately narrate the defect this routing replaced, so the
   # contract is asserted against executable lines only.
   return '\n'.join(line.split('//')[0] for line in text.splitlines())
 tick=extract(s,'boolean tickWave4ObeliskAssault()')
 # The assault and the heartbeat now ask the shared ownership helper: a plain
 # /cmend wave spawn 4 never sets testWaveFrontVisualMode, so the front-visual
 # probe went false one tick after the obelisks were built.
 assert 'ownsDisposableWaveRuntime(4)' in code_only(tick)
 assert 'isDisposableWaveRuntimeActive(4)' not in code_only(tick)
 heartbeat=s[s.index('// Every wave — official'):s.index('if (endUnlocked && (phase == EventPhase.VICTORY_PROCESSING',s.index('// Every wave — official'))]
 assert 'if (ownsDisposableWaveRuntime(activeWave))' in code_only(heartbeat)
 assert 'isDisposableWaveRuntimeActive(activeWave)' not in code_only(heartbeat)
 assert '|| canOwnWaveObjective(activeWave)' not in code_only(heartbeat)
 assert '-> tickCurrentIntermission();' in heartbeat
