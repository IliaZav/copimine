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

    # `forbidden_static_path` returns 1 for an allowed path. Keep every safety
    # check inside an explicit conditional so `set -e` cannot abort a valid
    # static deployment before the first release is copied.
    assert 'if forbidden_static_path "$candidate"; then' in text
    assert 'if forbidden_static_path "$PACKAGE_ROOT"; then' in text
    assert 'if forbidden_static_path "$ARCHIVE"; then' in text
    assert 'if forbidden_static_path "$root"; then' in text
    assert 'if forbidden_static_path "/$relative"; then' in text
    assert 'if forbidden_static_path "/$entry"; then' in text
    assert 'if [[ -L "$item" ]]; then' in text
    assert 'if [[ -z "$entry" ]]; then' in text
    assert 'forbidden_static_path "$candidate" && die' not in text
    assert 'forbidden_static_path "$PACKAGE_ROOT" && die' not in text
    assert 'forbidden_static_path "$ARCHIVE" && die' not in text
    assert 'forbidden_static_path "$root" && die' not in text
    assert 'forbidden_static_path "/$relative" && die' not in text
    assert 'forbidden_static_path "/$entry" && die' not in text
    assert '[[ -L "$item" ]] && die' not in text
    assert '[[ -z "$entry" ]] && continue' not in text


def test_static_release_script_has_shell_runtime_check_when_bash_is_available(tmp_path):
    bash = shutil.which("bash")
    if not bash:
        pytest.skip("bash is not installed in this Windows workspace")

    result = subprocess.run([bash, "-n", str(SCRIPT)], capture_output=True, text=True, check=False)
    assert result.returncode == 0, result.stderr
