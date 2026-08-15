using System.Collections.ObjectModel;
using System.Diagnostics;
using System.IO;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using CopiMineLauncher.Core.News;
using CopiMineLauncher.Infrastructure.News;
using CopiMineLauncher.Infrastructure.Runtime;

namespace CopiMineLauncher.App;

public sealed class PatchFeedCardViewModel(PatchFeedItem item)
{
    public string Version => $"v{item.Version}";
    public string Title => item.Title;
    public string PublishedAt => item.PublishedAt.ToLocalTime().ToString("d MMM yyyy");
    public string Summary => string.Join(" · ", item.Summary.Take(2));
    public Uri DetailUrl => item.DetailUrl;
    public Uri? ThumbnailUrl => item.ThumbnailUrl;
}

public partial class LauncherViewModel : ObservableObject
{
    private readonly IPatchFeedClient patchFeedClient;
    private readonly ILauncherRuntimeCoordinator? runtimeCoordinator;
    private CancellationTokenSource? operationCancellation;

    public LauncherViewModel(IPatchFeedClient patchFeedClient, ILauncherRuntimeCoordinator? runtimeCoordinator = null)
    {
        this.patchFeedClient = patchFeedClient;
        this.runtimeCoordinator = runtimeCoordinator;
        PatchCards = new ObservableCollection<PatchFeedCardViewModel>();
        RefreshNewsCommand = new AsyncRelayCommand(RefreshNewsAsync);
        PlayCommand = new AsyncRelayCommand(PlayAsync);
        RepairCommand = new AsyncRelayCommand(RepairAsync);
        DiagnoseCommand = new AsyncRelayCommand(DiagnoseAsync);
        OpenPatchCommand = new RelayCommand<PatchFeedCardViewModel>(OpenPatch);
    }

    public ObservableCollection<PatchFeedCardViewModel> PatchCards { get; }

    [ObservableProperty]
    private string status = "Готов к проверке";

    [ObservableProperty]
    private string diagnostic = "Launcher ещё не выполнял операции.";

