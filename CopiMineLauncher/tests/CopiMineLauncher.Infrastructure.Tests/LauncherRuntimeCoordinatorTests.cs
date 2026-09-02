using System.Diagnostics;
using System.Security.Cryptography;
using CopiMineLauncher.Core.Launch;
using CopiMineLauncher.Core.Manifest;
using CopiMineLauncher.Core.Updates;
using CopiMineLauncher.Infrastructure.Launch;
using CopiMineLauncher.Infrastructure.Manifest;
using CopiMineLauncher.Infrastructure.Provisioning;
using CopiMineLauncher.Infrastructure.Runtime;
using CopiMineLauncher.Infrastructure.Servers;
using CopiMineLauncher.Infrastructure.Updates;
using FluentAssertions;
using Xunit;

namespace CopiMineLauncher.Infrastructure.Tests;

public sealed class LauncherRuntimeCoordinatorTests
{
    [Fact]
    public async Task Repair_verifies_then_reconciles_provisions_and_updates_servers_without_launching()
    {
        using var temp = new TemporaryDirectory();
        var events = new List<string>();
        var services = FixtureServices(events, ReconciliationStatus.Updated);
        var coordinator = new LauncherRuntimeCoordinator(
            services.ManifestClient,
            services.Minecraft,
            services.Java,
            services.ReconcilerFactory,
            services.Servers,
            services.Launch);

        var result = await coordinator.RepairAsync(
            new LauncherOperationRequest(temp.Path, "Steve"),
            CancellationToken.None);

        result.Succeeded.Should().BeTrue(result.Diagnostic);
        result.Launch.Should().BeNull();
        services.Launch.Calls.Should().Be(0);
        events.Should().ContainInOrder("manifest", "reconcile", "java", "minecraft", "servers");
        File.Exists(Path.Combine(temp.Path, "servers.dat")).Should().BeFalse();
    }

    [Fact]
    public async Task Play_launches_only_after_all_preparation_steps_succeed()
    {
        using var temp = new TemporaryDirectory();
        var events = new List<string>();
        var services = FixtureServices(events, ReconciliationStatus.Updated);
        var coordinator = new LauncherRuntimeCoordinator(
            services.ManifestClient,
            services.Minecraft,
            services.Java,
            services.ReconcilerFactory,
            services.Servers,
            services.Launch);

        var result = await coordinator.PlayAsync(
            new LauncherOperationRequest(temp.Path, "Alex", MaximumRamMb: 3072),
            CancellationToken.None);

        result.Succeeded.Should().BeTrue(result.Diagnostic);
        result.Launch.Should().NotBeNull();
        services.Launch.Calls.Should().Be(1);
        events.Should().ContainInOrder("manifest", "reconcile", "java", "minecraft", "servers", "launch");
    }

    [Fact]
    public async Task Failed_reconciliation_stops_before_java_server_update_and_launch()
    {
        using var temp = new TemporaryDirectory();
        var events = new List<string>();
        var services = FixtureServices(events, ReconciliationStatus.Failed);
        var coordinator = new LauncherRuntimeCoordinator(
            services.ManifestClient,
            services.Minecraft,
            services.Java,
            services.ReconcilerFactory,
            services.Servers,
            services.Launch);

        var result = await coordinator.PlayAsync(
            new LauncherOperationRequest(temp.Path, "Steve"),
            CancellationToken.None);

        result.Succeeded.Should().BeFalse();
        result.ErrorCode.Should().Be("RECONCILIATION_FAILED");
        services.Java.Calls.Should().Be(0);
        services.Minecraft.Calls.Should().Be(0);
        services.Servers.Calls.Should().Be(0);
        services.Launch.Calls.Should().Be(0);
        events.Should().ContainInOrder("manifest", "reconcile");
    }

    [Fact]
    public async Task Invalid_player_name_is_rejected_before_manifest_fetch_or_filesystem_mutation()
    {
        using var temp = new TemporaryDirectory();
        var services = FixtureServices(new List<string>(), ReconciliationStatus.Updated);
        var coordinator = new LauncherRuntimeCoordinator(
            services.ManifestClient,
            services.Minecraft,
            services.Java,
            services.ReconcilerFactory,
            services.Servers,
            services.Launch);
        var instance = Path.Combine(temp.Path, "new-instance");

        var result = await coordinator.RepairAsync(
            new LauncherOperationRequest(instance, "bad name"),
            CancellationToken.None);

        result.Succeeded.Should().BeFalse();
        result.ErrorCode.Should().Be("PLAYER_NAME_INVALID");
        services.ManifestClient.Calls.Should().Be(0);
        Directory.Exists(instance).Should().BeFalse();
    }

