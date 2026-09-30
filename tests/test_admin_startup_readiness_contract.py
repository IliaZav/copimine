from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
ADMIN = (
    ROOT
    / "copimine-admin-plugin"
    / "src"
    / "me"
    / "copimine"
    / "ultimateplus"
    / "CopiMineUltimateAdminPlus.java"
).read_text(encoding="utf-8")


def test_startup_readiness_uses_the_current_election_core_sidebar():
    marker = '"STARTUP_SIDEBAR"'
    assert marker in ADMIN
    row = ADMIN.split(marker, 1)[1].split("));", 1)[0]
    assert 'pluginReady("CopiMineElectionCore")' in row
    assert "sidebarTask!=null" not in row


def test_legacy_admin_sidebar_task_remains_disabled_after_migration():
    assert "runTaskTimer(this, this::tickSidebar" not in ADMIN
