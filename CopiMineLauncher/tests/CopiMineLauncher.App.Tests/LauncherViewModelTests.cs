using System.Diagnostics;
using System.IO;
using CopiMineLauncher.Core.Launch;
using CopiMineLauncher.Core.News;
using CopiMineLauncher.Infrastructure.Binding;
using CopiMineLauncher.Infrastructure.Launch;
using CopiMineLauncher.Infrastructure.News;
using CopiMineLauncher.Infrastructure.Runtime;
using CopiMineLauncher.Infrastructure.SelfUpdate;
using FluentAssertions;
using Xunit;

namespace CopiMineLauncher.App.Tests;

public sealed class LauncherViewModelTests
{
    [Fact]
    public async Task Initialization_state_is_visible_during_loading_and_clears_after_success()
    {
        using var temp = new TemporaryDirectory();
        var feed = new BlockingPatchFeedClient();
        var viewModel = new LauncherViewModel(feed, new FakeRuntimeCoordinator())
        {
            InstancePath = temp.Path
        };

        var initializeTask = viewModel.InitializeAsync();
        await feed.Started.Task.WaitAsync(TimeSpan.FromSeconds(5));

        viewModel.IsInitializing.Should().BeTrue();
        feed.Release.TrySetResult(true);
        await initializeTask;

        viewModel.IsInitializing.Should().BeFalse();
    }

    [Fact]
    public async Task Initialization_state_clears_when_loading_fails()
    {
        using var temp = new TemporaryDirectory();
        var viewModel = new LauncherViewModel(new ThrowingPatchFeedClient(), new FakeRuntimeCoordinator())
        {
            InstancePath = temp.Path
        };

        var action = () => viewModel.InitializeAsync();

        await action.Should().ThrowAsync<InvalidOperationException>();
        viewModel.IsInitializing.Should().BeFalse();
    }

    [Fact]
    public async Task First_start_prepares_the_game_without_blocking_the_launcher_window()
    {
        using var temp = new TemporaryDirectory();
        var runtime = new FakeRuntimeCoordinator();
        var viewModel = new LauncherViewModel(new FakePatchFeedClient(), runtime)
        {
            InstancePath = temp.Path
        };

        await viewModel.InitializeAsync();

        runtime.RepairCalls.Should().Be(1);
        runtime.PlayCalls.Should().Be(0);
        viewModel.Status.Should().Be("Игра готова");
    }

    [Fact]
    public async Task Initialization_does_not_wait_for_first_game_download()
    {
        using var temp = new TemporaryDirectory();
        var runtime = new FakeRuntimeCoordinator { HoldAfterProgress = true };
        var viewModel = new LauncherViewModel(new FakePatchFeedClient(), runtime)
        {
            InstancePath = temp.Path
        };

        var initialize = viewModel.InitializeAsync();
        var completed = await Task.WhenAny(initialize, Task.Delay(TimeSpan.FromMilliseconds(500)));
        if (completed != initialize)
        {
            runtime.ReleaseProgress.TrySetResult(true);
        }

        await initialize;

        completed.Should().Be(initialize);
        await runtime.ProgressReported.Task.WaitAsync(TimeSpan.FromSeconds(5));
        runtime.RepairCalls.Should().Be(1);
        viewModel.IsBusy.Should().BeTrue();

        runtime.ReleaseProgress.TrySetResult(true);
        await runtime.RepairCompleted.Task.WaitAsync(TimeSpan.FromSeconds(5));
        await WaitUntilAsync(() => !viewModel.IsBusy, TimeSpan.FromSeconds(5));
    }

    [Fact]
    public async Task Existing_launcher_state_skips_automatic_setup_on_later_start()
    {
        using var temp = new TemporaryDirectory();
        await CreateReadyInstanceAsync(temp.Path);
        var runtime = new FakeRuntimeCoordinator();
        var viewModel = new LauncherViewModel(new FakePatchFeedClient(), runtime)
        {
            InstancePath = temp.Path
        };

        await viewModel.InitializeAsync();

        runtime.RepairCalls.Should().Be(0);
        runtime.PlayCalls.Should().Be(0);
    }

