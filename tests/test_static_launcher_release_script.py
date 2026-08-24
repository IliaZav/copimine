from pathlib import Path
import shutil
import subprocess

import pytest


ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "scripts/replace_copimine_static_launcher_release.sh"


def test_static_release_script_is_scoped_to_web_assets():
    text = SCRIPT.read_text(encoding="utf-8")
    for marker in (
        "--package",
        "--archive",
        "--web-root",
        "--release-id",
        ".copimine-releases",
        "mv -Tf",
        "nginx -t",
        "nginx -s reload",
        "world_nether",
        "playerdata",
        "authme",
    ):
        assert marker in text, marker

    for forbidden in (
        "rsync --delete",
        "systemctl restart",
        "systemctl stop",
        "kill -9",
        "world/playerdata",
    ):
        assert forbidden not in text, forbidden

    assert 'rm -rf -- "$TMP_ROOT"' in text
    assert 'rm -rf -- "$WEB_ROOT"' not in text


def test_static_release_script_has_shell_runtime_check_when_bash_is_available(tmp_path):
    bash = shutil.which("bash")
    if not bash:
        pytest.skip("bash is not installed in this Windows workspace")

    result = subprocess.run([bash, "-n", str(SCRIPT)], capture_output=True, text=True, check=False)
    assert result.returncode == 0, result.stderr
