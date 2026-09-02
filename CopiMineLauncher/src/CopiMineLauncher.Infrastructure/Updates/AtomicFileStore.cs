using System.Text.Json;
using CopiMineLauncher.Core.Filesystem;
using CopiMineLauncher.Core.Updates;

namespace CopiMineLauncher.Infrastructure.Updates;

public interface IAtomicFileStore
{
    Task<ManagedState> LoadStateAsync(CancellationToken cancellationToken);

    Task<bool> RecoverAsync(CancellationToken cancellationToken);

    Task PrepareAsync(TransactionJournal journal, CancellationToken cancellationToken);

    Task CommitAsync(
        IReadOnlyList<UpdateOperation> operations,
        TransactionJournal journal,
        ManagedState nextState,
        CancellationToken cancellationToken);
}

public sealed class AtomicFileStore : IAtomicFileStore
{
    private const int CurrentStateSchemaVersion = 1;
    private const string InstanceMarkerFileName = "instance.json";
    private static readonly JsonSerializerOptions JsonOptions = new(JsonSerializerDefaults.Web)
    {
        WriteIndented = true
    };

    private readonly string instanceRoot;
    private readonly string metadataRoot;
    private readonly Func<int, bool>? failureInjector;

    public AtomicFileStore(string instanceRoot, Func<int, bool>? failureInjector = null)
    {
        this.instanceRoot = Path.GetFullPath(instanceRoot);
        metadataRoot = Path.Combine(this.instanceRoot, ".copimine");
        this.failureInjector = failureInjector;
    }

    private string JournalPath => Path.Combine(metadataRoot, "update-journal.json");

    private string StatePath => Path.Combine(metadataRoot, "managed-state.json");

    private string InstanceIdentityPath => Path.Combine(metadataRoot, InstanceMarkerFileName);

    public async Task<ManagedState> LoadStateAsync(CancellationToken cancellationToken)
    {
        var instanceId = await EnsureInstanceIdentityAsync(cancellationToken);
        if (!File.Exists(StatePath))
        {
            return ManagedState.Empty with { InstanceId = instanceId };
        }

        await using var stream = File.OpenRead(StatePath);
        var state = await JsonSerializer.DeserializeAsync<ManagedState>(stream, JsonOptions, cancellationToken);
        if (state is null)
        {
            throw new InvalidDataException("managed-state.json is empty");
        }

        if (state.SchemaVersion != CurrentStateSchemaVersion
            || !string.Equals(state.InstanceId, instanceId, StringComparison.Ordinal))
        {
            throw new InvalidDataException("managed-state.json is not trusted for this CopiMine instance; safe recovery is required");
        }

        return state;
    }

    public async Task<bool> RecoverAsync(CancellationToken cancellationToken)
    {
        var instanceId = await EnsureInstanceIdentityAsync(cancellationToken);
        if (!File.Exists(JournalPath))
        {
            return false;
        }

        TransactionJournal journal;
        await using (var stream = File.OpenRead(JournalPath))
        {
            journal = await JsonSerializer.DeserializeAsync<TransactionJournal>(stream, JsonOptions, cancellationToken)
                ?? throw new InvalidDataException("update-journal.json is empty");
        }

        ValidateJournal(journal);

        if (journal.Phase != TransactionPhase.Committed)
        {
            RestoreBackups(journal);
            await WriteStateAsync(journal.PreviousState with { InstanceId = instanceId }, cancellationToken);
        }

        CleanupJournalAndStaging(journal);
        return true;
    }

    public async Task PrepareAsync(TransactionJournal journal, CancellationToken cancellationToken)
    {
        ArgumentNullException.ThrowIfNull(journal);
        if (journal.Phase != TransactionPhase.Prepared)
        {
            throw new ArgumentException("A transaction must be prepared before downloads begin.", nameof(journal));
        }

        ValidateJournal(journal);

        await EnsureInstanceIdentityAsync(cancellationToken);
        Directory.CreateDirectory(Path.Combine(metadataRoot, "staging", journal.TransactionId));
        await WriteJournalAsync(journal, cancellationToken);
    }

