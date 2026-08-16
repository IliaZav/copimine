using System.Diagnostics;
using System.IO;
using CopiMineLauncher.Core.News;
using CopiMineLauncher.Infrastructure.Launch;
using CopiMineLauncher.Infrastructure.News;
using CopiMineLauncher.Infrastructure.Runtime;
using FluentAssertions;
using Xunit;

namespace CopiMineLauncher.App.Tests;

public sealed class LauncherViewModelTests
{
    [Fact]
    public async Task First_start_prepares_the_game_when_the_instance_has_no_launcher_state()
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
    public async Task Partial_instance_state_does_not_skip_automatic_setup()
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

        viewModel.IsProgressIndeterminate.Should().BeFalse();
        viewModel.ProgressPercent.Should().Be(100);
        viewModel.ProgressLabel.Should().Be("100%");
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

    private sealed class FakePatchFeedClient : IPatchFeedClient
    {
        public Task<PatchFeedFetchResult> GetLatestAsync(CancellationToken cancellationToken) =>
            Task.FromResult(new PatchFeedFetchResult(Array.Empty<PatchFeedItem>(), Array.Empty<string>(), false));
    }

    private sealed class FakeRuntimeCoordinator : ILauncherRuntimeCoordinator
    {
        public int RepairCalls { get; private set; }
        public int PlayCalls { get; private set; }
        public LauncherOperationResult? RepairResult { get; init; }
        public LauncherOperationResult? PlayResult { get; init; }
        public bool ReportProgress { get; init; }
        public bool HoldAfterProgress { get; init; }
        public bool HoldPlayAfterStart { get; init; }
        public TaskCompletionSource<bool> ProgressReported { get; } = new(TaskCreationOptions.RunContinuationsAsynchronously);
        public TaskCompletionSource<bool> ReleaseProgress { get; } = new(TaskCreationOptions.RunContinuationsAsynchronously);
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
            if (HoldAfterProgress)
            {
                progress?.Report(new("reconcile", "Проверяем managed-файлы…"));
                ProgressReported.TrySetResult(true);
                return WaitForReleaseAsync();
            }
            return Task.FromResult(RepairResult ?? new LauncherOperationResult(true, "repair", null, "ok"));
        }

        private async Task<LauncherOperationResult> WaitForReleaseAsync()
        {
            await ReleaseProgress.Task;
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
