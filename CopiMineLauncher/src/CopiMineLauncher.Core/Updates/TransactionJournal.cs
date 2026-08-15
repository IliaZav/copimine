namespace CopiMineLauncher.Core.Updates;

public enum TransactionPhase
{
    Prepared,
    Committing,
    Committed
}

public sealed record TransactionJournalEntry(
    UpdateOperationKind Kind,
    string ComponentId,
    string RelativePath,
    string? StagedPath,
    string? BackupPath,
    string? ExpectedSha256,
    string? NewSha256);

public sealed record TransactionJournal(
    string TransactionId,
    long ManifestSequence,
    TransactionPhase Phase,
    DateTimeOffset CreatedAtUtc,
    ManagedState PreviousState,
    IReadOnlyList<TransactionJournalEntry> Entries);
