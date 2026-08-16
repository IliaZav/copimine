using System.IO;
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

        LauncherInstallPaths.ResolveMinecraftRoot(@"D:\Games\CopiMine\Launcher\current")
            .Should().Be(@"D:\Games\CopiMine\Minecraft");

        LauncherInstallPaths.ResolveLauncherBootstrapRoot(@"D:\Games\CopiMine\current")
            .Should().Be(@"D:\Games\CopiMine\current\launcher-bootstrap");
    }

    [Fact]
    public void Direct_custom_application_directory_keeps_game_inside_selected_root()
    {
        LauncherInstallPaths.ResolveInstallRoot(@"D:\Games\CopiMine")
            .Should().Be(@"D:\Games\CopiMine");

        LauncherInstallPaths.ResolveMinecraftRoot(@"D:\Games\CopiMine")
            .Should().Be(@"D:\Games\CopiMine\Minecraft");
    }

    [Fact]
    public async Task Existing_legacy_sibling_instance_is_reused_instead_of_an_empty_selected_folder()
    {
        using var temp = new LocalApplicationDataTestDirectory();
        var selectedInstallRoot = Path.Combine(temp.Path, "CopiMine");
        var currentDirectory = Path.Combine(selectedInstallRoot, "current");
        var legacyInstance = Path.Combine(temp.Path, "Minecraft");
        Directory.CreateDirectory(Path.Combine(legacyInstance, ".copimine"));
        await File.WriteAllTextAsync(Path.Combine(legacyInstance, ".copimine", "managed-state.json"), "{}");

        LauncherInstallPaths.ResolveMinecraftRoot(currentDirectory)
            .Should().Be(Path.GetFullPath(legacyInstance));
    }

    [Fact]
    public void Launcher_data_is_outside_the_velopack_install_directory()
    {
        LauncherInstallPaths.ResolveLauncherDataRoot(@"D:\UserData")
            .Should().Be(@"D:\UserData\CopiMine\LauncherData");
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

    private sealed class LocalApplicationDataTestDirectory : IDisposable
    {
        public LocalApplicationDataTestDirectory()
        {
            Path = System.IO.Path.Combine(
                Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
                "CopiMineLauncherPathTests-" + Guid.NewGuid().ToString("N"));
            Directory.CreateDirectory(Path);
        }

        public string Path { get; }

        public void Dispose()
        {
            if (Directory.Exists(Path))
            {
                Directory.Delete(Path, recursive: true);
            }
        }
    }
}
