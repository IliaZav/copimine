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


def test_admin_header_omits_redundant_connection_badge():
    admin = (ROOT / "frontend/assets/js/admin-v71.js").read_text(encoding="utf-8")
    production = (ROOT / "frontend/assets/js/production.js").read_text(encoding="utf-8")

    assert "Подключено к серверу" not in admin
    assert "Сайт подключён" not in admin
    assert "v71-local-label" not in admin
    assert "SMTP не настроен" in production
    assert "Ожидает проверки домена" in production
    assert "ID приложения VK задан" in production


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
