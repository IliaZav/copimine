using System.Security.Cryptography;
using CopiMineLauncher.Core.Filesystem;
using CopiMineLauncher.Core.Manifest;
using CopiMineLauncher.Core.Updates;

namespace CopiMineLauncher.Infrastructure.Updates;

public interface ITransactionalReconciler
{
    Task<ReconciliationResult> ReconcileAsync(LauncherManifest manifest, CancellationToken cancellationToken);
}

public sealed class TransactionalReconciler : ITransactionalReconciler
{
    private readonly string instanceRoot;
    private readonly IManifestTrustGate trustGate;
    private readonly IResumableDownloadManager downloads;
    private readonly IAtomicFileStore atomicFileStore;

    public TransactionalReconciler(
        string instanceRoot,
        IManifestTrustGate trustGate,
        IResumableDownloadManager downloads,
        IAtomicFileStore atomicFileStore)
    {
        this.instanceRoot = Path.GetFullPath(instanceRoot);
        this.trustGate = trustGate;
        this.downloads = downloads;
        this.atomicFileStore = atomicFileStore;
    }

    public async Task<ReconciliationResult> ReconcileAsync(LauncherManifest manifest, CancellationToken cancellationToken)
    {
        ArgumentNullException.ThrowIfNull(manifest);
        if (!await trustGate.IsTrustedAsync(manifest, cancellationToken))
        {
            return new(ReconciliationStatus.Failed, Array.Empty<UpdateOperation>(), "MANIFEST_NOT_TRUSTED", "Manifest signature/trust verification did not pass");
        }

        try
        {
            var recovered = await atomicFileStore.RecoverAsync(cancellationToken);
            var previousState = await atomicFileStore.LoadStateAsync(cancellationToken);
            if (manifest.Sequence < previousState.ManifestSequence)
            {
                return new(
                    ReconciliationStatus.Failed,
                    Array.Empty<UpdateOperation>(),
                    "MANIFEST_SEQUENCE_ROLLBACK",
                    $"Manifest sequence {manifest.Sequence} is older than committed sequence {previousState.ManifestSequence}.",
                    recovered);
            }

            var plan = OwnershipPolicy.BuildPlan(manifest, previousState, Snapshot);
            if (plan.HasConflicts)
            {
                return new(ReconciliationStatus.Conflict, plan.Operations, "UNKNOWN_FILE_CONFLICT", "An unknown user file occupies an official managed path", recovered);
            }

            if (!plan.RequiresChanges)
            {
                return new(ReconciliationStatus.AlreadyCurrent, plan.Operations, RecoveredPreviousTransaction: recovered);
            }

            var transactionId = Guid.NewGuid().ToString("N");
            var plannedOperations = plan.Operations
                .Select(operation => operation.Kind is UpdateOperationKind.Add or UpdateOperationKind.Replace
                    ? operation with
                    {
                        StagedPath = Path.Combine(
                            instanceRoot,
                            ".copimine",
                            "staging",
                            transactionId,
                            SafeRelativePath.Parse(operation.RelativePath).Value.Replace('/', Path.DirectorySeparatorChar))
                    }
                    : operation)
                .ToArray();
            var plannedJournal = new TransactionJournal(
                transactionId,
                manifest.Sequence,
                TransactionPhase.Prepared,
                DateTimeOffset.UtcNow,
                previousState,
                plannedOperations.Select(operation => new TransactionJournalEntry(
                    operation.Kind,
                    operation.ComponentId,
                    operation.RelativePath,
                    operation.StagedPath,
                    null,
                    operation.ExpectedSha256,
                    operation.NewSha256)).ToArray());
            await atomicFileStore.PrepareAsync(plannedJournal, cancellationToken);

            var staged = new List<UpdateOperation>();
            foreach (var operation in plannedOperations)
            {
                cancellationToken.ThrowIfCancellationRequested();
                if (operation.Kind is not (UpdateOperationKind.Add or UpdateOperationKind.Replace))
                {
                    staged.Add(operation);
                    continue;
                }

                var entry = operation.Entry ?? throw new InvalidDataException("Add/replace operation has no manifest entry");
                var stagedPath = operation.StagedPath ?? throw new InvalidDataException("Add/replace operation has no prepared staging path");
                var verifiedPath = await downloads.DownloadAsync(new Uri(entry.Url, UriKind.Absolute), stagedPath, entry.SizeBytes, entry.Sha256, cancellationToken);
                staged.Add(operation with { StagedPath = verifiedPath });
            }

            var journalEntries = staged.Select(operation => new TransactionJournalEntry(
                operation.Kind,
                operation.ComponentId,
                operation.RelativePath,
                operation.StagedPath,
                null,
                operation.ExpectedSha256,
                operation.NewSha256)).ToArray();
            var journal = new TransactionJournal(transactionId, manifest.Sequence, TransactionPhase.Prepared, DateTimeOffset.UtcNow, previousState, journalEntries);
            await atomicFileStore.CommitAsync(staged, journal, plan.NextState, cancellationToken);
            return new(ReconciliationStatus.Updated, staged, RecoveredPreviousTransaction: recovered);
        }
        catch (OperationCanceledException)
        {
            throw;
        }
        catch (Exception exception)
        {
            return new(ReconciliationStatus.Failed, Array.Empty<UpdateOperation>(), "RECONCILIATION_FAILED", exception.Message);
        }
    }

    private LocalFileSnapshot Snapshot(string relativePath)
    {
        var safe = SafeRelativePath.Parse(relativePath).Value.Replace('/', Path.DirectorySeparatorChar);
        var fullPath = Path.GetFullPath(Path.Combine(instanceRoot, safe));
        if (!File.Exists(fullPath))
        {
            return LocalFileSnapshot.Missing;
        }

        var info = new FileInfo(fullPath);
        using var stream = File.OpenRead(fullPath);
        var hash = Convert.ToHexString(SHA256.HashData(stream)).ToLowerInvariant();
        return new LocalFileSnapshot(true, info.Length, hash);
    }
}
