using System.Buffers.Binary;
using System.Net;
using System.Security.Cryptography;
using System.Text;
using CopiMineLauncher.Infrastructure.Binding;
using CopiMineLauncher.Infrastructure.Launch;
using CopiMineLauncher.Infrastructure.Provisioning;
using CopiMineLauncher.Infrastructure.SelfUpdate;
using CopiMineLauncher.Infrastructure.Skins;
using FluentAssertions;
using Xunit;

namespace CopiMineLauncher.Infrastructure.Tests;

public sealed class LauncherInfrastructureBoundaryTests
{
    [Fact]
    public void Texture_source_normalizes_http_to_https_and_removes_the_default_port()
    {
        var accepted = CosmeticTextureSources.TryNormalize(
            new Uri("http://textures.minecraft.net:80/texture/skin"),
            out var normalized);

        accepted.Should().BeTrue();
        normalized.Should().Be(new Uri("https://textures.minecraft.net/texture/skin"));
    }

    [Theory]
    [InlineData("https://evil.example/skin.png")]
    [InlineData("file:///C:/Users/Player/skin.png")]
    [InlineData("https://textures.minecraft.net.evil.example/skin.png")]
    public void Texture_source_rejects_non_catalog_hosts_or_schemes(string raw)
    {
        CosmeticTextureSources.TryNormalize(new Uri(raw), out _).Should().BeFalse();
    }

    [Fact]
    public void Gif_texture_is_valid_for_capes_but_never_for_skins()
    {
        using var temp = new TemporaryDirectory();
        var gif = Path.Combine(temp.Path, "cape.gif");
        File.WriteAllBytes(gif, ValidAnimatedGif());

        SkinTextureValidator.ValidateFile(gif, CosmeticTextureKind.Cape).IsAnimated.Should().BeTrue();
        Action action = () => SkinTextureValidator.ValidateFile(gif, CosmeticTextureKind.Skin);

        action.Should().Throw<InvalidDataException>().Which.Message.Should().Contain("PNG");
    }

    [Fact]
    public void Default_game_settings_store_fails_closed_for_bad_json_or_schema()
    {
        using var temp = new TemporaryDirectory();
        var path = Path.Combine(temp.Path, ".copimine", "minecraft-default-settings.json");
        Directory.CreateDirectory(Path.GetDirectoryName(path)!);

        File.WriteAllText(path, "{\"schemaVersion\":99,\"useRussianLanguage\":true}");
        MinecraftDefaultSettingsStore.Load(temp.Path).Should().BeNull();

        File.WriteAllText(path, "not-json");
        MinecraftDefaultSettingsStore.Load(temp.Path).Should().BeNull();
    }

    [Fact]
    public void Default_game_settings_round_trip_preserves_each_checkbox()
    {
        using var temp = new TemporaryDirectory();
        var expected = new MinecraftDefaultSettings(
            UseRussianLanguage: false,
            DisableNarrator: true,
            SetMasterVolumeToFifteenPercent: false);

        MinecraftDefaultSettingsStore.Save(temp.Path, expected);

        MinecraftDefaultSettingsStore.Load(temp.Path).Should().Be(expected);
    }

    [Fact]
    public void Fabric_version_name_is_pinned_and_invalid_versions_fail_before_network_use()
    {
        FabricProvisioner.ResolveVersionName("1.21.1", "0.19.3")
            .Should().Be("fabric-loader-0.19.3-1.21.1");

        Action wrongMinecraft = () => FabricProvisioner.ResolveVersionName("1.21.2", "0.19.3");
        Action wrongFabric = () => FabricProvisioner.ResolveVersionName("1.21.1", "0.19.2");

        wrongMinecraft.Should().Throw<ArgumentException>();
        wrongFabric.Should().Throw<ArgumentException>();
    }

    [Fact]
    public async Task Ready_minecraft_profile_skips_profile_and_fabric_installers()
    {
        using var temp = new TemporaryDirectory();
        CreateReadyMinecraftProfile(temp.Path);
        var installer = new CountingProfileInstaller();
        var fabric = new CountingFabricProvisioner();
        var provisioner = new MinecraftProvisioner(new HttpClient(), fabric, installer);

        var result = await provisioner.EnsureMinecraftFabricAsync(
            temp.Path,
            "1.21.1",
            "0.19.3",
            CancellationToken.None);

        result.FabricVersionName.Should().Be("fabric-loader-0.19.3-1.21.1");
        installer.Calls.Should().Be(0);
        fabric.Calls.Should().Be(0);
    }

