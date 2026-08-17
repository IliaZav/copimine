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
