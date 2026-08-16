using System.Net;
using System.Text;
using System.Text.Json;

namespace CopiMineLauncher.Infrastructure.Binding;

public sealed record LauncherLinkChallenge(
    string ChallengeId,
    string PollToken,
    Uri AuthorizationUrl,
    DateTimeOffset ExpiresAtUtc,
    string MinecraftName);

public sealed record LauncherLinkStatus(
    bool Linked,
    string Status,
    string? SiteAccountId = null,
    string? SiteUsername = null,
    string? MinecraftName = null,
    string? LauncherAccessToken = null);

public sealed record LauncherNicknameChangeResult(
    bool Changed,
    string? MinecraftName = null,
    string? MinecraftUuid = null,
    bool PreservePlayerState = false,
    bool AuthMePasswordPreserved = false);

public sealed class LauncherBindingException : Exception
{
    public LauncherBindingException(string code, string message, Exception? innerException = null)
        : base(message, innerException)
    {
        Code = code;
    }

    public string Code { get; }
}

public interface ILauncherBindingClient
{
    string DeviceId { get; }

    Task<LauncherLinkChallenge> CreateChallengeAsync(string minecraftName, string launcherVersion, CancellationToken cancellationToken);

    Task<LauncherLinkStatus> GetStatusAsync(LauncherLinkChallenge challenge, CancellationToken cancellationToken);

    Task<LauncherNicknameChangeResult> ChangeNicknameAsync(string accessToken, string oldMinecraftName, string newMinecraftName, CancellationToken cancellationToken);
}

public sealed class HttpLauncherBindingClient : ILauncherBindingClient
{
    private static readonly JsonSerializerOptions JsonOptions = new(JsonSerializerDefaults.Web)
    {
        PropertyNameCaseInsensitive = true
    };
    private readonly HttpClient httpClient;
    private readonly Uri baseUri;

    public HttpLauncherBindingClient(HttpClient httpClient, Uri baseUri, string deviceId)
    {
        this.httpClient = httpClient ?? throw new ArgumentNullException(nameof(httpClient));
        this.baseUri = ValidateBaseUri(baseUri);
        DeviceId = ValidateDeviceId(deviceId);
    }

    public string DeviceId { get; }

    public async Task<LauncherLinkChallenge> CreateChallengeAsync(
        string minecraftName,
        string launcherVersion,
        CancellationToken cancellationToken)
    {
        var payload = JsonSerializer.Serialize(new
        {
            device_id = DeviceId,
            minecraft_name = minecraftName,
            launcher_version = launcherVersion
        }, JsonOptions);
        using var request = new HttpRequestMessage(HttpMethod.Post, new Uri(baseUri, "api/launcher/link/challenge"))
        {
            Content = new StringContent(payload, Encoding.UTF8, "application/json")
        };
        using var response = await SendAsync(request, cancellationToken);
        var document = await ReadJsonAsync(response, "LAUNCHER_LINK_CHALLENGE_FAILED", cancellationToken);
        var challengeId = RequiredString(document, "challengeId", "LAUNCHER_LINK_CHALLENGE_INVALID");
        var pollToken = RequiredString(document, "pollToken", "LAUNCHER_LINK_CHALLENGE_INVALID");
        var authorizationUrl = RequiredUri(document, "authorizationUrl", "LAUNCHER_LINK_AUTH_URL_INVALID");
        if (!DateTimeOffset.TryParse(document.GetProperty("expiresAt").ToString(), out var expiresAt))
        {
            throw new LauncherBindingException("LAUNCHER_LINK_CHALLENGE_INVALID", "The Launcher link challenge expiry is invalid.");
        }

        return new(
            challengeId,
            pollToken,
            authorizationUrl,
            expiresAt,
            document.TryGetProperty("minecraftName", out var name) ? name.GetString() ?? minecraftName : minecraftName);
    }

    public async Task<LauncherLinkStatus> GetStatusAsync(LauncherLinkChallenge challenge, CancellationToken cancellationToken)
    {
        ArgumentNullException.ThrowIfNull(challenge);
        var query = string.Join("&", new[]
        {
            $"challenge_id={Uri.EscapeDataString(challenge.ChallengeId)}",
            $"device_id={Uri.EscapeDataString(DeviceId)}",
            $"poll_token={Uri.EscapeDataString(challenge.PollToken)}"
        });
        using var request = new HttpRequestMessage(HttpMethod.Get, new Uri(baseUri, $"api/launcher/link/status?{query}"));
        using var response = await SendAsync(request, cancellationToken);
        var document = await ReadJsonAsync(response, "LAUNCHER_LINK_STATUS_FAILED", cancellationToken);
        return new(
            document.TryGetProperty("linked", out var linked) && linked.GetBoolean(),
            document.TryGetProperty("status", out var status) ? status.GetString() ?? "UNKNOWN" : "UNKNOWN",
            OptionalString(document, "siteAccountId"),
            OptionalString(document, "siteUsername"),
            OptionalString(document, "minecraftName"),
            OptionalString(document, "launcherAccessToken"));
    }