    [Fact]
    public async Task Partial_instance_state_does_not_skip_background_setup()
    {
        using var temp = new TemporaryDirectory();
        Directory.CreateDirectory(Path.Combine(temp.Path, ".copimine"));
        await File.WriteAllTextAsync(Path.Combine(temp.Path, ".copimine", "managed-state.json"), "{}");
        var runtime = new FakeRuntimeCoordinator();
        var viewModel = new LauncherViewModel(new FakePatchFeedClient(), runtime)
        {
            InstancePath = temp.Path
        };

        await viewModel.InitializeAsync();

        runtime.RepairCalls.Should().Be(1);
    }

    [Fact]
    public async Task Launch_diagnostic_includes_the_process_log_path()
    {
        using var temp = new TemporaryDirectory();
        var logPath = System.IO.Path.Combine(temp.Path, "logs", "launcher-process.log");
        var runtime = new FakeRuntimeCoordinator
        {
            RepairResult = new LauncherOperationResult(
                true,
                "repair",
                null,
                "Сборка проверена.",
                Launch: new LaunchEvidence(
                    Process.GetCurrentProcess(),
                    DateTimeOffset.UtcNow,
                    "fabric-loader-0.19.3-1.21.1",
                    temp.Path,
                    "java.exe",
                    logPath))
        };
        var viewModel = new LauncherViewModel(new FakePatchFeedClient(), runtime)
        {
            InstancePath = temp.Path
        };

        await viewModel.InitializeAsync();

        viewModel.Diagnostic.Should().Contain($"Launch log: {logPath}");
    }

    [Fact]
    public async Task Loading_progress_reaches_completed_state_after_runtime_reports_stages()
    {
        using var temp = new TemporaryDirectory();
        var runtime = new FakeRuntimeCoordinator { ReportProgress = true };
        var viewModel = new LauncherViewModel(new FakePatchFeedClient(), runtime)
        {
            InstancePath = temp.Path
        };

        await viewModel.InitializeAsync();

        viewModel.ProgressPercent.Should().Be(100);
        viewModel.LoadingStage.Should().Be("Сборка готова");
        viewModel.IsBusy.Should().BeFalse();
        viewModel.IsProgressIndeterminate.Should().BeFalse();
    }

    [Fact]
    public async Task Play_button_enters_starting_state_until_minecraft_process_is_ready()
    {
        using var temp = new TemporaryDirectory();
        await CreateReadyInstanceAsync(temp.Path);
        using var process = StartTestProcess("/c ping -n 4 127.0.0.1 >nul");
        var runtime = new FakeRuntimeCoordinator
        {
            HoldPlayAfterStart = true,
            PlayResult = new LauncherOperationResult(
                true,
                "play",
                null,
                "Minecraft запущен.",
                Launch: new LaunchEvidence(
                    process,
                    DateTimeOffset.UtcNow,
                    "fabric-loader-0.19.3-1.21.1",
                    temp.Path,
                    "java.exe"))
        };
        var viewModel = new LauncherViewModel(new FakePatchFeedClient(), runtime)
        {
            InstancePath = temp.Path
        };

        var hideRequested = new TaskCompletionSource<bool>(TaskCreationOptions.RunContinuationsAsynchronously);
        viewModel.LauncherHideRequested += (_, _) => hideRequested.TrySetResult(true);
        var playTask = viewModel.PlayCommand.ExecuteAsync(null);
        await runtime.PlayStarted.Task.WaitAsync(TimeSpan.FromSeconds(5));

        viewModel.IsBusy.Should().BeTrue();
        viewModel.IsLaunching.Should().BeTrue();
        viewModel.PlayButtonText.Should().Be("Запуск…");

        runtime.ReleasePlay.TrySetResult(true);
        await playTask;
        await hideRequested.Task.WaitAsync(TimeSpan.FromSeconds(5));
    }

    [Fact]
    public async Task Play_without_a_linked_account_shows_required_binding_warning_and_does_not_launch()
    {
        using var temp = new TemporaryDirectory();
        await CreateReadyInstanceAsync(temp.Path);
        var runtime = new FakeRuntimeCoordinator();
        var viewModel = new LauncherViewModel(
            new FakePatchFeedClient(),
            runtime,
            launcherBindingClient: new FakeLauncherBindingClient(),
            launcherBindingStateStore: new LauncherBindingStateStore(Path.Combine(temp.Path, "launcher-data")))
        {
            InstancePath = temp.Path
        };

        await viewModel.InitializeAsync();
        await viewModel.PlayCommand.ExecuteAsync(null);

        runtime.PlayCalls.Should().Be(0);
        viewModel.IsLauncherLinked.Should().BeFalse();
        viewModel.Diagnostic.Should().Contain("LAUNCHER_LINK_REQUIRED");
        viewModel.IsDiagnosticOpen.Should().BeTrue();
    }

