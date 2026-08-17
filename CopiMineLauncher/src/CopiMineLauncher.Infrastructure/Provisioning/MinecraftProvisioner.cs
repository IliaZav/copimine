using CmlLib.Core;

namespace CopiMineLauncher.Infrastructure.Provisioning;

public sealed record MinecraftProvisioningResult(string MinecraftVersion, string FabricLoaderVersion, string FabricVersionName, string InstanceRoot);

public sealed class MinecraftProvisioningException : Exception
{
    public MinecraftProvisioningException(string code, string message, Exception? innerException = null)
        : base(message, innerException)
    {
        Code = code;
    }

    public string Code { get; }
}

public interface IMinecraftProfileInstaller
{
    Task InstallAsync(string versionName, CancellationToken cancellationToken);
}

public sealed class CmlibMinecraftProfileInstaller : IMinecraftProfileInstaller
{
    private readonly MinecraftPath minecraftPath;
    private readonly HttpClient httpClient;

    public CmlibMinecraftProfileInstaller(string instanceRoot, HttpClient httpClient)
    {
        minecraftPath = new MinecraftPath(instanceRoot);
        this.httpClient = httpClient;
    }

    public async Task InstallAsync(string versionName, CancellationToken cancellationToken)
    {
        var parameters = MinecraftLauncherParameters.CreateDefault(minecraftPath, httpClient);
        var launcher = new MinecraftLauncher(parameters);
        await launcher.InstallAsync(versionName, cancellationToken);
    }
}

public interface IMinecraftProvisioner
{
    Task<MinecraftProvisioningResult> EnsureMinecraftFabricAsync(string instanceRoot, string minecraftVersion, string fabricLoaderVersion, CancellationToken cancellationToken);
}

public sealed class MinecraftProvisioner : IMinecraftProvisioner
{
    private readonly IFabricProvisioner fabricProvisioner;
    private readonly IMinecraftProfileInstaller? profileInstallerOverride;
    private readonly HttpClient httpClient;

    public MinecraftProvisioner(
        HttpClient httpClient,
        IFabricProvisioner? fabricProvisioner = null,
        IMinecraftProfileInstaller? profileInstaller = null)
    {
        this.httpClient = httpClient;
        this.fabricProvisioner = fabricProvisioner ?? new FabricProvisioner(httpClient);
        profileInstallerOverride = profileInstaller;
    }

    public async Task<MinecraftProvisioningResult> EnsureMinecraftFabricAsync(string instanceRoot, string minecraftVersion, string fabricLoaderVersion, CancellationToken cancellationToken)
    {
        if (!string.Equals(minecraftVersion, "1.21.1", StringComparison.Ordinal))
        {
            throw new ArgumentException("Minecraft version must be exactly 1.21.1", nameof(minecraftVersion));
        }

        if (OfflineMinecraftBaseline.IsMinecraftProfileReady(instanceRoot, minecraftVersion, fabricLoaderVersion))
        {
            return new(
                minecraftVersion,
                fabricLoaderVersion,
                FabricProvisioner.ResolveVersionName(minecraftVersion, fabricLoaderVersion),
                Path.GetFullPath(instanceRoot));
        }

        if (profileInstallerOverride is null)
        {
            throw new MinecraftProvisioningException(
                "MINECRAFT_RUNTIME_NOT_READY",
                "Minecraft/Fabric runtime не установлен из подписанного серверного пакета CopiMine. Внешняя загрузка Mojang/Fabric отключена.");
        }

        var profileInstaller = profileInstallerOverride;
        await profileInstaller.InstallAsync(minecraftVersion, cancellationToken);
        var fabric = await fabricProvisioner.EnsureFabricAsync(instanceRoot, minecraftVersion, fabricLoaderVersion, cancellationToken);
        await profileInstaller.InstallAsync(fabric.VersionName, cancellationToken);
        return new(minecraftVersion, fabricLoaderVersion, fabric.VersionName, Path.GetFullPath(instanceRoot));
    }
}
