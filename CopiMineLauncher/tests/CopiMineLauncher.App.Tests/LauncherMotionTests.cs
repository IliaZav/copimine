using System.Reflection;
using FluentAssertions;
using Xunit;

namespace CopiMineLauncher.App.Tests;

public sealed class LauncherMotionTests
{
    [Fact]
    public void Launcher_motion_exposes_bounded_transition_tokens()
    {
        var motionType = typeof(LauncherVisualAssetCatalog).Assembly.GetType("CopiMineLauncher.App.LauncherMotion");
        motionType.Should().NotBeNull("the Launcher needs one motion policy instead of scattered timings");

        var shortTransition = (TimeSpan)motionType!.GetProperty("ShortTransition")!.GetValue(null)!;
        var mediumTransition = (TimeSpan)motionType.GetProperty("MediumTransition")!.GetValue(null)!;

        shortTransition.Should().Be(TimeSpan.FromMilliseconds(180));
        mediumTransition.Should().Be(TimeSpan.FromMilliseconds(320));
        shortTransition.Should().BeLessThan(mediumTransition);
    }

    [Fact]
    public void Launcher_motion_progress_is_monotonic_and_clamped()
    {
        var motionType = typeof(LauncherVisualAssetCatalog).Assembly.GetType("CopiMineLauncher.App.LauncherMotion");
        motionType.Should().NotBeNull();
        var method = motionType!.GetMethod("GetOpacityAt", BindingFlags.Public | BindingFlags.Static);

        method.Should().NotBeNull();
        var start = (double)method!.Invoke(null, [0d])!;
        var middle = (double)method.Invoke(null, [0.5d])!;
        var end = (double)method.Invoke(null, [1d])!;
        var below = (double)method.Invoke(null, [-1d])!;
        var above = (double)method.Invoke(null, [2d])!;

        start.Should().Be(0d);
        middle.Should().BeGreaterThan(start).And.BeLessThan(end);
        end.Should().Be(1d);
        below.Should().Be(0d);
        above.Should().Be(1d);
    }
}
