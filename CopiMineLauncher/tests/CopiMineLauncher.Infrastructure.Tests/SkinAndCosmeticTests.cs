using System.Buffers.Binary;
using System.Net;
using System.Net.Http;
using System.Text;
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
