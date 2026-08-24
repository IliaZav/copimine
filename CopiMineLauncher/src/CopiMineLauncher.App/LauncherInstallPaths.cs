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
            // Velopack's normal layout is <selected-root>\current. Some
            // existing installs keep the app one level deeper at
            // <selected-root>\Launcher\current; both layouts must point at
            // the same selected root so the game instance is never redirected
            // to an unrelated default directory.
            return string.Equals(currentDirectory.Parent.Name, "Launcher", StringComparison.OrdinalIgnoreCase)
                   && currentDirectory.Parent.Parent is not null
                ? currentDirectory.Parent.Parent.FullName
                : currentDirectory.Parent.FullName;
        }

        // Portable/direct installs do not have a Velopack `current` folder.
        // Treat the executable directory itself as the selected install root
        // instead of silently falling back to %LOCALAPPDATA%.
        return baseDirectory;
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
            var isPreferred = string.Equals(candidate, preferred, StringComparison.OrdinalIgnoreCase);
            if ((!isPreferred && IsUnderTemporaryDirectory(candidate)) || !HasLauncherInstanceEvidence(candidate))
            {
                continue;
            }

            return candidate;
        }

        return preferred;
    }

    public static string ResolveLauncherBootstrapRoot(string? applicationBaseDirectory = null) =>
        Path.Combine(
            Path.GetFullPath(applicationBaseDirectory ?? AppContext.BaseDirectory),
            "launcher-bootstrap");

    public static bool HasBundledOfflineMinecraftBaseline(string? applicationBaseDirectory = null)
    {
        var bootstrapRoot = ResolveLauncherBootstrapRoot(applicationBaseDirectory);
        return File.Exists(Path.Combine(bootstrapRoot, "offline-minecraft-baseline.json"))
            && File.Exists(Path.Combine(bootstrapRoot, "offline-minecraft-baseline.zip"));
    }

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

    public static Uri ResolveLocalBindingBaseUrl()
    {
        var configured = Environment.GetEnvironmentVariable("COPIMINE_LAUNCHER_LOCAL_BASE_URL");
        foreach (var candidate in new[] { TryParseUri(configured) })
        {
            if (candidate is not null && IsLoopbackStagingUrl(candidate))
            {
                return EnsureTrailingSlash(candidate);
            }
        }

        return new Uri("http://127.0.0.1:8090/", UriKind.Absolute);
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

    private static Uri? TryParseUri(string? value) =>
        Uri.TryCreate(value, UriKind.Absolute, out var uri) ? uri : null;

    private static Uri EnsureTrailingSlash(Uri value) =>
        value.AbsoluteUri.EndsWith("/", StringComparison.Ordinal)
            ? value
            : new Uri(value.AbsoluteUri + "/", UriKind.Absolute);

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
            var attributes = File.GetAttributes(root);
            if ((attributes & FileAttributes.ReparsePoint) != 0)
            {
                return false;
            }

            var markerPath = Path.Combine(root, ".copimine", "instance.json");
            if (!File.Exists(markerPath))
            {
                return false;
            }

            using var document = JsonDocument.Parse(File.ReadAllText(markerPath));
            var marker = document.RootElement;
            return marker.GetProperty("schemaVersion").GetInt32() == 1
                && string.Equals(marker.GetProperty("product").GetString(), "CopiMine", StringComparison.Ordinal)
                && Guid.TryParse(marker.GetProperty("instanceId").GetString(), out _);
        }
        catch (Exception exception) when (exception is IOException or UnauthorizedAccessException or JsonException or KeyNotFoundException or InvalidOperationException)
        {
            return false;
        }
    }

    private static bool IsUnderTemporaryDirectory(string path)
    {
        var fullPath = Path.GetFullPath(path).TrimEnd(Path.DirectorySeparatorChar) + Path.DirectorySeparatorChar;
        var temporaryRoot = Path.GetFullPath(Path.GetTempPath()).TrimEnd(Path.DirectorySeparatorChar) + Path.DirectorySeparatorChar;
        return fullPath.StartsWith(temporaryRoot, StringComparison.OrdinalIgnoreCase);
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
