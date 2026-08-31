from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
VIEW_MODEL = (ROOT / "CopiMineLauncher/src/CopiMineLauncher.App/LauncherViewModel.cs").read_text(encoding="utf-8")
MAIN_WINDOW = (ROOT / "CopiMineLauncher/src/CopiMineLauncher.App/MainWindow.xaml.cs").read_text(encoding="utf-8")
APP = (ROOT / "CopiMineLauncher/src/CopiMineLauncher.App/App.xaml.cs").read_text(encoding="utf-8")
PATHS = (ROOT / "CopiMineLauncher/src/CopiMineLauncher.App/LauncherInstallPaths.cs").read_text(encoding="utf-8")
CLIENT = (ROOT / "CopiMineLauncher/src/CopiMineLauncher.Infrastructure/Binding/LauncherBindingClient.cs").read_text(encoding="utf-8")
BACKEND = (ROOT / "admin-web/backend/main.py").read_text(encoding="utf-8")


def test_play_is_blocked_until_launcher_binding_is_confirmed():
    assert "LauncherBindingRequired" not in VIEW_MODEL
    assert "LAUNCHER_LINK_REQUIRED" in VIEW_MODEL
    assert "LauncherBindingRequired" not in MAIN_WINDOW
    assert "MessageBox.Show" in VIEW_MODEL


def test_binding_required_warning_offers_the_browser_flow():
    assert "OpenAccountLinkCommand" in VIEW_MODEL
    assert "launcher-link.html" in VIEW_MODEL
    assert "LauncherLinkRequired" in VIEW_MODEL
    launcher_shell = (ROOT / "CopiMineLauncher/src/CopiMineLauncher.App/MainWindow.xaml").read_text(encoding="utf-8")
    assert "Аккаунт не привязан" in launcher_shell
    assert "Привязать на сайте" in launcher_shell


def test_binding_browser_allows_only_real_copimine_or_loopback_staging_urls():
    assert "IsLoopback" in VIEW_MODEL
    assert "UseShellExecute = true" in VIEW_MODEL
    assert "launcher-link.html" in VIEW_MODEL
    assert "FallbackLauncherBindingClient" in APP
    assert "http://127.0.0.1:8090/" in PATHS
    assert "COPIMINE_LAUNCHER_LOCAL_BASE_URL" in PATHS
    assert "LAUNCHER_LINK_ALL_ENDPOINTS_FAILED" in CLIENT


def test_non_browser_launcher_requests_are_not_blocked_by_browser_csrf_middleware():
    csrf_paths = BACKEND[BACKEND.index("def csrf_exempt_paths"):BACKEND.index("def normalized_request_path")]
    for marker in (
        '"/api/launcher/link/challenge"',
        '"/api/launcher/profile/nickname"',
    ):
        assert marker in csrf_paths, marker
