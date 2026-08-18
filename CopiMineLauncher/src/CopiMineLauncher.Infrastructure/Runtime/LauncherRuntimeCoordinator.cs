using System.Text.RegularExpressions;
using CopiMineLauncher.Core.Launch;
using CopiMineLauncher.Core.Manifest;
using CopiMineLauncher.Core.Updates;
using CopiMineLauncher.Infrastructure.Launch;
using CopiMineLauncher.Infrastructure.Manifest;
using CopiMineLauncher.Infrastructure.Provisioning;
using CopiMineLauncher.Infrastructure.Servers;
using CopiMineLauncher.Infrastructure.Updates;

namespace CopiMineLauncher.Infrastructure.Runtime;

public sealed record LauncherOperationRequest(
    string InstanceRoot,
    string PlayerName,
    Uri? ManifestUri = null,
    int MaximumRamMb = 4096,
    int ResolutionWidth = 1280,
    int ResolutionHeight = 720,
    bool Fullscreen = false);

public sealed record LauncherProgress(string Stage, string Message);

public sealed record LauncherOperationResult(
    bool Succeeded,
    string Operation,
    string? ErrorCode,
    string Diagnostic,
    VerifiedInstanceManifest? VerifiedManifest = null,
    ReconciliationResult? Reconciliation = null,
    JavaProvisioningResult? Java = null,
    MinecraftProvisioningResult? Minecraft = null,
    ServersDatEvidence? ServersDat = null,
    LaunchEvidence? Launch = null,
    MinecraftLaunchFailureReport? LaunchFailure = null);

public interface ITransactionalReconcilerFactory
{
    ITransactionalReconciler Create(string instanceRoot, VerifiedInstanceManifest manifest);
}

public sealed class TransactionalReconcilerFactory(IResumableDownloadManager downloads) : ITransactionalReconcilerFactory
{
    public ITransactionalReconciler Create(string instanceRoot, VerifiedInstanceManifest manifest)
    {
        ArgumentNullException.ThrowIfNull(manifest);
        return new TransactionalReconciler(
            instanceRoot,
            new VerifiedManifestTrustGate(manifest.ReconcilerManifest),
            downloads,
            new AtomicFileStore(instanceRoot));
    }
}

public sealed class VerifiedManifestTrustGate(LauncherManifest trustedManifest) : IManifestTrustGate
{
    public ValueTask<bool> IsTrustedAsync(LauncherManifest manifest, CancellationToken cancellationToken)
    {
        cancellationToken.ThrowIfCancellationRequested();
        return ValueTask.FromResult(ReferenceEquals(manifest, trustedManifest));
    }
}

public interface ILauncherRuntimeCoordinator
{
    Task<LauncherOperationResult> RepairAsync(
        LauncherOperationRequest request,
        CancellationToken cancellationToken,
        IProgress<LauncherProgress>? progress = null);

    Task<LauncherOperationResult> PlayAsync(
        LauncherOperationRequest request,
        CancellationToken cancellationToken,
        IProgress<LauncherProgress>? progress = null);
}

public sealed class LauncherRuntimeCoordinator : ILauncherRuntimeCoordinator
{
    private static readonly Regex PlayerNamePattern = new("^[A-Za-z0-9_]{3,16}$", RegexOptions.CultureInvariant);
    private readonly IManifestClient manifestClient;
    private readonly IMinecraftProvisioner minecraftProvisioner;
    private readonly IJavaProvisioner javaProvisioner;
    private readonly ITransactionalReconcilerFactory reconcilerFactory;
    private readonly IServersDatService serversDatService;
    private readonly IMinecraftLaunchService launchService;
    private readonly IOfflineMinecraftBaseline? offlineMinecraftBaseline;
    private readonly IHostedMinecraftRuntimeInstaller? hostedMinecraftRuntime;

    public LauncherRuntimeCoordinator(
        IManifestClient manifestClient,
        IMinecraftProvisioner minecraftProvisioner,
        IJavaProvisioner javaProvisioner,
        ITransactionalReconcilerFactory reconcilerFactory,
        IServersDatService serversDatService,
        IMinecraftLaunchService launchService,
        IOfflineMinecraftBaseline? offlineMinecraftBaseline = null,
        IHostedMinecraftRuntimeInstaller? hostedMinecraftRuntime = null)
    {
        this.manifestClient = manifestClient ?? throw new ArgumentNullException(nameof(manifestClient));
        this.minecraftProvisioner = minecraftProvisioner ?? throw new ArgumentNullException(nameof(minecraftProvisioner));
        this.javaProvisioner = javaProvisioner ?? throw new ArgumentNullException(nameof(javaProvisioner));
        this.reconcilerFactory = reconcilerFactory ?? throw new ArgumentNullException(nameof(reconcilerFactory));
        this.serversDatService = serversDatService ?? throw new ArgumentNullException(nameof(serversDatService));
        this.launchService = launchService ?? throw new ArgumentNullException(nameof(launchService));
        this.offlineMinecraftBaseline = offlineMinecraftBaseline;
        this.hostedMinecraftRuntime = hostedMinecraftRuntime;
    }

