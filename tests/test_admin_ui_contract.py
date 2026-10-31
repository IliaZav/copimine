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
        encoding="utf-8",
        errors="replace",
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
        encoding="utf-8",
        errors="replace",
        check=False,
    )
    assert result.returncode == 0, result.stdout + result.stderr


def test_search_covers_every_inner_tab_and_the_chat_tab():
    search = (ROOT / "frontend/assets/js/search-v73.js").read_text(encoding="utf-8")
    players = (ROOT / "frontend/assets/js/players-v6.js").read_text(encoding="utf-8")

    # Вкладка чата в карточке игрока и её синонимы в поиске.
    assert "['messages','Чат и команды']" in players
    assert "messages:'" in search
    assert "const subtabs=[" in search
    # Каждый внутренний подраздел, который реально рисуется, должен иметь строку поиска.
    for area, sub in (
        ("statistics", "audience"), ("statistics", "economy"),
        ("shop", "catalog"), ("shop", "materials"), ("shop", "delivery"),
        ("finance", "accounts"), ("finance", "ledger"),
        ("world", "elections"), ("world", "points"),
        ("content", "pages"), ("content", "gallery"),
        ("admins", "team"), ("admins", "easter"),
        ("system", "logs"), ("system", "connections"),
        ("moderation", "sanctions"), ("recipes", "editor"),
    ):
        assert "'" + area + "','" + sub + "'" in search, (area, sub)
    node = shutil.which("node")
    if not node:
        pytest.skip("Node.js is needed for the search behavior regression")

    result = subprocess.run(
        [node, "--test", str(ROOT / "tests" / "search-ui.test.mjs")],
        cwd=ROOT,
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
        check=False,
    )
    assert result.returncode == 0, result.stdout + result.stderr