    public async Task CommitAsync(
        IReadOnlyList<UpdateOperation> operations,
        TransactionJournal journal,
        ManagedState nextState,
        CancellationToken cancellationToken)
    {
        if (operations.Any(operation => operation.Kind == UpdateOperationKind.Conflict))
        {
            throw new InvalidOperationException("Cannot commit a plan containing conflicts");
        }

        ValidateJournal(journal);
        ValidateTransactionId(journal.TransactionId);
        var normalizedOperations = operations
            .Select(operation => operation with
            {
                StagedPath = operation.Kind is UpdateOperationKind.Add or UpdateOperationKind.Replace
                    ? ResolveJournalPath(operation.StagedPath, "staging", journal.TransactionId)
                    : operation.StagedPath
            })
            .ToArray();

        var instanceId = await EnsureInstanceIdentityAsync(cancellationToken);
        Directory.CreateDirectory(metadataRoot);
        var preparedEntries = normalizedOperations.Select(operation =>
        {
            var targetPath = Resolve(operation.RelativePath);
            var backupPath = File.Exists(targetPath)
                ? Path.Combine(metadataRoot, "backups", journal.TransactionId, operation.RelativePath.Replace('/', Path.DirectorySeparatorChar))
                : null;
            return new TransactionJournalEntry(
                operation.Kind,
                operation.ComponentId,
                operation.RelativePath,
                operation.StagedPath,
                backupPath,
                operation.ExpectedSha256,
                operation.NewSha256);
        }).ToArray();
        var prepared = journal with { Phase = TransactionPhase.Prepared, Entries = preparedEntries };
        await WriteJournalAsync(prepared, cancellationToken);
        var currentJournal = prepared;
        try
        {
            var entries = new List<TransactionJournalEntry>();
            for (var index = 0; index < operations.Count; index++)
            {
                var operation = normalizedOperations[index];
                cancellationToken.ThrowIfCancellationRequested();
                var targetPath = Resolve(operation.RelativePath);
                var preparedEntry = preparedEntries[index];
                if (!string.IsNullOrWhiteSpace(preparedEntry.BackupPath) && File.Exists(targetPath))
                {
                    Directory.CreateDirectory(Path.GetDirectoryName(preparedEntry.BackupPath)!);
                    File.Move(targetPath, preparedEntry.BackupPath, overwrite: true);
                }

                if (operation.Kind is UpdateOperationKind.Add or UpdateOperationKind.Replace)
                {
                    if (string.IsNullOrWhiteSpace(operation.StagedPath) || !File.Exists(operation.StagedPath))
                    {
                        throw new FileNotFoundException("Staged file is missing", operation.StagedPath);
                    }

                    Directory.CreateDirectory(Path.GetDirectoryName(targetPath)!);
                    File.Move(operation.StagedPath, targetPath, overwrite: true);
                }

                entries.Add(new(
                    operation.Kind,
                    operation.ComponentId,
                    operation.RelativePath,
                    operation.StagedPath,
                    preparedEntry.BackupPath,
                    operation.ExpectedSha256,
                    operation.NewSha256));
                currentJournal = currentJournal with { Phase = TransactionPhase.Committing, Entries = entries.ToArray() };
                await WriteJournalAsync(currentJournal, cancellationToken);
                if (failureInjector?.Invoke(index) == true)
                {
                    throw new IOException($"Injected commit failure at operation {index}");
                }
            }

            await WriteStateAsync(nextState with { InstanceId = instanceId }, cancellationToken);
            currentJournal = currentJournal with { Phase = TransactionPhase.Committed };
            await WriteJournalAsync(currentJournal, cancellationToken);
            CleanupJournalAndStaging(currentJournal);
        }
        catch
        {
            RestoreBackups(currentJournal);
            await WriteStateAsync(journal.PreviousState with { InstanceId = instanceId }, CancellationToken.None);
            CleanupJournalAndStaging(currentJournal);
            throw;
        }
    }