    public Task<LauncherOperationResult> RepairAsync(
        LauncherOperationRequest request,
        CancellationToken cancellationToken,
        IProgress<LauncherProgress>? progress = null) => ExecuteAsync(request, launch: false, cancellationToken, progress);

    public Task<LauncherOperationResult> PlayAsync(
        LauncherOperationRequest request,
        CancellationToken cancellationToken,
        IProgress<LauncherProgress>? progress = null) => ExecuteAsync(request, launch: true, cancellationToken, progress);

    private async Task<LauncherOperationResult> ExecuteAsync(
        LauncherOperationRequest request,
        bool launch,
        CancellationToken cancellationToken,
        IProgress<LauncherProgress>? progress)
    {
        var operation = launch ? "play" : "repair";
        var validation = ValidateRequest(request);
        if (validation is not null)
        {
            return Failure(operation, validation.Value.Code, validation.Value.Message);
        }

        try
        {
            progress?.Report(new("manifest", "Проверяем подписанный instance manifest…"));
            var manifest = await manifestClient.FetchVerifiedAsync(
                request.ManifestUri ?? SignedInstanceManifestClient.DefaultManifestUri,
                cancellationToken);

            var instanceRoot = Path.GetFullPath(request.InstanceRoot);
            Directory.CreateDirectory(instanceRoot);
            await using var instanceLock = await InstanceOperationLock.AcquireAsync(instanceRoot, cancellationToken);

            if (hostedMinecraftRuntime is not null)
            {
                if (manifest.ReconcilerManifest.MinecraftRuntime is null)
                {
                    throw new OfflineMinecraftBaselineException(
                        "MINECRAFT_RUNTIME_MISSING",
                        "Подписанный manifest не содержит серверный Minecraft/Fabric runtime.");
                }

                progress?.Report(new("minecraft-runtime", "Скачиваем проверенный Minecraft/Fabric runtime с сервера CopiMine…"));
                await hostedMinecraftRuntime.EnsureAsync(
                    instanceRoot,
                    manifest.Document.Minecraft.Version,
                    manifest.Document.Minecraft.FabricLoader,
                    manifest.ReconcilerManifest.MinecraftRuntime,
                    cancellationToken);
            }
            else if (offlineMinecraftBaseline is not null)
            {
                progress?.Report(new("offline-baseline", "Проверяем офлайн-файлы Minecraft из установщика…"));
                await offlineMinecraftBaseline.EnsureAsync(
                    instanceRoot,
                    manifest.Document.Minecraft.Version,
                    manifest.Document.Minecraft.FabricLoader,
                    cancellationToken);
            }

            progress?.Report(new("reconcile", "Сверяем managed-файлы и готовим безопасное обновление…"));
            var reconciler = reconcilerFactory.Create(instanceRoot, manifest);
            var reconciliation = await reconciler.ReconcileAsync(manifest.ReconcilerManifest, cancellationToken);
            if (!reconciliation.IsSuccess)
            {
                return new(
                    false,
                    operation,
                    reconciliation.ErrorCode ?? "RECONCILIATION_FAILED",
                    reconciliation.Diagnostic ?? "Managed instance reconciliation did not complete.",
                    manifest,
                    reconciliation);
            }

            progress?.Report(new("preflight", "Проверяем целостность архивов модов…"));
            MinecraftInstancePreflight.ValidateModArchives(instanceRoot);

            progress?.Report(new("java", "Проверяем Java 21 для отдельного экземпляра…"));
            var java = await javaProvisioner.EnsureJava21Async(instanceRoot, manifest.ReconcilerManifest, cancellationToken);

            progress?.Report(new("minecraft", "Проверяем Minecraft 1.21.1 и Fabric Loader 0.19.3…"));
            var minecraft = await minecraftProvisioner.EnsureMinecraftFabricAsync(
                instanceRoot,
                manifest.Document.Minecraft.Version,
                manifest.Document.Minecraft.FabricLoader,
                cancellationToken);

            progress?.Report(new("servers", "Обновляем servers.dat без удаления чужих серверов…"));
            var server = manifest.Document.Server;
            var servers = await serversDatService.EnsureCopiMineServerAsync(
                Path.Combine(instanceRoot, "servers.dat"),
                new ManagedServerRecord(server.Name, server.Address, server.Port, server.AcceptServerResourcePack),
                cancellationToken);

            LaunchEvidence? launchEvidence = null;
            if (launch)
            {
                progress?.Report(new("launch", "Запускаем Minecraft с подготовленным Fabric-профилем…"));
                launchEvidence = await launchService.LaunchAsync(
                    new LaunchRequest(
                        instanceRoot,
                        minecraft.FabricVersionName,
                        request.PlayerName,
                        java.JavaExecutablePath,
                        request.MaximumRamMb,
                        request.ResolutionWidth,
                        request.ResolutionHeight,
                        request.Fullscreen,
                        ManagedModFileNames: manifest.ReconcilerManifest.Files
                            .Where(file => file.Path.StartsWith("mods/", StringComparison.OrdinalIgnoreCase))
                            .Select(file => Path.GetFileName(file.Path.Replace('/', Path.DirectorySeparatorChar)))
                            .Where(fileName => !string.IsNullOrWhiteSpace(fileName))
                            .Cast<string>()
                            .ToHashSet(StringComparer.OrdinalIgnoreCase)),
                    cancellationToken);
            }

            return new(
                true,
                operation,
                null,
                launch
                    ? "Сборка проверена, сервер добавлен, Minecraft запущен."
                    : "Сборка проверена и восстановлена; сервер добавлен в servers.dat.",
                manifest,
                reconciliation,
                java,
                minecraft,
                servers,
                launchEvidence);
        }
        catch (ManifestFetchException exception)
        {
            return Failure(operation, exception.Code, exception.Message);
        }
        catch (OfflineMinecraftBaselineException exception)
        {
            return Failure(operation, exception.Code, exception.Message);
        }
        catch (MinecraftPreflightException exception)
        {
            return Failure(operation, exception.Code, exception.Message);
        }
        catch (MinecraftProvisioningException exception)
        {
            return Failure(operation, exception.Code, exception.Message);
        }
        catch (MinecraftLaunchException exception)
        {
            return Failure(operation, exception.Code, exception.Message, exception.Report);
        }
        catch (OperationCanceledException)
        {
            throw;
        }
        catch (Exception exception)
        {
            return Failure(operation, "LAUNCHER_RUNTIME_FAILED", exception.Message);
        }
    }

