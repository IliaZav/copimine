using System.IO.Compression;
using System.Security.Cryptography;
using System.Text.Json;
using CopiMineLauncher.Core.Filesystem;
using CopiMineLauncher.Core.Manifest;
using CopiMineLauncher.Infrastructure.Updates;

namespace CopiMineLauncher.Infrastructure.Provisioning;

public sealed record OfflineMinecraftBaselineMetadata(
    int SchemaVersion,
    string MinecraftVersion,
    string FabricLoaderVersion,
    string ArchiveFileName,
    long SizeBytes,
    string Sha256);

public sealed record OfflineMinecraftBaselineResult(
    bool Available,
    bool Applied,
    bool AlreadyPresent,
    string MinecraftVersion,
    string FabricLoaderVersion,
    string Diagnostic);

public sealed class OfflineMinecraftBaselineException : Exception
{
    public OfflineMinecraftBaselineException(string code, string message, Exception? innerException = null)
        : base(message, innerException)
    {
        Code = code;
    }

    public string Code { get; }
}

public interface IOfflineMinecraftBaseline
{
    Task<OfflineMinecraftBaselineResult> EnsureAsync(
        string instanceRoot,
        string minecraftVersion,
        string fabricLoaderVersion,
        CancellationToken cancellationToken);
}

/// <summary>
/// Installs the immutable Minecraft/Fabric data that is shipped with the
/// offline installer. User-owned files are never replaced: managed game
/// directories are swapped transactionally, while defaults such as options
/// and config are copied only when they are missing.
/// </summary>
public sealed class OfflineMinecraftBaseline : IOfflineMinecraftBaseline
{
    private const int SupportedSchemaVersion = 1;
    private const string MetadataFileName = "offline-minecraft-baseline.json";
    private const string DefaultArchiveFileName = "offline-minecraft-baseline.zip";
    private static readonly string[] ManagedDirectories = ["assets", "libraries", "versions"];
    private static readonly string[] OptionalDirectories = [".fabric", "config", "CustomSkinLoader", "resourcepacks", "shaderpacks"];
    private static readonly string[] OptionalFiles = ["options.txt", "optionsof.txt"];
    private static readonly JsonSerializerOptions JsonOptions = new(JsonSerializerDefaults.Web);
    private readonly string bootstrapRoot;

    public OfflineMinecraftBaseline(string bootstrapRoot)
    {
        this.bootstrapRoot = Path.GetFullPath(bootstrapRoot ?? throw new ArgumentNullException(nameof(bootstrapRoot)));
    }

    public async Task<OfflineMinecraftBaselineResult> EnsureAsync(
        string instanceRoot,
        string minecraftVersion,
        string fabricLoaderVersion,
        CancellationToken cancellationToken)
    {
        ArgumentException.ThrowIfNullOrWhiteSpace(instanceRoot);
        ArgumentException.ThrowIfNullOrWhiteSpace(minecraftVersion);
        ArgumentException.ThrowIfNullOrWhiteSpace(fabricLoaderVersion);

        var metadataPath = Path.Combine(bootstrapRoot, MetadataFileName);
        if (!File.Exists(metadataPath))
        {
            return new(false, false, false, minecraftVersion, fabricLoaderVersion, "Офлайн-пакет Minecraft не включён в эту сборку лаунчера.");
        }

        var metadata = await ReadMetadataAsync(metadataPath, cancellationToken);
        ValidateMetadata(metadata, minecraftVersion, fabricLoaderVersion);

        var root = Path.GetFullPath(instanceRoot);
        Directory.CreateDirectory(root);
        if (IsReady(root, metadata))
        {
            return new(true, false, true, metadata.MinecraftVersion, metadata.FabricLoaderVersion, "Офлайн-пакет Minecraft уже установлен.");
        }

        var archivePath = ResolveArchivePath(metadata);
        if (!File.Exists(archivePath))
        {
            throw new OfflineMinecraftBaselineException(
                "OFFLINE_BASELINE_MISSING",
                $"В установщике отсутствует архив Minecraft baseline: {archivePath}");
        }

        return await EnsureArchiveAsync(root, metadata, archivePath, cancellationToken);
    }

