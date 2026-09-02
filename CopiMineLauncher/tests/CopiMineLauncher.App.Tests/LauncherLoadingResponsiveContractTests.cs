using System.IO;
using FluentAssertions;
using Xunit;

namespace CopiMineLauncher.App.Tests;

public sealed class LauncherLoadingResponsiveContractTests
{
    [Fact]
    public void Operation_overlay_uses_the_responsive_progress_control_without_fixed_shimmer_distance()
    {
        var xaml = ReadSource("src", "CopiMineLauncher.App", "LauncherLoadingOverlay.xaml");

        xaml.Should().Contain("x:Name=\"OperationProgress\"");
        xaml.Should().Contain("IsIndeterminate=\"{Binding IsIndeterminate, ElementName=Root}\"");
        xaml.Should().NotContain("To=\"560\"");
        xaml.Should().NotContain("To=\"560px\"");
    }

    [Fact]
    public void Splash_copy_describes_the_real_startup_work()
    {
        var xaml = ReadSource("src", "CopiMineLauncher.App", "LauncherLoadingOverlay.xaml");

        xaml.Should().Contain("Проверяем профиль и обновления");
        xaml.Should().Contain("LauncherDisplayFont");
        xaml.Should().Contain("LauncherDataFont");
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