    [Fact]
    public async Task Launcher_binding_stops_polling_immediately_after_the_site_acknowledges_it()
    {
        using var temp = new TemporaryDirectory();
        var challenge = new LauncherLinkChallenge(
            "challenge-1234567890",
            "poll-token-1234567890",
            new Uri("https://copimine.ru/cabinet/link.html?launcher_challenge=challenge-1234567890"),
            DateTimeOffset.UtcNow.AddMinutes(5),
            "CopiMinePlayer");
        var binding = new FakeLauncherBindingClient
        {
            Status = new LauncherLinkStatus(
                true,
                "AUTHORIZED",
                "account-42",
                "player-login",
                "CopiMinePlayer",
                "access-token"),
        };
        var bindingStore = new LauncherBindingStateStore(Path.Combine(temp.Path, "launcher-data"));
        bindingStore.SavePendingChallenge(challenge);
        var viewModel = new LauncherViewModel(
            new FakePatchFeedClient(),
            launcherBindingClient: binding,
            launcherBindingStateStore: bindingStore)
        {
            InstancePath = temp.Path
        };

        var stopwatch = Stopwatch.StartNew();
        await viewModel.HandleLauncherProtocolCallbackAsync(
            "copimine://launcher/link?challenge=challenge-1234567890");
        stopwatch.Stop();

        stopwatch.Elapsed.Should().BeLessThan(TimeSpan.FromSeconds(1));
        binding.StatusCalls.Should().Be(1);
        viewModel.IsLauncherLinked.Should().BeTrue();
        viewModel.Status.Should().Be("Launcher привязан к сайту");
    }

    [Fact]
    public async Task Launcher_binding_reuses_a_live_pending_challenge_instead_of_expiring_the_browser_tab()
    {
        using var temp = new TemporaryDirectory();
        var challenge = new LauncherLinkChallenge(
            "challenge-reused-123456",
            "poll-token-reused-123456",
            new Uri("https://copimine.ru/launcher-link.html?launcher_challenge=challenge-reused-123456&launcher_code=ABCDEFGH&launcher_nick=CopiMinePlayer"),
            DateTimeOffset.UtcNow.AddMinutes(5),
            "CopiMinePlayer");
        var binding = new RecordingLauncherBindingClient
        {
            Status = new LauncherLinkStatus(
                true,
                "AUTHORIZED",
                "account-reused",
                "player-login",
                "CopiMinePlayer",
                "access-token-reused"),
        };
        var bindingStore = new LauncherBindingStateStore(Path.Combine(temp.Path, "launcher-data"));
        bindingStore.SavePendingChallenge(challenge);
        var opened = new List<Uri>();
        var viewModel = new LauncherViewModel(
            new FakePatchFeedClient(),
            launcherBindingClient: binding,
            launcherBindingStateStore: bindingStore,
            bindingUrlOpener: opened.Add)
        {
            PlayerName = "CopiMinePlayer",
            InstancePath = temp.Path,
        };

        await viewModel.OpenAccountLinkCommand.ExecuteAsync(null);

        binding.CreateCalls.Should().Be(0);
        binding.StatusCalls.Should().Be(1);
        opened.Should().ContainSingle().Which.Should().Be(challenge.AuthorizationUrl);
        viewModel.IsLauncherLinked.Should().BeTrue();
    }

    [Fact]
    public async Task Launcher_is_restored_when_minecraft_process_exits()
    {
        using var temp = new TemporaryDirectory();
        await CreateReadyInstanceAsync(temp.Path);
        using var process = StartTestProcess("/c ping -n 2 127.0.0.1 >nul");
        var runtime = new FakeRuntimeCoordinator
        {
            PlayResult = new LauncherOperationResult(
                true,
                "play",
                null,
                "Minecraft запущен.",
                Launch: new LaunchEvidence(
                    process,
                    DateTimeOffset.UtcNow,
                    "fabric-loader-0.19.3-1.21.1",
                    temp.Path,
                    "java.exe"))
        };
        var viewModel = new LauncherViewModel(new FakePatchFeedClient(), runtime)
        {
            InstancePath = temp.Path
        };

        var hideRequested = new TaskCompletionSource<bool>(TaskCreationOptions.RunContinuationsAsynchronously);
        var restoreRequested = new TaskCompletionSource<bool>(TaskCreationOptions.RunContinuationsAsynchronously);
        viewModel.LauncherHideRequested += (_, _) => hideRequested.TrySetResult(true);
        viewModel.LauncherRestoreRequested += (_, _) => restoreRequested.TrySetResult(true);

        await viewModel.PlayCommand.ExecuteAsync(null);
        await hideRequested.Task.WaitAsync(TimeSpan.FromSeconds(5));
        await restoreRequested.Task.WaitAsync(TimeSpan.FromSeconds(10));

        viewModel.Status.Should().Be("Minecraft завершён");
        viewModel.LoadingStage.Should().Be("Можно запустить снова");
        viewModel.IsLaunching.Should().BeFalse();
    }