    private async Task WriteJournalAsync(TransactionJournal journal, CancellationToken cancellationToken)
    {
        Directory.CreateDirectory(metadataRoot);
        await WriteJsonAtomicallyAsync(JournalPath, journal, cancellationToken);
    }

    private async Task WriteStateAsync(ManagedState state, CancellationToken cancellationToken)
    {
        Directory.CreateDirectory(metadataRoot);
        await WriteJsonAtomicallyAsync(
            StatePath,
            state with { SchemaVersion = CurrentStateSchemaVersion },
            cancellationToken);
    }

    private async Task<string> EnsureInstanceIdentityAsync(CancellationToken cancellationToken)
    {
        Directory.CreateDirectory(metadataRoot);
        if (File.Exists(InstanceIdentityPath))
        {
            try
            {
                await using var stream = File.OpenRead(InstanceIdentityPath);
                using var document = await JsonDocument.ParseAsync(stream, cancellationToken: cancellationToken);
                var root = document.RootElement;
                var schemaVersion = root.GetProperty("schemaVersion").GetInt32();
                var product = root.GetProperty("product").GetString();
                var instanceId = root.GetProperty("instanceId").GetString();
                if (schemaVersion == CurrentStateSchemaVersion
                    && string.Equals(product, "CopiMine", StringComparison.Ordinal)
                    && Guid.TryParse(instanceId, out var parsed))
                {
                    return parsed.ToString("D");
                }
            }
            catch (Exception exception) when (exception is JsonException or KeyNotFoundException or InvalidOperationException or IOException)
            {
                throw new InvalidDataException("CopiMine instance marker is invalid; refusing to infer ownership", exception);
            }

            throw new InvalidDataException("CopiMine instance marker is invalid; refusing to infer ownership");
        }

        var created = Guid.NewGuid().ToString("D");
        await WriteJsonAtomicallyAsync(
            InstanceIdentityPath,
            new { schemaVersion = CurrentStateSchemaVersion, product = "CopiMine", instanceId = created },
            cancellationToken);
        return created;
    }

    private static async Task WriteJsonAtomicallyAsync<T>(string path, T value, CancellationToken cancellationToken)
    {
        var tempPath = path + ".tmp";
        await using (var stream = new FileStream(tempPath, FileMode.Create, FileAccess.Write, FileShare.None, 64 * 1024, useAsync: true))
        {
            await JsonSerializer.SerializeAsync(stream, value, JsonOptions, cancellationToken);
            await stream.FlushAsync(cancellationToken);
            stream.Flush(flushToDisk: true);
        }

        File.Move(tempPath, path, overwrite: true);
    }

    private void RestoreBackups(TransactionJournal journal)
    {
        foreach (var entry in journal.Entries.Reverse())
        {
            var targetPath = Resolve(entry.RelativePath);
            var backupPath = ResolveJournalPath(entry.BackupPath, "backups", journal.TransactionId);
            if (backupPath is not null && File.Exists(backupPath))
            {
                Directory.CreateDirectory(Path.GetDirectoryName(targetPath)!);
                if (File.Exists(targetPath))
                {
                    File.Delete(targetPath);
                }

                File.Move(backupPath, targetPath, overwrite: true);
            }
            else if (entry.Kind == UpdateOperationKind.Add && File.Exists(targetPath))
            {
                File.Delete(targetPath);
            }
        }
    }

    private void CleanupJournalAndStaging(TransactionJournal journal)
    {
        ValidateJournal(journal);
        if (File.Exists(JournalPath))
        {
            File.Delete(JournalPath);
        }

        foreach (var entry in journal.Entries)
        {
            var stagedPath = ResolveJournalPath(entry.StagedPath, "staging", journal.TransactionId);
            if (stagedPath is not null && File.Exists(stagedPath))
            {
                File.Delete(stagedPath);
            }
        }

        var stagingPath = Path.Combine(metadataRoot, "staging", journal.TransactionId);
        if (Directory.Exists(stagingPath))
        {
            Directory.Delete(stagingPath, recursive: true);
        }
    }

