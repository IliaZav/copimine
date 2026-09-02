using System.Buffers.Binary;
using System.Net;
using System.Net.Http;
using System.Text;
using System.Text.Json;
using CopiMineLauncher.Infrastructure.Skins;
using FluentAssertions;
using Xunit;

namespace CopiMineLauncher.Infrastructure.Tests;

public sealed class SkinAndCosmeticTests
{
    [Theory]
    [InlineData(64, 64, CosmeticTextureKind.Skin)]
    [InlineData(64, 32, CosmeticTextureKind.Skin)]
    [InlineData(128, 128, CosmeticTextureKind.Skin)]
    [InlineData(64, 32, CosmeticTextureKind.Cape)]
    [InlineData(22, 17, CosmeticTextureKind.Cape)]
    [InlineData(46, 22, CosmeticTextureKind.Cape)]
    public void Texture_header_accepts_supported_dimensions(int width, int height, CosmeticTextureKind kind)
    {
        var header = PngHeader(width, height);

        var result = SkinTextureValidator.ValidatePngHeader(header, kind);

        result.Width.Should().Be(width);
        result.Height.Should().Be(height);
    }

    [Fact]
    public void Texture_header_rejects_unrelated_image_dimensions()
    {
        var action = () => SkinTextureValidator.ValidatePngHeader(PngHeader(100, 100), CosmeticTextureKind.Skin);

        action.Should().Throw<InvalidDataException>();
    }

    [Fact]
    public async Task ElyBy_catalog_parses_pages_and_filters_sensitive_tags_by_default()
    {
        var payload = """
        {
          "items": [
            {"id": 1, "skin_url": "https://ely.by/storage/skins/one.png", "is_slim": true, "tags": ["winter"], "count_views_total": 12, "count_wearers": 5},
            {"id": 2, "skin_url": "https://ely.by/storage/skins/two.png", "is_slim": false, "tags": ["nsfw"], "count_views_total": 20, "count_wearers": 7}
          ],
          "total_items": 10000,
          "current": 2,
          "last": 250
        }
        """;
        using var http = new HttpClient(new StaticHandler(payload));
        var client = new ElyByCatalogClient(http);

        var result = await client.GetPageAsync(new CosmeticCatalogQuery(Page: 2), CancellationToken.None);

        result.Items.Should().ContainSingle();
        result.Items[0].Id.Should().Be("1");
        result.Items[0].IsSlim.Should().BeTrue();
        result.HasNext.Should().BeTrue();
        result.Page.Should().Be(2);
    }

    [Fact]
    public async Task Player_profile_decodes_signed_texture_payload_and_normalizes_http_texture_url()
    {
        const string textureJson = "{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/skin\",\"metadata\":{\"model\":\"slim\"}},\"CAPE\":{\"url\":\"https://textures.minecraft.net/texture/cape\"}}}";
        var encoded = Convert.ToBase64String(Encoding.UTF8.GetBytes(textureJson));
        var handler = new RoutingHandler(request => request.AbsolutePath.Contains("users/profiles", StringComparison.Ordinal)
            ? Json("{\"id\":\"00000000000000000000000000000001\",\"name\":\"Player\"}")
            : Json($"{{\"id\":\"00000000000000000000000000000001\",\"name\":\"Player\",\"properties\":[{{\"name\":\"textures\",\"value\":\"{encoded}\"}}]}}"));
        using var http = new HttpClient(handler);
        var client = new PlayerCosmeticsClient(http);

        var result = await client.ResolveByNicknameAsync("Player", CancellationToken.None);

        result.Should().NotBeNull();
        result!.IsSlim.Should().BeTrue();
        result.SkinUrl!.Scheme.Should().Be("https");
        result.CapeUrl.Should().NotBeNull();
    }

    [Fact]
    public async Task Capes_dev_catalog_returns_only_owned_capes_and_prefers_minecraft_first()
    {
        const string payload = """
        {
          "optifine": {"exists": true, "playerName": "Player", "imageUrl": "https://api.capes.dev/img/optifine"},
          "minecraft": {"exists": true, "playerName": "Player", "imageUrls": {"base": {"full": "https://api.capes.dev/img/minecraft"}}},
          "labymod": {"exists": false, "imageUrl": null}
        }
        """;
        using var http = new HttpClient(new StaticHandler(payload));
        var client = new CapesDevClient(http);

        var result = await client.GetPlayerCapesAsync("Player", CancellationToken.None);

        result.Should().HaveCount(2);
        result[0].Type.Should().Be("minecraft");
        result[1].Type.Should().Be("optifine");
        result[0].TextureUrl.Host.Should().Be("api.capes.dev");
    }

