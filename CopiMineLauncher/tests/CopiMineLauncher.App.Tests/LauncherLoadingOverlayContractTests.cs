using System.IO;
using FluentAssertions;
using Xunit;

namespace CopiMineLauncher.App.Tests;

public sealed class LauncherLoadingOverlayContractTests
{
    [Fact]
    public void Loading_overlay_contains_real_artwork_progress_and_intermediate_motion()
    {
        var xaml = ReadSource("src", "CopiMineLauncher.App", "LauncherLoadingOverlay.xaml");

        xaml.Should().Contain("update-background.png");
        xaml.Should().Contain("loading-emblem.png");
        xaml.Should().Contain("splash.gif");
        xaml.Should().Contain("copimine-logo-animated.gif");
        xaml.Should().Contain("Progress");
        xaml.Should().Contain("Stage");
        xaml.Should().Contain("DoubleAnimation");
        xaml.Should().Contain("Storyboard");
        xaml.Should().Contain("LauncherMotion");
    }

    [Fact]
    public void Loading_overlay_is_a_user_control_not_a_second_window()
    {
        var xaml = ReadSource("src", "CopiMineLauncher.App", "LauncherLoadingOverlay.xaml");

        xaml.Should().StartWith("<UserControl");
        xaml.Should().NotContain("<Window ");
        xaml.Should().Contain("IsHitTestVisible");
    }

    [Fact]
    public void View_model_declares_a_separate_initialization_state()
    {
        typeof(LauncherViewModel).GetProperty("IsInitializing")
            .Should().NotBeNull("splash visibility must not be inferred from a finished operation");
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
