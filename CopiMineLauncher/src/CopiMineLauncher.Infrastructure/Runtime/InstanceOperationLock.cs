namespace CopiMineLauncher.Infrastructure.Runtime;

/// <summary>
/// Serializes Launcher mutations for one instance across multiple processes.
/// The lock file is intentionally retained; only its exclusive handle is state.
/// </summary>
public static class InstanceOperationLock
{
    public static async ValueTask<InstanceOperationLease> AcquireAsync(
        string instanceRoot,
        CancellationToken cancellationToken)
    {
        ArgumentException.ThrowIfNullOrWhiteSpace(instanceRoot);
        var root = Path.GetFullPath(instanceRoot);
        var metadataRoot = Path.Combine(root, ".copimine");
        Directory.CreateDirectory(metadataRoot);
        var lockPath = Path.Combine(metadataRoot, "instance-operation.lock");

        while (true)
        {
            cancellationToken.ThrowIfCancellationRequested();
            try
            {
                var stream = new FileStream(
                    lockPath,
                    FileMode.OpenOrCreate,
                    FileAccess.ReadWrite,
                    FileShare.None,
                    bufferSize: 1,
                    options: FileOptions.WriteThrough);
                return new InstanceOperationLease(stream);
            }
            catch (IOException)
            {
                await Task.Delay(TimeSpan.FromMilliseconds(100), cancellationToken);
            }
            catch (UnauthorizedAccessException)
            {
                throw new IOException($"CopiMine instance is not writable: {root}");
            }
        }
    }
}

public sealed class InstanceOperationLease : IAsyncDisposable, IDisposable
{
    private readonly FileStream stream;

    internal InstanceOperationLease(FileStream stream) => this.stream = stream;

    public void Dispose() => stream.Dispose();

    public ValueTask DisposeAsync()
    {
        stream.Dispose();
        return ValueTask.CompletedTask;
    }
}
