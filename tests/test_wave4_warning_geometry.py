"""The obelisk warns at its crown without displaying the future flight path."""
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]


def test_obelisk_warning_is_local_and_never_reveals_trajectory(tmp_path):
    policy = ROOT / "CopiMineClient/src/main/java/me/copimine/client/WaveCombatPresentationPolicy.java"
    java = tmp_path / "Wave4WarningProbe.java"
    java.write_text('''
import me.copimine.client.WaveCombatPresentationPolicy;
public class Wave4WarningProbe {
    public static void main(String[] args){
        var warning=WaveCombatPresentationPolicy.parse("event:8:world:wave-ai-1234abcd-charge-reflect");
        if(warning.shape()!=WaveCombatPresentationPolicy.Shape.BILLBOARD)throw new AssertionError("obelisk warning reveals its future flight path");
        var release=WaveCombatPresentationPolicy.parse("event:8:world:wave-ai-1234abcd-release-reflect");
        if(release.shape()!=WaveCombatPresentationPolicy.Shape.BILLBOARD)throw new AssertionError("release must remain distinct");
        if(WaveCombatPresentationPolicy.visible(warning,4097))throw new AssertionError("warning must keep existing distance budget");
        if(!WaveCombatPresentationPolicy.visible(warning,9))throw new AssertionError("local crown warning missing");
    }
}
''',encoding="utf-8")
    built = subprocess.run(["javac", "-d", str(tmp_path), str(policy), str(java)],capture_output=True,text=True)
    assert built.returncode == 0, built.stdout + built.stderr
    result = subprocess.run(["java", "-cp", str(tmp_path), "Wave4WarningProbe"],capture_output=True,text=True)
    assert result.returncode == 0, result.stdout + result.stderr
