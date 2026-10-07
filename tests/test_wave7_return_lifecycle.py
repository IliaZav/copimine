"""Execute the production lifecycle's personal Wave 7 return and restart fences."""
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]


def test_wave7_return_admission_grace_and_restart(tmp_path):
    lifecycle = ROOT / "copimine-end-event/src/me/copimine/endevent/runtime/AttemptLifecycleController.java"
    probe = ROOT / "tests/Wave7ReturnLifecycleTest.java"
    compiled = subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(tmp_path), str(lifecycle), str(probe)], capture_output=True, text=True)
    assert compiled.returncode == 0, compiled.stdout + compiled.stderr
    executed = subprocess.run(["java", "-cp", str(tmp_path), "Wave7ReturnLifecycleTest"], capture_output=True, text=True)
    assert executed.returncode == 0, executed.stdout + executed.stderr
