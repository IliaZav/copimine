using FluentAssertions;
using Xunit;

namespace CopiMineLauncher.App.Tests;

public sealed class LauncherInstallPathsTests
{
    [Fact]
    public void Velopack_current_directory_resolves_to_selected_install_root()
    {
        LauncherInstallPaths.ResolveInstallRoot(@"D:\Games\CopiMine\current")
            .Should().Be(@"D:\Games\CopiMine");

        LauncherInstallPaths.ResolveMinecraftRoot(@"D:\Games\CopiMine\current")
            .Should().Be(@"D:\Games\CopiMine\Minecraft");

        LauncherInstallPaths.ResolveLauncherBootstrapRoot(@"D:\Games\CopiMine\current")
            .Should().Be(@"D:\Games\CopiMine\current\launcher-bootstrap");
    }

    [Fact]
    public void Self_update_feed_uses_loopback_staging_without_touching_production()
    {
        LauncherInstallPaths.ResolveSelfUpdateFeed(new Uri("http://127.0.0.1:8287"))
            .Should().Be(new Uri("http://127.0.0.1:8287/downloads/launcher/"));
    }

    [Fact]
    public void Self_update_feed_rejects_non_loopback_override()
    {
        LauncherInstallPaths.ResolveSelfUpdateFeed(new Uri("http://example.test"))
            .Should().Be(new Uri("https://copimine.ru/downloads/launcher/"));
    }

    [Fact]
    public void Staging_environment_requires_a_loopback_http_url()
    {
        var previous = Environment.GetEnvironmentVariable("COPIMINE_LAUNCHER_STAGING_BASE_URL");
        try
        {
            Environment.SetEnvironmentVariable("COPIMINE_LAUNCHER_STAGING_BASE_URL", "http://127.0.0.1:8287");
            LauncherInstallPaths.IsLoopbackStagingEnvironment().Should().BeTrue();

            Environment.SetEnvironmentVariable("COPIMINE_LAUNCHER_STAGING_BASE_URL", "https://copimine.ru");
            LauncherInstallPaths.IsLoopbackStagingEnvironment().Should().BeFalse();
        }
        finally
        {
            Environment.SetEnvironmentVariable("COPIMINE_LAUNCHER_STAGING_BASE_URL", previous);
        }
    }
}
