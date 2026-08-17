using System.Text.RegularExpressions;

namespace CopiMineLauncher.Core.Launch;

public enum MinecraftLaunchFailureKind
{
    Unknown,
    ModResolution,
    ModInitialization,
    Mixin,
    JavaRuntime
}

public sealed record MinecraftLaunchFailureReport(
    MinecraftLaunchFailureKind Kind,
    string Title,
    string Summary,
    string Explanation,
    string LogPath,
    string? SuspectedModId,
    string? SuspectedModFileName,
    bool IsModRelated,
    bool IsLikelyUserMod,
    IReadOnlyList<string> Evidence);

public static class MinecraftLaunchFailureParser
{
    private static readonly Regex ProvidedByPattern = new(
        "provided by ['\"](?<id>[A-Za-z0-9_.-]+)['\"]",
        RegexOptions.IgnoreCase | RegexOptions.CultureInvariant);

    private static readonly Regex ResolutionModPattern = new(
        "(?:Replace|Install|Remove) mod ['\"](?<name>[^'\"]+)['\"](?:\\s+\\((?<id>[A-Za-z0-9_.-]+)\\))?",
        RegexOptions.IgnoreCase | RegexOptions.CultureInvariant);

    private static readonly Regex ModReferencePattern = new(
        "(?:mod|module) ['\"]?(?<id>[A-Za-z0-9_.-]+)['\"]?",
        RegexOptions.IgnoreCase | RegexOptions.CultureInvariant);

    public static MinecraftLaunchFailureReport Parse(
        string? logText,
        string logPath,
        IReadOnlyCollection<string>? userModFileNames = null)
    {
        ArgumentException.ThrowIfNullOrWhiteSpace(logPath);

        var text = logText ?? string.Empty;
        var normalized = text.ToLowerInvariant();
        var resolutionMatch = ResolutionModPattern.Match(text);
        var providedByMatch = ProvidedByPattern.Match(text);
        var modId = FirstNonEmpty(
            resolutionMatch.Groups["id"].Value,
            providedByMatch.Groups["id"].Value,
            FindModReference(text));

        var kind = DetectKind(normalized, modId is not null);
        var suspectedFile = FindUserModFile(modId, userModFileNames);
        var isModRelated = kind is MinecraftLaunchFailureKind.ModResolution
            or MinecraftLaunchFailureKind.ModInitialization
            or MinecraftLaunchFailureKind.Mixin;
        var isLikelyUserMod = isModRelated && suspectedFile is not null;
        var evidence = ExtractEvidence(text);

        var title = BuildTitle(kind, isLikelyUserMod);
        var summary = BuildSummary(kind, modId, suspectedFile, isLikelyUserMod);
        var explanation = BuildExplanation(kind, modId, suspectedFile, isLikelyUserMod);

        return new(
            kind,
            title,
            summary,
            explanation,
            logPath,
            modId,
            suspectedFile,
            isModRelated,
            isLikelyUserMod,
            evidence);
    }

    private static MinecraftLaunchFailureKind DetectKind(string normalizedLog, bool hasModId)
    {
        if (normalizedLog.Contains("mod resolution failed", StringComparison.Ordinal)
            || normalizedLog.Contains("requires", StringComparison.Ordinal) && hasModId
            || normalizedLog.Contains("incompatible mod", StringComparison.Ordinal))
        {
            return MinecraftLaunchFailureKind.ModResolution;
        }

        if (normalizedLog.Contains("mixin", StringComparison.Ordinal)
            || normalizedLog.Contains("invalidinjectionexception", StringComparison.Ordinal)
            || normalizedLog.Contains("mixintransformererror", StringComparison.Ordinal))
        {
            return MinecraftLaunchFailureKind.Mixin;
        }

        if (normalizedLog.Contains("entrypoint stage", StringComparison.Ordinal)
            || normalizedLog.Contains("failed to load", StringComparison.Ordinal)
            || normalizedLog.Contains("could not execute entrypoint", StringComparison.Ordinal))
        {
            return MinecraftLaunchFailureKind.ModInitialization;
        }

        if (normalizedLog.Contains("outofmemoryerror", StringComparison.Ordinal)
            || normalizedLog.Contains("could not reserve enough space", StringComparison.Ordinal)
            || normalizedLog.Contains("could not create the java virtual machine", StringComparison.Ordinal)
            || normalizedLog.Contains("unsupportedclassversionerror", StringComparison.Ordinal))
        {
            return MinecraftLaunchFailureKind.JavaRuntime;
        }

        return MinecraftLaunchFailureKind.Unknown;
    }

    private static string? FindModReference(string text)
    {
        foreach (Match match in ModReferencePattern.Matches(text))
        {
            var value = match.Groups["id"].Value.Trim();
            if (!string.IsNullOrWhiteSpace(value)
                && !value.Equals("resolution", StringComparison.OrdinalIgnoreCase)
                && !value.Equals("loading", StringComparison.OrdinalIgnoreCase))
            {
                return value;
            }
        }

        return null;
    }

