using CopiMineLauncher.Core.Filesystem;
using CopiMineLauncher.Core.Manifest;

namespace CopiMineLauncher.Core.Updates;

public sealed record ManagedFileRecord(
    string ComponentId,
    string RelativePath,
    string Sha256,
    string Version);

public sealed record ManagedState(
    long ManifestSequence,
    IReadOnlyList<ManagedFileRecord> Files)
{
    public static ManagedState Empty { get; } = new(0, Array.Empty<ManagedFileRecord>());
}

public sealed record LocalFileSnapshot(
    bool Exists,
    long SizeBytes,
    string? Sha256)
{
    public static LocalFileSnapshot Missing { get; } = new(false, 0, null);
}

public static class OwnershipPolicy
{
    public static UpdatePlan BuildPlan(
        LauncherManifest manifest,
        ManagedState previousState,
        Func<string, LocalFileSnapshot> snapshot)
    {
        ArgumentNullException.ThrowIfNull(manifest);
        ArgumentNullException.ThrowIfNull(previousState);
        ArgumentNullException.ThrowIfNull(snapshot);

        var operations = new List<UpdateOperation>();
        var previousByComponent = previousState.Files
            .GroupBy(file => file.ComponentId, StringComparer.OrdinalIgnoreCase)
            .ToDictionary(group => group.Key, group => group.First(), StringComparer.OrdinalIgnoreCase);
        var previousByPath = previousState.Files
            .ToDictionary(file => file.RelativePath, StringComparer.OrdinalIgnoreCase);
        var desiredManaged = new List<ManagedFileRecord>();
        var desiredComponents = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
        var desiredPaths = new HashSet<string>(StringComparer.OrdinalIgnoreCase);

        foreach (var entry in manifest.Files.Where(entry => entry.Ownership is "managed" or "merge"))
        {
            var safePath = SafeRelativePath.Parse(entry.Path).Value;
            desiredComponents.Add(entry.ComponentId);
            desiredPaths.Add(safePath);

            previousByComponent.TryGetValue(entry.ComponentId, out var previousComponent);
            var current = snapshot(safePath);
            var isPreviouslyManaged = previousByPath.ContainsKey(safePath)
                || (previousComponent is not null && string.Equals(previousComponent.RelativePath, safePath, StringComparison.OrdinalIgnoreCase));

            var launcherOwnsCurrentFile = false;
            if (entry.Ownership is "merge")
            {
                operations.Add(new(UpdateOperationKind.Conflict, entry.ComponentId, safePath, current.Sha256, entry.Sha256, entry, "merge-policy-unsupported"));
            }
            else if (!current.Exists)
            {
                operations.Add(new(UpdateOperationKind.Add, entry.ComponentId, safePath, null, entry.Sha256, entry, null));
                launcherOwnsCurrentFile = true;
            }
            else if (!isPreviouslyManaged)
            {
                operations.Add(new(UpdateOperationKind.Conflict, entry.ComponentId, safePath, current.Sha256, entry.Sha256, entry, "unknown-file-at-official-path"));
            }
            else if (!string.Equals(current.Sha256, entry.Sha256, StringComparison.OrdinalIgnoreCase))
            {
                operations.Add(new(UpdateOperationKind.Replace, entry.ComponentId, safePath, current.Sha256, entry.Sha256, entry, null));
                launcherOwnsCurrentFile = true;
            }
            else
            {
                launcherOwnsCurrentFile = true;
            }

            if (launcherOwnsCurrentFile)
            {
                desiredManaged.Add(new ManagedFileRecord(entry.ComponentId, safePath, entry.Sha256, entry.Version));
            }

            if (previousComponent is not null && !string.Equals(previousComponent.RelativePath, safePath, StringComparison.OrdinalIgnoreCase))
            {
                operations.Add(new(UpdateOperationKind.Remove, previousComponent.ComponentId, previousComponent.RelativePath, previousComponent.Sha256, null, null, "component-filename-changed"));
            }
        }

        foreach (var previous in previousState.Files)
        {
            if (!desiredComponents.Contains(previous.ComponentId)
                && !desiredPaths.Contains(previous.RelativePath))
            {
                operations.Add(new(UpdateOperationKind.Remove, previous.ComponentId, previous.RelativePath, previous.Sha256, null, null, "component-removed-from-manifest"));
            }
        }

        return new UpdatePlan(manifest.Sequence, DeduplicateOperations(operations), new ManagedState(manifest.Sequence, desiredManaged));
    }

    private static IReadOnlyList<UpdateOperation> DeduplicateOperations(IEnumerable<UpdateOperation> operations) => operations
        .GroupBy(operation => (operation.Kind, operation.ComponentId, operation.RelativePath), EqualityComparer<(UpdateOperationKind, string, string)>.Default)
        .Select(group => group.First())
        .ToArray();
}
