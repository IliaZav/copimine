using System.IO.Compression;

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
            foreach (var modPath in Directory.EnumerateFiles(modsDirectory, "*.jar", SearchOption.TopDirectoryOnly))
            {
                try
                {
                    using var archive = ZipFile.OpenRead(modPath);
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
}
