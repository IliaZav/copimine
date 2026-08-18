using System.IO.Compression;
using System.Text.Json;

namespace CopiMineLauncher.Infrastructure.Launch;

public sealed class MinecraftPreflightException : Exception
{
    public MinecraftPreflightException(string code, string message, Exception? innerException = null)
        : base(message, innerException)
    {
        Code = code;
    }

    public string Code { get; }
}

public static class MinecraftInstancePreflight
{
    public static void ValidateModArchives(string instanceRoot)
    {
        ArgumentException.ThrowIfNullOrWhiteSpace(instanceRoot);

        var fullRoot = Path.GetFullPath(instanceRoot);
        var modsDirectory = Path.Combine(fullRoot, "mods");
        if (!Directory.Exists(modsDirectory))
        {
            return;
        }

        try
        {
            var modIds = new Dictionary<string, List<string>>(StringComparer.OrdinalIgnoreCase);
            foreach (var modPath in Directory.EnumerateFiles(modsDirectory, "*.jar", SearchOption.TopDirectoryOnly))
            {
                try
                {
                    using var archive = ZipFile.OpenRead(modPath);
                    var modId = TryReadFabricModId(archive);
                    if (!string.IsNullOrWhiteSpace(modId))
                    {
                        if (!modIds.TryGetValue(modId, out var files))
                        {
                            files = new List<string>();
                            modIds.Add(modId, files);
                        }

                        files.Add(Path.GetFileName(modPath));
                    }
                }
                catch (Exception exception) when (exception is InvalidDataException
                    or IOException
                    or UnauthorizedAccessException
                    or ArgumentException
                    or NotSupportedException)
                {
                    throw new MinecraftPreflightException(
                        "INVALID_MOD_ARCHIVE",
                        $"Повреждённый или неполный JAR мода: {Path.GetFullPath(modPath)}. "
                        + "Launcher не удаляет пользовательские моды автоматически — замените или уберите этот файл и повторите проверку.",
                        exception);
                }
            }

            var duplicate = modIds
                .Where(pair => pair.Value.Count > 1)
                .OrderBy(pair => pair.Key, StringComparer.OrdinalIgnoreCase)
                .FirstOrDefault();
            if (!string.IsNullOrWhiteSpace(duplicate.Key))
            {
                throw new MinecraftPreflightException(
                    "DUPLICATE_MOD_ID",
                    $"В папке mods найдены два мода с одинаковым ID «{duplicate.Key}»: {string.Join(", ", duplicate.Value.OrderBy(value => value, StringComparer.OrdinalIgnoreCase))}. "
                    + "Уберите лишнюю версию мода и повторите запуск.");
            }
        }
        catch (MinecraftPreflightException)
        {
            throw;
        }
        catch (Exception exception) when (exception is IOException or UnauthorizedAccessException)
        {
            throw new MinecraftPreflightException(
                "MOD_DIRECTORY_UNREADABLE",
                $"Не удалось прочитать папку модов: {modsDirectory}.",
                exception);
        }
    }

    private static string? TryReadFabricModId(ZipArchive archive)
    {
        var entry = archive.GetEntry("fabric.mod.json");
        if (entry is null || entry.Length <= 0 || entry.Length > 1_048_576)
        {
            return null;
        }

        try
        {
            using var stream = entry.Open();
            using var document = JsonDocument.Parse(stream, new JsonDocumentOptions { MaxDepth = 16 });
            return document.RootElement.ValueKind == JsonValueKind.Object
                && document.RootElement.TryGetProperty("id", out var id)
                && id.ValueKind == JsonValueKind.String
                ? id.GetString()?.Trim()
                : null;
        }
        catch (JsonException)
        {
            // Fabric will report malformed metadata during its own loading. The
            // preflight only adds the duplicate-ID guard and must not reject
            // unrelated libraries that happen to carry an empty descriptor.
            return null;
        }
    }
}
