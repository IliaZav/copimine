from __future__ import annotations

import subprocess
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
INSPECTOR = ROOT / "scripts/minecraft/inspect_legacy_release_client_bridge.py"


def inspect_config(tmp_path: Path, contents: str) -> subprocess.CompletedProcess[str]:
    config = tmp_path / "config.yml"
    config.write_text(contents, encoding="utf-8")
    return subprocess.run(
        [sys.executable, str(INSPECTOR), str(config)],
        capture_output=True,
        check=False,
        text=True,
    )


def test_unicode_escaped_yaml_key_cannot_hide_minecraft_26_3_requirement(tmp_path: Path) -> None:
    result = inspect_config(
        tmp_path,
        'client_bridge:\n  "required_client_mod_\\u0076ersion": "0.2.0+26.3"\n',
    )

    assert result.returncode == 0, result.stderr
    assert result.stdout.strip() == "requires-26.3"


def test_inline_comment_text_does_not_create_a_second_legacy_version_key(tmp_path: Path) -> None:
    result = inspect_config(
        tmp_path,
        "client_bridge:\n"
        "  required_client_mod_version: '0.1.1+1.21.1' "
        "# old required_client_mod_version: example\n",
    )

    assert result.returncode == 0, result.stderr
    assert result.stdout.strip() == "legacy"


def test_duplicate_bridge_version_keys_fail_closed(tmp_path: Path) -> None:
    result = inspect_config(
        tmp_path,
        "client_bridge:\n"
        "  required_client_mod_version: '0.1.1+1.21.1'\n"
        "  required_client_mod_version: '0.2.0+26.3'\n",
    )

    assert result.returncode == 2
    assert "cannot safely parse" in result.stderr.lower()


def test_explicit_bridge_version_can_override_an_inherited_yaml_merge_value(tmp_path: Path) -> None:
    result = inspect_config(
        tmp_path,
        "defaults: &defaults\n"
        "  required_client_mod_version: '0.1.1+1.21.1'\n"
        "client_bridge:\n"
        "  <<: *defaults\n"
        "  required_client_mod_version: '0.2.0+26.3'\n",
    )

    assert result.returncode == 0, result.stderr
    assert result.stdout.strip() == "requires-26.3"
