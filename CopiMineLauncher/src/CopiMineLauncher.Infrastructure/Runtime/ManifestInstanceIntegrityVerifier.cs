using System.Security.Cryptography;
using CopiMineLauncher.Core.Filesystem;
using CopiMineLauncher.Core.Manifest;

namespace CopiMineLauncher.Infrastructure.Runtime;

public sealed record InstanceIntegrityResult(
    bool IsValid,
    int VerifiedFileCount,
    string? ErrorCode = null,
    string? Diagnostic = null);

public interface IInstanceIntegrityVerifier
{
    Task<InstanceIntegrityResult> VerifyAsync(
        string instanceRoot,
        LauncherManifest manifest,
        CancellationToken cancellationToken);
}

/// <summary>
/// Re-hashes every Launcher-owned payload immediately before Java is started.
/// Reconciliation verifies downloads, but this second check closes the gap
/// between the atomic commit and process launch if a managed file is removed or
/// changed in the meantime.
/// </summary>
public sealed class ManifestInstanceIntegrityVerifier : IInstanceIntegrityVerifier
{
    public async Task<InstanceIntegrityResult> VerifyAsync(
        string instanceRoot,
        LauncherManifest manifest,
        CancellationToken cancellationToken)
    {
        ArgumentException.ThrowIfNullOrWhiteSpace(instanceRoot);
        ArgumentNullException.ThrowIfNull(manifest);

        var root = Path.GetFullPath(instanceRoot);
        var rootPrefix = root.TrimEnd(Path.DirectorySeparatorChar, Path.AltDirectorySeparatorChar)
            + Path.DirectorySeparatorChar;
        var managedEntries = manifest.Files
            .Where(entry => string.Equals(entry.Ownership, "managed", StringComparison.OrdinalIgnoreCase)
                && !string.Equals(entry.InstallPolicy, "PRESERVE", StringComparison.OrdinalIgnoreCase))
            .ToArray();
        var verified = 0;

        foreach (var entry in managedEntries)
        {
            cancellationToken.ThrowIfCancellationRequested();

            SafeRelativePath safePath;
            try
            {
                safePath = SafeRelativePath.Parse(entry.Path);
            }
            catch (ArgumentException exception)
            {
                return Invalid(
                    "MANAGED_FILE_PATH_INVALID",
                    $"Официальный путь файла «{entry.Path}» не прошёл проверку безопасности.",
                    exception);
            }

            var fullPath = Path.GetFullPath(Path.Combine(
                root,
                safePath.Value.Replace('/', Path.DirectorySeparatorChar)));
            if (!fullPath.StartsWith(rootPrefix, StringComparison.OrdinalIgnoreCase))
            {
                return Invalid(
                    "MANAGED_FILE_PATH_INVALID",
                    $"Официальный путь файла «{entry.Path}» выходит за пределы экземпляра.");
            }

            if (!File.Exists(fullPath))
            {
                return Invalid(
                    "MANAGED_FILE_MISSING",
                    $"Обязательный файл сборки отсутствует: {safePath.Value}.");
            }

            try
            {
                var info = new FileInfo(fullPath);
                if (info.Length != entry.SizeBytes)
                {
                    return Invalid(
                        "MANAGED_FILE_SIZE_MISMATCH",
                        $"Размер файла {safePath.Value} не совпадает с подписанным manifest: {info.Length} вместо {entry.SizeBytes}.");
                }

                await using var stream = new FileStream(
                    fullPath,
                    FileMode.Open,
                    FileAccess.Read,
                    FileShare.Read,
                    1024 * 1024,
                    useAsync: true);
                var actualHash = Convert.ToHexString(
                    await SHA256.HashDataAsync(stream, cancellationToken)).ToLowerInvariant();
                if (!string.Equals(actualHash, entry.Sha256, StringComparison.OrdinalIgnoreCase))
                {
                    return Invalid(
                        "MANAGED_FILE_HASH_MISMATCH",
                        $"Контрольная сумма файла {safePath.Value} не совпадает с подписанным manifest.");
                }

                verified++;
            }
            catch (OperationCanceledException)
            {
                throw;
            }
            catch (Exception exception) when (exception is IOException or UnauthorizedAccessException)
            {
                return Invalid(
                    "MANAGED_FILE_READ_FAILED",
                    $"Не удалось проверить файл сборки {safePath.Value}.",
                    exception);
            }
        }

        return new(true, verified);
    }

    private static InstanceIntegrityResult Invalid(
        string code,
        string diagnostic,
        Exception? exception = null) =>
        new(false, 0, code, exception is null ? diagnostic : $"{diagnostic} {exception.Message}");
}
