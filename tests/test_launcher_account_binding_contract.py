from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
BACKEND = (ROOT / "admin-web/backend/main.py").read_text(encoding="utf-8")
VIEW_MODEL = (ROOT / "CopiMineLauncher/src/CopiMineLauncher.App/LauncherViewModel.cs").read_text(encoding="utf-8")
APP = (ROOT / "CopiMineLauncher/src/CopiMineLauncher.App/App.xaml.cs").read_text(encoding="utf-8")
MAIN_WINDOW = (ROOT / "CopiMineLauncher/src/CopiMineLauncher.App/MainWindow.xaml").read_text(encoding="utf-8")
APP_ROUTES = (ROOT / "admin-web/frontend/assets/js/shared/app-routes.js").read_text(encoding="utf-8")
HIDDEN_LINK_PAGE = ROOT / "admin-web/frontend/launcher-link.html"
HIDDEN_LINK_RUNTIME = ROOT / "admin-web/frontend/assets/js/launcher-link-page.js"
LEGACY_LINK_REDIRECT = ROOT / "admin-web/frontend/assets/js/launcher-link-legacy-redirect.js"


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
    # The redesigned shell keeps this warning compact in the left rail. The
    # behavior contract is the visible blocked state and the binding action,
    # not the wording from the old layout.
    assert "Аккаунт не привязан" in MAIN_WINDOW
    assert "Привязать на сайте" in MAIN_WINDOW


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

    assert "Код вводить не нужно" in link_page
    assert "const automaticLinkPanel = hasLauncherRequest ? \"\" :" in link_page


def test_launcher_link_page_requires_confirmation_before_authorizing_and_returns_to_launcher():
    link_page = (ROOT / "admin-web/frontend/assets/js/cabinet-runtime.js").read_text(encoding="utf-8")
    start = link_page.index("async function loadPlayerLink()")
    end = link_page.index("async function loadPlayerBank()", start)
    rendered_link_page = link_page[start:end]

    assert "async function confirmLauncherLink()" in rendered_link_page
    assert 'data-click="confirmLauncherLink()"' in rendered_link_page
    assert "Привязать Launcher к этому аккаунту" in rendered_link_page
    assert "window.alert" in rendered_link_page
    assert "Привязка подтверждена к аккаунту" in rendered_link_page
    assert "window.close()" in rendered_link_page
    assert "copimine://launcher/link?challenge=" in rendered_link_page
    assert "window.confirmLauncherLink = confirmLauncherLink" in rendered_link_page
    assert "window.cancelLauncherLink = cancelLauncherLink" in rendered_link_page
    assert "hasLauncherAuthorization" not in rendered_link_page
    assert "hasLauncherRequest\n        ? \"Проверьте аккаунт" in rendered_link_page
    assert "await refreshCsrfCookie();" in rendered_link_page

    before_confirmation = rendered_link_page.split("async function confirmLauncherLink()", 1)[0]
    assert '/api/player/launcher/link/authorize' not in before_confirmation

    protocol = (ROOT / "CopiMineLauncher/src/CopiMineLauncher.App/LauncherProtocolRegistration.cs").read_text(encoding="utf-8")
    assert "Registry.CurrentUser" in protocol
    assert "Software" in protocol and "Classes" in protocol and "copimine" in protocol


def test_launcher_link_page_does_not_render_the_legacy_manual_code_form():
    link_page = (ROOT / "admin-web/frontend/assets/js/cabinet-runtime.js").read_text(encoding="utf-8")
    start = link_page.index("async function loadPlayerLink()")
    end = link_page.index("async function loadPlayerBank()", start)
    rendered_link_page = link_page[start:end]

    assert "linkCodeInput" not in rendered_link_page
    assert "playerConfirmLinkCode" not in rendered_link_page
    assert "Получить код в Minecraft" not in rendered_link_page
    assert "Откройте Launcher" in rendered_link_page
    assert "/api/player/launcher/link/authorize" in rendered_link_page


def test_cabinet_boot_has_a_bounded_error_and_retry_state():
    runtime = (ROOT / "admin-web/frontend/assets/js/cabinet-runtime.js").read_text(encoding="utf-8")
    for marker in (
        "function renderBootError",
        'makeButton("Повторить", "btn btn-primary", "retryCabinetBoot()")',
        "setBootState(\"error\")",
        "BOOTSTRAP_TIMEOUT_MS",
    ):
        assert marker in runtime, marker

    for page in (ROOT / "admin-web/frontend/cabinet").glob("*.html"):
        text = page.read_text(encoding="utf-8")
        assert "Подготавливаем кабинет" not in text, page.name


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


