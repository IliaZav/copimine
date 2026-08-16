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

    public static string ResolveMinecraftRoot(string? applicationBaseDirectory = null)
    {
        // Velopack replaces only its current application directory during an
        // update. Keep the mutable game instance beside that directory. When a
        // user selected a custom root directly (for example D:\\Games\\CopiMine),
        // keep Minecraft under that selected root instead of unexpectedly moving
        // it to D:\\Games\\Minecraft. The default packaged layout uses a
        // Launcher subdirectory and therefore keeps the instance beside it.
        var installRoot = ResolveInstallRoot(applicationBaseDirectory);
        var preferred = ResolvePreferredMinecraftRoot(installRoot);
        foreach (var candidate in EnumerateCompatibilityRoots(installRoot, preferred))
        {
            if (HasLauncherInstanceEvidence(candidate))
            {
                return candidate;
            }
        }

        return preferred;
    }

    public static string ResolveLauncherBootstrapRoot(string? applicationBaseDirectory = null) =>
        Path.Combine(
            Path.GetFullPath(applicationBaseDirectory ?? AppContext.BaseDirectory),
            "launcher-bootstrap");

    public static string ResolveLauncherDataRoot(string? localAppDataRoot = null) =>
        Path.Combine(
            Path.GetFullPath(localAppDataRoot ?? Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData)),
            "CopiMine",
            "LauncherData");

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

    private static string ResolvePreferredMinecraftRoot(string installRoot)
    {
        var installDirectory = new DirectoryInfo(installRoot);
        if (string.Equals(installDirectory.Name, "Launcher", StringComparison.OrdinalIgnoreCase)
            && installDirectory.Parent is not null)
        {
            return Path.Combine(installDirectory.Parent.FullName, "Minecraft");
        }

        return Path.Combine(installRoot, "Minecraft");
    }

    private static IEnumerable<string> EnumerateCompatibilityRoots(string installRoot, string preferred)
    {
        yield return Path.GetFullPath(preferred);

        var parent = Directory.GetParent(installRoot)?.FullName;
        if (!string.IsNullOrWhiteSpace(parent))
        {
            var legacySibling = Path.GetFullPath(Path.Combine(parent, "Minecraft"));
            if (!string.Equals(legacySibling, preferred, StringComparison.OrdinalIgnoreCase))
            {
                yield return legacySibling;
            }
        }
    }

    private static bool HasLauncherInstanceEvidence(string path)
    {
        try
        {
            var root = Path.GetFullPath(path);
            return File.Exists(Path.Combine(root, ".copimine", "managed-state.json"))
                || File.Exists(Path.Combine(root, "servers.dat"))
                || Directory.Exists(Path.Combine(root, "versions", "1.21.1"))
                || Directory.EnumerateFiles(Path.Combine(root, "mods"), "*.jar", SearchOption.TopDirectoryOnly).Any();
        }
        catch (IOException)
        {
            return false;
        }
        catch (UnauthorizedAccessException)
        {
            return false;
        }
    }
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
