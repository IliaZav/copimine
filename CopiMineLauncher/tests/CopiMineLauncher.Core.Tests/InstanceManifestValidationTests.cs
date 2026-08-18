using CopiMineLauncher.Core.Manifest;
using FluentAssertions;
using Xunit;

namespace CopiMineLauncher.Core.Tests;

public sealed class InstanceManifestValidationTests
{
    private static readonly DateTimeOffset Now = DateTimeOffset.Parse("2026-08-15T12:00:00Z");

    [Fact]
    public void Published_v4_wire_manifest_is_accepted_and_adapts_to_reconciler_contract()
    {
        var document = ValidDocument();

        var validation = new InstanceManifestValidator().Validate(document, Now, signaturePublicKeyId: "launcher-v1");

        validation.IsValid.Should().BeTrue(validation.ToString());
        var internalManifest = InstanceManifestAdapter.ToLauncherManifest(document, "launcher-v1");
        internalManifest.Sequence.Should().Be(17);
        internalManifest.Files.Should().ContainSingle().Which.Ownership.Should().Be("managed");
        internalManifest.Files.Single().InstallPolicy.Should().Be("REPLACE");
    }

    [Fact]
    public void Duplicate_paths_are_rejected_case_insensitively_and_unknown_install_policy_is_rejected()
    {
        var first = ValidDocument().Files[0];
        var document = ValidDocument() with
        {
            Files = new[]
            {
                first,
                first with { ComponentId = "another", Path = "mods/COPIMINECLIENT.JAR", InstallPolicy = "DELETE_ALL" }
            }
        };

        var validation = new InstanceManifestValidator().Validate(document, Now, signaturePublicKeyId: "launcher-v1");

        validation.ErrorCodes.Should().Contain(new[] { "DUPLICATE_FILE_PATH", "INSTALL_POLICY_INVALID" });
    }

    [Theory]
    [InlineData("../mods/client.jar")]
    [InlineData("C:/mods/client.jar")]
    [InlineData("/mods/client.jar")]
    public void Unsafe_wire_paths_are_rejected(string path)
    {
        var document = ValidDocument() with { Files = new[] { ValidDocument().Files[0] with { Path = path } } };

        var validation = new InstanceManifestValidator().Validate(document, Now, signaturePublicKeyId: "launcher-v1");

        validation.ErrorCodes.Should().Contain("FILE_PATH_INVALID");
    }

    [Fact]
    public void Exact_minecraft_fabric_java_and_release_sequence_policies_are_enforced()
    {
        var document = ValidDocument() with
        {
            ReleaseSequence = 8,
            Minecraft = new InstanceMinecraft("1.21.2", "0.19.2", 17),
            JavaRuntime = ValidDocument().JavaRuntime! with { Platform = "linux-x64" }
        };

        var validation = new InstanceManifestValidator().Validate(
            document,
            Now,
            previousReleaseSequence: 9,
            signaturePublicKeyId: "different-key");

        validation.ErrorCodes.Should().Contain(new[]
        {
            "RELEASE_SEQUENCE_ROLLBACK",
            "MINECRAFT_VERSION_UNSUPPORTED",
            "FABRIC_LOADER_VERSION_UNSUPPORTED",
            "JAVA_MAJOR_UNSUPPORTED",
            "JAVA_PLATFORM_UNSUPPORTED",
            "PUBLIC_KEY_ID_MISMATCH"
        });
    }

    [Fact]
    public void Missing_java_runtime_is_not_accepted_for_self_contained_installation()
    {
        var validation = new InstanceManifestValidator().Validate(
            ValidDocument() with { JavaRuntime = null },
            Now,
            signaturePublicKeyId: "launcher-v1");

        validation.ErrorCodes.Should().Contain("JAVA_RUNTIME_MISSING");
    }

    [Fact]
    public void Missing_minecraft_runtime_is_not_accepted_for_server_hosted_installation()
    {
        var validation = new InstanceManifestValidator().Validate(
            ValidDocument() with { MinecraftRuntime = null },
            Now,
            signaturePublicKeyId: "launcher-v1");

        validation.ErrorCodes.Should().Contain("MINECRAFT_RUNTIME_MISSING");
    }

    private static InstanceManifestDocument ValidDocument() => new(
        2,
        "stable",
        "2026.08.15.1",
        DateTimeOffset.Parse("2026-08-15T10:00:00Z"),
        "1.0.0",
        new InstanceMinecraft("1.21.1", "0.19.3", 21),
        new InstanceManifestServer("CopiMine", "mc.copimine.ru", true),
        new[]
        {
            new InstanceManifestFile(
                "copimine-client",
                "mods/CopiMineClient.jar",
                "https://copimine.ru/launcher/files/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                new string('b', 64),
                123,
                "MANAGED",
                true,
                "mod",
                "1.4.0",
                "REPLACE")
        },
        Array.Empty<InstanceConfigPolicy>(),
        "https://copimine.ru/news.html",
        17,
        new InstanceJavaRuntime(
            "Eclipse Adoptium",
            "temurin-21",
            "windows-x64",
            "21.0.10",
            "https://copimine.ru/launcher/files/cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc",
            456,
            new string('c', 64)),
        "launcher-v1",
        new InstanceMinecraftRuntime(
            "https://copimine.ru/launcher/files/dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd",
            789,
            new string('d', 64)));
}
