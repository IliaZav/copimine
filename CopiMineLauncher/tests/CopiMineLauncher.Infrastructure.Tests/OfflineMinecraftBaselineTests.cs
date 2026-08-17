using System.IO.Compression;
using System.Security.Cryptography;
using System.Text.Json;
using CopiMineLauncher.Infrastructure.Provisioning;
using FluentAssertions;
using Xunit;

namespace CopiMineLauncher.Infrastructure.Tests;

public sealed class OfflineMinecraftBaselineTests
{
    [Fact]
    public async Task Installs_verified_baseline_once_and_preserves_existing_user_options()
    {
        using var temp = new TempDirectory();
        var bootstrap = Path.Combine(temp.Path, "bootstrap");
        var instance = Path.Combine(temp.Path, "instance");
        await CreateBaselineAsync(bootstrap);
        Directory.CreateDirectory(instance);
        await File.WriteAllTextAsync(Path.Combine(instance, "options.txt"), "user-option=true\n");

        var installer = new OfflineMinecraftBaseline(bootstrap);
        var first = await installer.EnsureAsync(instance, "1.21.1", "0.19.3", CancellationToken.None);
        var second = await installer.EnsureAsync(instance, "1.21.1", "0.19.3", CancellationToken.None);

        first.Available.Should().BeTrue();
        first.Applied.Should().BeTrue();
        second.AlreadyPresent.Should().BeTrue();
        File.Exists(Path.Combine(instance, "assets", "indexes", "1.21.1.json")).Should().BeTrue();
        File.Exists(Path.Combine(instance, "libraries", "fixture", "fixture.jar")).Should().BeTrue();
        File.Exists(Path.Combine(instance, "versions", "1.21.1", "1.21.1.json")).Should().BeTrue();
        File.Exists(Path.Combine(instance, "versions", "fabric-loader-0.19.3-1.21.1", "fabric-loader-0.19.3-1.21.1.json")).Should().BeTrue();
        (await File.ReadAllTextAsync(Path.Combine(instance, "options.txt"))).Should().Be("user-option=true\n");
        Directory.Exists(Path.Combine(instance, ".copimine", "offline-baseline-backups")).Should().BeTrue();
    }

    [Fact]
    public async Task Rejects_corrupt_baseline_before_touching_instance()
    {
        using var temp = new TempDirectory();
        var bootstrap = Path.Combine(temp.Path, "bootstrap");
        var instance = Path.Combine(temp.Path, "instance");
        await CreateBaselineAsync(bootstrap);
        Directory.CreateDirectory(instance);
        await File.WriteAllTextAsync(Path.Combine(instance, "sentinel.txt"), "keep");
        await File.AppendAllTextAsync(Path.Combine(bootstrap, "offline-minecraft-baseline.zip"), "corrupt");

        var installer = new OfflineMinecraftBaseline(bootstrap);
        var action = () => installer.EnsureAsync(instance, "1.21.1", "0.19.3", CancellationToken.None);

        await action.Should().ThrowAsync<OfflineMinecraftBaselineException>()
            .Where(exception => exception.Code == "OFFLINE_BASELINE_SIZE_MISMATCH");
        File.Exists(Path.Combine(instance, "sentinel.txt")).Should().BeTrue();
        File.Exists(Path.Combine(instance, ".copimine", "offline-baseline.json")).Should().BeFalse();
    }

    [Fact]
    public async Task Rejects_archive_traversal()
    {
        using var temp = new TempDirectory();
        var bootstrap = Path.Combine(temp.Path, "bootstrap");
        Directory.CreateDirectory(bootstrap);
        var archivePath = Path.Combine(bootstrap, "offline-minecraft-baseline.zip");
        using (var archive = ZipFile.Open(archivePath, ZipArchiveMode.Create))
        {
            await using var stream = archive.CreateEntry("../escape.txt").Open();
            await stream.WriteAsync("bad"u8.ToArray());
        }

        var bytes = await File.ReadAllBytesAsync(archivePath);
        var metadata = new OfflineMinecraftBaselineMetadata(
            1,
            "1.21.1",
            "0.19.3",
            "offline-minecraft-baseline.zip",
            bytes.LongLength,
            Convert.ToHexString(SHA256.HashData(bytes)).ToLowerInvariant());
        await File.WriteAllTextAsync(
            Path.Combine(bootstrap, "offline-minecraft-baseline.json"),
            JsonSerializer.Serialize(metadata, new JsonSerializerOptions(JsonSerializerDefaults.Web)));

        var installer = new OfflineMinecraftBaseline(bootstrap);
        var action = () => installer.EnsureAsync(Path.Combine(temp.Path, "instance"), "1.21.1", "0.19.3", CancellationToken.None);

        await action.Should().ThrowAsync<OfflineMinecraftBaselineException>()
            .Where(exception => exception.Code == "OFFLINE_BASELINE_PATH_INVALID");
        File.Exists(Path.Combine(temp.Path, "escape.txt")).Should().BeFalse();
    }

    [Fact]
    public async Task Accepts_windows_style_directory_entries_in_offline_archive()
    {
        using var temp = new TempDirectory();
        var bootstrap = Path.Combine(temp.Path, "bootstrap");
        var instance = Path.Combine(temp.Path, "instance");
        await CreateBaselineAsync(bootstrap);

        using (var archive = ZipFile.Open(
                   Path.Combine(bootstrap, "offline-minecraft-baseline.zip"),
                   ZipArchiveMode.Update))
        {
            archive.CreateEntry("resourcepacks\\");
        }

        await RefreshMetadataAsync(bootstrap);

        var result = await new OfflineMinecraftBaseline(bootstrap)
            .EnsureAsync(instance, "1.21.1", "0.19.3", CancellationToken.None);

        result.Applied.Should().BeTrue();
        Directory.Exists(Path.Combine(instance, "versions", "1.21.1")).Should().BeTrue();
    }

