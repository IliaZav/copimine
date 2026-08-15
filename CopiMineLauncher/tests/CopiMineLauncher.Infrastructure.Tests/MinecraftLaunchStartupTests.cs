using System.Diagnostics;
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

        var exception = await Assert.ThrowsAsync<InvalidOperationException>(action);
        exception.Message.Should().Contain("MINECRAFT_PROCESS_EXITED");
        exception.Message.Should().Contain("code 23");
        exception.Message.Should().Contain("launcher-process.log");
    }
}
