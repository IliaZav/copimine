using System.Security.Cryptography;
using CopiMineLauncher.Core.Manifest;
using CopiMineLauncher.Infrastructure.Runtime;
using FluentAssertions;
using Xunit;

namespace CopiMineLauncher.Infrastructure.Tests;

public sealed class ManifestInstanceIntegrityVerifierTests
{
    [Fact]
    public async Task Verification_accepts_a_managed_file_when_size_and_sha256_match()
    {
        using var temp = new TemporaryDirectory();
        var bytes = new byte[] { 1, 2, 3, 4 };
        var relativePath = "mods/CopiMineClient.jar";
        var fullPath = Path.Combine(temp.Path, relativePath.Replace('/', Path.DirectorySeparatorChar));
        Directory.CreateDirectory(Path.GetDirectoryName(fullPath)!);
        await File.WriteAllBytesAsync(fullPath, bytes);
        var manifest = ManifestFor(relativePath, bytes);

        var result = await new ManifestInstanceIntegrityVerifier().VerifyAsync(
            temp.Path,
            manifest,
            CancellationToken.None);

        result.IsValid.Should().BeTrue();
        result.VerifiedFileCount.Should().Be(1);
        result.ErrorCode.Should().BeNull();
    }

    [Fact]
    public async Task Verification_rejects_a_managed_file_when_the_local_sha256_is_stale()
    {
        using var temp = new TemporaryDirectory();
        var relativePath = "mods/CopiMineClient.jar";
        var fullPath = Path.Combine(temp.Path, relativePath.Replace('/', Path.DirectorySeparatorChar));
        Directory.CreateDirectory(Path.GetDirectoryName(fullPath)!);
        await File.WriteAllBytesAsync(fullPath, new byte[] { 9, 9, 9, 9 });
        var manifest = ManifestFor(relativePath, new byte[] { 1, 2, 3, 4 });

        var result = await new ManifestInstanceIntegrityVerifier().VerifyAsync(
            temp.Path,
            manifest,
            CancellationToken.None);

        result.IsValid.Should().BeFalse();
        result.ErrorCode.Should().Be("MANAGED_FILE_HASH_MISMATCH");
        result.Diagnostic.Should().Contain("mods/CopiMineClient.jar");
    }

    [Fact]
    public async Task Verification_rejects_a_required_managed_file_that_was_deleted_after_reconciliation()
    {
        using var temp = new TemporaryDirectory();
        var bytes = new byte[] { 1, 2, 3, 4 };
        var relativePath = "mods/CopiMineClient.jar";
        var manifest = ManifestFor(relativePath, bytes);

        var result = await new ManifestInstanceIntegrityVerifier().VerifyAsync(
            temp.Path,
            manifest,
            CancellationToken.None);

        result.IsValid.Should().BeFalse();
        result.ErrorCode.Should().Be("MANAGED_FILE_MISSING");
    }

    private static LauncherManifest ManifestFor(string relativePath, byte[] expectedBytes) => new(
        1,
        "CopiMineLauncher",
        "stable",
        1,
        "1.0.6",
        "1.21.1",
        "0.19.3",
        DateTimeOffset.UtcNow,
        null,
        null,
        new[]
        {
            new ManifestFileEntry(
                "copimine-client",
                relativePath,
                "mod",
                "1.0.0",
                "https://copimine.ru/launcher/files/client",
                expectedBytes.LongLength,
                Convert.ToHexString(SHA256.HashData(expectedBytes)).ToLowerInvariant(),
                true,
                "managed")
        },
        new ManifestServer("mc.copimine.ru", 25565, "CopiMine"),
        "launcher-v1");

    private sealed class TemporaryDirectory : IDisposable
    {
        public TemporaryDirectory() => Path = Directory.CreateTempSubdirectory("copimine-integrity-tests-").FullName;
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
