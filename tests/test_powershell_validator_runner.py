from pathlib import Path
import shutil
import subprocess

import pytest


ROOT = Path(__file__).resolve().parents[1]


@pytest.mark.parametrize("shell_name", ["pwsh", "powershell"])
def test_validator_runner_counts_a_nonzero_child_exit_as_failure(tmp_path, shell_name):
    shell = shutil.which(shell_name)
    if shell is None:
        pytest.skip(f"{shell_name} is unavailable on this host")

    runner = tmp_path / "RunCopiMineValidators.ps1"
    shutil.copyfile(ROOT / "tests/RunCopiMineValidators.ps1", runner)
    (tmp_path / "ValidateRunnerExitProbe.ps1").write_text(
        "Write-Output 'probe validator failed'; exit 23\n", encoding="utf-8"
    )

    result = subprocess.run(
        [shell, "-NoLogo", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
         "-File", str(runner), "-Pattern", "ValidateRunnerExitProbe.ps1"],
        cwd=tmp_path,
        capture_output=True,
        text=True,
        check=False,
    )

    output = result.stdout + result.stderr
    assert result.returncode != 0, output
    assert "VALIDATOR_SUMMARY total=1 passed=0 failed=1 skipped=0" in output
    assert "ValidateRunnerExitProbe.ps1" in output
    assert "23" in output