    [Fact]
    public void Local_store_installs_skin_and_cape_under_CustomSkinLoader_LocalSkin()
    {
        using var temp = new TemporaryDirectory();
        var sourceSkin = Path.Combine(temp.Path, "skin.png");
        var sourceCape = Path.Combine(temp.Path, "cape.png");
        File.WriteAllBytes(sourceSkin, PngHeader(64, 64));
        File.WriteAllBytes(sourceCape, PngHeader(64, 32));
        var store = new LocalCosmeticsStore(Path.Combine(temp.Path, "Minecraft"), Path.Combine(temp.Path, "Launcher"));

        var skinPath = store.InstallFile(sourceSkin, "Player", CosmeticTextureKind.Skin);
        var capePath = store.InstallFile(sourceCape, "Player", CosmeticTextureKind.Cape);

        skinPath.Should().EndWith(Path.Combine("CustomSkinLoader", "LocalSkin", "skins", "Player.png"));
        capePath.Should().EndWith(Path.Combine("CustomSkinLoader", "LocalSkin", "capes", "Player.png"));
        File.Exists(skinPath).Should().BeTrue();
        File.Exists(capePath).Should().BeTrue();

        var configPath = Path.Combine(temp.Path, "Minecraft", "CustomSkinLoader", "CustomSkinLoader.json");
        File.Exists(configPath).Should().BeTrue();
        using var config = JsonDocument.Parse(File.ReadAllText(configPath));
        config.RootElement.GetProperty("loadlist")[0].GetProperty("name").GetString().Should().Be("LocalSkin");
    }

    [Fact]
    public void Custom_skin_loader_local_profile_is_first_and_external_profiles_are_preserved()
    {
        using var temp = new TemporaryDirectory();
        var instanceRoot = Path.Combine(temp.Path, "Minecraft");
        var configPath = Path.Combine(instanceRoot, "CustomSkinLoader", "CustomSkinLoader.json");
        Directory.CreateDirectory(Path.GetDirectoryName(configPath)!);
        File.WriteAllText(configPath, """
        {
          "version": "14.26.1",
          "loadlist": [
            { "name": "Mojang", "type": "MojangAPI" },
            { "name": "LocalSkin", "type": "Legacy", "skin": "old/{USERNAME}.png", "cape": "old/{USERNAME}.png" },
            { "name": "ElyBy", "type": "ElyByAPI" }
          ]
        }
        """);

        CustomSkinLoaderConfigService.EnsureLocalSkinPriority(instanceRoot);

        using var config = JsonDocument.Parse(File.ReadAllText(configPath));
        var loadlist = config.RootElement.GetProperty("loadlist");
        loadlist[0].GetProperty("name").GetString().Should().Be("LocalSkin");
        loadlist[0].GetProperty("skin").GetString().Should().Be("LocalSkin/skins/{USERNAME}.png");
        loadlist[0].GetProperty("cape").GetString().Should().Be("LocalSkin/capes/{USERNAME}.png");
        loadlist[0].GetProperty("checkPNG").GetBoolean().Should().BeFalse();
        loadlist.EnumerateArray().Count(item => item.GetProperty("name").GetString() == "LocalSkin").Should().Be(1);
        loadlist[1].GetProperty("name").GetString().Should().Be("Mojang");
        loadlist[2].GetProperty("name").GetString().Should().Be("ElyBy");
    }

    [Fact]
    public void Custom_skin_loader_config_is_created_when_the_instance_has_no_config()
    {
        using var temp = new TemporaryDirectory();
        var instanceRoot = Path.Combine(temp.Path, "Minecraft");

        var configPath = CustomSkinLoaderConfigService.EnsureLocalSkinPriority(instanceRoot);

        configPath.Should().Be(Path.Combine(instanceRoot, "CustomSkinLoader", "CustomSkinLoader.json"));
        using var config = JsonDocument.Parse(File.ReadAllText(configPath));
        config.RootElement.GetProperty("loadlist")[0].GetProperty("name").GetString().Should().Be("LocalSkin");
        config.RootElement.GetProperty("enableCape").GetBoolean().Should().BeTrue();
    }

