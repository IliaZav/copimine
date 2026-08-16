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


def test_launcher_binding_is_required_before_play_and_has_a_persistent_device_identity():
    for marker in (
        "IsLauncherLinked",
        "LauncherLinkRequired",
        "LauncherDeviceIdentity",
        "Привязка Launcher обязательна",
        "OpenAccountLinkCommand",
    ):
        assert marker in VIEW_MODEL or marker in APP or marker in MAIN_WINDOW, marker


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

    protocol = (ROOT / "CopiMineLauncher/src/CopiMineLauncher.App/LauncherProtocolRegistration.cs").read_text(encoding="utf-8")
    assert "Registry.CurrentUser" in protocol
    assert "Software" in protocol and "Classes" in protocol and "copimine" in protocol


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
