using System.IO;
using FluentAssertions;
using Xunit;

namespace CopiMineLauncher.App.Tests;

public sealed class LauncherVisualSystemContractTests
{
    [Fact]
    public void Launcher_theme_uses_the_site_aurora_palette_instead_of_the_old_green_surface()
    {
        var xaml = ReadSource("src", "CopiMineLauncher.App", "App.xaml");

        xaml.Should().Contain("Color=\"#07111F\"");
        xaml.Should().Contain("x:Key=\"LauncherLine\"");
        xaml.Should().Contain("x:Key=\"LauncherAccentPurple\"");
        xaml.Should().NotContain("#071B16");
    }

    [Fact]
    public void Launcher_shared_surfaces_are_quiet_and_use_a_single_small_radius()
    {
        var xaml = ReadSource("src", "CopiMineLauncher.App", "App.xaml");

        xaml.Should().Contain("x:Key=\"LauncherCard\"");
        xaml.Should().Contain("<Setter Property=\"CornerRadius\" Value=\"8\" />");
        xaml.Should().Contain("x:Key=\"LauncherRule\"");
    }

    [Fact]
    public void Main_window_uses_the_aurora_rule_for_the_editorial_footer()
    {
        var xaml = ReadSource("src", "CopiMineLauncher.App", "MainWindow.xaml");

        xaml.Should().Contain("x:Name=\"LauncherFooter\"");
        xaml.Should().Contain("Style=\"{StaticResource LauncherRule}\"");
        xaml.Should().Contain("x:Name=\"LaunchDock\"");
    }

    [Fact]
    public void Settings_and_skin_screens_reuse_the_shared_control_language()
    {
        var settings = ReadSource("src", "CopiMineLauncher.App", "LauncherSettingsWindow.xaml");
        var skins = ReadSource("src", "CopiMineLauncher.App", "SkinManagerWindow.xaml");

        settings.Should().Contain("Style=\"{StaticResource LauncherSlider}\"");
        settings.Should().Contain("StaticResource LauncherLine");
        settings.Should().NotContain("#7af0aa");
        skins.Should().Contain("StaticResource LauncherAccentPurple");
        skins.Should().Contain("StaticResource LauncherLine");
        skins.Should().NotContain("#7af0aa");
    }

    [Fact]
    public void Main_window_content_rows_scale_without_fixed_pixel_bands()
    {
        var xaml = ReadSource("src", "CopiMineLauncher.App", "MainWindow.xaml");

        xaml.Should().Contain("<RowDefinition Height=\"2.25*\" MinHeight=\"225\" />");
        xaml.Should().Contain("<RowDefinition Height=\"1.15*\" MinHeight=\"120\" />");
        xaml.Should().Contain("<RowDefinition Height=\"1.75*\" MinHeight=\"175\" />");
        xaml.Should().NotContain("<RowDefinition Height=\"300\" />");
        xaml.Should().NotContain("<RowDefinition Height=\"170\" />");
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