    public async Task<OfflineMinecraftBaselineResult> EnsureHostedAsync(
        string instanceRoot,
        string minecraftVersion,
        string fabricLoaderVersion,
        MinecraftRuntimeMetadata runtime,
        IResumableDownloadManager downloads,
        CancellationToken cancellationToken,
        IProgress<DownloadProgress>? progress = null)
    {
        ArgumentException.ThrowIfNullOrWhiteSpace(instanceRoot);
        ArgumentException.ThrowIfNullOrWhiteSpace(minecraftVersion);
        ArgumentException.ThrowIfNullOrWhiteSpace(fabricLoaderVersion);
        ArgumentNullException.ThrowIfNull(runtime);
        ArgumentNullException.ThrowIfNull(downloads);

        ValidateHostedRuntime(runtime);
        var metadata = new OfflineMinecraftBaselineMetadata(
            SupportedSchemaVersion,
            minecraftVersion,
            fabricLoaderVersion,
            DefaultArchiveFileName,
            runtime.SizeBytes,
            runtime.Sha256);
        ValidateMetadata(metadata, minecraftVersion, fabricLoaderVersion);

        var root = Path.GetFullPath(instanceRoot);
        Directory.CreateDirectory(root);
        if (IsReady(root, metadata))
        {
            return new(true, false, true, metadata.MinecraftVersion, metadata.FabricLoaderVersion, "Серверный пакет Minecraft уже установлен.");
        }

        var cachePath = Path.Combine(
            root,
            ".copimine",
            "cache",
            "minecraft-runtime",
            runtime.Sha256.ToLowerInvariant() + ".zip");
        string archivePath;
        try
        {
            var source = new Uri(runtime.Url, UriKind.Absolute);
            archivePath = downloads is IProgressiveDownloadManager progressive
                ? await progressive.DownloadAsync(
                    source,
                    cachePath,
                    runtime.SizeBytes,
                    runtime.Sha256,
                    progress,
                    cancellationToken)
                : await downloads.DownloadAsync(
                    source,
                    cachePath,
                    runtime.SizeBytes,
                    runtime.Sha256,
                    cancellationToken);
        }
        catch (OperationCanceledException)
        {
            throw;
        }
        catch (Exception exception) when (exception is InvalidDataException or IOException or HttpRequestException)
        {
            throw new OfflineMinecraftBaselineException(
                "HOSTED_MINECRAFT_RUNTIME_DOWNLOAD_FAILED",
                "Не удалось скачать проверенный Minecraft/Fabric runtime с сервера CopiMine.",
                exception);
        }

        return await EnsureArchiveAsync(root, metadata, archivePath, cancellationToken, progress);
    }