    [Fact]
    public async Task Unknown_duration_preparation_stage_shows_activity_until_operation_finishes()
    {
        using var temp = new TemporaryDirectory();
        var runtime = new FakeRuntimeCoordinator { HoldAfterProgress = true };
        var viewModel = new LauncherViewModel(new FakePatchFeedClient(), runtime)
        {
            InstancePath = temp.Path
        };

        var initialize = viewModel.InitializeAsync();
        await runtime.ProgressReported.Task.WaitAsync(TimeSpan.FromSeconds(5));

        viewModel.IsProgressIndeterminate.Should().BeTrue();
        viewModel.ProgressPercent.Should().Be(28);
        viewModel.ProgressLabel.Should().Be("…");

        runtime.ReleaseProgress.TrySetResult(true);
        await initialize;
        await runtime.RepairCompleted.Task.WaitAsync(TimeSpan.FromSeconds(5));
        await WaitUntilAsync(() => !viewModel.IsBusy, TimeSpan.FromSeconds(5));

        viewModel.IsProgressIndeterminate.Should().BeFalse();
        viewModel.ProgressPercent.Should().Be(100);
        viewModel.ProgressLabel.Should().Be("100%");
    }

    [Fact]
    public async Task Runtime_download_progress_replaces_the_manifest_percentage()
    {
        using var temp = new TemporaryDirectory();
        var runtime = new FakeRuntimeCoordinator
        {
            ProgressSequence =
            [
                new("manifest", "Проверяем подписанный manifest…"),
                new("minecraft-runtime", "Скачиваем Minecraft/Fabric… 14.5%", 14.5),
            ],
            HoldAfterProgress = true
        };
        var viewModel = new LauncherViewModel(new FakePatchFeedClient(), runtime)
        {
            InstancePath = temp.Path
        };

        var initialize = viewModel.InitializeAsync();
        await runtime.ProgressReported.Task.WaitAsync(TimeSpan.FromSeconds(5));
        await Task.Delay(200);
        viewModel.ProgressPercent.Should().Be(14.5, $"stage={viewModel.LoadingStage}; label={viewModel.ProgressLabel}; status={viewModel.Status}");

        viewModel.ProgressLabel.Should().Be("15%");
        viewModel.IsProgressIndeterminate.Should().BeFalse();
        viewModel.LoadingStage.Should().Contain("14.5%");

        runtime.ReleaseProgress.TrySetResult(true);
        await initialize;
        await runtime.RepairCompleted.Task.WaitAsync(TimeSpan.FromSeconds(5));
        await WaitUntilAsync(() => !viewModel.IsBusy, TimeSpan.FromSeconds(5));
    }

    [Fact]
    public async Task Failed_operation_exposes_the_error_code_and_opens_diagnostics()
    {
        using var temp = new TemporaryDirectory();
        var runtime = new FakeRuntimeCoordinator
        {
            RepairResult = new LauncherOperationResult(
                false,
                "repair",
                "MANIFEST_HTTP_FAILED",
                "GET /launcher/stable/instance-manifest.json returned 404 Not Found.")
        };
        var viewModel = new LauncherViewModel(new FakePatchFeedClient(), runtime)
        {
            InstancePath = temp.Path
        };

        await viewModel.InitializeAsync();

        viewModel.Status.Should().Be("Не удалось подготовить игру");
        viewModel.LoadingStage.Should().Be("Причина: MANIFEST_HTTP_FAILED");
        viewModel.IsDiagnosticOpen.Should().BeTrue();
        viewModel.Diagnostic.Should().Contain("MANIFEST_HTTP_FAILED");
        viewModel.Diagnostic.Should().Contain("404 Not Found");
        viewModel.Diagnostic.Should().Contain($"Instance path: {Path.GetFullPath(temp.Path)}");
    }