    private static string? FindUserModFile(string? modId, IReadOnlyCollection<string>? userModFileNames)
    {
        if (string.IsNullOrWhiteSpace(modId) || userModFileNames is null)
        {
            return null;
        }

        var normalizedId = NormalizeToken(modId);
        return userModFileNames
            .Where(fileName => !string.IsNullOrWhiteSpace(fileName))
            .FirstOrDefault(fileName =>
            {
                var stem = NormalizeToken(Path.GetFileNameWithoutExtension(fileName));
                return stem.Contains(normalizedId, StringComparison.Ordinal)
                    || normalizedId.Contains(stem, StringComparison.Ordinal);
            });
    }

    private static IReadOnlyList<string> ExtractEvidence(string text)
    {
        var lines = text
            .Split(new[] { '\r', '\n' }, StringSplitOptions.RemoveEmptyEntries)
            .Select(line => line.Trim())
            .Where(line => line.Contains("error", StringComparison.OrdinalIgnoreCase)
                || line.Contains("mod resolution", StringComparison.OrdinalIgnoreCase)
                || line.Contains("provided by", StringComparison.OrdinalIgnoreCase)
                || line.Contains("entrypoint", StringComparison.OrdinalIgnoreCase)
                || line.Contains("mixin", StringComparison.OrdinalIgnoreCase)
                || line.Contains("requires", StringComparison.OrdinalIgnoreCase)
                || line.Contains("exception", StringComparison.OrdinalIgnoreCase))
            .Distinct(StringComparer.Ordinal)
            .Take(6)
            .Select(TrimEvidence)
            .ToArray();

        return lines.Length == 0
            ? new[] { "В логе нет строки с точной причиной завершения." }
            : lines;
    }

    private static string TrimEvidence(string line) => line.Length <= 360
        ? line
        : line[..357] + "…";

    private static string BuildTitle(MinecraftLaunchFailureKind kind, bool isLikelyUserMod) =>
        isLikelyUserMod
            ? "Дополнительный мод остановил запуск"
            : kind switch
            {
                MinecraftLaunchFailureKind.ModResolution => "Конфликт модов остановил запуск",
                MinecraftLaunchFailureKind.ModInitialization or MinecraftLaunchFailureKind.Mixin => "Ошибка загрузки модов",
                MinecraftLaunchFailureKind.JavaRuntime => "Minecraft не запустился",
                _ => "Minecraft завершился во время запуска"
            };

    private static string BuildSummary(
        MinecraftLaunchFailureKind kind,
        string? modId,
        string? suspectedFile,
        bool isLikelyUserMod)
    {
        if (isLikelyUserMod && suspectedFile is not null)
        {
            return $"Minecraft не запустился из-за конфликта зависимостей в дополнительном моде «{suspectedFile}».";
        }

        return kind switch
        {
            MinecraftLaunchFailureKind.ModResolution => "Minecraft не запустился из-за конфликта зависимостей между модами.",
            MinecraftLaunchFailureKind.ModInitialization or MinecraftLaunchFailureKind.Mixin =>
                modId is null
                    ? "Minecraft остановился на этапе загрузки модов."
                    : $"Ошибка произошла во время загрузки мода «{modId}».",
            MinecraftLaunchFailureKind.JavaRuntime => "Minecraft не запустился из-за Java или выделенной памяти.",
            _ => "Minecraft закрылся сразу после запуска."
        };
    }

    private static string BuildExplanation(
        MinecraftLaunchFailureKind kind,
        string? modId,
        string? suspectedFile,
        bool isLikelyUserMod)
    {
        if (isLikelyUserMod && suspectedFile is not null)
        {
            return $"Файл «{suspectedFile}» не входит в обязательную сборку CopiMine или не совпадает с ней. "
                + "Уберите его из папки mods либо установите версию, совместимую с Minecraft 1.21.1 и Fabric. "
                + "Полный текст ошибки можно посмотреть в логе.";
        }

        return kind switch
        {
            MinecraftLaunchFailureKind.ModResolution =>
                "Один из модов требует другую версию Minecraft, Fabric или зависимость. "
                + "Проверьте недавно добавленные моды и их версии для Minecraft 1.21.1.",
            MinecraftLaunchFailureKind.ModInitialization or MinecraftLaunchFailureKind.Mixin =>
                "Мод начал загружаться, но его код не подошёл к текущей сборке или конфликтует с другим модом. "
                + "Проверьте недавно добавленные файлы в папке mods.",
            MinecraftLaunchFailureKind.JavaRuntime =>
                "Java не смогла создать процесс Minecraft. Проверьте доступную память и установленный runtime Java 21.",
            _ =>
                "В логе не найден признак конкретного мода. Откройте полный лог, чтобы увидеть точную техническую причину."
        };
    }

    private static string? FirstNonEmpty(params string?[] values) => values
        .Select(value => value?.Trim())
        .FirstOrDefault(value => !string.IsNullOrWhiteSpace(value));

    private static string NormalizeToken(string value) => new(value
        .Where(char.IsLetterOrDigit)
        .Select(char.ToLowerInvariant)
        .ToArray());
}
