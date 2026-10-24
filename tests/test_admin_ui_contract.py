from pathlib import Path
import shutil
import subprocess

import pytest


ROOT = Path(__file__).parents[1]


def test_project_settings_screen_uses_the_server_owned_settings_editor():
    admin = (ROOT / "frontend/assets/js/admin-v71.js").read_text(encoding="utf-8")
    cms = (ROOT / "frontend/assets/js/cms-production-v8.js").read_text(encoding="utf-8")

    assert "window.CMCMS?.siteSettingsView?.()" in admin
    assert "F['v71-site-settings']" not in admin
    assert "window.CMCMS.siteSettingsView" in cms
    assert "X.request('/admin/site-settings'" in cms


def test_admin_health_screen_only_shows_integrations_in_use():
    admin = (ROOT / "frontend/assets/js/admin-v71.js").read_text(encoding="utf-8")
    production = (ROOT / "frontend/assets/js/production.js").read_text(encoding="utf-8")

    assert "Подключено к серверу" not in admin
    assert "Сайт подключён" not in admin
    assert "v71-local-label" not in admin
    assert "SMTP не настроен" not in production
    assert "Почта" not in production
    assert "VK ID" not in production


def test_account_auth_and_navigation_contract():
    node = shutil.which("node")
    if not node:
        pytest.skip("Node.js is needed for the account UI behavior regression")

    result = subprocess.run(
        [node, "--test", str(ROOT / "tests" / "account-v9-ui.test.mjs")],
        cwd=ROOT,
        capture_output=True,
        text=True,
        check=False,
    )
    assert result.returncode == 0, result.stdout + result.stderr


def test_recovery_ui_uses_authenticated_server_identity_without_player_row():
    node = shutil.which("node")
    if not node:
        pytest.skip("Node.js is needed for the recovery UI behavior regression")

    result = subprocess.run(
        [node, "--test", str(ROOT / "tests" / "recovery-ui.test.mjs")],
        cwd=ROOT,
        capture_output=True,
        text=True,
        check=False,
    )
    assert result.returncode == 0, result.stdout + result.stderr
