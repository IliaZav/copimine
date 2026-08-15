using CmlLib.Core;

namespace CopiMineLauncher.Infrastructure.Provisioning;

public sealed record MinecraftProvisioningResult(string MinecraftVersion, string FabricLoaderVersion, string FabricVersionName, string InstanceRoot);

public interface IMinecraftProvisioner
{
    Task<MinecraftProvisioningResult> EnsureMinecraftFabricAsync(string instanceRoot, string minecraftVersion, string fabricLoaderVersion, CancellationToken cancellationToken);
}

public sealed class MinecraftProvisioner : IMinecraftProvisioner
{
    private readonly HttpClient httpClient;
    private readonly IFabricProvisioner fabricProvisioner;

    public MinecraftProvisioner(HttpClient httpClient, IFabricProvisioner? fabricProvisioner = null)
    {
        this.httpClient = httpClient;
        this.fabricProvisioner = fabricProvisioner ?? new FabricProvisioner(httpClient);
    }

    public async Task<MinecraftProvisioningResult> EnsureMinecraftFabricAsync(string instanceRoot, string minecraftVersion, string fabricLoaderVersion, CancellationToken cancellationToken)
    {
        if (!string.Equals(minecraftVersion, "1.21.1", StringComparison.Ordinal))
        {
            throw new ArgumentException("Minecraft version must be exactly 1.21.1", nameof(minecraftVersion));
        }

        var parameters = MinecraftLauncherParameters.CreateDefault(new MinecraftPath(instanceRoot), httpClient);
        var launcher = new MinecraftLauncher(parameters);
        await launcher.InstallAsync(minecraftVersion, cancellationToken);
        var fabric = await fabricProvisioner.EnsureFabricAsync(instanceRoot, minecraftVersion, fabricLoaderVersion, cancellationToken);
        return new(minecraftVersion, fabricLoaderVersion, fabric.VersionName, Path.GetFullPath(instanceRoot));
    }
}