    [Fact]
    public void Self_update_policy_rejects_downgrades_uppercase_hashes_and_path_traversal()
    {
        var policy = new SelfUpdatePolicy(new[] { "copimine.ru" });
        var valid = ValidUpdate();

        policy.Validate(valid with { Version = "1.0.0" }, "1.0.0").ErrorCode
            .Should().Be("SELF_UPDATE_NOT_NEWER");
        policy.Validate(valid with { Sha256 = valid.Sha256.ToUpperInvariant() }, "1.0.0").ErrorCode
            .Should().Be("SELF_UPDATE_HASH_INVALID");
        policy.Validate(valid with { PackageFileName = "../CopiMineLauncher-1.0.1-full.nupkg" }, "1.0.0").ErrorCode
            .Should().Be("SELF_UPDATE_PACKAGE_NAME_INVALID");
    }

    [Fact]
    public void Launcher_memory_ceiling_is_a_safe_512_mb_step_above_minimum()
    {
        LauncherMemoryLimits.MaximumRamMb.Should().BeGreaterThanOrEqualTo(LauncherMemoryLimits.MinimumRamMb);
        (LauncherMemoryLimits.MaximumRamMb % 512).Should().Be(0);
    }

    [Fact]
    public async Task Binding_status_defaults_to_pending_when_backend_omits_optional_fields()
    {
        using var http = new HttpClient(new JsonHandler("{}"));
        var client = new HttpLauncherBindingClient(http, new Uri("https://copimine.ru/"), "cm-device-1234567890");
        var challenge = new LauncherLinkChallenge(
            "challenge-status-123456",
            "poll-status-abcdefghijklmnopqrstuvwxyz-123456",
            new Uri("https://copimine.ru/cabinet/link.html"),
            DateTimeOffset.UtcNow.AddMinutes(5),
            "Player");

        var status = await client.GetStatusAsync(challenge, CancellationToken.None);

        status.Linked.Should().BeFalse();
        status.Status.Should().Be("UNKNOWN");
        status.SiteUsername.Should().BeNull();
    }

    [Fact]
    public async Task Binding_challenge_requires_all_security_fields()
    {
        using var http = new HttpClient(new JsonHandler("{\"pollToken\":\"poll-token-abcdefghijklmnopqrstuvwxyz-123456\"}"));
        var client = new HttpLauncherBindingClient(http, new Uri("https://copimine.ru/"), "cm-device-1234567890");

        Func<Task> action = () => client.CreateChallengeAsync("Player", "1.0.0", CancellationToken.None);

        var exception = await action.Should().ThrowAsync<LauncherBindingException>();
        exception.Which.Code.Should().Be("LAUNCHER_LINK_CHALLENGE_INVALID");
    }

    [Fact]
    public async Task Binding_nickname_change_rejects_missing_access_context_before_http()
    {
        using var http = new HttpClient(new ThrowingHandler());
        var client = new HttpLauncherBindingClient(http, new Uri("https://copimine.ru/"), "cm-device-1234567890");

        Func<Task> action = () => client.ChangeNicknameAsync("", "Player", "NewPlayer", CancellationToken.None);

        var exception = await action.Should().ThrowAsync<LauncherBindingException>();
        exception.Which.Code.Should().Be("LAUNCHER_NICKNAME_INVALID");
    }

    private static VerifiedSelfUpdate ValidUpdate()
    {
        var bytes = Encoding.UTF8.GetBytes("launcher-package");
        return new(
            "CopiMineLauncher",
            "stable",
            "1.0.1",
            new Uri("https://copimine.ru/downloads/launcher/"),
            new Uri("https://cdn.copimine.ru/downloads/launcher/CopiMineLauncher-1.0.1-full.nupkg"),
            "CopiMineLauncher-1.0.1-full.nupkg",
            bytes.Length,
            Convert.ToHexString(SHA256.HashData(bytes)).ToLowerInvariant());
    }

