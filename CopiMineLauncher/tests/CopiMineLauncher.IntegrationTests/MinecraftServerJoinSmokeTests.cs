using CopiMineLauncher.Infrastructure.Launch;
using CopiMineLauncher.Infrastructure.Servers;
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
        var clientLog = Environment.GetEnvironmentVariable("COPIMINE_SERVER_SMOKE_CLIENT_LOG");
        var cosmeticLog = Path.Combine(instanceRoot, "CustomSkinLoader", "CustomSkinLoader.log");
        var expectLocalCosmetics = string.Equals(
            Environment.GetEnvironmentVariable("COPIMINE_SERVER_SMOKE_EXPECT_LOCAL_COSMETICS"),
            "1",
            StringComparison.Ordinal);
        var expectedGate = ReadExpectedGate();
        var javaPath = Path.Combine(instanceRoot, ".copimine", "java", "21.0.10", "bin", "java.exe");

        Directory.Exists(instanceRoot).Should().BeTrue();
        File.Exists(javaPath).Should().BeTrue();
        File.Exists(serverLog).Should().BeTrue();

        var serversEvidence = await new ServersDatService().EnsureCopiMineServerAsync(
            Path.Combine(instanceRoot, "servers.dat"),
            new ManagedServerRecord("CopiMine", serverHost, serverPort, AcceptTextures: true),
            CancellationToken.None);
        serversEvidence.CopiMineServerCount.Should().Be(1);
        var serversDatBytes = await File.ReadAllBytesAsync(Path.Combine(instanceRoot, "servers.dat"));
        serversDatBytes.Length.Should().BeGreaterThan(2);
        serversDatBytes[0].Should().Be(0x0A);

        var startingLength = new FileInfo(serverLog).Length;
        var clientAcknowledgementLog = ResolveClientAcknowledgementLog(instanceRoot, clientLog);
        var clientStartingLength = !File.Exists(clientAcknowledgementLog)
            ? 0L
            : new FileInfo(clientAcknowledgementLog).Length;
        // CustomSkinLoader can rewrite the file to the same byte length, so
        // a byte offset is not reliable evidence for this provider log.
        var cosmeticStartingLength = 0L;
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
                "CLIENT_GATE_REJECT");

            if (expectedGate == "ACCEPT")
            {
                result.Should().Contain("CLIENT_GATE_ACCEPT player=SmokePlayer");
                result.Should().NotContain("CLIENT_GATE_REJECT");
            }
            else
            {
                result.Should().Contain("CLIENT_GATE_REJECT");
                result.Should().NotContain("CLIENT_GATE_ACCEPT");
            }

            if (expectedGate == "ACCEPT")
            {
                var acknowledgement = await WaitForServerLogAsync(
                    clientAcknowledgementLog,
                    clientStartingLength,
                    "Server accepted protocol=3");
                acknowledgement.Should().Contain("Server accepted protocol=3");
            }

            if (expectedGate == "ACCEPT" && expectLocalCosmetics)
            {
                var cosmetics = await WaitForServerLogAsync(
                    cosmeticLog,
                    cosmeticStartingLength,
                    "SkinUrl: (LOCAL_LEGACY)");
                cosmetics.Should().Contain("CapeUrl: (LOCAL_LEGACY)");
            }
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

    private static string ResolveClientAcknowledgementLog(string instanceRoot, string? configuredLog)
    {
        var clientLog = Path.Combine(Path.GetFullPath(instanceRoot), "logs", "copimineclient.log");
        if (File.Exists(clientLog))
        {
            return clientLog;
        }

        return string.IsNullOrWhiteSpace(configuredLog)
            ? clientLog
            : Path.GetFullPath(configuredLog);
    }

    private static async Task<string> WaitForServerLogAsync(
        string path,
        long startingLength,
        params string[] markers)
    {
        var waitSeconds = int.TryParse(
            Environment.GetEnvironmentVariable("COPIMINE_SERVER_SMOKE_WAIT_SECONDS"),
            out var configuredWaitSeconds)
            ? Math.Clamp(configuredWaitSeconds, 30, 180)
            : 90;
        var deadline = DateTimeOffset.UtcNow.AddSeconds(waitSeconds);
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

    private static string ReadExpectedGate()
    {
        var configured = Environment.GetEnvironmentVariable("COPIMINE_SERVER_SMOKE_EXPECTED_GATE")
            ?.Trim()
            .ToUpperInvariant();
        return configured switch
        {
            null or "" or "ACCEPT" => "ACCEPT",
            "REJECT" => "REJECT",
            _ => throw new InvalidOperationException(
                "COPIMINE_SERVER_SMOKE_EXPECTED_GATE must be ACCEPT or REJECT.")
        };
    }

    private static string ReadTail(string path, long startingLength)
    {
        if (!File.Exists(path))
        {
            return string.Empty;
        }

        using var stream = new FileStream(path, FileMode.Open, FileAccess.Read, FileShare.ReadWrite | FileShare.Delete);
        // Minecraft and CustomSkinLoader may truncate/recreate their logs on
        // every launch. If that happened, read the new file from the start
        // instead of seeking past the newly written evidence.
        stream.Position = startingLength <= stream.Length ? startingLength : 0;
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
