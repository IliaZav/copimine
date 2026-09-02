using CopiMineLauncher.Core.Manifest;
using CopiMineLauncher.Infrastructure.Updates;

namespace CopiMineLauncher.Infrastructure.Provisioning;

public interface IHostedMinecraftRuntimeInstaller
{
    Task<OfflineMinecraftBaselineResult> EnsureAsync(
        string instanceRoot,
        string minecraftVersion,
        string fabricLoaderVersion,
        MinecraftRuntimeMetadata runtime,
        CancellationToken cancellationToken,
        IProgress<DownloadProgress>? progress = null);
}

public sealed class HostedMinecraftRuntimeInstaller(
    OfflineMinecraftBaseline baseline,
    IResumableDownloadManager downloads) : IHostedMinecraftRuntimeInstaller
{
    public Task<OfflineMinecraftBaselineResult> EnsureAsync(
        string instanceRoot,
        string minecraftVersion,
        string fabricLoaderVersion,
        MinecraftRuntimeMetadata runtime,
        CancellationToken cancellationToken,
        IProgress<DownloadProgress>? progress = null)
    {
        ArgumentNullException.ThrowIfNull(baseline);
        ArgumentNullException.ThrowIfNull(downloads);
        return baseline.EnsureHostedAsync(
            instanceRoot,
            minecraftVersion,
            fabricLoaderVersion,
            runtime,
            downloads,
            cancellationToken,
            progress);
    }
}
