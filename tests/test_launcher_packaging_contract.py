from __future__ import annotations

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def read(relative: str) -> str:
    return (ROOT / relative).read_text(encoding="utf-8")


def test_launcher_packaging_produces_a_folder_selecting_msi() -> None:
    build = read("scripts/build_copimine_launcher.ps1")
    stage = read("scripts/stage_copimine_launcher_site.ps1")
    assert "--msi" in build
    assert "--instLocation" in build
    assert "--splashImage" in build
    assert "--splashProgressColor" in build
    assert "--msiBanner" in build
    assert "--msiLogo" in build
    assert "--instWelcome" in build
    assert "--instReadme" in build
    assert "--instConclusion" in build
    assert "--shortcuts" in build
    assert "prepare_copimine_installer_assets.ps1" in build
    assert "CopiMineLauncherSetup-$Version.msi" in build
    assert "MsiPath" in stage
    assert "downloads/launcher" in stage


def test_launcher_packaging_produces_a_self_contained_folder_picker() -> None:
    build = read("scripts/build_copimine_launcher.ps1")
    metadata = read("scripts/build_launcher_public_metadata.ps1")
    stage = read("scripts/stage_copimine_launcher_site.ps1")
    project = ROOT / "CopiMineLauncher/installer/CopiMineLauncher.Installer/CopiMineLauncher.Installer.csproj"

    assert project.is_file()
    assert "InstallerMsiUrl" in build
    assert "CopiMineLauncher.Installer.csproj" in build
    assert "CUSTOM_INSTALLER_OUTPUT" in build
    assert "CustomInstallerPath" in metadata
    assert "customInstallerFilename" in metadata
    assert "CustomInstallerPath" in stage
    assert "customInstallerDownloadUrl" in stage
    installer = read("CopiMineLauncher/installer/CopiMineLauncher.Installer/MainWindow.xaml.cs")
    assert "InstallPathBox.Text = dialog.SelectedPath;" in installer
    assert "Path.Combine(dialog.SelectedPath, \"CopiMine Launcher\")" not in installer
    assert "MsiIntegrityVerifier.VerifyFileAsync" in installer
    assert "CopiMineInstallerMsiSha256" in read("CopiMineLauncher/installer/CopiMineLauncher.Installer/CopiMineLauncher.Installer.csproj")
    assert "InstallerMsiSha256" in build
    assert "InstallerMsiSizeBytes" in build


def test_server_hosted_packaging_does_not_duplicate_runtime_payload() -> None:
    build = read("scripts/build_copimine_launcher.ps1")

    assert "$packageExclude = if ($ServerHostedRuntimeOnly)" in build
    assert r"launcher-bootstrap[\\/]files" in build
    assert r"(?:^|[\\/])Minecraft[\\/].*" in build
    assert "CopiMineLauncher\\.App\\.exe\\.WebView2" in build
    assert "ServerHostedRuntimeOnly and RequireOfflineBundle are mutually exclusive" in build


def test_offline_packaging_omits_the_duplicate_server_runtime_payload() -> None:
    build = read("scripts/build_copimine_launcher.ps1")
    app = read("CopiMineLauncher/src/CopiMineLauncher.App/App.xaml.cs")
    paths = read("CopiMineLauncher/src/CopiMineLauncher.App/LauncherInstallPaths.cs")

    assert "offlineRuntimeDigest" in build
    assert r"launcher-bootstrap[\\/]files[\\/]" in build
    assert "HasBundledOfflineMinecraftBaseline" in paths
    assert "HasBundledOfflineMinecraftBaseline" in app
    assert "var hostedMinecraftRuntime = hasBundledOfflineBaseline" in app
    assert "? null" in app


def test_launcher_packaging_requires_a_bundled_webview2_runtime() -> None:
    build = read("scripts/build_copimine_launcher.ps1")

    assert "WebView2StandalonePath is required for a self-contained installer" in build
    assert "not $SkipPackaging" in build
    assert "Assets/WebView2/MicrosoftEdgeWebView2RuntimeInstallerX64.exe" in build


def test_launcher_installer_requires_browser_binding_without_manual_code_entry() -> None:
    contract = ROOT / "CopiMineLauncher/packaging/launcher-install-contract.json"
    build = read("scripts/build_copimine_launcher.ps1")

    assert contract.is_file()
    assert '"bindingRequired": true' in contract.read_text(encoding="utf-8")
    assert '"flow": "browser"' in contract.read_text(encoding="utf-8")
    assert '"manualCodeEntry": false' in contract.read_text(encoding="utf-8")
    assert '"allowSkip": false' in contract.read_text(encoding="utf-8")
    assert '"playBlockedUntilLinked": true' in contract.read_text(encoding="utf-8")
    assert "launcher-install-contract.json" in build
    assert "Copy-Item" in build


