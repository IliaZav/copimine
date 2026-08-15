using System.Text.Json;
using CopiMineLauncher.Core.Filesystem;
using CopiMineLauncher.Core.Updates;

namespace CopiMineLauncher.Infrastructure.Updates;

public interface IAtomicFileStore
{
    Task<ManagedState> LoadStateAsync(CancellationToken cancellationToken);

    Task<bool> RecoverAsync(CancellationToken cancellationToken);

    Task CommitAsync(
        IReadOnlyList<UpdateOperation> operations,
        TransactionJournal journal,
        ManagedState nextState,
        CancellationToken cancellationToken);
}

public sealed class AtomicFileStore : IAtomicFileStore
{
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

    public async Task<ManagedState> LoadStateAsync(CancellationToken cancellationToken)
    {
        if (!File.Exists(StatePath))
        {
            return ManagedState.Empty;
        }

        await using var stream = File.OpenRead(StatePath);
        var state = await JsonSerializer.DeserializeAsync<ManagedState>(stream, JsonOptions, cancellationToken);
        return state ?? throw new InvalidDataException("managed-state.json is empty");
    }

    public async Task<bool> RecoverAsync(CancellationToken cancellationToken)
    {
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

        if (journal.Phase != TransactionPhase.Committed)
        {
            RestoreBackups(journal);
            await WriteStateAsync(journal.PreviousState, cancellationToken);
        }

        CleanupJournalAndStaging(journal);
        return true;
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

        Directory.CreateDirectory(metadataRoot);
        var preparedEntries = operations.Select(operation =>
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
                var operation = operations[index];
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

            await WriteStateAsync(nextState, cancellationToken);
            currentJournal = currentJournal with { Phase = TransactionPhase.Committed };
            await WriteJournalAsync(currentJournal, cancellationToken);
            CleanupJournalAndStaging(currentJournal);
        }
        catch
        {
            RestoreBackups(currentJournal);
            await WriteStateAsync(journal.PreviousState, CancellationToken.None);
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
        await WriteJsonAtomicallyAsync(StatePath, state, cancellationToken);
    }

    private static async Task WriteJsonAtomicallyAsync<T>(string path, T value, CancellationToken cancellationToken)
    {
        var tempPath = path + ".tmp";
        await using (var stream = File.Create(tempPath))
        {
            await JsonSerializer.SerializeAsync(stream, value, JsonOptions, cancellationToken);
            await stream.FlushAsync(cancellationToken);
        }

        File.Move(tempPath, path, overwrite: true);
    }

    private void RestoreBackups(TransactionJournal journal)
    {
        foreach (var entry in journal.Entries.Reverse())
        {
            var targetPath = Resolve(entry.RelativePath);
            if (!string.IsNullOrWhiteSpace(entry.BackupPath) && File.Exists(entry.BackupPath))
            {
                Directory.CreateDirectory(Path.GetDirectoryName(targetPath)!);
                if (File.Exists(targetPath))
                {
                    File.Delete(targetPath);
                }

                File.Move(entry.BackupPath, targetPath, overwrite: true);
            }
            else if (entry.Kind == UpdateOperationKind.Add && File.Exists(targetPath))
            {
                File.Delete(targetPath);
            }
        }
    }

    private void CleanupJournalAndStaging(TransactionJournal journal)
    {
        if (File.Exists(JournalPath))
        {
            File.Delete(JournalPath);
        }

        foreach (var entry in journal.Entries)
        {
            if (!string.IsNullOrWhiteSpace(entry.StagedPath) && File.Exists(entry.StagedPath))
            {
                File.Delete(entry.StagedPath);
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

        return combined;
    }
}