    [Fact]
    public async Task Manifest_failure_is_reported_without_creating_or_mutating_instance()
    {
        using var temp = new TemporaryDirectory();
        var services = FixtureServices(new List<string>(), ReconciliationStatus.Updated);
        services.ManifestClient.Exception = new ManifestFetchException("SIGNATURE_INVALID", "bad signature");
        var coordinator = new LauncherRuntimeCoordinator(
            services.ManifestClient,
            services.Minecraft,
            services.Java,
            services.ReconcilerFactory,
            services.Servers,
            services.Launch);
        var instance = Path.Combine(temp.Path, "new-instance");

        var result = await coordinator.RepairAsync(
            new LauncherOperationRequest(instance, "Steve"),
            CancellationToken.None);

        result.Succeeded.Should().BeFalse();
        result.ErrorCode.Should().Be("SIGNATURE_INVALID");
        Directory.Exists(instance).Should().BeFalse();
        services.Minecraft.Calls.Should().Be(0);
        services.Java.Calls.Should().Be(0);
    }

    [Fact]
    public async Task Minecraft_start_failure_keeps_the_structured_log_report()
    {
        using var temp = new TemporaryDirectory();
        var services = FixtureServices(new List<string>(), ReconciliationStatus.Updated);
        var report = MinecraftLaunchFailureParser.Parse(
            "[main/ERROR] Could not execute entrypoint stage 'main' due to errors, provided by 'better-leaves'!",
            Path.Combine(temp.Path, "logs", "launcher-process.log"),
            new[] { "BetterLeaves-1.4.0.jar" });
        services.Launch.Exception = new MinecraftLaunchException(
            "MINECRAFT_START_FAILED",
            report,
            "Minecraft start failed",
            new InvalidOperationException("fixture"));
        var coordinator = new LauncherRuntimeCoordinator(
            services.ManifestClient,
            services.Minecraft,
            services.Java,
            services.ReconcilerFactory,
            services.Servers,
            services.Launch);

        var result = await coordinator.PlayAsync(
            new LauncherOperationRequest(temp.Path, "Steve"),
            CancellationToken.None);

        result.Succeeded.Should().BeFalse();
        result.ErrorCode.Should().Be("MINECRAFT_START_FAILED");
        result.LaunchFailure.Should().BeSameAs(report);
    }

    [Fact]
    public async Task Play_stops_before_launch_when_a_managed_file_changes_after_reconciliation()
    {
        using var temp = new TemporaryDirectory();
        var events = new List<string>();
        var services = FixtureServices(events, ReconciliationStatus.Updated);
        var coordinator = new LauncherRuntimeCoordinator(
            services.ManifestClient,
            services.Minecraft,
            services.Java,
            services.ReconcilerFactory,
            services.Servers,
            services.Launch,
            integrityVerifier: new FakeIntegrityVerifier(new(
                false,
                0,
                "MANAGED_FILE_HASH_MISMATCH",
                "Контрольная сумма mods/CopiMineClient.jar не совпадает с manifest."), events));

        var result = await coordinator.PlayAsync(
            new LauncherOperationRequest(temp.Path, "Steve"),
            CancellationToken.None);

        result.Succeeded.Should().BeFalse();
        result.ErrorCode.Should().Be("MANAGED_FILE_HASH_MISMATCH");
        services.Java.Calls.Should().Be(0);
        services.Minecraft.Calls.Should().Be(0);
        services.Servers.Calls.Should().Be(0);
        services.Launch.Calls.Should().Be(0);
        events.Should().ContainInOrder("manifest", "reconcile", "integrity");
    }

    private static ServicesFixture FixtureServices(List<string> events, ReconciliationStatus reconciliationStatus)
    {
        var verified = CreateVerifiedManifest();
        var manifest = new FakeManifestClient(verified, events);
        var reconciler = new FakeReconciler(new ReconciliationResult(
            reconciliationStatus,
            Array.Empty<UpdateOperation>(),
            reconciliationStatus == ReconciliationStatus.Failed ? "RECONCILIATION_FAILED" : null,
            reconciliationStatus == ReconciliationStatus.Failed ? "fixture failure" : null));
        var factory = new FakeReconcilerFactory(reconciler, events);
        return new(
            manifest,
            new FakeMinecraftProvisioner(events),
            new FakeJavaProvisioner(events),
            factory,
            new FakeServersService(events),
            new FakeLaunchService(events));
    }

    private static VerifiedInstanceManifest CreateVerifiedManifest()
    {
        var document = new InstanceManifestDocument(
            2,
            "stable",
            "2026.08.15.1",
            DateTimeOffset.Parse("2026-08-15T10:00:00Z"),
            "1.0.0",
            new InstanceMinecraft("1.21.1", "0.19.3", 21),
            new InstanceManifestServer("CopiMine", "mc.copimine.ru", true),
            Array.Empty<InstanceManifestFile>(),
            Array.Empty<InstanceConfigPolicy>(),
            "https://copimine.ru/news.html",
            17,
            new InstanceJavaRuntime("Adoptium", "temurin-21", "windows-x64", "21.0.10", "https://copimine.ru/java.zip", 1, new string('c', 64)),
            "launcher-v1",
            new InstanceMinecraftRuntime("https://copimine.ru/launcher/files/dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd", 1, new string('d', 64)));
        var internalManifest = InstanceManifestAdapter.ToLauncherManifest(document, "launcher-v1");
        return new(
            document,
            internalManifest,
            new ManifestSignature("Ed25519", "launcher-v1", Convert.ToBase64String(new byte[64])),
            Array.Empty<byte>(),
            Convert.ToHexString(SHA256.HashData(Array.Empty<byte>())).ToLowerInvariant(),
            DateTimeOffset.UtcNow);
    }

