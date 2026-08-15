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
    }
}
