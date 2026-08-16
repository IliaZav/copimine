using CopiMineLauncher.Infrastructure.Launch;
using FluentAssertions;
using Xunit;

namespace CopiMineLauncher.IntegrationTests;

public sealed class MinecraftServerJoinSmokeTests
{
    [MinecraftServerJoinSmokeFact]
    public async Task Real_client_joins_local_paper_and_completes_ready_ack_gate()
    {
        var instanceRoot = Environment.GetEnvironmentVariable("COPIMINE_SERVER_SMOKE_INSTANCE")!;
        var serverHost = Environment.GetEnvironmentVariable("COPIMINE_SERVER_SMOKE_HOST")!;
        var serverPort = int.Parse(Environment.GetEnvironmentVariable("COPIMINE_SERVER_SMOKE_PORT")!);
        var serverLog = Environment.GetEnvironmentVariable("COPIMINE_SERVER_SMOKE_LOG")!;
        var javaPath = Path.Combine(instanceRoot, ".copimine", "java", "21.0.10", "bin", "java.exe");

        Directory.Exists(instanceRoot).Should().BeTrue();
        File.Exists(javaPath).Should().BeTrue();
        File.Exists(serverLog).Should().BeTrue();

        var startingLength = new FileInfo(serverLog).Length;
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
                ResolutionHeight: 600,
                ServerAddress: serverHost,
                ServerPort: serverPort),
            CancellationToken.None);

        try
        {
            var result = await WaitForServerLogAsync(
                serverLog,
                startingLength,
                "CLIENT_GATE_ACCEPT player=SmokePlayer",
                "CLIENT_GATE_REJECT player=SmokePlayer");

            result.Should().Contain("CLIENT_GATE_ACCEPT player=SmokePlayer");
            result.Should().NotContain("CLIENT_GATE_REJECT");
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

    private static async Task<string> WaitForServerLogAsync(
        string path,
        long startingLength,
        params string[] markers)
    {
        var deadline = DateTimeOffset.UtcNow.AddSeconds(30);
        while (DateTimeOffset.UtcNow < deadline)
        {
            var text = ReadTail(path, startingLength);
            if (markers.Any(text.Contains))
            {
                return text;
            }

            await Task.Delay(250);
        }

        return ReadTail(path, startingLength);
    }

    private static string ReadTail(string path, long startingLength)
    {
        using var stream = new FileStream(path, FileMode.Open, FileAccess.Read, FileShare.ReadWrite | FileShare.Delete);
        stream.Position = Math.Min(startingLength, stream.Length);
        using var reader = new StreamReader(stream);
        return reader.ReadToEnd();
    }

    private sealed class MinecraftServerJoinSmokeFactAttribute : FactAttribute
    {
        public MinecraftServerJoinSmokeFactAttribute()
        {
            if (string.IsNullOrWhiteSpace(Environment.GetEnvironmentVariable("COPIMINE_SERVER_SMOKE_INSTANCE"))
                || string.IsNullOrWhiteSpace(Environment.GetEnvironmentVariable("COPIMINE_SERVER_SMOKE_HOST"))
                || string.IsNullOrWhiteSpace(Environment.GetEnvironmentVariable("COPIMINE_SERVER_SMOKE_PORT"))
                || string.IsNullOrWhiteSpace(Environment.GetEnvironmentVariable("COPIMINE_SERVER_SMOKE_LOG")))
            {
                Skip = "Set COPIMINE_SERVER_SMOKE_INSTANCE/HOST/PORT/LOG to run the local Paper join smoke test.";
            }
        }
    }
}
