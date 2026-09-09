namespace CopiMineLauncher.Core.Updates;

public sealed record UpdatePlan(
    long ManifestSequence,
    IReadOnlyList<UpdateOperation> Operations,
    ManagedState NextState)
{
    public bool HasConflicts => Operations.Any(operation => operation.Kind == UpdateOperationKind.Conflict);

    public bool RequiresChanges => Operations.Count > 0;
}
