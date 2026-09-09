using System.IO;
using System.Text.Json;
using CopiMineLauncher.Infrastructure.Launch;

namespace CopiMineLauncher.App;

public sealed record LauncherSettings(
    int MaximumRamMb = 4096,
    int ResolutionWidth = 1280,
    int ResolutionHeight = 720,
    bool Fullscreen = false);

public sealed class LauncherSettingsStore
{
    private readonly string settingsPath;

    public LauncherSettingsStore(string stateRoot)
    {
        settingsPath = Path.Combine(Path.GetFullPath(stateRoot), "launcher-settings.json");
    }

    public LauncherSettings Load()
    {
        if (!File.Exists(settingsPath))
        {
            return new LauncherSettings();
        }

        try
        {
            var settings = JsonSerializer.Deserialize<LauncherSettings>(File.ReadAllText(settingsPath));
            return settings is not null && IsValid(settings) ? settings : new LauncherSettings();
        }
        catch (JsonException)
        {
            return new LauncherSettings();
        }
        catch (IOException)
        {
            return new LauncherSettings();
        }
    }

    public void Save(LauncherSettings settings)
    {
        ArgumentNullException.ThrowIfNull(settings);
        if (!IsValid(settings))
        {
            throw new ArgumentOutOfRangeException(nameof(settings), "Launcher settings are outside the supported range.");
        }

        var directory = Path.GetDirectoryName(settingsPath)!;
        Directory.CreateDirectory(directory);
        var temporary = settingsPath + ".tmp";
        var json = JsonSerializer.Serialize(settings, new JsonSerializerOptions { WriteIndented = true });
        File.WriteAllText(temporary, json + Environment.NewLine);
        File.Move(temporary, settingsPath, overwrite: true);
    }

    public static bool IsValid(LauncherSettings settings) =>
        settings.MaximumRamMb is >= LauncherMemoryLimits.MinimumRamMb
        && settings.MaximumRamMb <= LauncherMemoryLimits.MaximumRamMb
        && settings.ResolutionWidth is >= 800 and <= 7680
        && settings.ResolutionHeight is >= 600 and <= 4320;
}