    [Fact]
    public async Task Launcher_does_not_show_latest_version_before_a_successful_feed_check()
    {
        using var temp = new TemporaryDirectory();
        var selfUpdate = new FakeSelfUpdateService
        {
            Result = new(
                SelfUpdateStatusKind.Failed,
                "1.0.6",
                ErrorCode: "SELF_UPDATE_CHECK_FAILED",
                Diagnostic: "GET /releases.stable.json returned 404 Not Found.")
        };
        var viewModel = new LauncherViewModel(
            new FakePatchFeedClient(),
            selfUpdateService: selfUpdate)
        {
            InstancePath = temp.Path
        };

        viewModel.IsLatestVersionVerified.Should().BeFalse();
        viewModel.SelfUpdateStatus.Should().Be("Версия не проверена");

        await viewModel.CheckSelfUpdateCommand.ExecuteAsync(null);

        viewModel.IsLatestVersionVerified.Should().BeFalse();
        viewModel.SelfUpdateStatus.Should().Be("Не удалось проверить обновление");
        viewModel.Diagnostic.Should().Contain("SELF_UPDATE_CHECK_FAILED");
    }

    [Fact]
    public async Task Launcher_shows_latest_version_only_after_the_feed_confirms_no_update()
    {
        using var temp = new TemporaryDirectory();
        var selfUpdate = new FakeSelfUpdateService
        {
            Result = new(SelfUpdateStatusKind.NoUpdate, "1.0.6")
        };
        var viewModel = new LauncherViewModel(
            new FakePatchFeedClient(),
            selfUpdateService: selfUpdate)
        {
            InstancePath = temp.Path
        };

        await viewModel.CheckSelfUpdateCommand.ExecuteAsync(null);

        viewModel.IsLatestVersionVerified.Should().BeTrue();
        viewModel.SelfUpdateStatus.Should().Be("Последняя версия установлена");
    }

    [Fact]
    public async Task Startup_applies_a_verified_launcher_update_before_preparing_the_game()
    {
        using var temp = new TemporaryDirectory();
        var update = new VerifiedSelfUpdate(
            "CopiMineLauncher",
            "stable",
            "1.0.7",
            new Uri("https://copimine.ru/downloads/launcher/"),
            new Uri("https://copimine.ru/downloads/launcher/CopiMineLauncher-1.0.7-full.nupkg"),
            "CopiMineLauncher-1.0.7-full.nupkg",
            1,
            new string('a', 64));
        var selfUpdate = new FakeSelfUpdateService
        {
            Result = new(SelfUpdateStatusKind.UpdateAvailable, "1.0.6", update),
            ApplyResult = new(SelfUpdateStatusKind.PendingRestart, "1.0.6", update)
        };
        var runtime = new FakeRuntimeCoordinator();
        var viewModel = new LauncherViewModel(
            new FakePatchFeedClient(),
            runtime,
            selfUpdateService: selfUpdate,
            defaultInstancePath: temp.Path)
        {
            InstancePath = temp.Path
        };

        await viewModel.InitializeAsync();

        selfUpdate.ApplyCalls.Should().Be(1);
        selfUpdate.AppliedUpdate.Should().Be(update);
        runtime.RepairCalls.Should().Be(0);
        viewModel.Status.Should().Be("Перезапускаем Launcher…");
        viewModel.SelfUpdateStatus.Should().Be("Обновление установлено. Перезапускаем Launcher…");
    }

    [Fact]
    public async Task User_mod_launch_failure_opens_the_structured_failure_dialog_request()
    {
        using var temp = new TemporaryDirectory();
        await CreateReadyInstanceAsync(temp.Path);
        var logPath = Path.Combine(temp.Path, "logs", "launcher-process.log");
        var report = MinecraftLaunchFailureParser.Parse(
            "[main/ERROR] Could not execute entrypoint stage 'main' due to errors, provided by 'better-leaves'!",
            logPath,
            new[] { "BetterLeaves-1.4.0.jar" });
        var runtime = new FakeRuntimeCoordinator
        {
            PlayResult = new LauncherOperationResult(
                false,
                "play",
                "MINECRAFT_START_FAILED",
                report.Summary,
                LaunchFailure: report)
        };
        var viewModel = new LauncherViewModel(new FakePatchFeedClient(), runtime)
        {
            InstancePath = temp.Path
        };
        var failureRequested = new TaskCompletionSource<MinecraftLaunchFailureReport>(TaskCreationOptions.RunContinuationsAsynchronously);
        viewModel.LaunchFailureRequested += (_, args) => failureRequested.TrySetResult(args.Report);

        await viewModel.PlayCommand.ExecuteAsync(null);

        (await failureRequested.Task.WaitAsync(TimeSpan.FromSeconds(5))).Should().BeSameAs(report);
        viewModel.IsDiagnosticOpen.Should().BeTrue();
        viewModel.Diagnostic.Should().Contain(logPath);
    }

