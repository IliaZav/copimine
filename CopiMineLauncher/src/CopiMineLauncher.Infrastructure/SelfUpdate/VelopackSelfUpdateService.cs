using System.Security.Cryptography;
using System.Text.Json;
using CopiMineLauncher.Core;
using Velopack;
using Velopack.Locators;

namespace CopiMineLauncher.Infrastructure.SelfUpdate;

public enum SelfUpdateStatusKind
{
    NoUpdate,
    UpdateAvailable,
    PendingRestart,
    Failed
}

public sealed record SelfUpdateStatus(
    SelfUpdateStatusKind Kind,
    string CurrentVersion,
    VerifiedSelfUpdate? Update = null,
    string? ErrorCode = null,
    string? Diagnostic = null)
{
    public bool IsSuccess => Kind is SelfUpdateStatusKind.NoUpdate or SelfUpdateStatusKind.UpdateAvailable or SelfUpdateStatusKind.PendingRestart;
}

public sealed record VelopackUpdateCandidate(
    string Product,
    string Channel,
    string Version,
    Uri FeedUri,
    Uri PackageUri,
    string PackageFileName,
    long SizeBytes,
    string Sha256,
    string? ReleaseNotes = null);

public sealed record DownloadedSelfUpdate(string PackagePath);

public interface IVelopackUpdateBackend
{
    Task<VelopackUpdateCandidate?> CheckAsync(Uri feedUri, string channel, CancellationToken cancellationToken);

    Task<DownloadedSelfUpdate> DownloadAsync(VerifiedSelfUpdate update, string destination, CancellationToken cancellationToken);

    Task ApplyAsync(VerifiedSelfUpdate update, string packagePath, CancellationToken cancellationToken);
}

public interface ISelfUpdateService
{
    Task<SelfUpdateStatus> CheckAsync(CancellationToken cancellationToken);

    Task<SelfUpdateStatus> ApplyAsync(VerifiedSelfUpdate update, CancellationToken cancellationToken);

    Task<SelfUpdateStatus> RecoverAsync(CancellationToken cancellationToken);
}

public sealed class VelopackSelfUpdateService : ISelfUpdateService
{
    private static readonly JsonSerializerOptions JsonOptions = new(JsonSerializerDefaults.Web)
    {
        WriteIndented = true
    };

    private readonly Uri feedUri;
    private readonly IVelopackUpdateBackend backend;
    private readonly string stateRoot;
    private readonly SelfUpdatePolicy policy;
    private readonly Func<string> currentVersionProvider;

    public VelopackSelfUpdateService(
        Uri feedUri,
        IVelopackUpdateBackend backend,
        string stateRoot,
        SelfUpdatePolicy? policy = null,
        Func<string>? currentVersionProvider = null,
        string channel = "stable")
    {
        this.feedUri = feedUri ?? throw new ArgumentNullException(nameof(feedUri));
        this.backend = backend ?? throw new ArgumentNullException(nameof(backend));
        this.stateRoot = Path.GetFullPath(stateRoot ?? throw new ArgumentNullException(nameof(stateRoot)));
        this.policy = policy ?? new SelfUpdatePolicy();
        this.currentVersionProvider = currentVersionProvider ?? (() => LauncherVersionFallback());
        Channel = channel;
    }

    public string Channel { get; }

    private string StatePath => Path.Combine(stateRoot, "self-update-state.json");

    public async Task<SelfUpdateStatus> CheckAsync(CancellationToken cancellationToken)
    {
        var currentVersion = currentVersionProvider();
        try
        {
            var candidate = await backend.CheckAsync(feedUri, Channel, cancellationToken);
            if (candidate is null)
            {
                return new(SelfUpdateStatusKind.NoUpdate, currentVersion);
            }

            var update = ToVerified(candidate);
            var validation = policy.Validate(update, currentVersion);
            return validation.IsValid
                ? new(SelfUpdateStatusKind.UpdateAvailable, currentVersion, update)
                : Failed(currentVersion, validation.ErrorCode!, validation.Diagnostic!);
        }
        catch (OperationCanceledException)
        {
            throw;
        }
        catch (Exception exception)
        {
            return Failed(currentVersion, "SELF_UPDATE_CHECK_FAILED", exception.Message);
        }
    }