    [Fact]
    public async Task Concurrent_custom_skin_loader_repairs_do_not_collide_on_shared_instance()
    {
        using var temp = new TemporaryDirectory();
        var instanceRoot = Path.Combine(temp.Path, "Minecraft");

        var repairs = Enumerable.Range(0, 32)
            .Select(_ => Task.Run(() => CustomSkinLoaderConfigService.EnsureLocalSkinPriority(instanceRoot)))
            .ToArray();

        var act = () => Task.WhenAll(repairs);
        await act.Should().NotThrowAsync();

        var configPath = Path.Combine(instanceRoot, "CustomSkinLoader", "CustomSkinLoader.json");
        using var config = JsonDocument.Parse(File.ReadAllText(configPath));
        config.RootElement.GetProperty("loadlist")[0].GetProperty("name").GetString().Should().Be("LocalSkin");
    }

    [Fact]
    public void Local_store_persists_separate_skin_and_cape_libraries_across_store_instances()
    {
        using var temp = new TemporaryDirectory();
        var sourceSkin = Path.Combine(temp.Path, "skin.png");
        var sourceCape = Path.Combine(temp.Path, "cape.png");
        File.WriteAllBytes(sourceSkin, PngHeader(64, 64));
        File.WriteAllBytes(sourceCape, PngHeader(64, 32));
        var instanceRoot = Path.Combine(temp.Path, "Minecraft");
        var launcherRoot = Path.Combine(temp.Path, "LauncherData");
        var store = new LocalCosmeticsStore(instanceRoot, launcherRoot);

        var skinLibraryPath = store.SaveToLibrary(sourceSkin, "Player", CosmeticTextureKind.Skin);
        var capeLibraryPath = store.SaveToLibrary(sourceCape, "Player", CosmeticTextureKind.Cape);

        skinLibraryPath.Should().EndWith(Path.Combine("cosmetics", "skins", "Player.png"));
        capeLibraryPath.Should().EndWith(Path.Combine("cosmetics", "capes", "Player.png"));
        File.Exists(skinLibraryPath).Should().BeTrue();
        File.Exists(capeLibraryPath).Should().BeTrue();

        var restartedStore = new LocalCosmeticsStore(instanceRoot, launcherRoot);
        restartedStore.FindLibraryPath("Player", CosmeticTextureKind.Skin).Should().Be(skinLibraryPath);
        restartedStore.FindLibraryPath("Player", CosmeticTextureKind.Cape).Should().Be(capeLibraryPath);
    }

    [Fact]
    public void Animated_gif_cape_is_validated_and_kept_as_gif_in_the_persistent_library()
    {
        using var temp = new TemporaryDirectory();
        var sourceCape = Path.Combine(temp.Path, "cape.gif");
        File.WriteAllBytes(sourceCape, AnimatedGif(64, 32));
        var instanceRoot = Path.Combine(temp.Path, "Minecraft");
        var launcherRoot = Path.Combine(temp.Path, "LauncherData");
        var store = new LocalCosmeticsStore(instanceRoot, launcherRoot);

        var info = SkinTextureValidator.ValidateFile(sourceCape, CosmeticTextureKind.Cape);
        var libraryPath = store.SaveToLibrary(sourceCape, "Player", CosmeticTextureKind.Cape);

        info.IsAnimated.Should().BeTrue();
        libraryPath.Should().EndWith(Path.Combine("cosmetics", "capes", "Player.gif"));
        File.ReadAllBytes(libraryPath).Should().Equal(File.ReadAllBytes(sourceCape));
        new LocalCosmeticsStore(instanceRoot, launcherRoot)
            .FindLibraryPath("Player", CosmeticTextureKind.Cape)
            .Should().Be(libraryPath);
    }

