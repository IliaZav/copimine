using System.Net;
using System.Text;
using System.Text.Json;

namespace CopiMineLauncher.Infrastructure.Skins;

public sealed class PlayerCosmeticsClient
{
    private static readonly Uri MojangNameEndpoint = new("https://api.mojang.com/users/profiles/minecraft/", UriKind.Absolute);
    private static readonly Uri MojangSessionEndpoint = new("https://sessionserver.mojang.com/session/minecraft/profile/", UriKind.Absolute);
    private static readonly Uri ElyByProfileEndpoint = new("https://skinsystem.ely.by/profile/", UriKind.Absolute);
    private readonly HttpClient httpClient;

    public PlayerCosmeticsClient(HttpClient httpClient)
    {
        this.httpClient = httpClient ?? throw new ArgumentNullException(nameof(httpClient));
    }

    public async Task<PlayerCosmeticsProfile?> ResolveByNicknameAsync(string nickname, CancellationToken cancellationToken)
    {
        if (!IsValidNickname(nickname))
        {
            throw new ArgumentException("Ник должен содержать 3–16 символов A–Z, 0–9 или _.", nameof(nickname));
        }

        var encodedName = Uri.EscapeDataString(nickname.Trim());
        try
        {
            using var nameResponse = await GetAsync(new Uri(MojangNameEndpoint, encodedName), cancellationToken);
            if (nameResponse.StatusCode == HttpStatusCode.OK)
            {
                using var nameDocument = JsonDocument.Parse(await nameResponse.Content.ReadAsStringAsync(cancellationToken));
                if (nameDocument.RootElement.TryGetProperty("id", out var idElement))
                {
                    var profile = await ReadProfileAsync(new Uri(MojangSessionEndpoint, idElement.GetString() ?? string.Empty), nickname, "Mojang", cancellationToken);
                    if (profile is not null)
                    {
                        return profile;
                    }
                }
            }
        }
        catch (HttpRequestException)
        {
            // The alternate source below can still serve non-premium/Ely.by profiles.
        }

        try
        {
            using var elyResponse = await GetAsync(new Uri(ElyByProfileEndpoint, encodedName + "?unsigned=false"), cancellationToken);
            if (elyResponse.StatusCode is HttpStatusCode.OK or HttpStatusCode.NonAuthoritativeInformation)
            {
                return await ReadProfileAsync(elyResponse, nickname, "Ely.by", cancellationToken);
            }
        }
        catch (HttpRequestException)
        {
            // Keep the launcher usable when a public provider is unavailable.
        }

        return null;
    }

    private async Task<PlayerCosmeticsProfile?> ReadProfileAsync(Uri uri, string nickname, string source, CancellationToken cancellationToken)
    {
        using var response = await GetAsync(uri, cancellationToken);
        return await ReadProfileAsync(response, nickname, source, cancellationToken);
    }

    private static async Task<PlayerCosmeticsProfile?> ReadProfileAsync(HttpResponseMessage response, string nickname, string source, CancellationToken cancellationToken)
    {
        if (!response.IsSuccessStatusCode)
        {
            return null;
        }

        var payload = await response.Content.ReadAsStringAsync(cancellationToken);
        using var document = JsonDocument.Parse(payload);
        if (!document.RootElement.TryGetProperty("properties", out var properties)
            || properties.ValueKind != JsonValueKind.Array)
        {
            return null;
        }

        foreach (var property in properties.EnumerateArray())
        {
            if (!property.TryGetProperty("name", out var propertyName)
                || !string.Equals(propertyName.GetString(), "textures", StringComparison.OrdinalIgnoreCase)
                || !property.TryGetProperty("value", out var value))
            {
                continue;
            }

            try
            {
                var encoded = value.GetString() ?? string.Empty;
                var padding = encoded.Length % 4;
                if (padding != 0) encoded = encoded.PadRight(encoded.Length + (4 - padding), '=');
                var decoded = Encoding.UTF8.GetString(Convert.FromBase64String(encoded));
                using var textureDocument = JsonDocument.Parse(decoded);
                var textures = textureDocument.RootElement.GetProperty("textures");
                var skin = ReadTexture(textures, "SKIN");
                var cape = ReadTexture(textures, "CAPE");
                var slim = textures.TryGetProperty("SKIN", out var skinElement)
                    && skinElement.TryGetProperty("metadata", out var metadata)
                    && metadata.TryGetProperty("model", out var model)
                    && string.Equals(model.GetString(), "slim", StringComparison.OrdinalIgnoreCase);
                return new(nickname, skin, cape, slim, source);
            }
            catch (FormatException)
            {
                return null;
            }
            catch (JsonException)
            {
                return null;
            }
        }

        return null;
    }

    private async Task<HttpResponseMessage> GetAsync(Uri uri, CancellationToken cancellationToken)
    {
        using var timeout = CancellationTokenSource.CreateLinkedTokenSource(cancellationToken);
        timeout.CancelAfter(TimeSpan.FromSeconds(10));
        return await httpClient.GetAsync(uri, HttpCompletionOption.ResponseHeadersRead, timeout.Token);
    }

    private static Uri? ReadTexture(JsonElement textures, string property)
    {
        if (!textures.TryGetProperty(property, out var texture)
            || !texture.TryGetProperty("url", out var url)
            || !Uri.TryCreate(url.GetString(), UriKind.Absolute, out var raw)
            || !CosmeticTextureSources.TryNormalize(raw, out var normalized))
        {
            return null;
        }

        return normalized;
    }

    public static bool IsValidNickname(string? nickname) =>
        !string.IsNullOrWhiteSpace(nickname)
        && nickname.Length is >= 3 and <= 16
        && nickname.All(character => char.IsAsciiLetterOrDigit(character) || character == '_');
}