    private static async Task<OfflineMinecraftBaselineResult> EnsureArchiveAsync(
        string root,
        OfflineMinecraftBaselineMetadata metadata,
        string archivePath,
        CancellationToken cancellationToken,
        IProgress<DownloadProgress>? progress = null)
    {
        await VerifyArchiveAsync(archivePath, metadata, cancellationToken);
        var transactionId = Guid.NewGuid().ToString("N");
        var stagingRoot = Path.Combine(root, ".copimine", "staging", "offline-baseline", transactionId);
        var extractedRoot = Path.Combine(stagingRoot, "extracted");
        var backupRoot = Path.Combine(root, ".copimine", "offline-baseline-backups", transactionId);
        var movedDirectories = new List<(string Target, string Backup)>();

        try
        {
            Directory.CreateDirectory(extractedRoot);
            // ZIP extraction is CPU/IO heavy for the bundled Minecraft runtime.
            // Keep it off the WPF dispatcher and report a separate phase so a
            // cached archive does not look frozen at the download percentage.
            await Task.Run(
                () => ExtractZipSafely(archivePath, extractedRoot, progress, cancellationToken),
                cancellationToken);
            ValidateExtractedBaseline(extractedRoot, metadata);
            Directory.CreateDirectory(backupRoot);

            foreach (var directoryName in ManagedDirectories)
            {
                var source = Path.Combine(extractedRoot, directoryName);
                var target = Path.Combine(root, directoryName);
                var backup = Path.Combine(backupRoot, directoryName);
                if (Directory.Exists(target))
                {
                    Directory.Move(target, backup);
                    movedDirectories.Add((target, backup));
                }

                Directory.Move(source, target);
            }

            foreach (var directoryName in OptionalDirectories)
            {
                CopyMissingDirectory(Path.Combine(extractedRoot, directoryName), Path.Combine(root, directoryName));
            }

            foreach (var fileName in OptionalFiles)
            {
                CopyMissingFile(Path.Combine(extractedRoot, fileName), Path.Combine(root, fileName));
            }

            await WriteMarkerAsync(root, metadata, cancellationToken);
            return new(true, true, false, metadata.MinecraftVersion, metadata.FabricLoaderVersion, "Офлайн-пакет Minecraft установлен из установщика.");
        }
        catch (OfflineMinecraftBaselineException)
        {
            RestoreMovedDirectories(movedDirectories);
            throw;
        }
        catch (Exception exception) when (exception is IOException or UnauthorizedAccessException or InvalidDataException)
        {
            RestoreMovedDirectories(movedDirectories);
            throw new OfflineMinecraftBaselineException(
                "OFFLINE_BASELINE_COMMIT_FAILED",
                "Не удалось безопасно установить офлайн-пакет Minecraft.",
                exception);
        }
        finally
        {
            TryDeleteDirectory(stagingRoot);
        }
    }

    private static void ValidateHostedRuntime(MinecraftRuntimeMetadata runtime)
    {
        if (!Uri.TryCreate(runtime.Url, UriKind.Absolute, out var uri)
            || !string.Equals(uri.Scheme, Uri.UriSchemeHttps, StringComparison.OrdinalIgnoreCase)
            || !string.IsNullOrEmpty(uri.UserInfo)
            || !new[] { "copimine.ru", "www.copimine.ru", "cdn.copimine.ru" }.Contains(uri.Host, StringComparer.OrdinalIgnoreCase)
            || !uri.AbsolutePath.StartsWith("/launcher/files/", StringComparison.Ordinal))
        {
            throw new OfflineMinecraftBaselineException(
                "HOSTED_MINECRAFT_RUNTIME_URL_INVALID",
                "Minecraft runtime должен загружаться только с разрешённого CopiMine launcher storage.");
        }
    }

    public static bool IsReady(string instanceRoot, OfflineMinecraftBaselineMetadata metadata)
    {
        var root = Path.GetFullPath(instanceRoot);
        var markerPath = Path.Combine(root, ".copimine", "offline-baseline.json");
        if (!File.Exists(markerPath))
        {
            return false;
        }

        try
        {
            using var document = JsonDocument.Parse(File.ReadAllText(markerPath));
            var marker = document.RootElement;
            if (!string.Equals(marker.GetProperty("sha256").GetString(), metadata.Sha256, StringComparison.OrdinalIgnoreCase)
                || !string.Equals(marker.GetProperty("minecraftVersion").GetString(), metadata.MinecraftVersion, StringComparison.Ordinal)
                || !string.Equals(marker.GetProperty("fabricLoaderVersion").GetString(), metadata.FabricLoaderVersion, StringComparison.Ordinal))
            {
                return false;
            }

            return IsMinecraftProfileReady(root, metadata.MinecraftVersion, metadata.FabricLoaderVersion);
        }
        catch (Exception exception) when (exception is JsonException or KeyNotFoundException or InvalidOperationException or IOException)
        {
            return false;
        }
    }