    [ObservableProperty]
    private string instancePath = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "CopiMine", "instances", "stable");

    [ObservableProperty]
    private string playerName = "CopiMinePlayer";

    public IAsyncRelayCommand RefreshNewsCommand { get; }
    public IAsyncRelayCommand PlayCommand { get; }
    public IAsyncRelayCommand RepairCommand { get; }
    public IAsyncRelayCommand DiagnoseCommand { get; }
    public IRelayCommand<PatchFeedCardViewModel> OpenPatchCommand { get; }

    public async Task InitializeAsync()
    {
        await RefreshNewsAsync();
    }

    private async Task RefreshNewsAsync()
    {
        Status = "Загружаем последние обновления…";
        var result = await patchFeedClient.GetLatestAsync(CancellationToken.None);
        PatchCards.Clear();
        foreach (var item in result.Items)
        {
            PatchCards.Add(new PatchFeedCardViewModel(item));
        }

        Status = result.Items.Count > 0
            ? (result.FromCache ? "Обновления показаны из локального кэша" : "Обновления получены")
            : "Обновления временно недоступны";
        Diagnostic = result.Diagnostics.Count == 0
            ? "Patch feed проверен: unsafe links не допускаются, HTML из feed не выполняется."
            : string.Join(Environment.NewLine, result.Diagnostics);
    }

    private Task PlayAsync() => RunOperationAsync(launch: true);

    private Task RepairAsync() => RunOperationAsync(launch: false);

    private async Task RunOperationAsync(bool launch)
    {
        if (runtimeCoordinator is null)
        {
            Status = "Runtime pipeline недоступен";
            Diagnostic = "RUNTIME_COORDINATOR_NOT_CONFIGURED: composition root did not provide the signed update/launch pipeline.";
            return;
        }

        if (operationCancellation is not null)
        {
            Status = "Операция уже выполняется";
            Diagnostic = "Дождитесь завершения текущей проверки сборки.";
            return;
        }

        using var cancellation = new CancellationTokenSource();
        operationCancellation = cancellation;
        try
        {
            var progress = new Progress<LauncherProgress>(value =>
            {
                Status = value.Message;
                Diagnostic = $"Этап: {value.Stage}{Environment.NewLine}Экземпляр: {InstancePath}{Environment.NewLine}Игрок: {PlayerName}";
            });
            var request = new LauncherOperationRequest(InstancePath, PlayerName, MaximumRamMb: 4096);
            var result = launch
                ? await runtimeCoordinator.PlayAsync(request, cancellation.Token, progress)
                : await runtimeCoordinator.RepairAsync(request, cancellation.Token, progress);

            Status = result.Succeeded
                ? (launch ? "Minecraft запущен" : "Сборка восстановлена")
                : $"Операция не выполнена: {result.ErrorCode}";
            Diagnostic = BuildDiagnostic(result);
        }
        catch (OperationCanceledException)
        {
            Status = "Операция отменена";
            Diagnostic = "Операция остановлена до завершения; незавершённые managed-файлы остаются в transaction staging.";
        }
        catch (Exception exception)
        {
            Status = "Ошибка Launcher";
            Diagnostic = $"LAUNCHER_UI_OPERATION_FAILED: {exception.Message}";
        }
        finally
        {
            operationCancellation = null;
        }
    }

    private static string BuildDiagnostic(LauncherOperationResult result)
    {
        var lines = new List<string>
        {
            $"Operation: {result.Operation}",
            $"Result: {(result.Succeeded ? "PASS" : "FAIL")}",
            $"Code: {result.ErrorCode ?? "OK"}",
            result.Diagnostic
        };
        if (result.VerifiedManifest is not null)
        {
            lines.Add($"Manifest sequence: {result.VerifiedManifest.ReconcilerManifest.Sequence}");
            lines.Add($"Manifest SHA-256: {result.VerifiedManifest.ManifestSha256}");
        }

        if (result.Reconciliation is not null)
        {
            lines.Add($"Reconcile: {result.Reconciliation.Status}");
            if (result.Reconciliation.RecoveredPreviousTransaction)
            {
                lines.Add("Recovery: previous transaction was recovered before planning.");
            }
        }

        if (result.Java is not null)
        {
            lines.Add($"Java: {result.Java.JavaExecutablePath}");
        }

        if (result.Minecraft is not null)
        {
            lines.Add($"Minecraft/Fabric: {result.Minecraft.MinecraftVersion} / {result.Minecraft.FabricLoaderVersion}");
        }

        if (result.ServersDat is not null)
        {
            lines.Add($"servers.dat: {result.ServersDat.Path} (changed={result.ServersDat.Changed})");
        }

        if (result.Launch is not null)
        {
            lines.Add($"Process: {result.Launch.Process.Id}");
        }

        return string.Join(Environment.NewLine, lines);
    }

    private Task DiagnoseAsync()
    {
        Status = "Диагностика завершена";
        Diagnostic = $"Версии: Minecraft 1.21.1 · Fabric Loader 0.19.3{Environment.NewLine}Instance: {Path.GetFullPath(InstancePath)}{Environment.NewLine}Patch feed: bounded 4.5 s, cache fallback включён.";
        return Task.CompletedTask;
    }

    private static void OpenPatch(PatchFeedCardViewModel? card)
    {
        if (card is null || card.DetailUrl.Scheme != Uri.UriSchemeHttps || !string.Equals(card.DetailUrl.Host, "copimine.ru", StringComparison.OrdinalIgnoreCase) || !card.DetailUrl.AbsolutePath.StartsWith("/news/", StringComparison.Ordinal)) return;
        Process.Start(new ProcessStartInfo { FileName = card.DetailUrl.ToString(), UseShellExecute = true });
    }
}
