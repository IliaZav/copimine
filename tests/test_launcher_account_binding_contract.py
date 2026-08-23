from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
BACKEND = (ROOT / "admin-web/backend/main.py").read_text(encoding="utf-8")
VIEW_MODEL = (ROOT / "CopiMineLauncher/src/CopiMineLauncher.App/LauncherViewModel.cs").read_text(encoding="utf-8")
APP = (ROOT / "CopiMineLauncher/src/CopiMineLauncher.App/App.xaml.cs").read_text(encoding="utf-8")
MAIN_WINDOW = (ROOT / "CopiMineLauncher/src/CopiMineLauncher.App/MainWindow.xaml").read_text(encoding="utf-8")


def test_launcher_binding_uses_expiring_single_use_challenge_without_passwords():
    for marker in (
        "launcher_link_challenges",
        "launcher_account_links",
        "/api/launcher/link/challenge",
        "/api/launcher/link/status",
        "/api/player/launcher/link/authorize",
        "expires_at",
        "status='AUTHORIZED'",
        "poll_token_hash",
        "hmac.compare_digest",
    ):
        assert marker in BACKEND, marker

    assert "PlayerLinkRequestIn" in BACKEND
    binding_model = BACKEND[BACKEND.index("class LauncherLinkChallengeIn"):BACKEND.index("class PlayerRecoveryStartIn")]
    assert "password" not in binding_model.lower()


def test_launcher_binding_is_required_before_play():
    """The launcher must complete site binding before it starts Minecraft."""
    for marker in (
        "IsLauncherLinked",
        "LauncherLinkRequired",
        "OpenAccountLinkCommand",
    ):
        assert marker in VIEW_MODEL or marker in APP or marker in MAIN_WINDOW, marker

    assert "PlayCommand = new AsyncRelayCommand(PlayAsync, CanStartOperation)" in VIEW_MODEL
    assert "private bool CanStartOperation() => !IsBusy;" in VIEW_MODEL
    assert "LAUNCHER_LINK_REQUIRED" in VIEW_MODEL
    assert "Привязка аккаунта обязательна перед запуском" in MAIN_WINDOW


def test_website_authorizes_launcher_without_a_manual_code_and_returns_to_the_app():
    link_page = (ROOT / "admin-web/frontend/assets/js/cabinet-runtime.js").read_text(encoding="utf-8")
    for marker in (
        "launcher_challenge",
        "launcher_code",
        "/api/player/launcher/link/authorize",
        "copimine://launcher/link",
        "можно закрыть",
        "window.location.href",
    ):
        assert marker in link_page.lower(), marker

    assert 'const manualLinkPanels = hasLauncherAuthorization ? "" :' in link_page
    assert "Код вводить не нужно" in link_page

    protocol = (ROOT / "CopiMineLauncher/src/CopiMineLauncher.App/LauncherProtocolRegistration.cs").read_text(encoding="utf-8")
    assert "Registry.CurrentUser" in protocol
    assert "Software" in protocol and "Classes" in protocol and "copimine" in protocol


def test_unauthenticated_launcher_binding_preserves_the_return_target_after_login():
    routes = (ROOT / "admin-web/frontend/assets/js/shared/app-routes.js").read_text(encoding="utf-8")
    runtime = (ROOT / "admin-web/frontend/assets/js/cabinet-runtime.js").read_text(encoding="utf-8")
    for marker in (
        "launcherBindingHrefFromSearch",
        "launcherReturnHrefFromAuthSearch",
        "authLandingHref(\"signin\", launcherReturn)",
        "if (launcherReturn) {",
    ):
        assert marker in routes or marker in runtime, marker


def test_launcher_nickname_change_is_a_server_side_identity_operation():
    world_core = (ROOT / "copimine-world-core/src/me/copimine/worldcore/CopiMineWorldCore.java").read_text(encoding="utf-8")
    client = (ROOT / "CopiMineLauncher/src/CopiMineLauncher.Infrastructure/Binding/LauncherBindingClient.cs").read_text(encoding="utf-8")
    view_model = (ROOT / "CopiMineLauncher/src/CopiMineLauncher.App/LauncherViewModel.cs").read_text(encoding="utf-8")
    for marker in (
        "LauncherNicknameChangeIn",
        "/api/launcher/profile/nickname",
        "preserve_player_state",
        "playerdata",
        "stats",
        "advancements",
        "AuthMeApi",
        "updateRealName",
        "password_preserved",
        "identity rebind",
        "whitelist",
    ):
        assert marker in BACKEND or marker in world_core or marker in client or marker in view_model, marker
