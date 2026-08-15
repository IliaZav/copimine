using System.Collections.ObjectModel;
using System.Diagnostics;
using System.IO;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using CopiMineLauncher.Core.News;
using CopiMineLauncher.Infrastructure.News;
using CopiMineLauncher.Infrastructure.Runtime;
using CopiMineLauncher.Infrastructure.SelfUpdate;

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
    private readonly ISelfUpdateService? selfUpdateService;
    private CancellationTokenSource? operationCancellation;
    private VerifiedSelfUpdate? availableSelfUpdate;

    public LauncherViewModel(
        IPatchFeedClient patchFeedClient,
        ILauncherRuntimeCoordinator? runtimeCoordinator = null,
        ISelfUpdateService? selfUpdateService = null)
    {
        this.patchFeedClient = patchFeedClient;
        this.runtimeCoordinator = runtimeCoordinator;
        this.selfUpdateService = selfUpdateService;
        PatchCards = new ObservableCollection<PatchFeedCardViewModel>();
        RefreshNewsCommand = new AsyncRelayCommand(RefreshNewsAsync);
        PlayCommand = new AsyncRelayCommand(PlayAsync);
        RepairCommand = new AsyncRelayCommand(RepairAsync);
        DiagnoseCommand = new AsyncRelayCommand(DiagnoseAsync);
        CheckSelfUpdateCommand = new AsyncRelayCommand(CheckSelfUpdateAsync);
        ApplySelfUpdateCommand = new AsyncRelayCommand(ApplySelfUpdateAsync);
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

    [ObservableProperty]
    private string selfUpdateStatus = "Launcher обновлён";

    public IAsyncRelayCommand RefreshNewsCommand { get; }
    public IAsyncRelayCommand PlayCommand { get; }
    public IAsyncRelayCommand RepairCommand { get; }
    public IAsyncRelayCommand DiagnoseCommand { get; }
    public IAsyncRelayCommand CheckSelfUpdateCommand { get; }
    public IAsyncRelayCommand ApplySelfUpdateCommand { get; }
    public IRelayCommand<PatchFeedCardViewModel> OpenPatchCommand { get; }

    public async Task InitializeAsync()
    {
        await RefreshNewsAsync();
        await RecoverSelfUpdateAsync();
        await CheckSelfUpdateAsync();
        await PrepareFirstRunAsync();
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

    private async Task PrepareFirstRunAsync()
    {
        if (runtimeCoordinator is null || IsInstanceReady())
        {
            return;
        }

        Status = "Подготавливаем игру…";
        Diagnostic = "Первый запуск: скачиваем Java 21, Minecraft 1.21.1, Fabric и файлы сборки.";
        await RunOperationAsync(launch: false, automatic: true);
    }

    private bool IsInstanceReady()
    {
        try
        {
            var statePath = Path.Combine(Path.GetFullPath(InstancePath), ".copimine", "managed-state.json");
            return File.Exists(statePath);
        }
        catch (Exception exception)
        {
            Diagnostic = $"Путь экземпляра не удалось проверить: {exception.Message}";
            return false;
        }
    }

    private async Task RecoverSelfUpdateAsync()
    {
        if (selfUpdateService is null)
        {
            return;
        }

        try
        {
            var result = await selfUpdateService.RecoverAsync(CancellationToken.None);
            if (result.Kind == SelfUpdateStatusKind.PendingRestart)
            {
                SelfUpdateStatus = "Launcher обновлён после перезапуска";
            }
            else if (result.Kind == SelfUpdateStatusKind.Failed)
            {
                SelfUpdateStatus = "Проверка обновления Launcher не завершена";
                Diagnostic = FormatSelfUpdateDiagnostic(result);
            }
        }
        catch (Exception exception)
        {
            SelfUpdateStatus = "Проверка обновления Launcher не завершена";
            Diagnostic = $"SELF_UPDATE_RECOVERY_FAILED: {exception.Message}";
        }
    }

    private async Task CheckSelfUpdateAsync()
    {
        if (selfUpdateService is null)
        {
            return;
        }

        SelfUpdateStatus = "Проверяем обновление Launcher…";
        try
        {
            var result = await selfUpdateService.CheckAsync(CancellationToken.None);
            availableSelfUpdate = result.Update;
            SelfUpdateStatus = result.Kind switch
            {
                SelfUpdateStatusKind.UpdateAvailable => $"Доступно обновление Launcher: v{result.Update!.Version}",
                SelfUpdateStatusKind.Failed => "Проверка обновления Launcher не завершена",
                _ => "Launcher обновлён"
            };
            if (result.Kind == SelfUpdateStatusKind.Failed)
            {
                Diagnostic = FormatSelfUpdateDiagnostic(result);
            }
        }
        catch (OperationCanceledException)
        {
            throw;
        }
        catch (Exception exception)
        {
            availableSelfUpdate = null;
            SelfUpdateStatus = "Проверка обновления Launcher не завершена";
            Diagnostic = $"SELF_UPDATE_CHECK_FAILED: {exception.Message}";
        }
    }

    private async Task ApplySelfUpdateAsync()
    {
        if (selfUpdateService is null)
        {
            SelfUpdateStatus = "Обновление Launcher доступно только в установленной версии";
            return;
        }

        if (availableSelfUpdate is null)
        {
            await CheckSelfUpdateAsync();
        }

        if (availableSelfUpdate is null)
        {
            return;
        }

        Status = "Скачиваем обновление Launcher…";
        var result = await selfUpdateService.ApplyAsync(availableSelfUpdate, CancellationToken.None);
        if (result.Kind == SelfUpdateStatusKind.PendingRestart)
        {
            SelfUpdateStatus = "Обновление установлено; Launcher перезапустится";
            Status = "Launcher обновлён";
            availableSelfUpdate = null;
        }
        else
        {
            SelfUpdateStatus = "Обновление Launcher не установлено";
            Diagnostic = FormatSelfUpdateDiagnostic(result);
        }
    }

    private async Task RunOperationAsync(bool launch, bool automatic = false)
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
                ? (launch ? "Minecraft запущен" : "Игра готова")
                : (automatic ? "Не удалось подготовить игру" : $"Операция не выполнена: {result.ErrorCode}");
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
        Diagnostic = $"Minecraft 1.21.1 · Fabric Loader 0.19.3{Environment.NewLine}Папка игры: {Path.GetFullPath(InstancePath)}{Environment.NewLine}Новости: при сбое сети используется последний сохранённый выпуск.";
        return Task.CompletedTask;
    }

    private static string FormatSelfUpdateDiagnostic(SelfUpdateStatus result) =>
        $"{result.ErrorCode ?? "SELF_UPDATE_UNKNOWN"}: {result.Diagnostic ?? "Нет дополнительной информации."}";

    private static void OpenPatch(PatchFeedCardViewModel? card)
    {
        if (card is null || card.DetailUrl.Scheme != Uri.UriSchemeHttps || !string.Equals(card.DetailUrl.Host, "copimine.ru", StringComparison.OrdinalIgnoreCase) || !card.DetailUrl.AbsolutePath.StartsWith("/news/", StringComparison.Ordinal)) return;
        Process.Start(new ProcessStartInfo { FileName = card.DetailUrl.ToString(), UseShellExecute = true });
    }
}