    private static void CreateReadyMinecraftProfile(string root)
    {
        Directory.CreateDirectory(Path.Combine(root, "libraries"));
        Directory.CreateDirectory(Path.Combine(root, "assets", "indexes"));
        Directory.CreateDirectory(Path.Combine(root, "versions", "1.21.1"));
        Directory.CreateDirectory(Path.Combine(root, "versions", "fabric-loader-0.19.3-1.21.1"));
        File.WriteAllText(Path.Combine(root, "assets", "indexes", "1.21.1.json"), "{}");
        File.WriteAllText(Path.Combine(root, "versions", "1.21.1", "1.21.1.json"), "{}");
        File.WriteAllText(
            Path.Combine(root, "versions", "fabric-loader-0.19.3-1.21.1", "fabric-loader-0.19.3-1.21.1.json"),
            "{}");
    }

    private static byte[] ValidAnimatedGif() => Convert.FromBase64String(
        "R0lGODlhQAAgAIEAAP8AAAAAAAAAAAAAACH/C05FVFNDQVBFMi4wAwEAAAAh+QQBCgAAACwAAAAAQAAgAAAISwABCBxIsKDBgwgTKlzIsKHDhxAjSpxIsaLFixgzatzIsaPHjyBDihxJsqTJkyhTqlzJsqXLlzBjypxJs6bNmzhz6tzJs6fPnyADAgAh+QQBCgAAACwAAAAAQAAgAIEAAP8AAAAAAAAAAAAISwABCBxIsKDBgwgTKlzIsKHDhxAjSpxIsaLFixgzatzIsaPHjyBDihxJsqTJkyhTqlzJsqXLlzBjypxJs6bNmzhz6tzJs6fPnyADAgA7");

    private static byte[] AnimatedGif() => Convert.FromBase64String(
        "R0lGODlhQAAgAIEAAP8AAAAAAAAAAAAAACH/C05FVFNDQVBFMi4wAwEAAAAh+QQBCgAAACwAAAAAQAAgAAAISwABCBxIsKDBgwgTKlzIsKHDhxAjSpxIsaLFixgzatzIsaPHjyBDihxJsqTJkyhTqlzJsqXLlzBjypxJs6bNmzhz6tzJs6fPnyADAgAh+QQBCgAAACwAAAAAQAAgAIEAAP8AAAAAAAAAAAAISwABCBxIsKDBgwgTKlzIsKHDhxAjSpxIsaLFixgzatzIsaPHjyBDihxJsqTJkyhTqlzJs6bNmzhz6tzJs6fPnyADAgA7");

    private sealed class CountingProfileInstaller : IMinecraftProfileInstaller
    {
        public int Calls { get; private set; }
        public Task InstallAsync(string versionName, CancellationToken cancellationToken)
        {
            Calls++;
            return Task.CompletedTask;
        }
    }

    private sealed class CountingFabricProvisioner : IFabricProvisioner
    {
        public int Calls { get; private set; }
        public Task<FabricProvisioningResult> EnsureFabricAsync(string instanceRoot, string minecraftVersion, string fabricLoaderVersion, CancellationToken cancellationToken)
        {
            Calls++;
            return Task.FromResult(new FabricProvisioningResult(
                minecraftVersion,
                fabricLoaderVersion,
                FabricProvisioner.ResolveVersionName(minecraftVersion, fabricLoaderVersion),
                instanceRoot));
        }
    }

    private sealed class JsonHandler(string payload) : HttpMessageHandler
    {
        protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken) =>
            Task.FromResult(new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new StringContent(payload, Encoding.UTF8, "application/json")
            });
    }

    private sealed class ThrowingHandler : HttpMessageHandler
    {
        protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken) =>
            throw new InvalidOperationException("HTTP should not be reached");
    }

    private sealed class TemporaryDirectory : IDisposable
    {
        public TemporaryDirectory() => Path = Directory.CreateTempSubdirectory("copimine-infrastructure-boundary-").FullName;
        public string Path { get; }
        public void Dispose()
        {
            if (Directory.Exists(Path)) Directory.Delete(Path, recursive: true);
        }
    }
}
