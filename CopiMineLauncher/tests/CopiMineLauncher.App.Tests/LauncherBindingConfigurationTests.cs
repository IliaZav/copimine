using System.IO;
using FluentAssertions;
using Xunit;

namespace CopiMineLauncher.App.Tests;

public sealed class LauncherBindingConfigurationTests
{
    [Fact]
    public void Binding_uses_a_short_control_plane_timeout_before_trying_loopback_fallback()
    {
        var source = File.ReadAllText(SourcePath("App.xaml.cs"));

        source.Should().Contain("var bindingHttpClient = new HttpClient(CreateHttpHandler())");
        source.Should().Contain("Timeout = TimeSpan.FromSeconds(8)");
        source.Should().Contain("new HttpLauncherBindingClient(bindingHttpClient, new Uri(\"https://copimine.ru/\"), deviceId)");
        source.Should().Contain("LauncherInstallPaths.ResolveLocalBindingBaseUrl()");
    }

    private static string SourcePath(string fileName) =>
        Path.GetFullPath(Path.Combine(
            new[] { AppContext.BaseDirectory, "..", "..", "..", "..", "..", "src", "CopiMineLauncher.App", fileName }));
}
