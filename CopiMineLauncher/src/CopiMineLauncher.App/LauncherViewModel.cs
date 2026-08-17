using System.Collections.ObjectModel;
using System.Diagnostics;
using System.IO;
using System.Windows;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using CopiMineLauncher.Core;
using CopiMineLauncher.Core.Launch;
using CopiMineLauncher.Core.News;
using CopiMineLauncher.Infrastructure.Binding;
using CopiMineLauncher.Infrastructure.News;
using CopiMineLauncher.Infrastructure.Launch;
using CopiMineLauncher.Infrastructure.Runtime;
using CopiMineLauncher.Infrastructure.SelfUpdate;

namespace CopiMineLauncher.App;

public sealed class PatchFeedCardViewModel(PatchFeedItem item, int index = 0)
{
    public string Version => $"v{item.Version}";
    public string Title => item.Title;
    public string PublishedAt => item.PublishedAt.ToLocalTime().ToString("d MMM yyyy");
    public string Summary => string.Join(" · ", item.Summary.Take(2));
    public Uri DetailUrl => item.DetailUrl;
    public Uri? ThumbnailUrl => item.ThumbnailUrl;
    public bool UsesRemoteThumbnail => item.ThumbnailUrl is not null;
    public string ArtworkPath => $"{LauncherVisualAssetCatalog.Root}/{LauncherVisualAssetCatalog.GetNewsArtwork(index)}";
}

public sealed class MinecraftLaunchFailureEventArgs(MinecraftLaunchFailureReport report) : EventArgs
{
    public MinecraftLaunchFailureReport Report { get; } = report ?? throw new ArgumentNullException(nameof(report));
}

public partial class LauncherViewModel : ObservableObject
{
    private readonly IPatchFeedClient patchFeedClient;
    private readonly ILauncherRuntimeCoordinator? runtimeCoordinator;
    private readonly ISelfUpdateService? selfUpdateService;
    private readonly LauncherProfileStore profileStore;
    private readonly LauncherSettingsStore settingsStore;
    private readonly ILauncherBindingClient? launcherBindingClient;
    private readonly LauncherBindingStateStore launcherBindingStateStore;
    private readonly Action<string, string> nicknameChangedNotifier;
    private CancellationTokenSource? operationCancellation;
    private VerifiedSelfUpdate? availableSelfUpdate;
    private bool loadingProfile;
    private bool applyingNicknameSync;
    private string launcherAccessToken = string.Empty;
    private LauncherBindingState launcherBindingState = new();
    private string savedPlayerName = "CopiMinePlayer";
    private readonly object minecraftLifecycleGate = new();
    private Process? monitoredMinecraftProcess;
    private bool launcherHiddenForMinecraft;

    public LauncherViewModel(
        IPatchFeedClient patchFeedClient,
        ILauncherRuntimeCoordinator? runtimeCoordinator = null,
        ISelfUpdateService? selfUpdateService = null,
        string? defaultInstancePath = null,
        LauncherProfileStore? profileStore = null,
        Action<string, string>? nicknameChangedNotifier = null,
        LauncherSettingsStore? settingsStore = null,
        ILauncherBindingClient? launcherBindingClient = null,
        LauncherBindingStateStore? launcherBindingStateStore = null)
    {
        this.patchFeedClient = patchFeedClient;
        this.runtimeCoordinator = runtimeCoordinator;
        this.selfUpdateService = selfUpdateService;
        this.profileStore = profileStore ?? new LauncherProfileStore(LauncherInstallPaths.ResolveLauncherDataRoot());
        this.settingsStore = settingsStore ?? new LauncherSettingsStore(LauncherInstallPaths.ResolveLauncherDataRoot());
        this.launcherBindingClient = launcherBindingClient;
        this.launcherBindingStateStore = launcherBindingStateStore ?? new LauncherBindingStateStore(LauncherInstallPaths.ResolveLauncherDataRoot());
        this.nicknameChangedNotifier = nicknameChangedNotifier ?? ShowNicknameChangedWarning;
        InstancePath = defaultInstancePath ?? LauncherInstallPaths.ResolveMinecraftRoot();
        PatchCards = new ObservableCollection<PatchFeedCardViewModel>();
        PatchCards.CollectionChanged += (_, _) => OnPropertyChanged(nameof(HasPatchCards));
        RefreshNewsCommand = new AsyncRelayCommand(RefreshNewsAsync);
        PlayCommand = new AsyncRelayCommand(PlayAsync, CanStartOperation);
        RepairCommand = new AsyncRelayCommand(RepairAsync, CanStartOperation);
        DiagnoseCommand = new AsyncRelayCommand(DiagnoseAsync);
        CheckSelfUpdateCommand = new AsyncRelayCommand(CheckSelfUpdateAsync);
        ApplySelfUpdateCommand = new AsyncRelayCommand(ApplySelfUpdateAsync);
        OpenPatchCommand = new RelayCommand<PatchFeedCardViewModel>(OpenPatch);
        OpenInstanceFolderCommand = new RelayCommand(OpenInstanceFolder);
        OpenWebsiteCommand = new RelayCommand(OpenWebsite);
        OpenDiscordCommand = new RelayCommand(OpenDiscord);
        OpenAccountLinkCommand = new AsyncRelayCommand(OpenAccountLinkAsync);
    }

