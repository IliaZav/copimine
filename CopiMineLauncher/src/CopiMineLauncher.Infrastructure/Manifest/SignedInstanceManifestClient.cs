using System.Net;
using System.Security.Cryptography;
using System.Text.Json;
using CopiMineLauncher.Core.Manifest;

namespace CopiMineLauncher.Infrastructure.Manifest;

public sealed record VerifiedInstanceManifest(
    InstanceManifestDocument Document,
    LauncherManifest ReconcilerManifest,
    ManifestSignature Signature,
    byte[] ManifestBytes,
    string ManifestSha256,
    DateTimeOffset VerifiedAtUtc);

public sealed class ManifestFetchException : Exception
{
    public ManifestFetchException(string code, string message, Exception? innerException = null)
        : base(message, innerException)
    {
        Code = code;
    }

    public string Code { get; }
}

public interface IManifestClient
{
    Task<VerifiedInstanceManifest> FetchVerifiedAsync(Uri manifestUri, CancellationToken cancellationToken);
}

public sealed class SignedInstanceManifestClient : IManifestClient
{
    public static readonly Uri DefaultManifestUri = new(
        "https://copimine.ru/launcher/stable/instance-manifest.json",
        UriKind.Absolute);

    private const long MaximumManifestBytes = 2L * 1024 * 1024;
    private const long MaximumSignatureBytes = 32L * 1024;
    private readonly HttpClient httpClient;
    private readonly IManifestSignatureVerifier signatureVerifier;
    private readonly byte[] pinnedPublicKey;
    private readonly string pinnedPublicKeyId;
    private readonly InstanceManifestValidator validator;

    public SignedInstanceManifestClient(
        HttpClient httpClient,
        IManifestSignatureVerifier signatureVerifier,
        ReadOnlySpan<byte> pinnedPublicKey,
        string pinnedPublicKeyId,
        InstanceManifestValidator? validator = null)
    {
        this.httpClient = httpClient ?? throw new ArgumentNullException(nameof(httpClient));
        this.signatureVerifier = signatureVerifier ?? throw new ArgumentNullException(nameof(signatureVerifier));
        if (pinnedPublicKey.Length == 0)
        {
            throw new ArgumentException("A pinned public key is required", nameof(pinnedPublicKey));
        }

        this.pinnedPublicKey = pinnedPublicKey.ToArray();
        this.pinnedPublicKeyId = string.IsNullOrWhiteSpace(pinnedPublicKeyId)
            ? throw new ArgumentException("A pinned public key id is required", nameof(pinnedPublicKeyId))
            : pinnedPublicKeyId;
        this.validator = validator ?? new InstanceManifestValidator();
    }

    public async Task<VerifiedInstanceManifest> FetchVerifiedAsync(Uri manifestUri, CancellationToken cancellationToken)
    {
        ValidateEndpoint(manifestUri);
        var signatureUri = GetSignatureUri(manifestUri);

        var manifestBytes = await GetBytesAsync(manifestUri, MaximumManifestBytes, "MANIFEST_HTTP_FAILED", cancellationToken);
        var signatureBytes = await GetBytesAsync(signatureUri, MaximumSignatureBytes, "SIGNATURE_HTTP_FAILED", cancellationToken);
        var signature = ParseSignature(signatureBytes);

        if (!string.Equals(signature.Algorithm, "Ed25519", StringComparison.Ordinal))
        {
            throw new ManifestFetchException("SIGNATURE_ALGORITHM_UNSUPPORTED", "The instance manifest signature algorithm is not Ed25519.");
        }

        if (!string.Equals(signature.PublicKeyId, pinnedPublicKeyId, StringComparison.Ordinal))
        {
            throw new ManifestFetchException("SIGNATURE_KEY_ID_MISMATCH", "The detached signature does not use the pinned public key id.");
        }

        byte[] detachedSignature;
        try
        {
            detachedSignature = Convert.FromBase64String(signature.SignatureBase64);
        }
        catch (FormatException exception)
        {
            throw new ManifestFetchException("SIGNATURE_BASE64_INVALID", "The detached signature is not valid base64.", exception);
        }

        var verification = signatureVerifier.Verify(manifestBytes, detachedSignature, pinnedPublicKey);
        if (!verification.IsValid)
        {
            throw new ManifestFetchException(
                verification.ErrorCode ?? "SIGNATURE_INVALID",
                "The instance manifest signature did not verify against the pinned public key.");
        }

        InstanceManifestDocument document;
        try
        {
            // This also rejects duplicate JSON properties. The signature above is
            // still over the exact downloaded bytes, never over this normalized copy.
            _ = CanonicalJson.Normalize(manifestBytes);
            document = JsonSerializer.Deserialize<InstanceManifestDocument>(manifestBytes, JsonOptions)
                ?? throw new FormatException("The instance manifest JSON is empty.");
        }
        catch (Exception exception) when (exception is FormatException or JsonException or NotSupportedException)
        {
            throw new ManifestFetchException("MANIFEST_JSON_INVALID", "The verified instance manifest is not valid JSON for the published contract.", exception);
        }

        if (!string.IsNullOrWhiteSpace(document.PublicKeyId)
            && !string.Equals(document.PublicKeyId, pinnedPublicKeyId, StringComparison.Ordinal))
        {
            throw new ManifestFetchException("MANIFEST_KEY_ID_MISMATCH", "The manifest publicKeyId does not match the pinned public key id.");
        }

        document = document with { PublicKeyId = pinnedPublicKeyId };
        var validation = validator.Validate(document, DateTimeOffset.UtcNow, signaturePublicKeyId: pinnedPublicKeyId);
        if (!validation.IsValid)
        {
            throw new ManifestFetchException("MANIFEST_POLICY_INVALID", validation.ToString());
        }

        var internalManifest = InstanceManifestAdapter.ToLauncherManifest(document, pinnedPublicKeyId);
        var hash = Convert.ToHexString(SHA256.HashData(manifestBytes)).ToLowerInvariant();
        return new(document, internalManifest, signature, manifestBytes, hash, DateTimeOffset.UtcNow);
    }