    public async Task<SelfUpdateStatus> ApplyAsync(VerifiedSelfUpdate update, CancellationToken cancellationToken)
    {
        var currentVersion = currentVersionProvider();
        var validation = policy.Validate(update, currentVersion);
        if (!validation.IsValid)
        {
            return Failed(currentVersion, validation.ErrorCode!, validation.Diagnostic!);
        }

        var stagingRoot = Path.Combine(stateRoot, "staging", update.Version);
        var destination = Path.Combine(stagingRoot, update.PackageFileName);
        var state = new SelfUpdateState(currentVersion, update.Version, update.PackageFileName, SelfUpdatePhase.Applying, null, null, DateTimeOffset.UtcNow);
        try
        {
            Directory.CreateDirectory(stagingRoot);
            await WriteStateAsync(state, cancellationToken);
            var downloaded = await backend.DownloadAsync(update, destination, cancellationToken);
            var downloadedPath = Path.GetFullPath(downloaded.PackagePath);
            if (!IsWithin(stagingRoot, downloadedPath) || !File.Exists(downloadedPath))
            {
                return await FailApplyAsync(currentVersion, update.Version, "SELF_UPDATE_PACKAGE_PATH_INVALID", "Velopack returned a package outside the private staging directory.", downloadedPath);
            }

            var actualLength = new FileInfo(downloadedPath).Length;
            var actualHash = await ComputeSha256Async(downloadedPath, cancellationToken);
            if (actualLength != update.SizeBytes || !string.Equals(actualHash, update.Sha256, StringComparison.Ordinal))
            {
                return await FailApplyAsync(currentVersion, update.Version, "SELF_UPDATE_PACKAGE_HASH_MISMATCH", "The downloaded Velopack package failed its size or SHA-256 verification.", downloadedPath);
            }

            await backend.ApplyAsync(update, downloadedPath, cancellationToken);
            await WriteStateAsync(state with { Phase = SelfUpdatePhase.PendingRestart }, cancellationToken);
            TryDeleteDirectory(stagingRoot);
            return new(SelfUpdateStatusKind.PendingRestart, currentVersion, update);
        }
        catch (OperationCanceledException)
        {
            TryDeleteDirectory(stagingRoot);
            throw;
        }
        catch (Exception exception)
        {
            await WriteStateBestEffortAsync(state with { Phase = SelfUpdatePhase.Failed, ErrorCode = "SELF_UPDATE_APPLY_FAILED", Diagnostic = exception.Message });
            TryDeleteDirectory(stagingRoot);
            return Failed(currentVersion, "SELF_UPDATE_APPLY_FAILED", exception.Message);
        }
    }

    public async Task<SelfUpdateStatus> RecoverAsync(CancellationToken cancellationToken)
    {
        if (!File.Exists(StatePath))
        {
            return new(SelfUpdateStatusKind.NoUpdate, currentVersionProvider());
        }

        SelfUpdateState state;
        try
        {
            await using var stream = File.OpenRead(StatePath);
            state = await JsonSerializer.DeserializeAsync<SelfUpdateState>(stream, JsonOptions, cancellationToken)
                ?? throw new InvalidDataException("self-update-state.json is empty");
        }
        catch (OperationCanceledException)
        {
            throw;
        }
        catch (Exception exception)
        {
            return Failed(currentVersionProvider(), "SELF_UPDATE_STATE_CORRUPT", exception.Message);
        }

        var currentVersion = currentVersionProvider();
        if (state.Phase == SelfUpdatePhase.PendingRestart
            && string.Equals(currentVersion, state.TargetVersion, StringComparison.OrdinalIgnoreCase))
        {
            ClearStateAndStaging(state.TargetVersion);
            return new(SelfUpdateStatusKind.PendingRestart, currentVersion, ErrorCode: "SELF_UPDATE_CONFIRMED");
        }

        if (state.Phase == SelfUpdatePhase.PendingRestart
            && string.Equals(currentVersion, state.PreviousVersion, StringComparison.OrdinalIgnoreCase))
        {
            ClearStateAndStaging(state.TargetVersion);
            return Failed(currentVersion, "SELF_UPDATE_ROLLED_BACK", "The previous Launcher version restarted after the update attempt.");
        }

        if (state.Phase == SelfUpdatePhase.Applying)
        {
            ClearStateAndStaging(state.TargetVersion);
            return Failed(currentVersion, "SELF_UPDATE_INTERRUPTED", "The Launcher stopped while the self-update was being staged.");
        }

        return Failed(currentVersion, state.ErrorCode ?? "SELF_UPDATE_RECOVERY_PENDING", state.Diagnostic ?? "The self-update state requires administrative inspection.");
    }

