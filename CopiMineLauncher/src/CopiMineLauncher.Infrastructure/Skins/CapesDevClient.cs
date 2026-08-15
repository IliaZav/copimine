using System.Net;
using System.Text.Json;

namespace CopiMineLauncher.Infrastructure.Skins;

/// <summary>
/// Reads the public, read-only capes.dev player index. It is a provider index,
/// not an account mutation API: the launcher only downloads a texture selected
/// by the user and stores it in CustomSkinLoader's local directory.
/// </summary>
public sealed class CapesDevClient
{
    private static readonly Uri PlayerEndpoint = new("https://api.capes.dev/load/", UriKind.Absolute);
    private readonly HttpClient httpClient;

    public CapesDevClient(HttpClient httpClient)
    {
        this.httpClient = httpClient ?? throw new ArgumentNullException(nameof(httpClient));
    }

    public async Task<IReadOnlyList<CapeCatalogItem>> GetPlayerCapesAsync(string playerName, CancellationToken cancellationToken)
    {
        if (!PlayerCosmeticsClient.IsValidNickname(playerName))
        {
            throw new ArgumentException("Ник должен содержать 3–16 символов A–Z, 0–9 или _.", nameof(playerName));
        }

        using var timeout = CancellationTokenSource.CreateLinkedTokenSource(cancellationToken);
        timeout.CancelAfter(TimeSpan.FromSeconds(12));
        using var request = new HttpRequestMessage(HttpMethod.Get, new Uri(PlayerEndpoint, Uri.EscapeDataString(playerName.Trim())));
        request.Headers.UserAgent.ParseAdd("CopiMineLauncher/1.0 (+https://copimine.ru/launcher.html)");
        using var response = await httpClient.SendAsync(request, HttpCompletionOption.ResponseHeadersRead, timeout.Token);
        if (response.StatusCode is HttpStatusCode.NotFound or HttpStatusCode.NoContent)
        {
            return Array.Empty<CapeCatalogItem>();
        }

        response.EnsureSuccessStatusCode();
        if (response.Content.Headers.ContentLength > 4 * 1024 * 1024)
        {
            throw new InvalidDataException("Ответ каталога плащей превышает безопасный размер.");
        }

        var payload = await response.Content.ReadAsStringAsync(timeout.Token);
        if (payload.Length > 4 * 1024 * 1024)
        {
            throw new InvalidDataException("Ответ каталога плащей превышает безопасный размер.");
        }

        using var document = JsonDocument.Parse(payload);
        if (document.RootElement.ValueKind != JsonValueKind.Object)
        {
            return Array.Empty<CapeCatalogItem>();
        }

        var result = new List<CapeCatalogItem>();
        foreach (var property in document.RootElement.EnumerateObject())
        {
            var cape = property.Value;
            if (cape.ValueKind != JsonValueKind.Object
                || !cape.TryGetProperty("exists", out var exists)
                || exists.ValueKind != JsonValueKind.True)
            {
                continue;
            }

            var rawUrl = ReadString(cape, "imageUrl")
                ?? ReadNestedString(cape, "imageUrls", "base", "full");
            if (!Uri.TryCreate(rawUrl, UriKind.Absolute, out var source)
                || !CosmeticTextureSources.TryNormalize(source, out var textureUrl))
            {
                continue;
            }

            var player = ReadString(cape, "playerName") ?? playerName.Trim();
            var animated = cape.TryGetProperty("imageUrls", out var imageUrls)
                && imageUrls.TryGetProperty("animated", out var animatedUrls)
                && animatedUrls.ValueKind == JsonValueKind.Object
                && animatedUrls.EnumerateObject().Any();
            result.Add(new(property.Name, textureUrl, player, animated, "capes.dev"));
        }

        return result
            .OrderBy(item => item.Type.Equals("minecraft", StringComparison.OrdinalIgnoreCase) ? 0 : 1)
            .ThenBy(item => item.Type, StringComparer.OrdinalIgnoreCase)
            .ToArray();
    }

    private static string? ReadString(JsonElement element, string property) =>
        element.TryGetProperty(property, out var value) && value.ValueKind == JsonValueKind.String
            ? value.GetString()
            : null;

    private static string? ReadNestedString(JsonElement element, params string[] properties)
    {
        var current = element;
        foreach (var property in properties)
        {
            if (!current.TryGetProperty(property, out current))
            {
                return null;
            }
        }

        return current.ValueKind == JsonValueKind.String ? current.GetString() : null;
    }
}
