using CopiMineLauncher.Core.Manifest;

namespace CopiMineLauncher.Core.Updates;

public enum UpdateOperationKind
{
    Add,
    Replace,
    Remove,
    Conflict
}

public sealed record UpdateOperation(
    UpdateOperationKind Kind,
    string ComponentId,
    string RelativePath,
    string? ExpectedSha256,
    string? NewSha256,
    ManifestFileEntry? Entry,
    string? Reason,
    string? StagedPath = null);