    private sealed record ServicesFixture(
        FakeManifestClient ManifestClient,
        FakeMinecraftProvisioner Minecraft,
        FakeJavaProvisioner Java,
        FakeReconcilerFactory ReconcilerFactory,
        FakeServersService Servers,
        FakeLaunchService Launch);

    private sealed class FakeManifestClient(VerifiedInstanceManifest verified, List<string> events) : IManifestClient
    {
        public int Calls { get; private set; }
        public Exception? Exception { get; set; }

        public Task<VerifiedInstanceManifest> FetchVerifiedAsync(Uri manifestUri, CancellationToken cancellationToken)
        {
            Calls++;
            events.Add("manifest");
            return Exception is null ? Task.FromResult(verified) : Task.FromException<VerifiedInstanceManifest>(Exception);
        }
    }

    private sealed class FakeReconcilerFactory(ITransactionalReconciler reconciler, List<string> events) : ITransactionalReconcilerFactory
    {
        public ITransactionalReconciler Create(string instanceRoot, VerifiedInstanceManifest manifest)
        {
            events.Add("reconcile");
            return reconciler;
        }
    }

    private sealed class FakeReconciler(ReconciliationResult result) : ITransactionalReconciler
    {
        public Task<ReconciliationResult> ReconcileAsync(LauncherManifest manifest, CancellationToken cancellationToken)
        {
            return Task.FromResult(result);
        }
    }

    private sealed class FakeJavaProvisioner(List<string> events) : IJavaProvisioner
    {
        public int Calls { get; private set; }
        public Task<JavaProvisioningResult> EnsureJava21Async(string instanceRoot, LauncherManifest manifest, CancellationToken cancellationToken)
        {
            Calls++;
            events.Add("java");
            return Task.FromResult(new JavaProvisioningResult("java.exe", "openjdk version \"21\"", false));
        }
    }

    private sealed class FakeMinecraftProvisioner(List<string> events) : IMinecraftProvisioner
    {
        public int Calls { get; private set; }
        public Task<MinecraftProvisioningResult> EnsureMinecraftFabricAsync(string instanceRoot, string minecraftVersion, string fabricLoaderVersion, CancellationToken cancellationToken)
        {
            Calls++;
            events.Add("minecraft");
            return Task.FromResult(new MinecraftProvisioningResult(minecraftVersion, fabricLoaderVersion, "fabric-loader", instanceRoot));
        }
    }

    private sealed class FakeServersService(List<string> events) : IServersDatService
    {
        public int Calls { get; private set; }
        public Task<ServersDatEvidence> EnsureCopiMineServerAsync(string serversDatPath, ManagedServerRecord record, CancellationToken cancellationToken = default)
        {
            Calls++;
            events.Add("servers");
            return Task.FromResult(new ServersDatEvidence(false, 0, 1, serversDatPath));
        }
    }

    private sealed class FakeLaunchService(List<string> events) : IMinecraftLaunchService
    {
        public int Calls { get; private set; }
        public Exception? Exception { get; set; }

        public Task<LaunchEvidence> LaunchAsync(LaunchRequest request, CancellationToken cancellationToken)
        {
            Calls++;
            events.Add("launch");
            if (Exception is not null)
            {
                return Task.FromException<LaunchEvidence>(Exception);
            }

            return Task.FromResult(new LaunchEvidence(
                Process.GetCurrentProcess(),
                DateTimeOffset.UtcNow,
                request.FabricVersionName,
                request.InstanceRoot,
                request.JavaExecutablePath ?? "java.exe"));
        }
    }

    private sealed class FakeIntegrityVerifier(InstanceIntegrityResult result, List<string>? events = null) : IInstanceIntegrityVerifier
    {
        public Task<InstanceIntegrityResult> VerifyAsync(
            string instanceRoot,
            LauncherManifest manifest,
            CancellationToken cancellationToken)
        {
            events?.Add("integrity");
            return Task.FromResult(result);
        }
    }

    private sealed class TemporaryDirectory : IDisposable
    {
        public TemporaryDirectory() => Path = Directory.CreateTempSubdirectory("copimine-launcher-tests-").FullName;
        public string Path { get; }
        public void Dispose()
        {
            if (Directory.Exists(Path)) Directory.Delete(Path, recursive: true);
        }
    }
}
