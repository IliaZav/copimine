using System.Reflection;
using System.Runtime.Versioning;
using CopiMineLauncher.Core;
using FluentAssertions;
using Xunit;

namespace CopiMineLauncher.Core.Tests;

public sealed class ScaffoldContractTests
{
    [Fact]
    public void Core_targets_net10()
    {
        var framework = typeof(LauncherVersionInfo).Assembly
            .GetCustomAttribute<TargetFrameworkAttribute>()
            ?.FrameworkName;

        framework.Should().Be(".NETCoreApp,Version=v10.0");
    }

    [Fact]
    public void Product_and_initial_version_are_exposed()
    {
        LauncherVersionInfo.Product.Should().Be("CopiMineLauncher");
        LauncherVersionInfo.Version.Should().Be("1.0.3");
    }

    [Fact]
    public void Runtime_version_matches_the_core_assembly_version()
    {
        var assemblyVersion = typeof(LauncherVersionInfo).Assembly.GetName().Version;

        assemblyVersion.Should().NotBeNull();
        LauncherVersionInfo.Version.Should().Be(
            $"{assemblyVersion!.Major}.{assemblyVersion.Minor}.{assemblyVersion.Build}");
    }
}