def test_launcher_binding_uses_a_standalone_page_for_both_authenticated_and_guest_flows():
    assert HIDDEN_LINK_PAGE.is_file()
    assert HIDDEN_LINK_RUNTIME.is_file()
    page = HIDDEN_LINK_PAGE.read_text(encoding="utf-8")
    runtime = HIDDEN_LINK_RUNTIME.read_text(encoding="utf-8")

    assert 'data-page-kind="launcher-link"' in page
    for marker in (
        "/api/player/me",
        "/api/player/launcher/link/authorize",
        "authLandingHref(\"signin\"",
        "authLandingHref(\"register\"",
        "window.confirm",
        "copimine://launcher/link?challenge=",
        "window.close()",
        "launcher_challenge",
        "launcher_code",
    ):
        assert marker in runtime, marker

    # The normal cabinet link tab stays on the cabinet shell; only a
    # Launcher-created challenge uses the standalone, non-navigation page.
    assert 'link: "/cabinet/link.html"' in APP_ROUTES
    assert 'return query ? `${LAUNCHER_BINDING_PATH}?${query}` : "";' in APP_ROUTES
    assert 'const safePath = target.pathname === LAUNCHER_BINDING_PATH' in APP_ROUTES
    assert 'target.pathname === LEGACY_LAUNCHER_BINDING_PATH' in APP_ROUTES


def test_launcher_link_success_screen_offers_a_native_app_open_prompt():
    runtime = HIDDEN_LINK_RUNTIME.read_text(encoding="utf-8")
    styles = (ROOT / "admin-web/frontend/assets/css/launcher-link.css").read_text(encoding="utf-8")

    for marker in (
        "function renderSuccess(account)",
        "function openLauncherApp()",
        "Открыть CopiMine Launcher",
        "Открыть CopiMine Launcher?",
        'setPageState("success")',
    ):
        assert marker in runtime, marker
    for marker in (
        '.launcher-link-page[data-state="success"]',
        ".launcher-link-success",
        ".launcher-link-success-actions",
    ):
        assert marker in styles, marker


def test_launcher_binding_page_never_asks_the_user_to_type_the_one_time_code():
    runtime = HIDDEN_LINK_RUNTIME.read_text(encoding="utf-8")
    for marker in ("linkCodeInput", "Введите код привязки", "input name=\"launcher_code\""):
        assert marker not in runtime


def test_expired_binding_request_has_a_clear_recovery_path_in_both_site_surfaces():
    standalone = HIDDEN_LINK_RUNTIME.read_text(encoding="utf-8")
    cabinet = (ROOT / "admin-web/frontend/assets/js/cabinet-runtime.js").read_text(encoding="utf-8")

    assert "renderExpiredRequest" in standalone
    assert "error?.status === 403" in standalone
    assert "Откройте Launcher и создайте новую привязку" in standalone
    assert "renderExpiredLauncherLink" in cabinet
    assert "status === 403" in cabinet
    assert "создайте новую привязку" in cabinet


def test_legacy_binding_url_redirects_valid_requests_to_the_standalone_page():
    page = (ROOT / "admin-web/frontend/cabinet/link.html").read_text(encoding="utf-8")
    redirect = LEGACY_LINK_REDIRECT.read_text(encoding="utf-8")
    assert 'launcher-link-legacy-redirect.js?v=20260829launcherlink6' in page
    assert "launcherBindingHrefFromSearch" in redirect
    assert 'window.location.pathname === "/cabinet/link.html"' in redirect
    assert 'window.location.replace(target)' in redirect


def test_backend_and_native_launcher_accept_the_standalone_binding_path():
    assert 'f"{ADMIN_PUBLIC_BASE_URL}/launcher-link.html?launcher_challenge=' in BACKEND
    assert 'uri.AbsolutePath.Equals("/launcher-link.html", StringComparison.Ordinal)' in VIEW_MODEL
    assert 'uri.AbsolutePath.Equals("/launcher-link.html", StringComparison.Ordinal)' in (
        ROOT / "CopiMineLauncher/src/CopiMineLauncher.App/LauncherBindingState.cs"
    ).read_text(encoding="utf-8")


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
