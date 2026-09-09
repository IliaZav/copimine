using System.Text;

namespace CopiMineLauncher.Infrastructure.Launch;

public static class MinecraftSettingsDefaults
{
    private static readonly (string Key, string Value)[] Defaults =
    [
        ("lang", "ru_ru"),
        ("narrator", "0"),
        ("soundCategory_master", "0.15")
    ];

    public static bool EnsureDefaults(string instanceRoot)
    {
        var root = Path.GetFullPath(instanceRoot);
        Directory.CreateDirectory(root);
        var path = Path.Combine(root, "options.txt");
        var lines = File.Exists(path)
            ? File.ReadAllLines(path, Encoding.UTF8).ToList()
            : [];
        var changed = false;
        var selected = MinecraftDefaultSettingsStore.Load(root) ?? new MinecraftDefaultSettings();

        foreach (var (key, value, enabled) in EnumerateDefaults(selected))
        {
            if (!enabled)
            {
                continue;
            }

            if (lines.Any(line => line.StartsWith($"{key}:", StringComparison.Ordinal)))
            {
                continue;
            }

            lines.Add($"{key}:{value}");
            changed = true;
        }

        if (!changed)
        {
            return false;
        }

        var temporary = path + ".tmp";
        File.WriteAllLines(temporary, lines, new UTF8Encoding(encoderShouldEmitUTF8Identifier: false));
        File.Move(temporary, path, overwrite: true);
        return true;
    }

    private static IEnumerable<(string Key, string Value, bool Enabled)> EnumerateDefaults(MinecraftDefaultSettings settings)
    {
        yield return (Defaults[0].Key, Defaults[0].Value, settings.UseRussianLanguage);
        yield return (Defaults[1].Key, Defaults[1].Value, settings.DisableNarrator);
        yield return (Defaults[2].Key, Defaults[2].Value, settings.SetMasterVolumeToFifteenPercent);
    }
}
