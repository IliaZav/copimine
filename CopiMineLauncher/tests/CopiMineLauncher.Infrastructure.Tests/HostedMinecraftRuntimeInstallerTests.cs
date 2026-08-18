using System.IO.Compression;
using System.Security.Cryptography;
using CopiMineLauncher.Core.Manifest;
using CopiMineLauncher.Infrastructure.Provisioning;
using CopiMineLauncher.Infrastructure.Updates;
using FluentAssertions;
using Xunit;

namespace CopiMineLauncher.Infrastructure.Tests;

public sealed class HostedMinecraftRuntimeInstallerTests
{
    [Fact]
    public async Task Downloads_verified_runtime_from_manifest_url_and_installs_it_transactionally()
    {
        using var temp = new TempDirectory();
        var bootstrap = Path.Combine(temp.Path, "bootstrap");
        var sourceArchive = await CreateBaselineArchiveAsync(temp.Path);
        var sourceBytes = await File.ReadAllBytesAsync(sourceArchive);
        var digest = Convert.ToHexString(SHA256.HashData(sourceBytes)).ToLowerInvariant();
        var runtime = new MinecraftRuntimeMetadata(
            $"https://copimine.ru/launcher/files/{digest}",
            sourceBytes.LongLength,
            digest);
        var downloads = new CopyingDownloadManager(sourceArchive);
        var installer = new HostedMinecraftRuntimeInstaller(
            new OfflineMinecraftBaseline(bootstrap),
            downloads);

        var result = await installer.EnsureAsync(
            Path.Combine(temp.Path, "instance"),
            "1.21.1",
            "0.19.3",
            runtime,
            CancellationToken.None);

        result.Applied.Should().BeTrue();
        downloads.Source.Should().Be(new Uri(runtime.Url));
        downloads.ExpectedSize.Should().Be(sourceBytes.LongLength);
        downloads.ExpectedSha256.Should().Be(digest);
        File.Exists(Path.Combine(temp.Path, "instance", "assets", "indexes", "1.21.1.json")).Should().BeTrue();
        File.Exists(Path.Combine(temp.Path, "instance", "versions", "fabric-loader-0.19.3-1.21.1", "fabric-loader-0.19.3-1.21.1.json")).Should().BeTrue();
    }

    private static async Task<string> CreateBaselineArchiveAsync(string root)
    {
        var content = Path.Combine(root, "content");
        Directory.CreateDirectory(Path.Combine(content, "assets", "indexes"));
        Directory.CreateDirectory(Path.Combine(content, "libraries", "fixture"));
        Directory.CreateDirectory(Path.Combine(content, "versions", "1.21.1"));
        Directory.CreateDirectory(Path.Combine(content, "versions", "fabric-loader-0.19.3-1.21.1"));
        await File.WriteAllTextAsync(Path.Combine(content, "assets", "indexes", "1.21.1.json"), "assets");
        await File.WriteAllTextAsync(Path.Combine(content, "libraries", "fixture", "fixture.jar"), "library");
        await File.WriteAllTextAsync(Path.Combine(content, "versions", "1.21.1", "1.21.1.json"), "vanilla");
        await File.WriteAllTextAsync(Path.Combine(content, "versions", "fabric-loader-0.19.3-1.21.1", "fabric-loader-0.19.3-1.21.1.json"), "fabric");
        var archive = Path.Combine(root, "runtime.zip");
        ZipFile.CreateFromDirectory(content, archive, CompressionLevel.Fastest, includeBaseDirectory: false);
        return archive;
    }

    private sealed class CopyingDownloadManager(string sourcePath) : IResumableDownloadManager
    {
        public Uri? Source { get; private set; }
        public long ExpectedSize { get; private set; }
        public string? ExpectedSha256 { get; private set; }

        public Task<string> DownloadAsync(
            Uri source,
            string destination,
            long expectedSize,
            string expectedSha256,
            CancellationToken cancellationToken)
        {
            Source = source;
            ExpectedSize = expectedSize;
            ExpectedSha256 = expectedSha256;
            Directory.CreateDirectory(Path.GetDirectoryName(destination)!);
            File.Copy(sourcePath, destination, overwrite: true);
            return Task.FromResult(destination);
        }
    }

    private sealed class TempDirectory : IDisposable
    {
        public TempDirectory()
        {
            Path = System.IO.Path.Combine(System.IO.Path.GetTempPath(), "copimine-hosted-runtime-" + Guid.NewGuid().ToString("N"));
            Directory.CreateDirectory(Path);
        }

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