    [Fact]
    public void Opening_the_game_folder_does_not_create_a_misleading_empty_instance()
    {
        using var temp = new TemporaryDirectory();
        var viewModel = new LauncherViewModel(new FakePatchFeedClient())
        {
            InstancePath = Path.Combine(temp.Path, "Minecraft")
        };

        viewModel.OpenInstanceFolderCommand.Execute(null);

        Directory.Exists(viewModel.InstancePath).Should().BeFalse();
        viewModel.Status.Should().Be("Папка игры пока не готова");
        viewModel.Diagnostic.Should().Contain("INSTANCE_NOT_READY");
        viewModel.IsDiagnosticOpen.Should().BeTrue();
    }

    [Fact]
    public async Task Saved_launch_settings_are_forwarded_to_the_runtime()
    {
        using var temp = new TemporaryDirectory();
        using var settingsRoot = new TemporaryDirectory();
        var settingsStore = new LauncherSettingsStore(settingsRoot.Path);
        settingsStore.Save(new LauncherSettings(8192, 1600, 900, true));
        var runtime = new FakeRuntimeCoordinator();
        var viewModel = new LauncherViewModel(
            new FakePatchFeedClient(),
            runtime,
            settingsStore: settingsStore)
        {
            InstancePath = temp.Path
        };

        await viewModel.InitializeAsync();

        runtime.LastRepairRequest.Should().NotBeNull();
        runtime.LastRepairRequest!.MaximumRamMb.Should().Be(8192);
        runtime.LastRepairRequest.ResolutionWidth.Should().Be(1600);
        runtime.LastRepairRequest.ResolutionHeight.Should().Be(900);
        runtime.LastRepairRequest.Fullscreen.Should().BeTrue();
    }

    [Fact]
    public async Task First_run_defaults_are_loaded_and_can_be_saved_without_touching_launcher_settings()
    {
        using var temp = new TemporaryDirectory();
        MinecraftDefaultSettingsStore.Save(
            temp.Path,
            new MinecraftDefaultSettings(
                UseRussianLanguage: false,
                DisableNarrator: true,
                SetMasterVolumeToFifteenPercent: false));
        var runtime = new FakeRuntimeCoordinator();
        var viewModel = new LauncherViewModel(new FakePatchFeedClient(), runtime)
        {
            InstancePath = temp.Path
        };

        await viewModel.InitializeAsync();

        viewModel.HasMinecraftDefaultsSelection.Should().BeTrue();
        viewModel.UseRussianLanguageDefault.Should().BeFalse();
        viewModel.DisableNarratorDefault.Should().BeTrue();
        viewModel.SetMasterVolumeToFifteenPercentDefault.Should().BeFalse();

        viewModel.UseRussianLanguageDefault = true;
        viewModel.SetMasterVolumeToFifteenPercentDefault = true;
        viewModel.SaveMinecraftDefaults();

        MinecraftDefaultSettingsStore.Load(temp.Path)
            .Should().Be(new MinecraftDefaultSettings(true, true, true));
        viewModel.HasMinecraftDefaultsSelection.Should().BeTrue();
    }

    private static async Task CreateReadyInstanceAsync(string instancePath)
    {
        Directory.CreateDirectory(Path.Combine(instancePath, ".copimine", "java", "21.0.10", "bin"));
        Directory.CreateDirectory(Path.Combine(instancePath, "versions", "1.21.1"));
        Directory.CreateDirectory(Path.Combine(instancePath, "mods"));
        await File.WriteAllTextAsync(Path.Combine(instancePath, ".copimine", "managed-state.json"), "{}");
        await File.WriteAllTextAsync(Path.Combine(instancePath, ".copimine", "java", "21.0.10", "bin", "java.exe"), "fixture");
        await File.WriteAllTextAsync(Path.Combine(instancePath, "servers.dat"), "fixture");
        await File.WriteAllTextAsync(Path.Combine(instancePath, "mods", "CopiMineClient.jar"), "fixture");
    }