    public ObservableCollection<PatchFeedCardViewModel> PatchCards { get; }
    public bool HasPatchCards => PatchCards.Count > 0;

    [ObservableProperty]
    private string status = "Готово";

    [ObservableProperty]
    private string diagnostic = "Операций ещё не было.";

    [ObservableProperty]
    private string instancePath = LauncherInstallPaths.ResolveMinecraftRoot();

    [ObservableProperty]
    private string playerName = "CopiMinePlayer";

    [ObservableProperty]
    private string selfUpdateStatus = "Обновлено";

    [ObservableProperty]
    private bool isBusy;

    [ObservableProperty]
    private bool isInitializing;

    [ObservableProperty]
    private bool isLaunching;

    [ObservableProperty]
    private double progressPercent;

    [ObservableProperty]
    private bool isProgressIndeterminate;

    [ObservableProperty]
    private string progressLabel = "0%";

    [ObservableProperty]
    private string loadingStage = "Готово";

    [ObservableProperty]
    private bool isDiagnosticOpen;

    [ObservableProperty]
    private bool isLauncherLinked;

    [ObservableProperty]
    private bool launcherLinkRequired;

    [ObservableProperty]
    private int maximumRamMb = 4096;

    [ObservableProperty]
    private int resolutionWidth = 1280;

    [ObservableProperty]
    private int resolutionHeight = 720;

    [ObservableProperty]
    private bool fullscreen;

    [ObservableProperty]
    private bool useRussianLanguageDefault = true;

    [ObservableProperty]
    private bool disableNarratorDefault = true;

    [ObservableProperty]
    private bool setMasterVolumeToFifteenPercentDefault = true;

    public IAsyncRelayCommand RefreshNewsCommand { get; }
    public IAsyncRelayCommand PlayCommand { get; }
    public IAsyncRelayCommand RepairCommand { get; }
    public IAsyncRelayCommand DiagnoseCommand { get; }
    public IAsyncRelayCommand CheckSelfUpdateCommand { get; }
    public IAsyncRelayCommand ApplySelfUpdateCommand { get; }
    public IRelayCommand<PatchFeedCardViewModel> OpenPatchCommand { get; }
    public IRelayCommand OpenInstanceFolderCommand { get; }
    public IRelayCommand OpenWebsiteCommand { get; }
    public IRelayCommand OpenDiscordCommand { get; }
    public IAsyncRelayCommand OpenAccountLinkCommand { get; }
    public string LauncherDataPath => LauncherInstallPaths.ResolveLauncherDataRoot();
    public int MaximumRamLimitMb => LauncherMemoryLimits.MaximumRamMb;
    public string PlayButtonText => IsLaunching ? "Запуск…" : "Играть";
    public bool HasMinecraftDefaultsSelection => MinecraftDefaultSettingsStore.IsConfigured(InstancePath);

    public event EventHandler? LauncherHideRequested;
    public event EventHandler? LauncherRestoreRequested;
    public event EventHandler? LauncherBindingRequired;
    public event EventHandler<MinecraftLaunchFailureEventArgs>? LaunchFailureRequested;

    partial void OnIsBusyChanged(bool value)
    {
        PlayCommand.NotifyCanExecuteChanged();
        RepairCommand.NotifyCanExecuteChanged();
    }

    partial void OnIsLaunchingChanged(bool value) => OnPropertyChanged(nameof(PlayButtonText));

    partial void OnInstancePathChanged(string value)
    {
        LoadMinecraftDefaults();
        OnPropertyChanged(nameof(HasMinecraftDefaultsSelection));
    }

