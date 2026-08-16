from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
VIEW_MODEL = (ROOT / "CopiMineLauncher/src/CopiMineLauncher.App/LauncherViewModel.cs").read_text(encoding="utf-8")
MAIN_WINDOW = (ROOT / "CopiMineLauncher/src/CopiMineLauncher.App/MainWindow.xaml.cs").read_text(encoding="utf-8")
APP = (ROOT / "CopiMineLauncher/src/CopiMineLauncher.App/App.xaml.cs").read_text(encoding="utf-8")
PATHS = (ROOT / "CopiMineLauncher/src/CopiMineLauncher.App/LauncherInstallPaths.cs").read_text(encoding="utf-8")
CLIENT = (ROOT / "CopiMineLauncher/src/CopiMineLauncher.Infrastructure/Binding/LauncherBindingClient.cs").read_text(encoding="utf-8")
BACKEND = (ROOT / "admin-web/backend/main.py").read_text(encoding="utf-8")


def test_play_gate_raises_a_ui_binding_request_instead_of_starting_minecraft():
    for marker in (
        "LauncherBindingRequired",
        "LAUNCHER_LINK_REQUIRED",
        "LauncherBindingRequired?.Invoke",
    ):
        assert marker in VIEW_MODEL, marker

    for marker in (
        "MessageBox.Show",
        "Аккаунт не привязан",
        "MessageBoxButton.OKCancel",
        "OpenAccountLinkCommand.ExecuteAsync",
    ):
        assert marker in MAIN_WINDOW, marker


def test_binding_browser_allows_only_real_copimine_or_loopback_staging_urls():
    assert "IsLoopback" in VIEW_MODEL
    assert "UseShellExecute = true" in VIEW_MODEL
    assert "cabinet/link.html" in VIEW_MODEL
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