    public static bool IsMinecraftProfileReady(string instanceRoot, string minecraftVersion, string fabricLoaderVersion)
    {
        var root = Path.GetFullPath(instanceRoot);
        var versionName = $"fabric-loader-{fabricLoaderVersion}-{minecraftVersion}";
        var assetIndexes = Path.Combine(root, "assets", "indexes");
        return Directory.Exists(Path.Combine(root, "libraries"))
            && Directory.Exists(assetIndexes)
            && Directory.EnumerateFiles(assetIndexes, "*.json", SearchOption.TopDirectoryOnly).Any()
            && File.Exists(Path.Combine(root, "versions", minecraftVersion, $"{minecraftVersion}.json"))
            && File.Exists(Path.Combine(root, "versions", versionName, $"{versionName}.json"));
    }

    private static async Task<OfflineMinecraftBaselineMetadata> ReadMetadataAsync(string path, CancellationToken cancellationToken)
    {
        try
        {
            await using var stream = File.OpenRead(path);
            return await JsonSerializer.DeserializeAsync<OfflineMinecraftBaselineMetadata>(stream, JsonOptions, cancellationToken)
                ?? throw new InvalidDataException("Offline baseline metadata is empty.");
        }
        catch (Exception exception) when (exception is JsonException or NotSupportedException or IOException)
        {
            throw new OfflineMinecraftBaselineException(
                "OFFLINE_BASELINE_METADATA_INVALID",
                "Метаданные офлайн-пакета Minecraft повреждены.",
                exception);
        }
    }

    private static void ValidateMetadata(
        OfflineMinecraftBaselineMetadata metadata,
        string minecraftVersion,
        string fabricLoaderVersion)
    {
        if (metadata.SchemaVersion != SupportedSchemaVersion
            || !string.Equals(metadata.MinecraftVersion, minecraftVersion, StringComparison.Ordinal)
            || !string.Equals(metadata.FabricLoaderVersion, fabricLoaderVersion, StringComparison.Ordinal))
        {
            throw new OfflineMinecraftBaselineException(
                "OFFLINE_BASELINE_VERSION_MISMATCH",
                "Офлайн-пакет Minecraft не соответствует версии сборки из подписанного manifest.");
        }

        if (!string.Equals(metadata.ArchiveFileName, DefaultArchiveFileName, StringComparison.Ordinal)
            || metadata.SizeBytes <= 0
            || metadata.Sha256 is null
            || metadata.Sha256.Length != 64
            || metadata.Sha256.Any(character => !Uri.IsHexDigit(character)))
        {
            throw new OfflineMinecraftBaselineException(
                "OFFLINE_BASELINE_METADATA_INVALID",
                "Метаданные офлайн-пакета Minecraft содержат небезопасные значения.");
        }
    }

    private string ResolveArchivePath(OfflineMinecraftBaselineMetadata metadata)
    {
        var root = bootstrapRoot.TrimEnd(Path.DirectorySeparatorChar, Path.AltDirectorySeparatorChar) + Path.DirectorySeparatorChar;
        var candidate = Path.GetFullPath(Path.Combine(bootstrapRoot, metadata.ArchiveFileName));
        if (!candidate.StartsWith(root, StringComparison.OrdinalIgnoreCase))
        {
            throw new OfflineMinecraftBaselineException("OFFLINE_BASELINE_PATH_INVALID", "Путь архива Minecraft выходит за пределы bootstrap-каталога.");
        }

        return candidate;
    }

