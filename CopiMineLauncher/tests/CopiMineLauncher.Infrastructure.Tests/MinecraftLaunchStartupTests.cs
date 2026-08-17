using System.Diagnostics;
using CopiMineLauncher.Core.Launch;
using CopiMineLauncher.Infrastructure.Launch;
using FluentAssertions;
using Xunit;

namespace CopiMineLauncher.Infrastructure.Tests;

public sealed class MinecraftLaunchStartupTests
{
    [Fact]
    public async Task Early_process_exit_is_reported_with_exit_code_and_log_path()
    {
        if (!OperatingSystem.IsWindows())
        {
            return;
        }

        using var process = Process.Start(new ProcessStartInfo
        {
            FileName = Environment.GetEnvironmentVariable("ComSpec") ?? "cmd.exe",
            Arguments = "/c exit 23",
            UseShellExecute = false,
            CreateNoWindow = true
        });

        process.Should().NotBeNull();
        Func<Task> action = () => MinecraftLaunchStartup.EnsureAliveAsync(
            process!,
            "C:\\CopiMine\\logs\\launcher-process.log",
            TimeSpan.FromSeconds(2),
            CancellationToken.None);

        var exception = await Assert.ThrowsAsync<MinecraftLaunchException>(action);
        exception.Message.Should().Contain("MINECRAFT_START_FAILED");
        exception.Message.Should().Contain("код 23");
        exception.Message.Should().Contain("launcher-process.log");
    }

    [Fact]
    public async Task Early_process_exit_preserves_the_parsed_user_mod_failure()
    {
        if (!OperatingSystem.IsWindows())
        {
            return;
        }

        var temp = Directory.CreateTempSubdirectory("copimine-launch-failure-");
        try
        {
            var logPath = Path.Combine(temp.FullName, "logs", "launcher-process.log");
            Directory.CreateDirectory(Path.GetDirectoryName(logPath)!);
            await File.WriteAllTextAsync(logPath, """
            [main/ERROR] Could not execute entrypoint stage 'main' due to errors, provided by 'better-leaves'!
            """);

            using var process = Process.Start(new ProcessStartInfo
            {
                FileName = Environment.GetEnvironmentVariable("ComSpec") ?? "cmd.exe",
                Arguments = "/c exit 23",
                UseShellExecute = false,
                CreateNoWindow = true
            });

            process.Should().NotBeNull();
            var exception = await Assert.ThrowsAsync<MinecraftLaunchException>(() => MinecraftLaunchStartup.EnsureAliveAsync(
                process!,
                logPath,
                TimeSpan.FromSeconds(2),
                CancellationToken.None,
                temp.FullName,
                new[] { "BetterLeaves-1.4.0.jar" }));

            exception.Report.IsLikelyUserMod.Should().BeTrue();
            exception.Report.SuspectedModFileName.Should().Be("BetterLeaves-1.4.0.jar");
            exception.Report.LogPath.Should().Be(logPath);
        }
        finally
        {
            temp.Delete(true);
        }
    }
}
