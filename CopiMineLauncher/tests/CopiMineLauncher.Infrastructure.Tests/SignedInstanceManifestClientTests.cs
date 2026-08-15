using System.Net;
using System.Text;
using System.Text.Json;
using CopiMineLauncher.Core.Manifest;
using CopiMineLauncher.Infrastructure.Manifest;
using FluentAssertions;
using Xunit;

namespace CopiMineLauncher.Infrastructure.Tests;

public sealed class SignedInstanceManifestClientTests
{
    [Fact]
    public async Task Verifies_exact_downloaded_bytes_before_parsing_and_maps_wire_contract()
    {
        var document = FixtureDocument();
        var raw = JsonSerializer.SerializeToUtf8Bytes(document, JsonOptions);
        var signature = Signature("launcher-v1");
        using var httpClient = new HttpClient(new ManifestHandler(raw, signature));
        var verifier = new RecordingVerifier(true);
        var client = new SignedInstanceManifestClient(
            httpClient,
            verifier,
            new byte[] { 1, 2, 3, 4 },
            "launcher-v1");

        var result = await client.FetchVerifiedAsync(SignedInstanceManifestClient.DefaultManifestUri, CancellationToken.None);

        result.Document.InstanceVersion.Should().Be("2026.08.15.1");
        result.ReconcilerManifest.Sequence.Should().Be(17);
        result.ReconcilerManifest.MinecraftVersion.Should().Be("1.21.1");
        result.ReconcilerManifest.Files.Should().ContainSingle().Which.Ownership.Should().Be("managed");
        result.ManifestSha256.Should().HaveLength(64).And.MatchRegex("^[0-9a-f]{64}$");
        verifier.ManifestBytes.Should().Equal(raw);
        verifier.PublicKeyBytes.Should().Equal(new byte[] { 1, 2, 3, 4 });
    }

    [Fact]
    public async Task Invalid_signature_stops_before_manifest_parse()
    {
        var raw = Encoding.UTF8.GetBytes("not-json");
        using var httpClient = new HttpClient(new ManifestHandler(raw, Signature("launcher-v1")));
        var client = new SignedInstanceManifestClient(
            httpClient,
            new RecordingVerifier(false),
            new byte[] { 1, 2, 3, 4 },
            "launcher-v1");

        var action = () => client.FetchVerifiedAsync(SignedInstanceManifestClient.DefaultManifestUri, CancellationToken.None);

        var exception = await action.Should().ThrowAsync<ManifestFetchException>();
        exception.Which.Code.Should().Be("SIGNATURE_INVALID");
    }

    [Fact]
    public async Task Malformed_verified_json_is_rejected_with_a_stable_code()
    {
        using var httpClient = new HttpClient(new ManifestHandler(Encoding.UTF8.GetBytes("{}"), Signature("launcher-v1")));
        var client = new SignedInstanceManifestClient(
            httpClient,
            new RecordingVerifier(true),
            new byte[] { 1, 2, 3, 4 },
            "launcher-v1");

        var action = () => client.FetchVerifiedAsync(SignedInstanceManifestClient.DefaultManifestUri, CancellationToken.None);

        var exception = await action.Should().ThrowAsync<ManifestFetchException>();
        exception.Which.Code.Should().Be("MANIFEST_POLICY_INVALID");
        exception.Which.Message.Should().Contain("INSTANCE_MANIFEST_SCHEMA_UNSUPPORTED");
    }

    [Fact]
    public async Task Foreign_or_http_manifest_endpoint_is_rejected_without_network_access()
    {
        var handler = new ManifestHandler(Array.Empty<byte>(), Signature("launcher-v1"));
        using var httpClient = new HttpClient(handler);
        var client = new SignedInstanceManifestClient(
            httpClient,
            new RecordingVerifier(true),
            new byte[] { 1, 2, 3, 4 },
            "launcher-v1");

        var action = () => client.FetchVerifiedAsync(new Uri("http://evil.example/instance-manifest.json"), CancellationToken.None);

        var exception = await action.Should().ThrowAsync<ManifestFetchException>();
        exception.Which.Code.Should().Be("MANIFEST_ENDPOINT_NOT_ALLOWED");
        handler.Requests.Should().BeEmpty();
    }

    [Fact]
    public async Task Signature_key_id_is_pinned_and_server_cannot_select_a_different_key()
    {
        using var httpClient = new HttpClient(new ManifestHandler(
            JsonSerializer.SerializeToUtf8Bytes(FixtureDocument(), JsonOptions),
            Signature("server-selected-key")));
        var client = new SignedInstanceManifestClient(
            httpClient,
            new RecordingVerifier(true),
            new byte[] { 1, 2, 3, 4 },
            "launcher-v1");

        var action = () => client.FetchVerifiedAsync(SignedInstanceManifestClient.DefaultManifestUri, CancellationToken.None);

        var exception = await action.Should().ThrowAsync<ManifestFetchException>();
        exception.Which.Code.Should().Be("SIGNATURE_KEY_ID_MISMATCH");
    }

    private static InstanceManifestDocument FixtureDocument() => new(
        1,
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
        "launcher-v1");

    private static ManifestSignature Signature(string keyId) => new(
        "Ed25519",
        keyId,
        Convert.ToBase64String(new byte[64]));

    private static readonly JsonSerializerOptions JsonOptions = new(JsonSerializerDefaults.Web);

    private sealed class RecordingVerifier(bool valid) : IManifestSignatureVerifier
    {
        public byte[] ManifestBytes { get; private set; } = Array.Empty<byte>();
        public byte[] PublicKeyBytes { get; private set; } = Array.Empty<byte>();

        public SignatureVerificationResult Verify(ReadOnlySpan<byte> manifestBytes, ReadOnlySpan<byte> signatureBytes, ReadOnlySpan<byte> publicKeyBytes)
        {
            ManifestBytes = manifestBytes.ToArray();
            PublicKeyBytes = publicKeyBytes.ToArray();
            return valid ? SignatureVerificationResult.Valid() : SignatureVerificationResult.Invalid("SIGNATURE_INVALID");
        }
    }

    private sealed class ManifestHandler(byte[] manifest, ManifestSignature signature) : HttpMessageHandler
    {
        public List<Uri> Requests { get; } = new();

        protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken)
        {
            Requests.Add(request.RequestUri!);
            var bytes = request.RequestUri!.AbsolutePath.EndsWith(".sig", StringComparison.Ordinal)
                ? JsonSerializer.SerializeToUtf8Bytes(signature, JsonOptions)
                : manifest;
            return Task.FromResult(new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new ByteArrayContent(bytes)
            });
        }
    }
}
