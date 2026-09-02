using System.IO;
using FluentAssertions;
using Xunit;

namespace CopiMineLauncher.App.Tests;

public sealed class LauncherRenderingContractTests
{
    [Fact]
    public void Wpf_app_uses_a_non_blank_render_fallback_for_the_launcher_surface()
    {
        var root = Path.GetFullPath(Path.Combine(
            AppContext.BaseDirectory,
            "..", "..", "..", "..", "..",
            "src", "CopiMineLauncher.App"));
        var source = File.ReadAllText(Path.Combine(root, "App.xaml.cs"));

        source.Should().Contain("using System.Windows.Interop;");
        source.Should().Contain("RenderOptions.ProcessRenderMode = RenderMode.SoftwareOnly;");
    }
}
