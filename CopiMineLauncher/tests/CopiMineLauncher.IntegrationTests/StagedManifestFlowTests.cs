using System.Diagnostics;
using System.IO.Compression;
using System.Net;
using CopiMineLauncher.Core.Manifest;
using CopiMineLauncher.Infrastructure.Launch;
using CopiMineLauncher.Infrastructure.Manifest;
using CopiMineLauncher.Infrastructure.Provisioning;
using CopiMineLauncher.Infrastructure.Runtime;
using CopiMineLauncher.Infrastructure.Servers;
using CopiMineLauncher.Infrastructure.Updates;
using FluentAssertions;
using Xunit;

namespace CopiMineLauncher.IntegrationTests;

public sealed class StagedManifestFlowTests
{
    [StagedReleaseFact]
    public async Task Published_staging_manifest_reconciles_real_mod_artifacts_java_and_servers_dat()
    {
        var releaseRoot = Environment.GetEnvironmentVariable("COPIMINE_STAGED_MANIFEST_ROOT");
        releaseRoot.Should().NotBeNullOrWhiteSpace();

        releaseRoot = Path.GetFullPath(releaseRoot);
        if (!File.Exists(Path.Combine(releaseRoot, "instance-manifest.json")))
        {
            throw new FileNotFoundException("Staged instance-manifest.json is missing", releaseRoot);
        }

        using var temp = new TemporaryDirectory();
        using var httpClient = new HttpClient(new StagedReleaseHandler(releaseRoot));
        var manifestClient = new SignedInstanceManifestClient(
            httpClient,
            new Ed25519ManifestVerifier(),
            PinnedManifestKey.PublicKey,
            PinnedManifestKey.KeyId);
        var downloads = new ResumableDownloadManager(httpClient);
        var coordinator = new LauncherRuntimeCoordinator(
            manifestClient,
            new FixtureMinecraftProvisioner(),
            new JavaProvisioner(downloads),
            new TransactionalReconcilerFactory(downloads),
            new ServersDatService(),
            new FixtureLaunchService());

        var first = await coordinator.RepairAsync(
            new LauncherOperationRequest(temp.Path, "StagingPlayer"),
            CancellationToken.None);

        first.Succeeded.Should().BeTrue(first.Diagnostic);
        first.VerifiedManifest.Should().NotBeNull();
        first.VerifiedManifest!.ManifestSha256.Should().MatchRegex("^[0-9a-f]{64}$");
        first.Java!.JavaExecutablePath.ToLowerInvariant().Should().EndWith("java.exe");
        first.Minecraft!.FabricLoaderVersion.Should().Be("0.19.3");
        first.ServersDat!.Changed.Should().BeTrue();

        var userMod = Path.Combine(temp.Path, "mods", "user-extra.jar");
        Directory.CreateDirectory(Path.GetDirectoryName(userMod)!);
        var userBytes = CreateValidModArchive();
        await File.WriteAllBytesAsync(userMod, userBytes);

        var second = await coordinator.RepairAsync(
            new LauncherOperationRequest(temp.Path, "StagingPlayer"),
            CancellationToken.None);

        second.Succeeded.Should().BeTrue(second.Diagnostic);
        var preservedUserBytes = await File.ReadAllBytesAsync(userMod);
        preservedUserBytes.Should().Equal(userBytes);
        File.Exists(Path.Combine(temp.Path, ".copimine", "managed-state.json")).Should().BeTrue();
        File.Exists(Path.Combine(temp.Path, "servers.dat")).Should().BeTrue();
    }

    private static byte[] CreateValidModArchive()
    {
        using var stream = new MemoryStream();
        using (var archive = new ZipArchive(stream, ZipArchiveMode.Create, leaveOpen: true))
        {
            archive.CreateEntry("fabric.mod.json");
        }

        return stream.ToArray();
    }

    private sealed class StagedReleaseFactAttribute : FactAttribute
    {
        public StagedReleaseFactAttribute()
        {
            if (string.IsNullOrWhiteSpace(Environment.GetEnvironmentVariable("COPIMINE_STAGED_MANIFEST_ROOT")))
            {
                Skip = "Set COPIMINE_STAGED_MANIFEST_ROOT to run the local staged release flow.";
            }
        }
    }

    private sealed class StagedReleaseHandler(string releaseRoot) : HttpMessageHandler
    {
        protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken)
        {
            var relative = request.RequestUri!.AbsolutePath.TrimStart('/').Replace('\\', '/');
            var path = relative switch
            {
                "launcher/stable/instance-manifest.json" => Path.Combine(releaseRoot, "instance-manifest.json"),
                "launcher/stable/instance-manifest.sig" => Path.Combine(releaseRoot, "instance-manifest.sig"),
                _ when relative.StartsWith("launcher/files/", StringComparison.Ordinal)
                    => Path.Combine(Path.GetFullPath(Path.Combine(releaseRoot, "..", "files")), Path.GetFileName(relative)),
                _ => throw new InvalidOperationException($"Unexpected staged request: {request.RequestUri}")
            };

            if (!File.Exists(path))
            {
                return Task.FromResult(new HttpResponseMessage(HttpStatusCode.NotFound));
            }

            var stream = File.OpenRead(path);
            return Task.FromResult(new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new StreamContent(stream)
            });
        }
    }

    private sealed class FixtureMinecraftProvisioner : IMinecraftProvisioner
    {
        public Task<MinecraftProvisioningResult> EnsureMinecraftFabricAsync(string instanceRoot, string minecraftVersion, string fabricLoaderVersion, CancellationToken cancellationToken) =>
            Task.FromResult(new MinecraftProvisioningResult(minecraftVersion, fabricLoaderVersion, "fabric-loader-0.19.3-1.21.1", instanceRoot));
    }

    private sealed class FixtureLaunchService : IMinecraftLaunchService
    {
        public Task<LaunchEvidence> LaunchAsync(LaunchRequest request, CancellationToken cancellationToken) =>
            Task.FromResult(new LaunchEvidence(Process.GetCurrentProcess(), DateTimeOffset.UtcNow, request.FabricVersionName, request.InstanceRoot, request.JavaExecutablePath ?? "java.exe"));
    }

    private sealed class TemporaryDirectory : IDisposable
    {
        public TemporaryDirectory() => Path = Directory.CreateTempSubdirectory("copimine-staged-flow-").FullName;
        public string Path { get; }
        public void Dispose()
        {
            if (Directory.Exists(Path)) Directory.Delete(Path, recursive: true);
        }
    }
}