    private static async Task VerifyArchiveAsync(
        string archivePath,
        OfflineMinecraftBaselineMetadata metadata,
        CancellationToken cancellationToken)
    {
        var info = new FileInfo(archivePath);
        if (info.Length != metadata.SizeBytes)
        {
            throw new OfflineMinecraftBaselineException(
                "OFFLINE_BASELINE_SIZE_MISMATCH",
                $"Размер офлайн-пакета Minecraft не совпадает с metadata: {info.Length} вместо {metadata.SizeBytes}.");
        }

        await using var stream = new FileStream(archivePath, FileMode.Open, FileAccess.Read, FileShare.Read, 1024 * 1024, useAsync: true);
        var actual = Convert.ToHexString(await SHA256.HashDataAsync(stream, cancellationToken)).ToLowerInvariant();
        if (!string.Equals(actual, metadata.Sha256, StringComparison.OrdinalIgnoreCase))
        {
            throw new OfflineMinecraftBaselineException(
                "OFFLINE_BASELINE_HASH_MISMATCH",
                "SHA-256 офлайн-пакета Minecraft не совпадает с подписанным package metadata.");
        }
    }

    private static void ExtractZipSafely(
        string archivePath,
        string destinationRoot,
        IProgress<DownloadProgress>? progress,
        CancellationToken cancellationToken)
    {
        var fullRoot = Path.GetFullPath(destinationRoot).TrimEnd(Path.DirectorySeparatorChar) + Path.DirectorySeparatorChar;
        try
        {
            using var archive = ZipFile.OpenRead(archivePath);
            var fileEntries = archive.Entries
                .Where(entry => !string.IsNullOrEmpty(entry.FullName)
                    && !entry.FullName.EndsWith("/", StringComparison.Ordinal)
                    && !entry.FullName.EndsWith("\\", StringComparison.Ordinal))
                .ToArray();
            var totalBytes = fileEntries.Sum(entry => entry.Length);
            var extractedBytes = 0L;
            progress?.Report(new DownloadProgress(0, totalBytes, "extract"));

            foreach (var entry in fileEntries)
            {
                cancellationToken.ThrowIfCancellationRequested();
                var normalizedEntryName = entry.FullName.Replace('\\', '/');
                if (string.IsNullOrEmpty(normalizedEntryName)
                    || normalizedEntryName.EndsWith("/", StringComparison.Ordinal))
                {
                    continue;
                }

                SafeRelativePath safeRelative;
                try
                {
                    safeRelative = SafeRelativePath.Parse(normalizedEntryName);
                }
                catch (ArgumentException exception)
                {
                    throw new OfflineMinecraftBaselineException(
                        "OFFLINE_BASELINE_PATH_INVALID",
                        "Офлайн-пакет Minecraft содержит небезопасный путь.",
                        exception);
                }

                var topLevel = safeRelative.Value.Split('/')[0];
                if (!ManagedDirectories.Contains(topLevel, StringComparer.Ordinal)
                    && !OptionalDirectories.Contains(topLevel, StringComparer.Ordinal)
                    && !OptionalFiles.Contains(safeRelative.Value, StringComparer.Ordinal))
                {
                    throw new OfflineMinecraftBaselineException(
                        "OFFLINE_BASELINE_PATH_INVALID",
                        $"Офлайн-пакет Minecraft содержит неожиданный путь: {safeRelative.Value}");
                }

                var target = Path.GetFullPath(Path.Combine(destinationRoot, safeRelative.Value.Replace('/', Path.DirectorySeparatorChar)));
                if (!target.StartsWith(fullRoot, StringComparison.OrdinalIgnoreCase))
                {
                    throw new OfflineMinecraftBaselineException("OFFLINE_BASELINE_PATH_INVALID", "Офлайн-пакет Minecraft выходит за пределы staging-каталога.");
                }

                Directory.CreateDirectory(Path.GetDirectoryName(target)!);
                entry.ExtractToFile(target, overwrite: true);
                extractedBytes = checked(extractedBytes + entry.Length);
                progress?.Report(new DownloadProgress(extractedBytes, totalBytes, "extract"));
            }
        }
        catch (OfflineMinecraftBaselineException)
        {
            throw;
        }
        catch (InvalidDataException exception)
        {
            throw new OfflineMinecraftBaselineException("OFFLINE_BASELINE_ARCHIVE_INVALID", "ZIP-архив Minecraft baseline повреждён.", exception);
        }
    }