    [Fact]
    public async Task Minecraft_provisioner_skips_network_install_when_profiles_are_seeded()
    {
        using var temp = new TempDirectory();
        var versions = Path.Combine(temp.Path, "versions");
        Directory.CreateDirectory(Path.Combine(versions, "1.21.1"));
        Directory.CreateDirectory(Path.Combine(versions, "fabric-loader-0.19.3-1.21.1"));
        Directory.CreateDirectory(Path.Combine(temp.Path, "assets", "indexes"));
        Directory.CreateDirectory(Path.Combine(temp.Path, "libraries"));
        await File.WriteAllTextAsync(Path.Combine(versions, "1.21.1", "1.21.1.json"), "{}");
        await File.WriteAllTextAsync(Path.Combine(versions, "fabric-loader-0.19.3-1.21.1", "fabric-loader-0.19.3-1.21.1.json"), "{}");
        await File.WriteAllTextAsync(Path.Combine(temp.Path, "assets", "indexes", "17.json"), "{}");

        var profileInstaller = new FakeProfileInstaller();
        var fabricProvisioner = new FakeFabricProvisioner();
        var provisioner = new MinecraftProvisioner(new HttpClient(), fabricProvisioner, profileInstaller);

        var result = await provisioner.EnsureMinecraftFabricAsync(temp.Path, "1.21.1", "0.19.3", CancellationToken.None);

        result.FabricVersionName.Should().Be("fabric-loader-0.19.3-1.21.1");
        profileInstaller.InstalledVersions.Should().BeEmpty();
        fabricProvisioner.Calls.Should().Be(0);
    }

    private static async Task CreateBaselineAsync(string bootstrap)
    {
        var content = Path.Combine(bootstrap, "content");
        Directory.CreateDirectory(Path.Combine(content, "assets", "indexes"));
        Directory.CreateDirectory(Path.Combine(content, "libraries", "fixture"));
        Directory.CreateDirectory(Path.Combine(content, "versions", "1.21.1"));
        Directory.CreateDirectory(Path.Combine(content, "versions", "fabric-loader-0.19.3-1.21.1"));
        await File.WriteAllTextAsync(Path.Combine(content, "assets", "indexes", "1.21.1.json"), "assets");
        await File.WriteAllTextAsync(Path.Combine(content, "libraries", "fixture", "fixture.jar"), "library");
        await File.WriteAllTextAsync(Path.Combine(content, "versions", "1.21.1", "1.21.1.json"), "vanilla");
        await File.WriteAllTextAsync(Path.Combine(content, "versions", "fabric-loader-0.19.3-1.21.1", "fabric-loader-0.19.3-1.21.1.json"), "fabric");
        await File.WriteAllTextAsync(Path.Combine(content, "options.txt"), "seed-option=true\n");

        var archivePath = Path.Combine(bootstrap, "offline-minecraft-baseline.zip");
        ZipFile.CreateFromDirectory(content, archivePath, CompressionLevel.Fastest, includeBaseDirectory: false);
        var bytes = await File.ReadAllBytesAsync(archivePath);
        var metadata = new OfflineMinecraftBaselineMetadata(
            1,
            "1.21.1",
            "0.19.3",
            "offline-minecraft-baseline.zip",
            bytes.LongLength,
            Convert.ToHexString(SHA256.HashData(bytes)).ToLowerInvariant());
        await File.WriteAllTextAsync(
            Path.Combine(bootstrap, "offline-minecraft-baseline.json"),
            JsonSerializer.Serialize(metadata, new JsonSerializerOptions(JsonSerializerDefaults.Web)));
        Directory.Delete(content, recursive: true);
    }

    private static async Task RefreshMetadataAsync(string bootstrap)
    {
        var archivePath = Path.Combine(bootstrap, "offline-minecraft-baseline.zip");
        var bytes = await File.ReadAllBytesAsync(archivePath);
        var metadata = new OfflineMinecraftBaselineMetadata(
            1,
            "1.21.1",
            "0.19.3",
            "offline-minecraft-baseline.zip",
            bytes.LongLength,
            Convert.ToHexString(SHA256.HashData(bytes)).ToLowerInvariant());
        await File.WriteAllTextAsync(
            Path.Combine(bootstrap, "offline-minecraft-baseline.json"),
            JsonSerializer.Serialize(metadata, new JsonSerializerOptions(JsonSerializerDefaults.Web)));
    }

    private sealed class FakeProfileInstaller : IMinecraftProfileInstaller
    {
        public List<string> InstalledVersions { get; } = [];

        public Task InstallAsync(string versionName, CancellationToken cancellationToken)
        {
            InstalledVersions.Add(versionName);
            return Task.CompletedTask;
        }
    }

    private sealed class FakeFabricProvisioner : IFabricProvisioner
    {
        public int Calls { get; private set; }

        public Task<FabricProvisioningResult> EnsureFabricAsync(
            string instanceRoot,
            string minecraftVersion,
            string fabricLoaderVersion,
            CancellationToken cancellationToken)
        {
            Calls++;
            return Task.FromResult(new FabricProvisioningResult(
                minecraftVersion,
                fabricLoaderVersion,
                "fabric-loader-0.19.3-1.21.1",
                instanceRoot));
        }
    }

    private sealed class TempDirectory : IDisposable
    {
        public TempDirectory()
        {
            Path = System.IO.Path.Combine(System.IO.Path.GetTempPath(), "copimine-offline-baseline-" + Guid.NewGuid().ToString("N"));
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