    public async Task InitializeAsync()
    {
        IsInitializing = true;
        try
        {
            TraceStartup("initialize:start");
            LoadPlayerProfile();
            TraceStartup($"profile:loaded:{PlayerName}");
            LoadSettings();
            TraceStartup("settings:loaded");
            LoadLauncherBinding();
            TraceStartup($"binding:loaded:{IsLauncherLinked}");
            await RefreshNewsAsync();
            TraceStartup("news:loaded");
            if (!LauncherInstallPaths.IsLoopbackStagingEnvironment())
            {
                TraceStartup("self-update:check");
                await RecoverSelfUpdateAsync();
                await CheckSelfUpdateAsync();
                TraceStartup("self-update:done");
            }
            else
            {
                TraceStartup("self-update:skipped-staging");
            }

            TraceStartup("prepare:start");
            await PrepareFirstRunAsync();
            TraceStartup("prepare:done");
            if (launcherBindingClient is not null && !IsLauncherLinked)
            {
                Status = "Привязка Launcher обязательна";
                LoadingStage = "Откройте сайт и подтвердите привязку";
                Diagnostic = "Для запуска Minecraft сначала привяжите Launcher к аккаунту сайта. Пароль сайта и AuthMe Launcher не получает.";
            }
        }
        finally
        {
            IsInitializing = false;
        }
    }

    private async Task RefreshNewsAsync()
    {
        TraceStartup("news:request");
        Status = "Загружаем новости…";
        var result = await patchFeedClient.GetLatestAsync(CancellationToken.None);
        PatchCards.Clear();
        for (var index = 0; index < result.Items.Count; index++)
        {
            PatchCards.Add(new PatchFeedCardViewModel(result.Items[index], index));
        }

        Status = result.Items.Count > 0
            ? (result.FromCache ? "Обновления показаны из локального кэша" : "Обновления получены")
            : "Обновления временно недоступны";
        Diagnostic = result.Diagnostics.Count == 0
            ? "Новости проверены."
            : string.Join(Environment.NewLine, result.Diagnostics);
        TraceStartup($"news:response:{result.Items.Count}:{result.Diagnostics.Count}");
    }

    async partial void OnPlayerNameChanged(string value)
    {
        if (loadingProfile || applyingNicknameSync || string.Equals(value, savedPlayerName, StringComparison.Ordinal))
        {
            return;
        }

        if (!LauncherProfileStore.IsValidPlayerName(value))
        {
            Diagnostic = "Ник: 3–16 латинских букв, цифр или _.";
            return;
        }

        var previousName = savedPlayerName;
        try
        {
            if (launcherBindingClient is not null && IsLauncherLinked)
            {
                if (string.IsNullOrWhiteSpace(launcherAccessToken))
                {
                    throw new LauncherBindingException("LAUNCHER_NICKNAME_ACCESS_REQUIRED", "У привязанного Launcher отсутствует локальный access token. Выполните привязку ещё раз.");
                }

                Status = "Синхронизируем новый ник…";
                LoadingStage = "Сохраняем данные игрока и AuthMe";
                var result = await launcherBindingClient.ChangeNicknameAsync(
                    launcherAccessToken,
                    previousName,
                    value,
                    CancellationToken.None);
                if (!result.PreservePlayerState || !result.AuthMePasswordPreserved)
                {
                    throw new LauncherBindingException("LAUNCHER_NICKNAME_NOT_PRESERVED", "Сервер не подтвердил перенос данных игрока и сохранение пароля AuthMe.");
                }

                launcherBindingState = launcherBindingState with { MinecraftName = value };
                launcherBindingStateStore.Save(launcherBindingState);
                Diagnostic = "Ник изменён. Данные игрока, whitelist и пароль AuthMe сохранены.";
            }
            else
            {
                Diagnostic = "Ник сохранён локально. Для синхронизации с сайтом сначала привяжите Launcher.";
            }
            profileStore.SavePlayerName(value);
            savedPlayerName = value;
            nicknameChangedNotifier(previousName, value);
            Status = "Ник сохранён";
        }
        catch (LauncherBindingException exception)
        {
            applyingNicknameSync = true;
            try
            {
                PlayerName = previousName;
            }
            finally
            {
                applyingNicknameSync = false;
            }
            Status = "Ник не изменён";
            LoadingStage = $"Причина: {exception.Code}";
            Diagnostic = $"{exception.Code}: {exception.Message}";
            IsDiagnosticOpen = true;
        }
        catch (Exception exception)
        {
            applyingNicknameSync = true;
            try
            {
                PlayerName = previousName;
            }
            finally
            {
                applyingNicknameSync = false;
            }
            Status = "Ник не изменён";
            Diagnostic = $"PLAYER_PROFILE_SAVE_FAILED: {exception.Message}";
            IsDiagnosticOpen = true;
        }
    }

    private void LoadPlayerProfile()
    {
        var storedName = profileStore.LoadPlayerName();
        if (string.IsNullOrWhiteSpace(storedName))
        {
            return;
        }

        loadingProfile = true;
        try
        {
            PlayerName = storedName;
            savedPlayerName = storedName;
        }
        finally
        {
            loadingProfile = false;
        }
    }

    private void LoadSettings()
    {
        var settings = settingsStore.Load();
        MaximumRamMb = settings.MaximumRamMb;
        ResolutionWidth = settings.ResolutionWidth;
        ResolutionHeight = settings.ResolutionHeight;
        Fullscreen = settings.Fullscreen;
        LoadMinecraftDefaults();
    }