    public async Task<LauncherNicknameChangeResult> ChangeNicknameAsync(
        string accessToken,
        string oldMinecraftName,
        string newMinecraftName,
        CancellationToken cancellationToken)
    {
        if (string.IsNullOrWhiteSpace(accessToken) || string.IsNullOrWhiteSpace(oldMinecraftName) || string.IsNullOrWhiteSpace(newMinecraftName))
        {
            throw new LauncherBindingException("LAUNCHER_NICKNAME_INVALID", "Для смены ника нужна активная привязка Launcher.");
        }

        var payload = JsonSerializer.Serialize(new
        {
            device_id = DeviceId,
            access_token = accessToken,
            old_minecraft_name = oldMinecraftName,
            new_minecraft_name = newMinecraftName
        }, JsonOptions);
        using var request = new HttpRequestMessage(HttpMethod.Post, new Uri(baseUri, "api/launcher/profile/nickname"))
        {
            Content = new StringContent(payload, Encoding.UTF8, "application/json")
        };
        using var response = await SendAsync(request, cancellationToken);
        var document = await ReadJsonAsync(response, "LAUNCHER_NICKNAME_FAILED", cancellationToken);
        return new(
            document.TryGetProperty("changed", out var changed) && changed.ValueKind == JsonValueKind.True,
            OptionalString(document, "minecraftName"),
            OptionalString(document, "minecraftUuid"),
            document.TryGetProperty("preserve_player_state", out var preserved) && preserved.ValueKind == JsonValueKind.True,
            document.TryGetProperty("authmePasswordPreserved", out var passwordPreserved) && passwordPreserved.ValueKind == JsonValueKind.True);
    }

    private async Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken)
    {
        try
        {
            return await httpClient.SendAsync(request, HttpCompletionOption.ResponseHeadersRead, cancellationToken);
        }
        catch (OperationCanceledException)
        {
            throw;
        }
        catch (HttpRequestException exception)
        {
            throw new LauncherBindingException("LAUNCHER_LINK_NETWORK_FAILED", "Сервис привязки Launcher недоступен.", exception);
        }
    }

    private static async Task<JsonElement> ReadJsonAsync(HttpResponseMessage response, string code, CancellationToken cancellationToken)
    {
        var payload = await response.Content.ReadAsStringAsync(cancellationToken);
        if (!response.IsSuccessStatusCode)
        {
            throw new LauncherBindingException(code, $"Сервис привязки Launcher вернул {(int)response.StatusCode} {response.ReasonPhrase}.");
        }

        try
        {
            using var document = JsonDocument.Parse(payload);
            return document.RootElement.Clone();
        }
        catch (JsonException exception)
        {
            throw new LauncherBindingException(code, "Сервис привязки Launcher вернул повреждённый JSON.", exception);
        }
    }

    private static string RequiredString(JsonElement document, string property, string code)
    {
        if (!document.TryGetProperty(property, out var value) || string.IsNullOrWhiteSpace(value.GetString()))
        {
            throw new LauncherBindingException(code, $"В ответе привязки Launcher отсутствует {property}.");
        }

        return value.GetString()!;
    }

    private static Uri RequiredUri(JsonElement document, string property, string code)
    {
        var raw = RequiredString(document, property, code);
        if (!Uri.TryCreate(raw, UriKind.Absolute, out var uri)
            || (!string.Equals(uri.Scheme, Uri.UriSchemeHttps, StringComparison.OrdinalIgnoreCase)
                && !(uri.IsLoopback && string.Equals(uri.Scheme, Uri.UriSchemeHttp, StringComparison.OrdinalIgnoreCase)))
            || (!uri.IsLoopback && !uri.Host.Equals("copimine.ru", StringComparison.OrdinalIgnoreCase) && !uri.Host.EndsWith(".copimine.ru", StringComparison.OrdinalIgnoreCase))
            || !uri.AbsolutePath.Equals("/cabinet/link.html", StringComparison.Ordinal))
        {
            throw new LauncherBindingException(code, "Сервис привязки вернул недопустимый адрес сайта.");
        }

        return uri;
    }

    private static string? OptionalString(JsonElement document, string property) =>
        document.TryGetProperty(property, out var value) && value.ValueKind == JsonValueKind.String
            ? value.GetString()
            : null;

    private static Uri ValidateBaseUri(Uri? value)
    {
        if (value is null || !value.IsAbsoluteUri || (!string.Equals(value.Scheme, Uri.UriSchemeHttps, StringComparison.OrdinalIgnoreCase)
            && !(value.IsLoopback && string.Equals(value.Scheme, Uri.UriSchemeHttp, StringComparison.OrdinalIgnoreCase))))
        {
            throw new ArgumentException("Launcher binding base URI must be HTTPS or a loopback HTTP staging URI.", nameof(value));
        }

        return value.AbsoluteUri.EndsWith("/", StringComparison.Ordinal) ? value : new Uri(value.AbsoluteUri + "/");
    }

    private static string ValidateDeviceId(string? value)
    {
        var deviceId = value?.Trim() ?? string.Empty;
        if (deviceId.Length is < 16 or > 128 || deviceId.Any(character => !(char.IsAsciiLetterOrDigit(character) || character is '.' or '_' or ':' or '-')))
        {
            throw new ArgumentException("Launcher device id is invalid.", nameof(value));
        }

        return deviceId;
    }
}
