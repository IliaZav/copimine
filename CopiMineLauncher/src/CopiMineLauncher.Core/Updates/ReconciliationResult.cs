using CopiMineLauncher.Core.Manifest;

namespace CopiMineLauncher.Core.Updates;

public interface IManifestTrustGate
{
    ValueTask<bool> IsTrustedAsync(LauncherManifest manifest, CancellationToken cancellationToken);
}

public enum ReconciliationStatus
{
    AlreadyCurrent,
    Updated,
    Conflict,
    Failed
}

public sealed record ReconciliationResult(
    ReconciliationStatus Status,
    IReadOnlyList<UpdateOperation> Operations,
    string? ErrorCode = null,
    string? Diagnostic = null,
    bool RecoveredPreviousTransaction = false)
{
    public bool IsSuccess => Status is ReconciliationStatus.AlreadyCurrent or ReconciliationStatus.Updated;
}
