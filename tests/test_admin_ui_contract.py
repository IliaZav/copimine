from pathlib import Path


ROOT = Path(__file__).parents[1]


def test_project_settings_screen_uses_the_server_owned_settings_editor():
    admin = (ROOT / "frontend/assets/js/admin-v71.js").read_text(encoding="utf-8")
    cms = (ROOT / "frontend/assets/js/cms-production-v8.js").read_text(encoding="utf-8")

    assert "window.CMCMS?.siteSettingsView?.()" in admin
    assert "F['v71-site-settings']" not in admin
    assert "window.CMCMS.siteSettingsView" in cms
    assert "X.request('/admin/site-settings'" in cms


def test_admin_status_labels_report_provider_and_game_readiness_truthfully():
    admin = (ROOT / "frontend/assets/js/admin-v71.js").read_text(encoding="utf-8")
    production = (ROOT / "frontend/assets/js/production.js").read_text(encoding="utf-8")

    assert "Подключено к серверу" not in admin
    assert "Сайт подключён" in admin
    assert "SMTP не настроен" in production
    assert "Ожидает проверки домена" in production
    assert "ID приложения VK задан" in production
