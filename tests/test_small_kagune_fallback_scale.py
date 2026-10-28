"""The per-tick server carrier must preserve the configured small-role size."""
from pathlib import Path
import subprocess
from tests.test_boss_phase_damage_transaction_adapter import _method

ROOT = Path(__file__).resolve().parents[1]


def test_small_carrier_does_not_reset_to_full_size_on_the_next_tick(tmp_path):
    source = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(encoding="utf-8")
    method = _method(source, "private void updateTentacleDisplay(")
    probe = tmp_path / "SmallCarrierScaleProbe.java"
    probe.write_text(r'''
import me.copimine.endevent.domain.TentacleAnimationPolicy;
public class SmallCarrierScaleProbe {
    record Vector3f(float x,float y,float z) { }
    static class AxisAngle4f { }
    record Transformation(Vector3f translation,AxisAngle4f left,Vector3f scale,AxisAngle4f right) { }
    static class ItemDisplay {
        Transformation transform;
        void setTransformation(Transformation t){transform=t;}
        Object getLocation(){return this;}
    }
    static class TentacleController {
        record VisualState(TentacleAnimationPolicy.Kind kind,TentacleAnimationPolicy.State state) { }
    }
    record Tuning(double scale) { }
    Tuning smallKaguneTuning(){return new Tuning(0.4);}
    final float TENTACLE_FALLBACK_CENTER=3,TENTACLE_FALLBACK_SCALE=6;
    long eventTickCounter=1;
    void renderTentaclePulse(Object location){ }
''' + method + r'''
    static void close(float value,float wanted){
        if(Math.abs(value-wanted)>0.0001f) throw new AssertionError(value+" != "+wanted);
    }
    public static void main(String[] args){
        var p=new SmallCarrierScaleProbe();var actor=new ItemDisplay();
        for(var kind:TentacleAnimationPolicy.Kind.values()){
            p.updateTentacleDisplay(actor,new TentacleController.VisualState(kind,TentacleAnimationPolicy.State.READY));
            float size=kind==TentacleAnimationPolicy.Kind.SMALL?2.4f:6f;
            float center=kind==TentacleAnimationPolicy.Kind.SMALL?-1.2f:-3f;
            close(actor.transform.scale.x,size);close(actor.transform.scale.y,size);close(actor.transform.scale.z,size);
            close(actor.transform.translation.x,center);close(actor.transform.translation.z,center);
        }
    }
}
''', encoding="utf-8")
    result = subprocess.run(["javac", "-encoding", "UTF-8", "-sourcepath", str(ROOT / "copimine-end-event/src"),
                             "-d", str(tmp_path), str(probe)], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
    result = subprocess.run(["java", "-cp", str(tmp_path), "SmallCarrierScaleProbe"], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