    private async Task<byte[]> GetBytesAsync(Uri uri, long maximumBytes, string failureCode, CancellationToken cancellationToken)
    {
        try
        {
            using var response = await httpClient.GetAsync(uri, HttpCompletionOption.ResponseHeadersRead, cancellationToken);
            if (!response.IsSuccessStatusCode)
            {
                throw new ManifestFetchException(failureCode, $"GET {uri.AbsolutePath} returned {(int)response.StatusCode} {response.ReasonPhrase}.");
            }

            if (response.Content.Headers.ContentLength is long contentLength && contentLength > maximumBytes)
            {
                throw new ManifestFetchException("MANIFEST_TOO_LARGE", $"GET {uri.AbsolutePath} declared {contentLength} bytes, exceeding the safe limit.");
            }

            var bytes = await response.Content.ReadAsByteArrayAsync(cancellationToken);
            if (bytes.LongLength > maximumBytes)
            {
                throw new ManifestFetchException("MANIFEST_TOO_LARGE", $"GET {uri.AbsolutePath} exceeded the safe size limit.");
            }

            return bytes;
        }
        catch (ManifestFetchException)
        {
            throw;
        }
        catch (OperationCanceledException)
        {
            throw;
        }
        catch (HttpRequestException exception)
        {
            throw new ManifestFetchException(failureCode, $"GET {uri.AbsolutePath} failed.", exception);
        }
    }

    private static ManifestSignature ParseSignature(byte[] bytes)
    {
        try
        {
            var signature = JsonSerializer.Deserialize<ManifestSignature>(bytes, JsonOptions);
            return signature ?? throw new FormatException("Signature document is empty.");
        }
        catch (Exception exception) when (exception is JsonException or FormatException or NotSupportedException)
        {
            throw new ManifestFetchException("SIGNATURE_JSON_INVALID", "The detached signature document is invalid.", exception);
        }
    }

    private static void ValidateEndpoint(Uri? endpoint)
    {
        if (endpoint is null
            || !endpoint.IsAbsoluteUri
            || !string.Equals(endpoint.Scheme, Uri.UriSchemeHttps, StringComparison.OrdinalIgnoreCase)
            || (endpoint.Port != 443 && endpoint.Port != -1)
            || !string.IsNullOrEmpty(endpoint.UserInfo)
            || !IsAllowedHost(endpoint.Host)
            || !endpoint.AbsolutePath.EndsWith("/instance-manifest.json", StringComparison.Ordinal))
        {
            throw new ManifestFetchException("MANIFEST_ENDPOINT_NOT_ALLOWED", "The instance manifest endpoint must be the allowlisted HTTPS instance-manifest.json resource.");
        }
    }

    private static Uri GetSignatureUri(Uri manifestUri)
    {
        var builder = new UriBuilder(manifestUri)
        {
            Path = manifestUri.AbsolutePath[..^".json".Length] + ".sig",
            Query = string.Empty,
            Fragment = string.Empty
        };
        return builder.Uri;
    }

    private static bool IsAllowedHost(string host) => host.Equals("copimine.ru", StringComparison.OrdinalIgnoreCase)
        || host.Equals("www.copimine.ru", StringComparison.OrdinalIgnoreCase)
        || host.Equals("cdn.copimine.ru", StringComparison.OrdinalIgnoreCase)
        || host.EndsWith(".copimine.ru", StringComparison.OrdinalIgnoreCase);

    private static readonly JsonSerializerOptions JsonOptions = new(JsonSerializerDefaults.Web)
    {
        PropertyNameCaseInsensitive = true
    };
}
