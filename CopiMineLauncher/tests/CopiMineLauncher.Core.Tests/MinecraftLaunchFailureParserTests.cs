using CopiMineLauncher.Core.Launch;
using FluentAssertions;
using Xunit;

namespace CopiMineLauncher.Core.Tests;

public sealed class MinecraftLaunchFailureParserTests
{
    [Fact]
    public void Fabric_entrypoint_failure_identifies_a_user_added_mod()
    {
        const string log = """
            [main/ERROR] Uncaught exception in thread "main"
            java.lang.RuntimeException: Could not execute entrypoint stage 'main' due to errors, provided by 'better-leaves'!
            Caused by: java.lang.NoSuchMethodError: net.minecraft.client.MinecraftClient.getInstance()
            """;

        var report = MinecraftLaunchFailureParser.Parse(
            log,
            "C:\\CopiMine\\logs\\launcher-process.log",
            new[] { "BetterLeaves-1.4.0.jar" });

        report.IsModRelated.Should().BeTrue();
        report.IsLikelyUserMod.Should().BeTrue();
        report.SuspectedModId.Should().Be("better-leaves");
        report.SuspectedModFileName.Should().Be("BetterLeaves-1.4.0.jar");
        report.Title.Should().Contain("мод");
        report.Explanation.Should().Contain("BetterLeaves-1.4.0.jar");
        report.LogPath.Should().EndWith("launcher-process.log");
    }

    [Fact]
    public void Fabric_mod_resolution_failure_explains_a_dependency_conflict()
    {
        const string log = """
            [main/ERROR] Mod resolution failed!
            [main/ERROR] A potential solution has been determined:
            [main/ERROR]  - Replace mod 'Better Leaves' (better-leaves) 1.4.0 with version 1.5.0 or later that is compatible with:
            [main/ERROR]      - minecraft 1.21.1
            """;

        var report = MinecraftLaunchFailureParser.Parse(
            log,
            "C:\\CopiMine\\logs\\launcher-process.log",
            new[] { "BetterLeaves-1.4.0.jar" });

        report.Kind.Should().Be(MinecraftLaunchFailureKind.ModResolution);
        report.SuspectedModId.Should().Be("better-leaves");
        report.Summary.Should().Contain("зависимост");
        report.Explanation.Should().Contain("совместим");
        report.Evidence.Should().Contain(line => line.Contains("Replace mod", StringComparison.Ordinal));
    }

    [Fact]
    public void Generic_java_failure_is_not_blamed_on_a_mod()
    {
        const string log = """
            [main/ERROR] Could not reserve enough space for 3145728KB object heap
            [main/ERROR] Error: Could not create the Java Virtual Machine.
            """;

        var report = MinecraftLaunchFailureParser.Parse(
            log,
            "C:\\CopiMine\\logs\\launcher-process.log",
            new[] { "BetterLeaves-1.4.0.jar" });

        report.IsModRelated.Should().BeFalse();
        report.IsLikelyUserMod.Should().BeFalse();
        report.SuspectedModId.Should().BeNull();
        report.Kind.Should().Be(MinecraftLaunchFailureKind.JavaRuntime);
        report.Explanation.ToLowerInvariant().Should().NotContain("betterleaves");
    }

    [Fact]
    public void Duplicate_mod_id_identifies_the_additional_mod_when_the_log_names_the_id()
    {
        const string log = "[main/ERROR] Mod resolution failed! Duplicate mods with the same ID: sodium";

        var report = MinecraftLaunchFailureParser.Parse(
            log,
            "C:\\CopiMine\\logs\\launcher-process.log",
            new[] { "sodium-extra-0.6.0.jar" });

        report.Kind.Should().Be(MinecraftLaunchFailureKind.ModResolution);
        report.SuspectedModId.Should().Be("sodium");
        report.SuspectedModFileName.Should().Be("sodium-extra-0.6.0.jar");
        report.IsLikelyUserMod.Should().BeTrue();
        report.Summary.Should().Contain("sodium-extra-0.6.0.jar");
    }
}
