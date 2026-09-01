using System.IO;
using FluentAssertions;
using Xunit;

namespace CopiMineLauncher.App.Tests;

public sealed class LauncherRadicalRedesignContractTests
{
    [Fact]
    public void Main_window_uses_the_expedition_console_composition()
    {
        var xaml = ReadSource("src", "CopiMineLauncher.App", "MainWindow.xaml");

        xaml.Should().Contain("Width=\"1480\" Height=\"920\" MinWidth=\"1240\" MinHeight=\"760\"");
        xaml.Should().Contain("x:Name=\"LauncherCommandRail\"");
        xaml.Should().Contain("x:Name=\"HeroScene\"");
        xaml.Should().Contain("x:Name=\"HeroSceneImage\"");
        xaml.Should().Contain("x:Name=\"LaunchDock\"");
        xaml.Should().Contain("Text=\"Экспедиция готова\"");
        xaml.Should().Contain("Text=\"Состояние сборки\"");
        xaml.Should().Contain("Name=\"HeroSceneDrift\"");
        xaml.Should().Contain("Name=\"HeroSceneGlow\"");
        xaml.Should().Contain("Name=\"LaunchStatePulse\"");
        xaml.Should().Contain("Binding=\"{Binding IsBusy}\"");
        xaml.Should().Contain("Text=\"СОСТОЯНИЕ\"");
    }

    [Fact]
    public void App_theme_declares_distinct_display_body_and_data_roles()
    {
        var xaml = ReadSource("src", "CopiMineLauncher.App", "App.xaml");

        xaml.Should().Contain("x:Key=\"LauncherDisplayFont\"");
        xaml.Should().Contain("x:Key=\"LauncherBodyFont\"");
        xaml.Should().Contain("x:Key=\"LauncherDataFont\"");
        xaml.Should().Contain("x:Key=\"LauncherRailButton\"");
        xaml.Should().Contain("x:Key=\"LauncherHeroCard\"");
        xaml.Should().Contain("#A797FF");
        xaml.Should().Contain("#59D6D0");
    }

    [Fact]
    public void Redesign_keeps_the_primary_runtime_commands_visible_in_the_new_shell()
    {
        var xaml = ReadSource("src", "CopiMineLauncher.App", "MainWindow.xaml");

        xaml.Should().Contain("Command=\"{Binding PlayCommand}\"");
        xaml.Should().Contain("Command=\"{Binding RepairCommand}\"");
        xaml.Should().Contain("Command=\"{Binding OpenAccountLinkCommand}\"");
        xaml.Should().Contain("Command=\"{Binding OpenInstanceFolderCommand}\"");
        xaml.Should().Contain("Command=\"{Binding CheckSelfUpdateCommand}\"");
        xaml.Should().Contain("Content=\"✦   Скины и плащи\"");
        xaml.Should().Contain("Content=\"⚙   Настройки\"");
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