    private void LoadMinecraftDefaults()
    {
        var settings = MinecraftDefaultSettingsStore.Load(InstancePath) ?? new MinecraftDefaultSettings();
        UseRussianLanguageDefault = settings.UseRussianLanguage;
        DisableNarratorDefault = settings.DisableNarrator;
        SetMasterVolumeToFifteenPercentDefault = settings.SetMasterVolumeToFifteenPercent;
    }

    private void LoadLauncherBinding()
    {
        var state = launcherBindingStateStore.Load();
        launcherBindingState = state;
        launcherAccessToken = state.AccessToken;
        IsLauncherLinked = launcherBindingClient is not null && state.Linked;
        LauncherLinkRequired = launcherBindingClient is not null && !IsLauncherLinked;
    }

    public void SaveSettings()
    {
        try
        {
            settingsStore.Save(new LauncherSettings(MaximumRamMb, ResolutionWidth, ResolutionHeight, Fullscreen));
            SaveMinecraftDefaults();
            Status = "Настройки сохранены";
            Diagnostic = $"RAM: {MaximumRamMb} МБ · Разрешение: {ResolutionWidth}×{ResolutionHeight} · Полный экран: {(Fullscreen ? "да" : "нет")}";
        }
        catch (Exception exception)
        {
            Status = "Настройки не сохранены";
            Diagnostic = $"LAUNCHER_SETTINGS_SAVE_FAILED: {exception.Message}";
            throw;
        }
    }

    public void SaveMinecraftDefaults()
    {
        MinecraftDefaultSettingsStore.Save(
            InstancePath,
            new MinecraftDefaultSettings(
                UseRussianLanguageDefault,
                DisableNarratorDefault,
                SetMasterVolumeToFifteenPercentDefault));
        OnPropertyChanged(nameof(HasMinecraftDefaultsSelection));
    }

    private Task PlayAsync() => RunOperationAsync(launch: true);

    private Task RepairAsync() => RunOperationAsync(launch: false);

    private bool CanStartOperation() => !IsBusy;

    private async Task PrepareFirstRunAsync()
    {
        TraceStartup($"prepare:check:coordinator={(runtimeCoordinator is not null)}:ready={IsInstanceReady()}");
        if (runtimeCoordinator is null || IsInstanceReady())
        {
            return;
        }

        Status = "Готовим игру…";
        Diagnostic = "Первый запуск: загружаем Java, Minecraft и моды.";
        await RunOperationAsync(launch: false, automatic: true);
        TraceStartup("prepare:operation-returned");
    }

