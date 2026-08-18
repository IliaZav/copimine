using CopiMineLauncher.Infrastructure.Launch;
using FluentAssertions;
using Xunit;

namespace CopiMineLauncher.IntegrationTests;

public sealed class MinecraftLaunchSmokeTests
{
    [MinecraftLaunchSmokeFact]
    public async Task Real_local_instance_stays_alive_after_launcher_startup_probe()
    {
        var instanceRoot = Environment.GetEnvironmentVariable("COPIMINE_LAUNCH_SMOKE_INSTANCE")!;
        var javaPath = Path.Combine(instanceRoot, ".copimine", "java", "21.0.10", "bin", "java.exe");
        Directory.Exists(instanceRoot).Should().BeTrue();
        File.Exists(javaPath).Should().BeTrue();

        using var httpClient = new HttpClient();
        var service = new MinecraftLaunchService(httpClient);
        var evidence = await service.LaunchAsync(
            new LaunchRequest(
                instanceRoot,
                "fabric-loader-0.19.3-1.21.1",
                "SmokePlayer",
                javaPath,
                MaximumRamMb: 2048,
                ResolutionWidth: 800,
                ResolutionHeight: 600),
            CancellationToken.None);

        try
        {
            await Task.Delay(TimeSpan.FromSeconds(5));
            evidence.Process.HasExited.Should().BeFalse();
        }
        finally
        {
            if (!evidence.Process.HasExited)
            {
                evidence.Process.Kill(entireProcessTree: true);
                await evidence.Process.WaitForExitAsync();
            }
        }
    }

    private sealed class MinecraftLaunchSmokeFactAttribute : FactAttribute
    {
        public MinecraftLaunchSmokeFactAttribute()
        {
            if (string.IsNullOrWhiteSpace(Environment.GetEnvironmentVariable("COPIMINE_LAUNCH_SMOKE_INSTANCE")))
            {
                Skip = "Set COPIMINE_LAUNCH_SMOKE_INSTANCE to run the real local Minecraft launch smoke test.";
            }
        }
    }
}
