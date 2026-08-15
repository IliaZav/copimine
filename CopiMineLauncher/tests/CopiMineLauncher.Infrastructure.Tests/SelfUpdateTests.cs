using System.Security.Cryptography;
using System.Text;
using CopiMineLauncher.Infrastructure.SelfUpdate;
using FluentAssertions;
using Xunit;

namespace CopiMineLauncher.Infrastructure.Tests;

public sealed class SelfUpdateTests
{
    [Fact]
    public async Task Check_without_update_does_not_create_or_mutate_self_update_state()
    {
        using var temp = new TemporaryDirectory();
        var backend = new FakeVelopackBackend(null);
        var service = CreateService(temp.Path, backend, "1.0.0");

        var result = await service.CheckAsync(CancellationToken.None);

        result.Kind.Should().Be(SelfUpdateStatusKind.NoUpdate);
        backend.CheckCalls.Should().Be(1);
        Directory.GetFiles(temp.Path, "*", SearchOption.AllDirectories).Should().BeEmpty();
    }

    [Fact]
    public async Task Check_returns_a_policy_verified_update_without_applying_it()
    {
        using var temp = new TemporaryDirectory();
        var backend = new FakeVelopackBackend(Candidate("1.0.1"));
        var service = CreateService(temp.Path, backend, "1.0.0");

        var result = await service.CheckAsync(CancellationToken.None);

        result.Kind.Should().Be(SelfUpdateStatusKind.UpdateAvailable);
        result.Update.Should().NotBeNull();
        result.Update!.Version.Should().Be("1.0.1");
        backend.ApplyCalls.Should().Be(0);
    }

    [Fact]
    public async Task Apply_rejects_corrupt_download_before_velopack_apply()
    {
        using var temp = new TemporaryDirectory();
        var update = Candidate("1.0.1");
        var backend = new FakeVelopackBackend(update, Encoding.UTF8.GetBytes("corrupt"));
        var service = CreateService(temp.Path, backend, "1.0.0");

        var result = await service.ApplyAsync(ToVerified(update), CancellationToken.None);

        result.Kind.Should().Be(SelfUpdateStatusKind.Failed);
        result.ErrorCode.Should().Be("SELF_UPDATE_PACKAGE_HASH_MISMATCH");
        backend.ApplyCalls.Should().Be(0);
        Directory.GetFiles(temp.Path, "*", SearchOption.AllDirectories).Should().BeEmpty();
    }

    [Fact]
    public async Task Interrupted_apply_is_recorded_and_does_not_promote_the_new_version()
    {
        using var temp = new TemporaryDirectory();
        var update = Candidate("1.0.1");
        var backend = new FakeVelopackBackend(update) { ThrowOnApply = true };
        var service = CreateService(temp.Path, backend, "1.0.0");

        var result = await service.ApplyAsync(ToVerified(update), CancellationToken.None);

        result.Kind.Should().Be(SelfUpdateStatusKind.Failed);
        result.ErrorCode.Should().Be("SELF_UPDATE_APPLY_FAILED");
        File.Exists(Path.Combine(temp.Path, "self-update-state.json")).Should().BeTrue();
        Directory.GetFiles(temp.Path, "*.part", SearchOption.AllDirectories).Should().BeEmpty();
    }

    [Fact]
    public async Task Previous_version_start_is_reported_as_rollback_and_clears_pending_state()
    {
        using var temp = new TemporaryDirectory();
        var update = Candidate("1.0.1");
        var backend = new FakeVelopackBackend(update);
        var service = CreateService(temp.Path, backend, "1.0.0");

        var applied = await service.ApplyAsync(ToVerified(update), CancellationToken.None);
        applied.Kind.Should().Be(SelfUpdateStatusKind.PendingRestart);

        var restartedWithPreviousVersion = CreateService(temp.Path, backend, "1.0.0");
        var recovery = await restartedWithPreviousVersion.RecoverAsync(CancellationToken.None);

        recovery.Kind.Should().Be(SelfUpdateStatusKind.Failed);
        recovery.ErrorCode.Should().Be("SELF_UPDATE_ROLLED_BACK");
        File.Exists(Path.Combine(temp.Path, "self-update-state.json")).Should().BeFalse();
    }

