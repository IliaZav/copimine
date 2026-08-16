from pathlib import Path


REPO = Path(__file__).resolve().parents[1]
APP = REPO / "CopiMineLauncher" / "src" / "CopiMineLauncher.App"


def test_skin_preview_uses_a_distinct_damage_animation():
    preview = (APP / "Assets" / "SkinPreview" / "skin-preview.html").read_text(encoding="utf-8")

    assert 'case "damage"' in preview
    assert "new skinview3d.FunctionAnimation" in preview
    assert "DiggingAnimation" not in preview


def test_skin_manager_keeps_catalog_available_when_preview_is_unavailable():
    code = (APP / "SkinManagerWindow.xaml.cs").read_text(encoding="utf-8")

    assert "initialized = true;" in code
    assert "await LoadCatalogAsync(reset: true);" in code
    assert code.index("await InitializePreviewAsync();") < code.index("await LoadCatalogAsync(reset: true);")
    assert "Предпросмотр не запущен" in code
    assert "Каталог недоступен" in code


def test_diagnostics_are_selectable_read_only_text():
    xaml = (APP / "MainWindow.xaml").read_text(encoding="utf-8")

    assert 'Text="{Binding Diagnostic, Mode=OneWay}"' in xaml
    assert 'IsReadOnly="True"' in xaml
    assert 'AcceptsReturn="True"' in xaml
    assert '<TextBlock Text="{Binding Diagnostic}"' not in xaml


def test_skin_manager_has_dark_dropdown_and_spaced_action_controls():
    xaml = (APP / "SkinManagerWindow.xaml").read_text(encoding="utf-8")

    assert 'x:Key="DarkComboBoxTemplate"' in xaml
    assert 'x:Key="ActionButton"' in xaml
    assert 'ItemContainerStyle' in xaml


def test_cosmetics_use_durable_separate_libraries_and_preserve_gif_capes():
    store = (REPO / "CopiMineLauncher" / "src" / "CopiMineLauncher.Infrastructure" / "Skins" / "LocalCosmeticsStore.cs").read_text(encoding="utf-8")
    validator = (REPO / "CopiMineLauncher" / "src" / "CopiMineLauncher.Infrastructure" / "Skins" / "SkinTextureValidator.cs").read_text(encoding="utf-8")
    manager = (APP / "SkinManagerWindow.xaml.cs").read_text(encoding="utf-8")

    assert 'Path.Combine(cosmeticsRoot, "skins")' in store
    assert 'Path.Combine(cosmeticsRoot, "capes")' in store
    assert "FindLibraryPath" in store
    assert "SaveToLibrary" in store
    assert "GIF89a" in validator
    assert "IsAnimated" in validator
    assert "SaveToLibrary" in manager


def test_gif_cape_keeps_library_animation_and_installs_a_valid_game_png_frame():
    store = (REPO / "CopiMineLauncher" / "src" / "CopiMineLauncher.Infrastructure" / "Skins" / "LocalCosmeticsStore.cs").read_text(encoding="utf-8")
    manager = (APP / "SkinManagerWindow.xaml.cs").read_text(encoding="utf-8")

    assert "InstallPngFile" in store
    assert 'ConvertToPng(gifLibraryPath)' in manager
    assert 'localStore.InstallPngFile(firstFramePath, player, kind)' in manager
    assert "GIF сохранён в библиотеке" in manager


def test_skin_preview_repaints_animated_gif_capes_and_clears_old_timer():
    preview = (APP / "Assets" / "SkinPreview" / "skin-preview.html").read_text(encoding="utf-8")
    manager = (APP / "SkinManagerWindow.xaml.cs").read_text(encoding="utf-8")

    assert "capeAnimated" in manager
    assert "capeAnimated" in preview
    assert "new Image()" in preview
    assert "setInterval" in preview
    assert "clearInterval" in preview
    assert "loadCape(image" in preview


def test_local_staging_runner_is_loopback_only():
    script = (REPO / "scripts" / "run_copimine_launcher_staging.ps1").read_text(encoding="utf-8")

    assert "127.0.0.1" in script
    assert "http.server" in script
    assert "COPIMINE_LAUNCHER_STAGING_BASE_URL" in script
    assert "ssh" not in script.lower()
    assert "scp" not in script.lower()
