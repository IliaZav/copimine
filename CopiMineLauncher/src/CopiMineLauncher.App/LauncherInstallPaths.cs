using System.IO;
using System.Text.Json;

namespace CopiMineLauncher.App;

public static class LauncherInstallPaths
{
    private static readonly Uri ProductionSelfUpdateFeed = new("https://copimine.ru/downloads/launcher/", UriKind.Absolute);

    public static string ResolveInstallRoot(string? applicationBaseDirectory = null)
    {
        var baseDirectory = Path.GetFullPath(applicationBaseDirectory ?? AppContext.BaseDirectory);
        var currentDirectory = new DirectoryInfo(baseDirectory.TrimEnd(Path.DirectorySeparatorChar));
        if (string.Equals(currentDirectory.Name, "current", StringComparison.OrdinalIgnoreCase)
            && currentDirectory.Parent is not null)
        {
            return currentDirectory.Parent.FullName;
        }

        return Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
            "CopiMine",
            "Launcher");
    }

    public static string ResolveMinecraftRoot(string? applicationBaseDirectory = null) =>
        Path.Combine(ResolveInstallRoot(applicationBaseDirectory), "Minecraft");

    public static string ResolveLauncherBootstrapRoot(string? applicationBaseDirectory = null) =>
        Path.Combine(
            Path.GetFullPath(applicationBaseDirectory ?? AppContext.BaseDirectory),
            "launcher-bootstrap");

    public static string ResolveLauncherDataRoot() =>
        Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
            "CopiMine",
            "Launcher");

    public static Uri ResolveSelfUpdateFeed(Uri? stagingBaseUrl = null)
    {
        if (stagingBaseUrl is not null && IsLoopbackStagingUrl(stagingBaseUrl))
        {
            var baseUri = stagingBaseUrl.AbsoluteUri.EndsWith("/", StringComparison.Ordinal)
                ? stagingBaseUrl
                : new Uri(stagingBaseUrl.AbsoluteUri + "/", UriKind.Absolute);
            return new Uri(baseUri, "downloads/launcher/");
        }

        return ProductionSelfUpdateFeed;
    }

    public static bool IsLoopbackStagingEnvironment()
    {
        var value = Environment.GetEnvironmentVariable("COPIMINE_LAUNCHER_STAGING_BASE_URL");
        return Uri.TryCreate(value, UriKind.Absolute, out var stagingBase)
            && stagingBase is not null
            && IsLoopbackStagingUrl(stagingBase);
    }

    private static bool IsLoopbackStagingUrl(Uri? value) =>
        value is { IsAbsoluteUri: true }
        && value.IsLoopback
        && string.Equals(value.Scheme, Uri.UriSchemeHttp, StringComparison.OrdinalIgnoreCase)
        && string.IsNullOrEmpty(value.UserInfo);
}

public sealed class LauncherProfileStore
{
    private readonly string profilePath;

    public LauncherProfileStore(string stateRoot)
    {
        profilePath = Path.Combine(Path.GetFullPath(stateRoot), "player-profile.json");
    }

    public string? LoadPlayerName()
    {
        if (!File.Exists(profilePath))
        {
            return null;
        }

        try
        {
            using var document = JsonDocument.Parse(File.ReadAllText(profilePath));
            var value = document.RootElement.TryGetProperty("playerName", out var name)
                ? name.GetString()
                : null;
            return IsValidPlayerName(value) ? value : null;
        }
        catch (JsonException)
        {
            return null;
        }
        catch (IOException)
        {
            return null;
        }
    }

    public void SavePlayerName(string playerName)
    {
        if (!IsValidPlayerName(playerName))
        {
            throw new ArgumentException("Player name must contain 3–16 Latin letters, digits, or underscores.", nameof(playerName));
        }

        var directory = Path.GetDirectoryName(profilePath)!;
        Directory.CreateDirectory(directory);
        var temporary = profilePath + ".tmp";
        var json = JsonSerializer.Serialize(new { playerName });
        File.WriteAllText(temporary, json + Environment.NewLine);
        File.Move(temporary, profilePath, overwrite: true);
    }

    public static bool IsValidPlayerName(string? playerName) =>
        !string.IsNullOrWhiteSpace(playerName)
        && playerName.Length is >= 3 and <= 16
        && playerName.All(character => char.IsAsciiLetterOrDigit(character) || character == '_');
}