    private static void ValidateExtractedBaseline(string extractedRoot, OfflineMinecraftBaselineMetadata metadata)
    {
        if (!IsMinecraftProfileReady(extractedRoot, metadata.MinecraftVersion, metadata.FabricLoaderVersion))
        {
            throw new OfflineMinecraftBaselineException(
                "OFFLINE_BASELINE_CONTENT_INVALID",
                "Офлайн-пакет Minecraft не содержит полного vanilla/Fabric профиля.");
        }

        foreach (var directoryName in ManagedDirectories)
        {
            if (!Directory.Exists(Path.Combine(extractedRoot, directoryName)))
            {
                throw new OfflineMinecraftBaselineException(
                    "OFFLINE_BASELINE_CONTENT_INVALID",
                    $"В офлайн-пакете отсутствует управляемый каталог {directoryName}.");
            }
        }
    }

    private static void CopyMissingDirectory(string source, string target)
    {
        if (!Directory.Exists(source))
        {
            return;
        }

        foreach (var directory in Directory.EnumerateDirectories(source, "*", SearchOption.AllDirectories))
        {
            var relative = Path.GetRelativePath(source, directory);
            Directory.CreateDirectory(Path.Combine(target, relative));
        }

        foreach (var file in Directory.EnumerateFiles(source, "*", SearchOption.AllDirectories))
        {
            var relative = Path.GetRelativePath(source, file);
            var destination = Path.Combine(target, relative);
            if (File.Exists(destination))
            {
                continue;
            }

            Directory.CreateDirectory(Path.GetDirectoryName(destination)!);
            File.Copy(file, destination);
        }
    }

    private static void CopyMissingFile(string source, string target)
    {
        if (File.Exists(source) && !File.Exists(target))
        {
            Directory.CreateDirectory(Path.GetDirectoryName(target)!);
            File.Copy(source, target);
        }
    }

    private static async Task WriteMarkerAsync(
        string instanceRoot,
        OfflineMinecraftBaselineMetadata metadata,
        CancellationToken cancellationToken)
    {
        var markerDirectory = Path.Combine(instanceRoot, ".copimine");
        Directory.CreateDirectory(markerDirectory);
        var markerPath = Path.Combine(markerDirectory, "offline-baseline.json");
        var temporaryPath = markerPath + ".tmp";
        var marker = new
        {
            schemaVersion = metadata.SchemaVersion,
            minecraftVersion = metadata.MinecraftVersion,
            fabricLoaderVersion = metadata.FabricLoaderVersion,
            sha256 = metadata.Sha256,
            installedAtUtc = DateTimeOffset.UtcNow
        };
        await File.WriteAllTextAsync(temporaryPath, JsonSerializer.Serialize(marker) + Environment.NewLine, cancellationToken);
        File.Move(temporaryPath, markerPath, overwrite: true);
    }

    private static void RestoreMovedDirectories(IEnumerable<(string Target, string Backup)> movedDirectories)
    {
        foreach (var (target, backup) in movedDirectories.Reverse())
        {
            try
            {
                if (Directory.Exists(target))
                {
                    Directory.Delete(target, recursive: true);
                }

                if (Directory.Exists(backup))
                {
                    Directory.Move(backup, target);
                }
            }
            catch
            {
                // The original exception is more useful to the caller; the
                // backup remains on disk for manual recovery diagnostics.
            }
        }
    }

    private static void TryDeleteDirectory(string path)
    {
        try
        {
            if (Directory.Exists(path))
            {
                Directory.Delete(path, recursive: true);
            }
        }
        catch
        {
            // Staging cleanup must not mask a verified baseline error.
        }
    }
}