    private Task<SelfUpdateStatus> FailApplyAsync(string currentVersion, string version, string errorCode, string diagnostic, string? downloadedPath)
    {
        if (!string.IsNullOrWhiteSpace(downloadedPath) && File.Exists(downloadedPath))
        {
            File.Delete(downloadedPath);
        }

        ClearStateAndStaging(version);
        return Task.FromResult(Failed(currentVersion, errorCode, diagnostic));
    }

    private async Task WriteStateAsync(SelfUpdateState state, CancellationToken cancellationToken)
    {
        Directory.CreateDirectory(stateRoot);
        var temporaryPath = StatePath + ".tmp";
        await using (var stream = File.Create(temporaryPath))
        {
            await JsonSerializer.SerializeAsync(stream, state, JsonOptions, cancellationToken);
            await stream.FlushAsync(cancellationToken);
        }

        File.Move(temporaryPath, StatePath, overwrite: true);
    }

    private async Task WriteStateBestEffortAsync(SelfUpdateState state)
    {
        try
        {
            await WriteStateAsync(state, CancellationToken.None);
        }
        catch
        {
            // Preserve the original apply failure; startup can still report the missing marker.
        }
    }

    private void ClearStateAndStaging(string version)
    {
        if (File.Exists(StatePath))
        {
            File.Delete(StatePath);
        }

        var stagingPath = Path.Combine(stateRoot, "staging", version);
        TryDeleteDirectory(stagingPath);
        var stagingRoot = Path.Combine(stateRoot, "staging");
        if (Directory.Exists(stagingRoot) && !Directory.EnumerateFileSystemEntries(stagingRoot).Any())
        {
            Directory.Delete(stagingRoot);
        }
    }

    private static string LauncherVersionFallback() => LauncherVersionInfo.Version;

    private static VerifiedSelfUpdate ToVerified(VelopackUpdateCandidate candidate) => new(
        candidate.Product,
        candidate.Channel,
        candidate.Version,
        candidate.FeedUri,
        candidate.PackageUri,
        candidate.PackageFileName,
        candidate.SizeBytes,
        candidate.Sha256,
        candidate.ReleaseNotes);

    private static SelfUpdateStatus Failed(string currentVersion, string errorCode, string diagnostic) =>
        new(SelfUpdateStatusKind.Failed, currentVersion, ErrorCode: errorCode, Diagnostic: diagnostic);

    private static bool IsWithin(string root, string path)
    {
        var relative = Path.GetRelativePath(root, path);
        return !Path.IsPathRooted(relative)
            && !string.Equals(relative, "..", StringComparison.Ordinal)
            && !relative.StartsWith(".." + Path.DirectorySeparatorChar, StringComparison.Ordinal)
            && !relative.StartsWith(".." + Path.AltDirectorySeparatorChar, StringComparison.Ordinal);
    }

    private static async Task<string> ComputeSha256Async(string path, CancellationToken cancellationToken)
    {
        await using var stream = File.OpenRead(path);
        var hash = await SHA256.HashDataAsync(stream, cancellationToken);
        return Convert.ToHexString(hash).ToLowerInvariant();
    }