    private static (string Code, string Message)? ValidateRequest(LauncherOperationRequest request)
    {
        if (request is null)
        {
            return ("REQUEST_INVALID", "Launcher operation request is missing.");
        }

        if (string.IsNullOrWhiteSpace(request.InstanceRoot))
        {
            return ("INSTANCE_PATH_INVALID", "Instance path is required.");
        }

        if (!PlayerNamePattern.IsMatch(request.PlayerName ?? string.Empty))
        {
            return ("PLAYER_NAME_INVALID", "Player name must contain 3–16 Latin letters, digits, or underscores.");
        }

        if (request.MaximumRamMb < LauncherMemoryLimits.MinimumRamMb || request.MaximumRamMb > LauncherMemoryLimits.MaximumRamMb)
        {
            return ("MEMORY_LIMIT_INVALID", $"Launcher memory must be between {LauncherMemoryLimits.MinimumRamMb} and {LauncherMemoryLimits.MaximumRamMb} MB for this PC.");
        }

        if (request.ResolutionWidth is < 800 or > 7680 || request.ResolutionHeight is < 600 or > 4320)
        {
            return ("RESOLUTION_INVALID", "Launcher resolution must be between 800×600 and 7680×4320.");
        }

        var fullPath = Path.GetFullPath(request.InstanceRoot);
        var normalMinecraft = Path.GetFullPath(Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData),
            ".minecraft"));
        if (string.Equals(fullPath.TrimEnd(Path.DirectorySeparatorChar), normalMinecraft.TrimEnd(Path.DirectorySeparatorChar), StringComparison.OrdinalIgnoreCase))
        {
            return ("INSTANCE_PATH_NOT_ALLOWED", "CopiMine Launcher never modifies the user's normal .minecraft directory.");
        }

        return null;
    }

    private static LauncherOperationResult Failure(
        string operation,
        string code,
        string diagnostic,
        MinecraftLaunchFailureReport? launchFailure = null) =>
        new(false, operation, code, diagnostic, LaunchFailure: launchFailure);
}
