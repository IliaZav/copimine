using CopiMineLauncher.Core.Filesystem;
using CopiMineLauncher.Core.Manifest;
using FluentAssertions;
using Xunit;

namespace CopiMineLauncher.Core.Tests;

public sealed class ManifestValidationTests
{
    [Fact]
    public void Valid_manifest_is_accepted()
    {
        var result = new ManifestValidator().Validate(ValidManifest(), DateTimeOffset.Parse("2026-08-15T12:00:00Z"));

        result.IsValid.Should().BeTrue(result.ToString());
    }

    [Fact]
    public void Minecraft_and_fabric_versions_are_exact()
    {
        var manifest = ValidManifest() with { MinecraftVersion = "1.21.2", FabricLoaderVersion = "0.19.2" };

        var result = new ManifestValidator().Validate(manifest, DateTimeOffset.Parse("2026-08-15T12:00:00Z"));

        result.ErrorCodes.Should().Contain(new[] { "MINECRAFT_VERSION_UNSUPPORTED", "FABRIC_LOADER_VERSION_UNSUPPORTED" });
    }

    [Fact]
    public void File_hash_size_duplicates_and_url_are_validated()
    {
        var entry = ValidManifest().Files[0];
        var manifest = ValidManifest() with
        {
            Files = new[]
            {
                entry with { SizeBytes = 0, Sha256 = "ABC", Url = "http://evil.example/file.jar" },
                entry with { ComponentId = entry.ComponentId, Path = entry.Path }
            }
        };

        var result = new ManifestValidator().Validate(manifest, DateTimeOffset.Parse("2026-08-15T12:00:00Z"));

        result.ErrorCodes.Should().Contain(new[]
        {
            "FILE_SIZE_INVALID", "FILE_SHA256_INVALID", "FILE_URL_INVALID", "DUPLICATE_COMPONENT_ID", "DUPLICATE_FILE_PATH"
        });
    }

    [Fact]
    public void Sequence_rollback_is_rejected()
    {
        var result = new ManifestValidator().Validate(ValidManifest(), DateTimeOffset.Parse("2026-08-15T12:00:00Z"), previousSequence: 9);

        result.ErrorCodes.Should().Contain("MANIFEST_SEQUENCE_ROLLBACK");
    }

    [Fact]
    public void Unknown_ownership_is_rejected()
    {
        var manifest = ValidManifest() with
        {
            Files = new[] { ValidManifest().Files[0] with { Ownership = "untrusted" } }
        };

        var result = new ManifestValidator().Validate(manifest, DateTimeOffset.Parse("2026-08-15T12:00:00Z"));

        result.ErrorCodes.Should().Contain("OWNERSHIP_INVALID");
    }

    [Theory]
    [InlineData("mods\\client.jar")]
    [InlineData("../mods/client.jar")]
    [InlineData("mods/../client.jar")]
    [InlineData("/mods/client.jar")]
    [InlineData("C:/mods/client.jar")]
    [InlineData("mods//client.jar")]
    [InlineData("mods/./client.jar")]
    [InlineData("mods/CON.txt")]
    [InlineData("mods/client.jar ")]
    [InlineData("mods/client.jar:alternate")]
    public void Unsafe_paths_are_rejected(string path)
    {
        var action = () => SafeRelativePath.Parse(path);

        action.Should().Throw<ArgumentException>();
    }

    [Fact]
    public void Safe_path_is_normalized_to_forward_slashes()
    {
        SafeRelativePath.Parse("mods/CopiMineClient.jar").Value.Should().Be("mods/CopiMineClient.jar");
    }

    private static LauncherManifest ValidManifest()
    {
        return new LauncherManifest(
            SchemaVersion: 1,
            Product: "CopiMineLauncher",
            Channel: "stable",
            Sequence: 9,
            LauncherVersion: "1.0.0",
            MinecraftVersion: "1.21.1",
            FabricLoaderVersion: "0.19.3",
            IssuedAtUtc: DateTimeOffset.Parse("2026-08-15T11:00:00Z"),
            ExpiresAtUtc: null,
            JavaRuntime: new JavaRuntimeMetadata("21", "https://copimine.ru/downloads/java.zip", 10, new string('a', 64)),
            Files: new[]
            {
                new ManifestFileEntry(
                    ComponentId: "copimine-client",
                    Path: "mods/CopiMineClient.jar",
                    Kind: "mod",
                    Version: "1.0.0",
                    Url: "https://copimine.ru/downloads/CopiMineClient.jar",
                    SizeBytes: 123,
                    Sha256: new string('b', 64),
                    Required: true,
                    Ownership: "managed")
            },
            Server: new ManifestServer("mc.copimine.ru", 25565, "CopiMine"),
            PublicKeyId: "launcher-v1");
    }
}