    [Fact]
    public async Task Feed_and_package_hosts_must_be_allowlisted_https_endpoints()
    {
        using var temp = new TemporaryDirectory();
        var candidate = Candidate("1.0.1") with { FeedUri = new Uri("https://evil.example/launcher") };
        var service = CreateService(temp.Path, new FakeVelopackBackend(candidate), "1.0.0");

        var result = await service.CheckAsync(CancellationToken.None);

        result.Kind.Should().Be(SelfUpdateStatusKind.Failed);
        result.ErrorCode.Should().Be("SELF_UPDATE_SOURCE_NOT_ALLOWED");
    }

    private static VelopackSelfUpdateService CreateService(string stateRoot, FakeVelopackBackend backend, string currentVersion) =>
        new(
            new Uri("https://copimine.ru/launcher"),
            backend,
            stateRoot,
            new SelfUpdatePolicy(new[] { "copimine.ru" }),
            () => currentVersion);

    private static VelopackUpdateCandidate Candidate(string version)
    {
        var bytes = Encoding.UTF8.GetBytes("package-" + version);
        return new(
            "CopiMineLauncher",
            "stable",
            version,
            new Uri("https://copimine.ru/launcher"),
            new Uri($"https://cdn.copimine.ru/launcher/CopiMineLauncher-{version}-full.nupkg"),
            $"CopiMineLauncher-{version}-full.nupkg",
            bytes.Length,
            Convert.ToHexString(SHA256.HashData(bytes)).ToLowerInvariant());
    }

    private static VerifiedSelfUpdate ToVerified(VelopackUpdateCandidate candidate) => new(
        candidate.Product,
        candidate.Channel,
        candidate.Version,
        candidate.FeedUri,
        candidate.PackageUri,
        candidate.PackageFileName,
        candidate.SizeBytes,
        candidate.Sha256);

    private sealed class FakeVelopackBackend(
        VelopackUpdateCandidate? candidate,
        byte[]? packageBytes = null) : IVelopackUpdateBackend
    {
        private readonly VelopackUpdateCandidate? candidate = candidate;
        private readonly byte[] packageBytes = packageBytes ?? Encoding.UTF8.GetBytes("package-" + candidate?.Version);

        public int CheckCalls { get; private set; }

        public int ApplyCalls { get; private set; }

        public bool ThrowOnApply { get; init; }

        public Task<VelopackUpdateCandidate?> CheckAsync(Uri feedUri, string channel, CancellationToken cancellationToken)
        {
            CheckCalls++;
            return Task.FromResult(candidate);
        }

        public async Task<DownloadedSelfUpdate> DownloadAsync(VerifiedSelfUpdate update, string destination, CancellationToken cancellationToken)
        {
            Directory.CreateDirectory(Path.GetDirectoryName(destination)!);
            await File.WriteAllBytesAsync(destination, packageBytes, cancellationToken);
            return new DownloadedSelfUpdate(destination);
        }

        public Task ApplyAsync(VerifiedSelfUpdate update, string packagePath, CancellationToken cancellationToken)
        {
            ApplyCalls++;
            if (ThrowOnApply)
            {
                throw new IOException("injected Velopack apply interruption");
            }

            return Task.CompletedTask;
        }
    }

    private sealed class TemporaryDirectory : IDisposable
    {
        public TemporaryDirectory() => Path = Directory.CreateTempSubdirectory("copimine-launcher-tests-").FullName;

        public string Path { get; }

        public void Dispose()
        {
            if (Directory.Exists(Path))
            {
                Directory.Delete(Path, recursive: true);
            }
        }
    }
}
