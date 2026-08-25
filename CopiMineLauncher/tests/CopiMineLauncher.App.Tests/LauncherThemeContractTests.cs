using System.IO;
using FluentAssertions;
using Xunit;

namespace CopiMineLauncher.App.Tests;

public sealed class LauncherThemeContractTests
{
    [Fact]
    public void App_resources_define_one_shared_launcher_theme()
    {
        var xaml = ReadSource("src", "CopiMineLauncher.App", "App.xaml");

        xaml.Should().Contain("x:Key=\"LauncherColors\"");
        xaml.Should().Contain("x:Key=\"LauncherButton\"");
        xaml.Should().Contain("x:Key=\"LauncherPrimaryButton\"");
        xaml.Should().Contain("x:Key=\"LauncherCard\"");
        xaml.Should().Contain("x:Key=\"LauncherTextBox\"");
        xaml.Should().Contain("x:Key=\"LauncherComboBox\"");
        xaml.Should().Contain("x:Key=\"LauncherProgressBar\"");
    }

    [Fact]
    public void Main_window_uses_the_supplied_atmospheric_background_and_common_card_style()
    {
        var xaml = ReadSource("src", "CopiMineLauncher.App", "MainWindow.xaml");

        xaml.Should().Contain("launcher-home-background.png");
        xaml.Should().Contain("StaticResource LauncherCard");
        xaml.Should().Contain("StaticResource LauncherPrimaryButton");
        xaml.Should().Contain("AutomationProperties.Name=\"Открыть папку игры\"");
        xaml.Should().Contain("Content=\"Скины\"");
        xaml.Should().Contain("Content=\"Настройки\"");
    }

    [Fact]
    public void Secondary_screens_are_user_controls_and_keep_the_existing_back_navigation()
    {
        var mainWindow = ReadSource("src", "CopiMineLauncher.App", "MainWindow.xaml");
        var settings = ReadSource("src", "CopiMineLauncher.App", "LauncherSettingsWindow.xaml");
        var skins = ReadSource("src", "CopiMineLauncher.App", "SkinManagerWindow.xaml");

        mainWindow.Should().Contain("x:Name=\"ScreenView\"");
        mainWindow.Should().Contain("x:Name=\"BackButton\"");
        settings.Should().StartWith("<UserControl");
        skins.Should().StartWith("<UserControl");
        settings.Should().NotContain("<Window ");
        skins.Should().NotContain("<Window ");
    }

    [Fact]
    public void Main_window_keeps_a_large_canvas_for_the_full_launcher_controls()
    {
        var xaml = ReadSource("src", "CopiMineLauncher.App", "MainWindow.xaml");

        xaml.Should().Contain("Width=\"1360\" Height=\"860\" MinWidth=\"1180\" MinHeight=\"720\"");
        xaml.Should().Contain("Opacity=\"0.86\"");
        xaml.Should().Contain("#80061210");
    }

    [Fact]
    public void Skin_preview_uses_packaged_minecraft_frames_instead_of_generated_gradients()
    {
        var code = ReadSource("src", "CopiMineLauncher.App", "SkinManagerWindow.xaml.cs");
        var preview = ReadSource("src", "CopiMineLauncher.App", "Assets", "SkinPreview", "skin-preview.html");

        code.Should().Contain("CopyPreviewAsset(\"shader-forest.jpg\")");
        code.Should().Contain("CopyPreviewAsset(\"shader-river.jpg\")");
        code.Should().Contain("CopyPreviewAsset(\"shader-mountain.jpg\")");
        preview.Should().Contain("shader-river.jpg");
        preview.Should().Contain("shader-mountain.jpg");
        preview.Should().Contain("shader-forest.jpg");
        preview.Should().NotContain("createLinearGradient");
        preview.Should().NotContain("ctx.fillRect");
    }

    private static string ReadSource(params string[] parts)
    {
        var path = Path.Combine(
            new[] { AppContext.BaseDirectory, "..", "..", "..", "..", ".." }
                .Concat(parts)
                .ToArray());
        File.Exists(path).Should().BeTrue(path);
        return File.ReadAllText(path);
    }
}