    [Fact]
    public void Animated_gif_cape_library_remains_separate_from_the_game_png_frame()
    {
        using var temp = new TemporaryDirectory();
        var sourceCape = Path.Combine(temp.Path, "cape.gif");
        File.WriteAllBytes(sourceCape, AnimatedGif(64, 32));
        var firstFrame = Path.Combine(temp.Path, "cape-frame.png");
        File.WriteAllBytes(firstFrame, PngHeader(64, 32));
        var instanceRoot = Path.Combine(temp.Path, "Minecraft");
        var launcherRoot = Path.Combine(temp.Path, "LauncherData");
        var store = new LocalCosmeticsStore(instanceRoot, launcherRoot);

        var libraryPath = store.SaveToLibrary(sourceCape, "Player", CosmeticTextureKind.Cape);
        var installedPath = store.InstallPngFile(firstFrame, "Player", CosmeticTextureKind.Cape);

        libraryPath.Should().EndWith(Path.Combine("cosmetics", "capes", "Player.gif"));
        installedPath.Should().EndWith(Path.Combine("CustomSkinLoader", "LocalSkin", "capes", "Player.png"));
        File.Exists(installedPath).Should().BeTrue();
        File.ReadAllBytes(libraryPath).Should().Equal(File.ReadAllBytes(sourceCape));
        SkinTextureValidator.ValidateFile(installedPath, CosmeticTextureKind.Cape).IsAnimated.Should().BeFalse();
    }

    [Fact]
    public async Task Remote_animated_gif_cape_cache_preserves_the_gif_extension()
    {
        using var temp = new TemporaryDirectory();
        using var http = new HttpClient(new BinaryHandler(AnimatedGif(64, 32), "image/gif"));
        var store = new LocalCosmeticsStore(Path.Combine(temp.Path, "Minecraft"), Path.Combine(temp.Path, "LauncherData"));

        var path = await store.CacheRemoteAsync(
            http,
            new Uri("https://textures.minecraft.net/cape.gif"),
            CosmeticTextureKind.Cape,
            CancellationToken.None);

        path.Should().EndWith(".gif");
        SkinTextureValidator.ValidateFile(path, CosmeticTextureKind.Cape).IsAnimated.Should().BeTrue();
    }

    private static byte[] PngHeader(int width, int height)
    {
        var header = new byte[33];
        new byte[] { 137, 80, 78, 71, 13, 10, 26, 10 }.CopyTo(header, 0);
        BinaryPrimitives.WriteUInt32BigEndian(header.AsSpan(8, 4), 13);
        "IHDR"u8.CopyTo(header.AsSpan(12, 4));
        BinaryPrimitives.WriteUInt32BigEndian(header.AsSpan(16, 4), (uint)width);
        BinaryPrimitives.WriteUInt32BigEndian(header.AsSpan(20, 4), (uint)height);
        header[24] = 8;
        header[25] = 6;
        return header;
    }

    private static byte[] AnimatedGif(int width, int height)
    {
        _ = width;
        _ = height;
        return Convert.FromBase64String("R0lGODlhQAAgAIEAAP8AAAAAAAAAAAAAACH/C05FVFNDQVBFMi4wAwEAAAAh+QQBCgAAACwAAAAAQAAgAAAISwABCBxIsKDBgwgTKlzIsKHDhxAjSpxIsaLFixgzatzIsaPHjyBDihxJsqTJkyhTqlzJsqXLlzBjypxJs6bNmzhz6tzJs6fPnyADAgAh+QQBCgAAACwAAAAAQAAgAIEAAP8AAAAAAAAAAAAISwABCBxIsKDBgwgTKlzIsKHDhxAjSpxIsaLFixgzatzIsaPHjyBDihxJsqTJkyhTqlzJsqXLlzBjypxJs6bNmzhz6tzJs6fPnyADAgA7");
    }

    private static HttpResponseMessage Json(string payload) =>
        new(HttpStatusCode.OK) { Content = new StringContent(payload, Encoding.UTF8, "application/json") };

    private sealed class StaticHandler(string payload) : HttpMessageHandler
    {
        protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken) =>
            Task.FromResult(Json(payload));
    }

    private sealed class RoutingHandler(Func<Uri, HttpResponseMessage> route) : HttpMessageHandler
    {
        protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken) =>
            Task.FromResult(route(request.RequestUri!));
    }

    private sealed class BinaryHandler(byte[] payload, string mediaType) : HttpMessageHandler
    {
        protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken) =>
            Task.FromResult(new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new ByteArrayContent(payload) { Headers = { ContentType = new System.Net.Http.Headers.MediaTypeHeaderValue(mediaType) } }
            });
    }

    private sealed class TemporaryDirectory : IDisposable
    {
        public TemporaryDirectory() => Path = Directory.CreateTempSubdirectory("copimine-skin-tests-").FullName;
        public string Path { get; }
        public void Dispose()
        {
            if (Directory.Exists(Path)) Directory.Delete(Path, recursive: true);
        }
    }
}