    private static Process StartTestProcess(string arguments) =>
        Process.Start(new ProcessStartInfo
        {
            FileName = "cmd.exe",
            Arguments = arguments,
            UseShellExecute = false,
            CreateNoWindow = true
        }) ?? throw new InvalidOperationException("Could not start the process fixture.");

    private static async Task WaitUntilAsync(Func<bool> condition, TimeSpan timeout)
    {
        var deadline = DateTime.UtcNow + timeout;
        while (!condition())
        {
            if (DateTime.UtcNow >= deadline)
            {
                throw new TimeoutException("The condition did not become true in time.");
            }

            await Task.Delay(10);
        }
    }

    private sealed class FakePatchFeedClient : IPatchFeedClient
    {
        public Task<PatchFeedFetchResult> GetLatestAsync(CancellationToken cancellationToken) =>
            Task.FromResult(new PatchFeedFetchResult(Array.Empty<PatchFeedItem>(), Array.Empty<string>(), false));
    }

    private sealed class BlockingPatchFeedClient : IPatchFeedClient
    {
        public TaskCompletionSource<bool> Started { get; } = new(TaskCreationOptions.RunContinuationsAsynchronously);
        public TaskCompletionSource<bool> Release { get; } = new(TaskCreationOptions.RunContinuationsAsynchronously);

        public async Task<PatchFeedFetchResult> GetLatestAsync(CancellationToken cancellationToken)
        {
            Started.TrySetResult(true);
            await Release.Task.WaitAsync(cancellationToken);
            return new PatchFeedFetchResult(Array.Empty<PatchFeedItem>(), Array.Empty<string>(), false);
        }
    }

    private sealed class ThrowingPatchFeedClient : IPatchFeedClient
    {
        public Task<PatchFeedFetchResult> GetLatestAsync(CancellationToken cancellationToken) =>
            Task.FromException<PatchFeedFetchResult>(new InvalidOperationException("feed fixture failed"));
    }

    private sealed class FakeRuntimeCoordinator : ILauncherRuntimeCoordinator
    {
        public int RepairCalls { get; private set; }
        public int PlayCalls { get; private set; }
        public LauncherOperationResult? RepairResult { get; init; }
        public LauncherOperationResult? PlayResult { get; init; }
        public bool ReportProgress { get; init; }
        public IReadOnlyList<LauncherProgress>? ProgressSequence { get; init; }
        public bool HoldAfterProgress { get; init; }
        public bool HoldPlayAfterStart { get; init; }
        public TaskCompletionSource<bool> ProgressReported { get; } = new(TaskCreationOptions.RunContinuationsAsynchronously);
        public TaskCompletionSource<bool> ReleaseProgress { get; } = new(TaskCreationOptions.RunContinuationsAsynchronously);
        public TaskCompletionSource<bool> RepairCompleted { get; } = new(TaskCreationOptions.RunContinuationsAsynchronously);
        public TaskCompletionSource<bool> PlayStarted { get; } = new(TaskCreationOptions.RunContinuationsAsynchronously);
        public TaskCompletionSource<bool> ReleasePlay { get; } = new(TaskCreationOptions.RunContinuationsAsynchronously);
        public LauncherOperationRequest? LastRepairRequest { get; private set; }

        public Task<LauncherOperationResult> RepairAsync(LauncherOperationRequest request, CancellationToken cancellationToken, IProgress<LauncherProgress>? progress = null)
        {
            RepairCalls++;
            LastRepairRequest = request;
            if (ReportProgress)
            {
                progress?.Report(new("minecraft", "Проверяем Minecraft 1.21.1…"));
            }
            if (ProgressSequence is not null)
            {
                foreach (var value in ProgressSequence)
                {
                    progress?.Report(value);
                }
            }
            if (HoldAfterProgress)
            {
                if (ProgressSequence is null)
                {
                    progress?.Report(new("reconcile", "Проверяем managed-файлы…"));
                }
                ProgressReported.TrySetResult(true);
                return WaitForReleaseAsync();
            }
            RepairCompleted.TrySetResult(true);
            return Task.FromResult(RepairResult ?? new LauncherOperationResult(true, "repair", null, "ok"));
        }