    private string Resolve(string relativePath)
    {
        var safe = SafeRelativePath.Parse(relativePath).Value.Replace('/', Path.DirectorySeparatorChar);
        var combined = Path.GetFullPath(Path.Combine(instanceRoot, safe));
        if (!combined.StartsWith(instanceRoot.TrimEnd(Path.DirectorySeparatorChar) + Path.DirectorySeparatorChar, StringComparison.OrdinalIgnoreCase))
        {
            throw new ArgumentException("Path escapes the instance root", nameof(relativePath));
        }

        EnsureNoReparsePoints(combined);
        return combined;
    }

    private void ValidateJournal(TransactionJournal journal)
    {
        ArgumentNullException.ThrowIfNull(journal);
        ValidateTransactionId(journal.TransactionId);
        foreach (var entry in journal.Entries)
        {
            _ = Resolve(entry.RelativePath);
            _ = ResolveJournalPath(entry.StagedPath, "staging", journal.TransactionId);
            _ = ResolveJournalPath(entry.BackupPath, "backups", journal.TransactionId);
        }
    }

    private string? ResolveJournalPath(string? path, string category, string transactionId)
    {
        if (string.IsNullOrWhiteSpace(path))
        {
            return null;
        }

        var transactionRoot = Path.GetFullPath(Path.Combine(metadataRoot, category, transactionId));
        var fullPath = Path.GetFullPath(path);
        var prefix = transactionRoot.TrimEnd(Path.DirectorySeparatorChar) + Path.DirectorySeparatorChar;
        if (!fullPath.StartsWith(prefix, StringComparison.OrdinalIgnoreCase))
        {
            throw new InvalidDataException($"Invalid journal path outside {category}/{transactionId}");
        }

        EnsureNoReparsePoints(fullPath);
        return fullPath;
    }

    private static void ValidateTransactionId(string transactionId)
    {
        if (string.IsNullOrWhiteSpace(transactionId)
            || transactionId is "." or ".."
            || transactionId.IndexOfAny(['/', '\\']) >= 0
            || transactionId.IndexOfAny(Path.GetInvalidFileNameChars()) >= 0)
        {
            throw new InvalidDataException("Invalid transaction identifier in update journal");
        }
    }

    private void EnsureNoReparsePoints(string targetPath)
    {
        var root = instanceRoot.TrimEnd(Path.DirectorySeparatorChar) + Path.DirectorySeparatorChar;
        var relative = Path.GetRelativePath(instanceRoot, targetPath);
        var current = instanceRoot;
        Check(current);
        foreach (var segment in relative.Split(Path.DirectorySeparatorChar, Path.AltDirectorySeparatorChar))
        {
            if (string.IsNullOrEmpty(segment) || segment == ".")
            {
                continue;
            }

            current = Path.Combine(current, segment);
            Check(current);
        }

        void Check(string path)
        {
            if (!path.StartsWith(root[..^1], StringComparison.OrdinalIgnoreCase)
                && !string.Equals(path, root[..^1], StringComparison.OrdinalIgnoreCase))
            {
                throw new ArgumentException("Path escapes the instance root", nameof(targetPath));
            }

            try
            {
                if ((File.GetAttributes(path) & FileAttributes.ReparsePoint) != 0)
                {
                    throw new IOException($"Reparse points are not allowed in the Launcher instance path: {path}");
                }
            }
            catch (FileNotFoundException)
            {
                // A missing target is safe; existing ancestors were checked above.
            }
            catch (DirectoryNotFoundException)
            {
                // A missing target is safe; existing ancestors were checked above.
            }
        }
    }
}
