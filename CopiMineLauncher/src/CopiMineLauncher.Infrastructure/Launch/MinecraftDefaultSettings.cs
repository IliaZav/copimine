using System.Text.Json;
using System.Text.Json.Serialization;

namespace CopiMineLauncher.Infrastructure.Launch;

public sealed record MinecraftDefaultSettings(
    bool UseRussianLanguage = true,
    bool DisableNarrator = true,
    bool SetMasterVolumeToFifteenPercent = true);

public static class MinecraftDefaultSettingsStore
{
    private const int SupportedSchemaVersion = 1;
    private const string FileName = "minecraft-default-settings.json";
    private static readonly JsonSerializerOptions JsonOptions = new(JsonSerializerDefaults.Web)
    {
        WriteIndented = true
    };

    public static bool IsConfigured(string instanceRoot) => Load(instanceRoot) is not null;

    public static MinecraftDefaultSettings? Load(string instanceRoot)
    {
        var path = ResolvePath(instanceRoot);
        if (!File.Exists(path))
        {
            return null;
        }

        try
        {
            var persisted = JsonSerializer.Deserialize<PersistedSettings>(File.ReadAllText(path), JsonOptions);
            return persisted is { SchemaVersion: SupportedSchemaVersion }
                ? new MinecraftDefaultSettings(
                    persisted.UseRussianLanguage,
                    persisted.DisableNarrator,
                    persisted.SetMasterVolumeToFifteenPercent)
                : null;
        }
        catch (JsonException)
        {
            return null;
        }
        catch (IOException)
        {
            return null;
        }
        catch (UnauthorizedAccessException)
        {
            return null;
        }
    }

    public static void Save(string instanceRoot, MinecraftDefaultSettings settings)
    {
        ArgumentNullException.ThrowIfNull(settings);
        var path = ResolvePath(instanceRoot);
        Directory.CreateDirectory(Path.GetDirectoryName(path)!);
        var temporary = path + ".tmp";
        var persisted = new PersistedSettings(
            SupportedSchemaVersion,
            settings.UseRussianLanguage,
            settings.DisableNarrator,
            settings.SetMasterVolumeToFifteenPercent);
        File.WriteAllText(temporary, JsonSerializer.Serialize(persisted, JsonOptions) + Environment.NewLine);
        File.Move(temporary, path, overwrite: true);
    }

    private static string ResolvePath(string instanceRoot) =>
        Path.Combine(Path.GetFullPath(instanceRoot), ".copimine", FileName);

    private sealed record PersistedSettings(
        [property: JsonPropertyName("schemaVersion")] int SchemaVersion,
        [property: JsonPropertyName("useRussianLanguage")] bool UseRussianLanguage,
        [property: JsonPropertyName("disableNarrator")] bool DisableNarrator,
        [property: JsonPropertyName("setMasterVolumeToFifteenPercent")] bool SetMasterVolumeToFifteenPercent);
}
