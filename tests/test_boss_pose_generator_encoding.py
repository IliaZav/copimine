"""The generated pose fingerprint must not depend on Windows PowerShell's ACP."""
from pathlib import Path
import os
import shutil
import subprocess
import pytest

ROOT=Path(__file__).resolve().parents[1]


@pytest.mark.skipif(os.name!="nt" or not shutil.which("powershell.exe"),reason="Windows PowerShell encoding regression")
def test_windows_powershell_generates_the_same_utf8_pose_table(tmp_path):
    output=tmp_path/"GeneratedBossAnimationPoses.java"
    result=subprocess.run(["powershell.exe","-NoProfile","-ExecutionPolicy","Bypass","-File",
      str(ROOT/"copimine-end-event/tools/GenerateBossAnimationPoses.ps1"),"-OutputPath",str(output)],
      capture_output=True,timeout=45)
    assert result.returncode==0,result.stdout+result.stderr
    expected=ROOT/"copimine-end-event/src/me/copimine/endevent/domain/GeneratedBossAnimationPoses.java"
    assert output.read_bytes().replace(b"\r\n",b"\n")==expected.read_bytes().replace(b"\r\n",b"\n")
