using System.IO;
using System.Text.Json;
using CopiMineLauncher.Infrastructure.Binding;

namespace CopiMineLauncher.App;

public sealed record LauncherBindingState(
    bool Linked = false,
    string SiteAccountId = "",
    string SiteUsername = "",
    string MinecraftName = "",
    string AccessToken = "");

public sealed class LauncherDeviceIdentityStore
{
    private readonly string path;

    public LauncherDeviceIdentityStore(string stateRoot)
    {
        path = Path.Combine(Path.GetFullPath(stateRoot), "launcher-device-id.txt");
    }

    public string LoadOrCreate()
    {
        try
        {
            if (File.Exists(path))
            {
                var existing = File.ReadAllText(path).Trim();
                if (IsValid(existing)) return existing;
            }

            Directory.CreateDirectory(Path.GetDirectoryName(path)!);
            var created = $"cm-{Guid.NewGuid():N}";
            var temporary = path + ".tmp";
            File.WriteAllText(temporary, created + Environment.NewLine);
            File.Move(temporary, path, overwrite: true);
            return created;
        }
        catch (IOException exception)
        {
            throw new InvalidOperationException("Не удалось сохранить идентификатор Launcher.", exception);
        }
    }

    private static bool IsValid(string value) =>
        value.Length is >= 16 and <= 128
        && value.All(character => char.IsAsciiLetterOrDigit(character) || character is '.' or '_' or ':' or '-');
}

public sealed class LauncherBindingStateStore
{
    private readonly string path;
    private readonly string pendingChallengePath;

    public LauncherBindingStateStore(string stateRoot)
    {
        var root = Path.GetFullPath(stateRoot);
        path = Path.Combine(root, "launcher-binding.json");
        pendingChallengePath = Path.Combine(root, "launcher-link-pending.json");
    }

    public LauncherBindingState Load()
    {
        if (!File.Exists(path)) return new();
        try
        {
            var state = JsonSerializer.Deserialize<LauncherBindingState>(File.ReadAllText(path));
            return state is { Linked: true } && !string.IsNullOrWhiteSpace(state.SiteAccountId)
                ? state
                : new();
        }
        catch (JsonException)
        {
            return new();
        }
        catch (IOException)
        {
            return new();
        }
    }

    public void Save(LauncherBindingState state)
    {
        ArgumentNullException.ThrowIfNull(state);
        Directory.CreateDirectory(Path.GetDirectoryName(path)!);
        var temporary = path + ".tmp";
        File.WriteAllText(temporary, JsonSerializer.Serialize(state, new JsonSerializerOptions { WriteIndented = true }) + Environment.NewLine);
        File.Move(temporary, path, overwrite: true);
    }

    public void SavePendingChallenge(LauncherLinkChallenge challenge)
    {
        ArgumentNullException.ThrowIfNull(challenge);
        if (!LauncherProtocolCallbackParser.IsSafeToken(challenge.ChallengeId, 16, 96)
            || !LauncherProtocolCallbackParser.IsSafeToken(challenge.PollToken, 16, 128)
            || challenge.ExpiresAtUtc <= DateTimeOffset.UtcNow
            || !IsAllowedAuthorizationUrl(challenge.AuthorizationUrl))
        {
            throw new ArgumentException("Нельзя сохранить недействительный запрос привязки.", nameof(challenge));
        }

        var document = new PendingLauncherChallengeDocument(
            challenge.ChallengeId,
            challenge.PollToken,
            challenge.AuthorizationUrl.AbsoluteUri,
            challenge.ExpiresAtUtc,
            challenge.MinecraftName);
        WriteJsonAtomically(pendingChallengePath, document);
    }

    public LauncherLinkChallenge? LoadPendingChallenge()
    {
        if (!File.Exists(pendingChallengePath)) return null;

        try
        {
            var document = JsonSerializer.Deserialize<PendingLauncherChallengeDocument>(File.ReadAllText(pendingChallengePath));
            if (document is null
                || !LauncherProtocolCallbackParser.IsSafeToken(document.ChallengeId, 16, 96)
                || !LauncherProtocolCallbackParser.IsSafeToken(document.PollToken, 16, 128)
                || document.ExpiresAtUtc <= DateTimeOffset.UtcNow
                || !Uri.TryCreate(document.AuthorizationUrl, UriKind.Absolute, out var authorizationUrl)
                || !IsAllowedAuthorizationUrl(authorizationUrl))
            {
                ClearPendingChallenge();
                return null;
            }

            return new LauncherLinkChallenge(
                document.ChallengeId,
                document.PollToken,
                authorizationUrl,
                document.ExpiresAtUtc,
                document.MinecraftName ?? string.Empty);
        }
        catch (JsonException)
        {
            ClearPendingChallenge();
            return null;
        }
        catch (IOException)
        {
            return null;
        }
    }

    public void ClearPendingChallenge()
    {
        try
        {
            if (File.Exists(pendingChallengePath)) File.Delete(pendingChallengePath);
        }
        catch (IOException)
        {
        }
        catch (UnauthorizedAccessException)
        {
        }
    }

    private static bool IsAllowedAuthorizationUrl(Uri uri)
    {
        var production = string.Equals(uri.Scheme, Uri.UriSchemeHttps, StringComparison.OrdinalIgnoreCase)
            && (string.Equals(uri.Host, "copimine.ru", StringComparison.OrdinalIgnoreCase)
                || string.Equals(uri.Host, "www.copimine.ru", StringComparison.OrdinalIgnoreCase)
                || uri.Host.EndsWith(".copimine.ru", StringComparison.OrdinalIgnoreCase));
        var local = string.Equals(uri.Scheme, Uri.UriSchemeHttp, StringComparison.OrdinalIgnoreCase) && uri.IsLoopback;
        return (production || local) && uri.AbsolutePath.Equals("/cabinet/link.html", StringComparison.Ordinal);
    }

    private static void WriteJsonAtomically<T>(string destination, T value)
    {
        Directory.CreateDirectory(Path.GetDirectoryName(destination)!);
        var temporary = destination + ".tmp";
        File.WriteAllText(temporary, JsonSerializer.Serialize(value, new JsonSerializerOptions { WriteIndented = true }) + Environment.NewLine);
        File.Move(temporary, destination, overwrite: true);
    }

    private sealed record PendingLauncherChallengeDocument(
        string ChallengeId,
        string PollToken,
        string AuthorizationUrl,
        DateTimeOffset ExpiresAtUtc,
        string? MinecraftName);
}