    private static void TryDeleteDirectory(string path)
    {
        if (Directory.Exists(path))
        {
            Directory.Delete(path, recursive: true);
        }
    }

    private enum SelfUpdatePhase
    {
        Applying,
        PendingRestart,
        Failed
    }

    private sealed record SelfUpdateState(
        string PreviousVersion,
        string TargetVersion,
        string PackageFileName,
        SelfUpdatePhase Phase,
        string? ErrorCode,
        string? Diagnostic,
        DateTimeOffset StartedAtUtc);
}

public sealed class VelopackUpdateBackend : IVelopackUpdateBackend
{
    public async Task<VelopackUpdateCandidate?> CheckAsync(Uri feedUri, string channel, CancellationToken cancellationToken)
    {
        var manager = CreateManager(feedUri, channel);
        if (!manager.IsInstalled)
        {
            return null;
        }

        var information = await manager.CheckForUpdatesAsync();
        cancellationToken.ThrowIfCancellationRequested();
        if (information is null)
        {
            return null;
        }

        var asset = information.TargetFullRelease;
        return ToCandidate(feedUri, channel, asset);
    }

    public async Task<DownloadedSelfUpdate> DownloadAsync(VerifiedSelfUpdate update, string destination, CancellationToken cancellationToken)
    {
        var manager = CreateManager(update.FeedUri, update.Channel);
        var information = await manager.CheckForUpdatesAsync() ?? throw new InvalidOperationException("Velopack no longer offers the requested update.");
        var asset = information.TargetFullRelease;
        EnsureMatches(update, asset);
        await manager.DownloadUpdatesAsync(information, _ => { }, cancellationToken);
        var packagesDirectory = VelopackLocator.Current.PackagesDir
            ?? throw new InvalidOperationException("Velopack package directory is unavailable.");
        var downloadedPackage = Path.Combine(packagesDirectory, asset.FileName);
        if (!File.Exists(downloadedPackage))
        {
            throw new FileNotFoundException("Velopack did not produce the expected package.", downloadedPackage);
        }

        Directory.CreateDirectory(Path.GetDirectoryName(destination)!);
        File.Copy(downloadedPackage, destination, overwrite: true);
        return new DownloadedSelfUpdate(destination);
    }

    public async Task ApplyAsync(VerifiedSelfUpdate update, string packagePath, CancellationToken cancellationToken)
    {
        cancellationToken.ThrowIfCancellationRequested();
        var manager = CreateManager(update.FeedUri, update.Channel);
        var information = await manager.CheckForUpdatesAsync() ?? throw new InvalidOperationException("Velopack no longer offers the requested update.");
        var asset = information.TargetFullRelease;
        EnsureMatches(update, asset);
        manager.ApplyUpdatesAndRestart(asset, Array.Empty<string>());
    }

    private static UpdateManager CreateManager(Uri feedUri, string channel) =>
        new(feedUri.ToString(), new UpdateOptions { ExplicitChannel = channel }, VelopackLocator.Current);

    private static VelopackUpdateCandidate ToCandidate(Uri feedUri, string channel, VelopackAsset asset) => new(
        "CopiMineLauncher",
        channel,
        asset.Version.ToString(),
        feedUri,
        new Uri(feedUri, asset.FileName),
        asset.FileName,
        asset.Size,
        asset.SHA256.ToLowerInvariant());

    private static void EnsureMatches(VerifiedSelfUpdate update, VelopackAsset asset)
    {
        if (!string.Equals(update.Version, asset.Version.ToString(), StringComparison.OrdinalIgnoreCase)
            || !string.Equals(update.PackageFileName, asset.FileName, StringComparison.Ordinal)
            || !string.Equals(update.Sha256, asset.SHA256, StringComparison.OrdinalIgnoreCase))
        {
            throw new InvalidDataException("Velopack update metadata changed between check and apply.");
        }
    }
}
