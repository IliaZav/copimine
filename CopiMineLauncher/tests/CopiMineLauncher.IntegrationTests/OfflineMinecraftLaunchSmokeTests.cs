using CopiMineLauncher.Infrastructure.Launch;
using FluentAssertions;
using Xunit;

namespace CopiMineLauncher.IntegrationTests;

public sealed class OfflineMinecraftLaunchSmokeTests
{
    [OfflineMinecraftLaunchFact]
    public async Task Seeded_instance_launches_without_any_network_request()
    {
        var instanceRoot = Environment.GetEnvironmentVariable("COPIMINE_OFFLINE_LAUNCH_INSTANCE")!;
        var javaPath = Path.Combine(instanceRoot, ".copimine", "java", "21.0.10", "bin", "java.exe");
        Directory.Exists(instanceRoot).Should().BeTrue();
        File.Exists(javaPath).Should().BeTrue();
        File.Exists(Path.Combine(instanceRoot, "versions", "1.21.1", "1.21.1.json")).Should().BeTrue();
        File.Exists(Path.Combine(instanceRoot, "versions", "fabric-loader-0.19.3-1.21.1", "fabric-loader-0.19.3-1.21.1.json")).Should().BeTrue();

        using var httpClient = new HttpClient(new NetworkMustNotBeUsedHandler());
        var service = new MinecraftLaunchService(httpClient);
        var evidence = await service.LaunchAsync(
            new LaunchRequest(
                instanceRoot,
                "fabric-loader-0.19.3-1.21.1",
                "OfflineSmoke",
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

    private sealed class NetworkMustNotBeUsedHandler : HttpMessageHandler
    {
        protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken) =>
            throw new HttpRequestException($"OFFLINE_LAUNCH_NETWORK_REQUEST: {request.RequestUri}");
    }

    private sealed class OfflineMinecraftLaunchFactAttribute : FactAttribute
    {
        public OfflineMinecraftLaunchFactAttribute()
        {
            if (string.IsNullOrWhiteSpace(Environment.GetEnvironmentVariable("COPIMINE_OFFLINE_LAUNCH_INSTANCE")))
            {
                Skip = "Set COPIMINE_OFFLINE_LAUNCH_INSTANCE to run the real offline Minecraft launch smoke test.";
            }
        }
    }
}