        private async Task<LauncherOperationResult> WaitForReleaseAsync()
        {
            await ReleaseProgress.Task;
            RepairCompleted.TrySetResult(true);
            return new LauncherOperationResult(true, "repair", null, "ok");
        }

        public Task<LauncherOperationResult> PlayAsync(LauncherOperationRequest request, CancellationToken cancellationToken, IProgress<LauncherProgress>? progress = null)
        {
            PlayCalls++;
            PlayStarted.TrySetResult(true);
            return HoldPlayAfterStart ? WaitForPlayReleaseAsync() : Task.FromResult(PlayResult ?? new LauncherOperationResult(true, "play", null, "ok"));
        }

        private async Task<LauncherOperationResult> WaitForPlayReleaseAsync()
        {
            await ReleasePlay.Task;
            return PlayResult ?? new LauncherOperationResult(true, "play", null, "ok");
        }
    }

    private sealed class FakeLauncherBindingClient : ILauncherBindingClient
    {
        public string DeviceId => "cm-device-1234567890";
        public LauncherLinkStatus Status { get; init; } = new(false, "PENDING");
        public int StatusCalls { get; private set; }

        public Task<LauncherLinkChallenge> CreateChallengeAsync(string minecraftName, string launcherVersion, CancellationToken cancellationToken) =>
            throw new NotSupportedException();

        public Task<LauncherLinkStatus> GetStatusAsync(LauncherLinkChallenge challenge, CancellationToken cancellationToken)
        {
            StatusCalls++;
            return Task.FromResult(Status);
        }

        public Task<LauncherNicknameChangeResult> ChangeNicknameAsync(string accessToken, string oldMinecraftName, string newMinecraftName, CancellationToken cancellationToken) =>
            throw new NotSupportedException();
    }

    private sealed class RecordingLauncherBindingClient : ILauncherBindingClient
    {
        public string DeviceId => "cm-device-reused-1234567890";
        public LauncherLinkStatus Status { get; init; } = new(false, "PENDING");
        public int CreateCalls { get; private set; }
        public int StatusCalls { get; private set; }

        public Task<LauncherLinkChallenge> CreateChallengeAsync(string minecraftName, string launcherVersion, CancellationToken cancellationToken)
        {
            CreateCalls++;
            return Task.FromResult(new LauncherLinkChallenge(
                "challenge-new-123456",
                "poll-token-new-123456",
                new Uri("https://copimine.ru/launcher-link.html?launcher_challenge=challenge-new-123456&launcher_code=ABCDEFGH&launcher_nick=" + minecraftName),
                DateTimeOffset.UtcNow.AddMinutes(5),
                minecraftName));
        }

        public Task<LauncherLinkStatus> GetStatusAsync(LauncherLinkChallenge challenge, CancellationToken cancellationToken)
        {
            StatusCalls++;
            return Task.FromResult(Status);
        }

        public Task<LauncherNicknameChangeResult> ChangeNicknameAsync(string accessToken, string oldMinecraftName, string newMinecraftName, CancellationToken cancellationToken) =>
            throw new NotSupportedException();
    }

    private sealed class FakeSelfUpdateService : ISelfUpdateService
    {
        public SelfUpdateStatus Result { get; init; } = new(SelfUpdateStatusKind.NoUpdate, "1.0.6");
        public SelfUpdateStatus? ApplyResult { get; init; }
        public int ApplyCalls { get; private set; }
        public VerifiedSelfUpdate? AppliedUpdate { get; private set; }

        public Task<SelfUpdateStatus> CheckAsync(CancellationToken cancellationToken) => Task.FromResult(Result);

        public Task<SelfUpdateStatus> ApplyAsync(VerifiedSelfUpdate update, CancellationToken cancellationToken)
        {
            ApplyCalls++;
            AppliedUpdate = update;
            return Task.FromResult(ApplyResult ?? Result);
        }

        public Task<SelfUpdateStatus> RecoverAsync(CancellationToken cancellationToken) =>
            Task.FromResult(new SelfUpdateStatus(SelfUpdateStatusKind.NoUpdate, Result.CurrentVersion));
    }

    private sealed class TemporaryDirectory : IDisposable
    {
        public TemporaryDirectory() => Path = Directory.CreateTempSubdirectory("copimine-launcher-app-tests-").FullName;

        public string Path { get; }

        public void Dispose()
        {
            if (Directory.Exists(Path))
            {
                Directory.Delete(Path, recursive: true);
            }
        }
    }
}
