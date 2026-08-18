using CmlLib.Core;
using CmlLib.Core.ModLoaders.FabricMC;

namespace CopiMineLauncher.Infrastructure.Provisioning;

public sealed record FabricProvisioningResult(string MinecraftVersion, string FabricLoaderVersion, string VersionName, string InstanceRoot);

public interface IFabricProvisioner
{
    Task<FabricProvisioningResult> EnsureFabricAsync(string instanceRoot, string minecraftVersion, string fabricLoaderVersion, CancellationToken cancellationToken);
}

public sealed class FabricProvisioner : IFabricProvisioner
{
    private readonly HttpClient httpClient;

    public FabricProvisioner(HttpClient httpClient)
    {
        this.httpClient = httpClient;
    }

    public async Task<FabricProvisioningResult> EnsureFabricAsync(string instanceRoot, string minecraftVersion, string fabricLoaderVersion, CancellationToken cancellationToken)
    {
        ValidateVersions(minecraftVersion, fabricLoaderVersion);
        var minecraftPath = new MinecraftPath(instanceRoot);
        var installer = new FabricInstaller(httpClient);
        var versionName = await installer.Install(minecraftVersion, fabricLoaderVersion, minecraftPath);
        cancellationToken.ThrowIfCancellationRequested();
        return new(minecraftVersion, fabricLoaderVersion, versionName, Path.GetFullPath(instanceRoot));
    }

    public static string ResolveVersionName(string minecraftVersion, string fabricLoaderVersion)
    {
        ValidateVersions(minecraftVersion, fabricLoaderVersion);
        return FabricInstaller.GetVersionName(minecraftVersion, fabricLoaderVersion);
    }

    private static void ValidateVersions(string minecraftVersion, string fabricLoaderVersion)
    {
        if (!string.Equals(minecraftVersion, "1.21.1", StringComparison.Ordinal))
        {
            throw new ArgumentException("Minecraft version must be exactly 1.21.1", nameof(minecraftVersion));
        }

        if (!string.Equals(fabricLoaderVersion, "0.19.3", StringComparison.Ordinal))
        {
            throw new ArgumentException("Fabric Loader version must be exactly 0.19.3", nameof(fabricLoaderVersion));
        }
    }
}