    private bool IsInstanceReady()
    {
        try
        {
            var instanceRoot = Path.GetFullPath(InstancePath);
            var requiredFiles = new[]
            {
                Path.Combine(instanceRoot, ".copimine", "managed-state.json"),
                Path.Combine(instanceRoot, ".copimine", "java", "21.0.10", "bin", "java.exe"),
                Path.Combine(instanceRoot, "servers.dat")
            };
            if (requiredFiles.Any(path => !File.Exists(path)))
            {
                return false;
            }

            return Directory.Exists(Path.Combine(instanceRoot, "versions", "1.21.1"))
                && Directory.Exists(Path.Combine(instanceRoot, "mods"))
                && Directory.EnumerateFiles(Path.Combine(instanceRoot, "mods"), "*.jar", SearchOption.TopDirectoryOnly).Any();
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
                SelfUpdateStatus = "Обновлено после перезапуска";
            }
            else if (result.Kind == SelfUpdateStatusKind.Failed)
            {
                SelfUpdateStatus = "Не удалось проверить обновление";
                Diagnostic = FormatSelfUpdateDiagnostic(result);
            }
        }
        catch (Exception exception)
        {
            SelfUpdateStatus = "Не удалось проверить обновление";
            Diagnostic = $"SELF_UPDATE_RECOVERY_FAILED: {exception.Message}";
        }
    }

    private async Task CheckSelfUpdateAsync()
    {
        if (selfUpdateService is null)
        {
            return;
        }

        SelfUpdateStatus = "Проверяем обновление…";
        try
        {
            var result = await selfUpdateService.CheckAsync(CancellationToken.None);
            availableSelfUpdate = result.Update;
            SelfUpdateStatus = result.Kind switch
            {
                SelfUpdateStatusKind.UpdateAvailable => $"Доступна новая версия: v{result.Update!.Version}",
                SelfUpdateStatusKind.Failed => "Не удалось проверить обновление",
                _ => "Обновлено"
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
            SelfUpdateStatus = "Не удалось проверить обновление";
            Diagnostic = $"SELF_UPDATE_CHECK_FAILED: {exception.Message}";
        }
    }

    private async Task ApplySelfUpdateAsync()
    {
        if (selfUpdateService is null)
        {
            SelfUpdateStatus = "Обновление доступно в установленной версии";
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

        Status = "Скачиваем обновление…";
        var result = await selfUpdateService.ApplyAsync(availableSelfUpdate, CancellationToken.None);
        if (result.Kind == SelfUpdateStatusKind.PendingRestart)
        {
            SelfUpdateStatus = "Обновление установлено. Перезапустите Launcher.";
            Status = "Обновлено";
            availableSelfUpdate = null;
        }
        else
        {
            SelfUpdateStatus = "Обновление не установлено";
            Diagnostic = FormatSelfUpdateDiagnostic(result);
        }
    }

    private async Task RunOperationAsync(bool launch, bool automatic = false)
    {
        TraceStartup($"operation:start:{(launch ? "play" : "repair")}");
        if (runtimeCoordinator is null)
        {
            Status = "Runtime pipeline недоступен";
            Diagnostic = "RUNTIME_COORDINATOR_NOT_CONFIGURED: composition root did not provide the signed update/launch pipeline.";
            return;
        }

        if (launch && launcherBindingClient is not null && !IsLauncherLinked)
        {
            Status = "Привязка Launcher обязательна";
            LoadingStage = "Сначала подтвердите аккаунт на сайте";
            Diagnostic = "LAUNCHER_LINK_REQUIRED: нажмите «Привязать на сайте», войдите в свой аккаунт и подтвердите привязку. Пароль не передаётся в Launcher.";
            IsDiagnosticOpen = true;
            LauncherBindingRequired?.Invoke(this, EventArgs.Empty);
            return;
        }

        if (operationCancellation is not null)
        {
            Status = "Операция уже выполняется";
            Diagnostic = "Дождитесь окончания проверки.";
            return;
        }

        using var cancellation = new CancellationTokenSource();
        operationCancellation = cancellation;
        IsBusy = true;
        IsLaunching = launch;
        ProgressPercent = 0;
        IsProgressIndeterminate = true;
        ProgressLabel = "…";
        LoadingStage = launch ? "Запускаем Minecraft…" : "Проверяем файлы…";
        var progressGate = new object();
        var operationFinished = false;
        try
        {
            var progress = new Progress<LauncherProgress>(value =>
            {
                lock (progressGate)
                {
                    if (operationFinished)
                    {
                        return;
                    }

                    Status = value.Message;
                    LoadingStage = value.Message;
                    IsProgressIndeterminate = value.Stage is "manifest" or "reconcile" or "preflight" or "java" or "minecraft";
                    ProgressPercent = value.Stage switch
                    {
                        "manifest" => 2,
                        "reconcile" => 28,
                        "preflight" => 36,
                        "java" => 46,
                        "minecraft" => 72,
                        "servers" => 88,
                        "launch" => 96,
                        _ => ProgressPercent
                    };
                    ProgressLabel = IsProgressIndeterminate ? "…" : $"{ProgressPercent:0}%";
                    Diagnostic = $"Этап: {value.Stage}{Environment.NewLine}Экземпляр: {InstancePath}{Environment.NewLine}Игрок: {PlayerName}";
                }
            });
            var request = new LauncherOperationRequest(
                InstancePath,
                PlayerName,
                MaximumRamMb: MaximumRamMb,
                ResolutionWidth: ResolutionWidth,
                ResolutionHeight: ResolutionHeight,
                Fullscreen: Fullscreen);
            var result = launch
                ? await runtimeCoordinator.PlayAsync(request, cancellation.Token, progress)
                : await runtimeCoordinator.RepairAsync(request, cancellation.Token, progress);

            MinecraftLaunchFailureReport? launchFailure = null;
            lock (progressGate)
            {
                operationFinished = true;
                Status = result.Succeeded
                    ? (launch ? "Minecraft запущен" : "Игра готова")
                    : (automatic ? "Не удалось подготовить игру" : "Не удалось выполнить действие");
                LoadingStage = result.Succeeded
                    ? (launch ? "Minecraft запущен" : "Сборка готова")
                    : $"Причина: {result.ErrorCode ?? "UNKNOWN_ERROR"}";
                IsDiagnosticOpen = !result.Succeeded;
                if (result.Succeeded)
                {
                    ProgressPercent = 100;
                }
                IsProgressIndeterminate = false;
                ProgressLabel = $"{ProgressPercent:0}%";
                Diagnostic = BuildDiagnostic(result, InstancePath);
                launchFailure = launch && !result.Succeeded ? result.LaunchFailure : null;
            }
            if (launchFailure is not null)
            {
                RunOnUi(() => LaunchFailureRequested?.Invoke(this, new MinecraftLaunchFailureEventArgs(launchFailure)));
            }
            if (launch && result.Succeeded && result.Launch is not null)
            {
                BeginMinecraftLifecycle(result.Launch.Process);
            }
            TraceStartup($"operation:result:{result.Succeeded}:{result.ErrorCode ?? "OK"}:{result.Diagnostic.Replace(Environment.NewLine, " | ", StringComparison.Ordinal)}");
        }
        catch (OperationCanceledException)
        {
            lock (progressGate)
            {
                operationFinished = true;
                Status = "Операция отменена";
                LoadingStage = "Файлы не изменены";
                Diagnostic = "Проверка остановлена. Незавершённые файлы не применены.";
                IsDiagnosticOpen = true;
                IsProgressIndeterminate = false;
                ProgressLabel = $"{ProgressPercent:0}%";
            }
        }
        catch (Exception exception)
        {
            lock (progressGate)
            {
                operationFinished = true;
                Status = "Ошибка";
                LoadingStage = "Причина указана ниже";
                Diagnostic = $"LAUNCHER_UI_OPERATION_FAILED: {exception.Message}";
                IsDiagnosticOpen = true;
                IsProgressIndeterminate = false;
                ProgressLabel = $"{ProgressPercent:0}%";
            }
            TraceStartup($"operation:exception:{exception.GetType().Name}:{exception.Message}");
        }
        finally
        {
            lock (progressGate)
            {
                operationFinished = true;
            }
            IsBusy = false;
            IsLaunching = false;
            operationCancellation = null;
            TraceStartup("operation:finished");
        }
    }

    private void BeginMinecraftLifecycle(Process process)
    {
        ArgumentNullException.ThrowIfNull(process);
        lock (minecraftLifecycleGate)
        {
            monitoredMinecraftProcess = process;
            launcherHiddenForMinecraft = true;
        }

        process.Exited += OnMinecraftProcessExited;
        process.EnableRaisingEvents = true;
        if (process.HasExited)
        {
            OnMinecraftProcessExited(process, EventArgs.Empty);
            return;
        }

        LauncherHideRequested?.Invoke(this, EventArgs.Empty);
        if (process.HasExited)
        {
            OnMinecraftProcessExited(process, EventArgs.Empty);
        }
    }

    private void OnMinecraftProcessExited(object? sender, EventArgs e)
    {
        if (sender is not Process process)
        {
            return;
        }

        lock (minecraftLifecycleGate)
        {
            if (!ReferenceEquals(monitoredMinecraftProcess, process) || !launcherHiddenForMinecraft)
            {
                return;
            }

            monitoredMinecraftProcess = null;
            launcherHiddenForMinecraft = false;
        }

        RunOnUi(() =>
        {
            var exitCode = "unknown";
            try
            {
                exitCode = process.ExitCode.ToString(System.Globalization.CultureInfo.InvariantCulture);
            }
            catch
            {
                // The process can disappear between Exited and ExitCode access.
            }

            Status = "Minecraft завершён";
            LoadingStage = "Можно запустить снова";
            ProgressPercent = 0;
            ProgressLabel = "0%";
            IsProgressIndeterminate = false;
            Diagnostic = $"Minecraft завершился (код {exitCode}). Launcher снова готов к запуску.";
            LauncherRestoreRequested?.Invoke(this, EventArgs.Empty);
        });
    }

    private static void RunOnUi(Action action)
    {
        var dispatcher = Application.Current?.Dispatcher;
        if (dispatcher is null || dispatcher.CheckAccess())
        {
            action();
            return;
        }

        dispatcher.BeginInvoke(action);
    }

    private void TraceStartup(string message)
    {
        try
        {
            var root = LauncherInstallPaths.ResolveLauncherDataRoot();
            Directory.CreateDirectory(root);
            File.AppendAllText(
                Path.Combine(root, "launcher-startup.log"),
                $"{DateTimeOffset.UtcNow:O} {message}{Environment.NewLine}");
        }
        catch
        {
            // Startup tracing must never prevent the Launcher from opening.
        }
    }

    private static string BuildDiagnostic(LauncherOperationResult result, string instancePath)
    {
        var lines = new List<string>
        {
            $"Operation: {result.Operation}",
            $"Result: {(result.Succeeded ? "PASS" : "FAIL")}",
            $"Code: {result.ErrorCode ?? "OK"}",
            $"Instance path: {Path.GetFullPath(instancePath)}",
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
            if (!string.IsNullOrWhiteSpace(result.Launch.ProcessLogPath))
            {
                lines.Add($"Launch log: {result.Launch.ProcessLogPath}");
            }
        }

        if (result.LaunchFailure is not null)
        {
            lines.Add($"Launch failure: {result.LaunchFailure.Summary}");
            lines.Add($"Launch log: {result.LaunchFailure.LogPath}");
            lines.AddRange(result.LaunchFailure.Evidence.Select(line => $"Evidence: {line}"));
        }

        return string.Join(Environment.NewLine, lines);
    }

    private Task DiagnoseAsync()
    {
        Status = "Диагностика завершена";
        Diagnostic = $"Minecraft 1.21.1 · Fabric Loader 0.19.3{Environment.NewLine}Папка игры: {Path.GetFullPath(InstancePath)}{Environment.NewLine}RAM: {MaximumRamMb} МБ · {ResolutionWidth}×{ResolutionHeight} · {(Fullscreen ? "полный экран" : "оконный режим")}{Environment.NewLine}Файлы Launcher: {LauncherDataPath}{Environment.NewLine}Новости: используется последняя сохранённая версия.";
        return Task.CompletedTask;
    }

    private void OpenInstanceFolder()
    {
        try
        {
            var path = Path.GetFullPath(InstancePath);
            if (!IsInstanceReady())
            {
                Status = "Папка игры пока не готова";
                LoadingStage = "Сначала нажмите «Проверить файлы»";
                Diagnostic = $"INSTANCE_NOT_READY: файлы игры ещё не подготовлены.{Environment.NewLine}Путь экземпляра: {path}";
                IsDiagnosticOpen = true;
                return;
            }

            Process.Start(new ProcessStartInfo { FileName = path, UseShellExecute = true });
        }
        catch (Exception exception)
        {
            Status = "Папку игры не удалось открыть";
            Diagnostic = $"INSTANCE_FOLDER_OPEN_FAILED: {exception.Message}";
        }
    }

    private static void OpenWebsite() => OpenTrustedUrl(new Uri("https://copimine.ru/"));

    private static void OpenDiscord() => OpenTrustedUrl(new Uri("https://discord.com/channels/1499360677725343744"));

    private async Task OpenAccountLinkAsync()
    {
        if (launcherBindingClient is null)
        {
            var encodedName = Uri.EscapeDataString(PlayerName.Trim());
            OpenBindingUrl(new Uri($"https://copimine.ru/cabinet/link.html?launcher_nick={encodedName}"));
            return;
        }

        try
        {
            Status = "Создаём безопасную привязку…";
            LoadingStage = "Откройте страницу сайта";
            var challenge = await launcherBindingClient.CreateChallengeAsync(PlayerName.Trim(), LauncherVersionInfo.Version, CancellationToken.None);
            launcherBindingStateStore.SavePendingChallenge(challenge);
            OpenBindingUrl(challenge.AuthorizationUrl);
            Status = "Ожидаем подтверждение на сайте…";
            Diagnostic = $"Проверьте страницу привязки в браузере. Запрос действует до {challenge.ExpiresAtUtc.ToLocalTime():HH:mm}. Пароль сайта и AuthMe не передаются.";

            await WaitForLauncherLinkAsync(challenge);
        }
        catch (OperationCanceledException)
        {
            throw;
        }
        catch (LauncherBindingException exception)
        {
            Status = "Привязка недоступна";
            LoadingStage = $"Причина: {exception.Code}";
            Diagnostic = $"{exception.Code}: {exception.Message}";
            IsDiagnosticOpen = true;
        }
        catch (Exception exception)
        {
            Status = "Привязка недоступна";
            LoadingStage = "Причина указана ниже";
            Diagnostic = $"LAUNCHER_LINK_FAILED: {exception.Message}";
            IsDiagnosticOpen = true;
        }
    }

    public async Task HandleLauncherProtocolCallbackAsync(string callback)
    {
        if (!LauncherProtocolCallbackParser.TryParse(callback, out var parsed))
        {
            Status = "Ответ браузера не распознан";
            LoadingStage = "Привязка не подтверждена";
            Diagnostic = "LAUNCHER_CALLBACK_INVALID: Launcher принимает только безопасный callback copimine://launcher/link.";
            IsDiagnosticOpen = true;
            return;
        }

        if (IsLauncherLinked || launcherBindingClient is null) return;

        var pending = launcherBindingStateStore.LoadPendingChallenge();
        if (pending is null || !string.Equals(pending.ChallengeId, parsed.ChallengeId, StringComparison.Ordinal))
        {
            Status = "Ответ браузера получен";
            LoadingStage = "Ожидается новая привязка";
            Diagnostic = "LAUNCHER_CALLBACK_NO_PENDING_CHALLENGE: callback не совпал с активным запросом этого Launcher. Запустите привязку ещё раз.";
            IsDiagnosticOpen = true;
            return;
        }

        Status = "Проверяем подтверждение на сайте…";
        LoadingStage = "Получаем результат привязки";
        Diagnostic = "Браузер вернулся в Launcher. Проверяем одноразовый запрос без ручного ввода.";
        await WaitForLauncherLinkAsync(pending);
    }

    private async Task WaitForLauncherLinkAsync(LauncherLinkChallenge challenge)
    {
        if (launcherBindingClient is null) return;

        while (DateTimeOffset.UtcNow < challenge.ExpiresAtUtc)
        {
            await Task.Delay(TimeSpan.FromSeconds(2));
            var result = await launcherBindingClient.GetStatusAsync(challenge, CancellationToken.None);
            if (!result.Linked)
            {
                if (string.Equals(result.Status, "EXPIRED", StringComparison.OrdinalIgnoreCase)) break;
                continue;
            }

            launcherBindingState = new LauncherBindingState(
                true,
                result.SiteAccountId ?? string.Empty,
                result.SiteUsername ?? string.Empty,
                result.MinecraftName ?? PlayerName,
                result.LauncherAccessToken ?? challenge.PollToken);
            launcherAccessToken = launcherBindingState.AccessToken;
            launcherBindingStateStore.Save(launcherBindingState);
            launcherBindingStateStore.ClearPendingChallenge();
            IsLauncherLinked = true;
            LauncherLinkRequired = false;
            Status = "Launcher привязан к сайту";
            LoadingStage = "Можно запускать игру";
            Diagnostic = $"Аккаунт сайта: {result.SiteUsername ?? "подтверждён"}{Environment.NewLine}Launcher связан без передачи пароля.";
            return;
        }

        launcherBindingStateStore.ClearPendingChallenge();
        Status = "Привязка не подтверждена";
        LoadingStage = "Запрос истёк или страница не подтверждена";
        Diagnostic = "LAUNCHER_LINK_NOT_CONFIRMED: откройте привязку ещё раз и подтвердите её в аккаунте сайта.";
        IsDiagnosticOpen = true;
    }

    private static void OpenTrustedUrl(Uri uri)
    {
        var allowedHost = string.Equals(uri.Host, "copimine.ru", StringComparison.OrdinalIgnoreCase)
            || string.Equals(uri.Host, "www.copimine.ru", StringComparison.OrdinalIgnoreCase)
            || string.Equals(uri.Host, "discord.com", StringComparison.OrdinalIgnoreCase);
        if (!string.Equals(uri.Scheme, Uri.UriSchemeHttps, StringComparison.OrdinalIgnoreCase)
            || !allowedHost)
        {
            return;
        }

        Process.Start(new ProcessStartInfo { FileName = uri.ToString(), UseShellExecute = true });
    }

    private static void OpenBindingUrl(Uri uri)
    {
        var isProduction = string.Equals(uri.Scheme, Uri.UriSchemeHttps, StringComparison.OrdinalIgnoreCase)
            && (string.Equals(uri.Host, "copimine.ru", StringComparison.OrdinalIgnoreCase)
                || string.Equals(uri.Host, "www.copimine.ru", StringComparison.OrdinalIgnoreCase)
                || uri.Host.EndsWith(".copimine.ru", StringComparison.OrdinalIgnoreCase));
        var isLoopback = string.Equals(uri.Scheme, Uri.UriSchemeHttp, StringComparison.OrdinalIgnoreCase) && uri.IsLoopback;
        if ((!isProduction && !isLoopback) || !uri.AbsolutePath.Equals("/cabinet/link.html", StringComparison.Ordinal))
        {
            throw new InvalidOperationException("Сервис привязки вернул недопустимый адрес.");
        }

        Process.Start(new ProcessStartInfo { FileName = uri.ToString(), UseShellExecute = true });
    }

    private static void ShowNicknameChangedWarning(string previousName, string newName)
    {
        MessageBox.Show(
            $"Ник изменён: {previousName} → {newName}.\n\nЕсли Launcher уже привязан, сервер синхронизировал whitelist и данные игрока. Пароль AuthMe не меняется.",
            "Новый ник",
            MessageBoxButton.OK,
            MessageBoxImage.Information);
    }

    private static string FormatSelfUpdateDiagnostic(SelfUpdateStatus result) =>
        $"{result.ErrorCode ?? "SELF_UPDATE_UNKNOWN"}: {result.Diagnostic ?? "Нет дополнительной информации."}";

    private static void OpenPatch(PatchFeedCardViewModel? card)
    {
        if (card is null || card.DetailUrl.Scheme != Uri.UriSchemeHttps || !string.Equals(card.DetailUrl.Host, "copimine.ru", StringComparison.OrdinalIgnoreCase) || !card.DetailUrl.AbsolutePath.StartsWith("/news/", StringComparison.Ordinal)) return;
        Process.Start(new ProcessStartInfo { FileName = card.DetailUrl.ToString(), UseShellExecute = true });
    }
}