def test_launcher_installer_contract_describes_first_run_minecraft_defaults() -> None:
    contract = ROOT / "CopiMineLauncher/packaging/launcher-install-contract.json"
    document = contract.read_text(encoding="utf-8")

    assert '"firstRunSettings": "minecraft-defaults"' in document
    assert '"preserveExistingOptions": true' in document
    assert '"lang": "ru_ru"' in document
    assert '"narrator": "0"' in document
    assert '"soundCategory_master": "0.15"' in document


def test_launcher_metadata_keeps_msi_payload_for_the_native_installer() -> None:
    launcher = read("admin-web/frontend/launcher.html")
    data = read("admin-web/frontend/assets/js/public/launcher-data.js")
    assert "msiDownloadUrl" in data
    assert 'id="launcherFolderBtn"' in launcher


def test_launcher_page_exposes_the_native_folder_picker_installer() -> None:
    launcher = read("admin-web/frontend/launcher.html")
    render = read("admin-web/frontend/assets/js/public/launcher-render.js")
    data = read("admin-web/frontend/assets/js/public/launcher-data.js")

    assert 'id="launcherFolderBtn"' in launcher
    assert "customInstallerDownloadUrl" in data
    assert "launcherFolderBtn" in render


def test_launcher_page_exposes_only_the_folder_selecting_installer() -> None:
    launcher = read("admin-web/frontend/launcher.html")
    render = read("admin-web/frontend/assets/js/public/launcher-render.js")

    assert 'id="launcherDownloadBtn"' not in launcher
    assert 'id="launcherMsiBtn"' not in launcher
    assert "Скачать для Windows" not in launcher
    assert "launcherDownloadBtn" not in render
    assert "launcherMsiBtn" not in render


def test_msi_background_keeps_standard_installer_copy_area_readable() -> None:
    assets = read("scripts/prepare_copimine_installer_assets.ps1")

    assert "$copyAreaX" in assets
    assert "FillRectangle" in assets
    assert "System.Drawing.Color]::FromArgb(245, 247, 249" in assets
    assert "DrawString('COPIMINE'" not in assets
    assert "DrawString('Launcher'" not in assets


def test_msi_banner_contains_art_only_so_the_installer_does_not_duplicate_copy() -> None:
    assets = read("scripts/prepare_copimine_installer_assets.ps1")

    assert "DrawString('COPIMINE LAUNCHER'" not in assets
    assert "DrawString('Minecraft 1.21.1" not in assets
    assert "FromArgb(190, $navy.R" not in assets


def test_msi_logo_panel_does_not_distort_the_icon_in_the_fixed_left_strip() -> None:
    assets = read("scripts/prepare_copimine_installer_assets.ps1")

    panel = assets.split("function New-LogoPanel", 1)[1].split("$logo =", 1)[0]
    assert "Draw-Logo" not in panel
    assert "FillRectangle" in panel


def test_installer_welcome_does_not_repeat_velopack_heading() -> None:
    welcome = read("CopiMineLauncher/packaging/installer-welcome.txt").lstrip()

    assert not welcome.startswith("Добро пожаловать")
    assert "Minecraft 1.21.1" in welcome
    assert "выбора произвольного диска и папки" in welcome
    assert "MSI-вариант оставлен как резервный" in welcome


def test_folder_installer_layout_has_explicit_action_columns_and_readable_disabled_controls() -> None:
    xaml = read("CopiMineLauncher/installer/CopiMineLauncher.Installer/MainWindow.xaml")
    resources = read("CopiMineLauncher/installer/CopiMineLauncher.Installer/App.xaml")

    assert xaml.count("<ColumnDefinition") >= 4
    assert "TextWrapping=\"Wrap\"" in xaml
    assert "Focusable=\"False\"" in xaml
    assert "x:Key=\"PrimaryButton\"" in resources
    assert "IsEnabled" in resources
    assert "#5B756E" in resources


def test_launcher_site_stages_the_velopack_feed_for_self_updates() -> None:
    stage = read("scripts/stage_copimine_launcher_site.ps1")

    for marker in (
        "RELEASES",
        "releases.win.json",
        "releases.stable.json",
        "latest.json",
        "releases.json",
        "release.win.json",
        "releases.windows.json",
        "assets.win.json",
        "-full.nupkg",
    ):
        assert marker in stage


def test_release_hashing_streams_large_windows_artifacts() -> None:
    metadata = read("scripts/build_launcher_public_metadata.ps1")
    verifier = read("scripts/verify_copimine_launcher_release.ps1")
    stage = read("scripts/stage_copimine_launcher_site.ps1")

    for script in (metadata, verifier, stage):
        assert "File]::OpenRead" in script
        assert "File]::ReadAllBytes" not in script
