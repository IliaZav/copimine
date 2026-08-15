using System.IO;
using CopiMineLauncher.Core.News;
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

    private sealed class FakePatchFeedClient : IPatchFeedClient
    {
        public Task<PatchFeedFetchResult> GetLatestAsync(CancellationToken cancellationToken) =>
            Task.FromResult(new PatchFeedFetchResult(Array.Empty<PatchFeedItem>(), Array.Empty<string>(), false));
    }

    private sealed class FakeRuntimeCoordinator : ILauncherRuntimeCoordinator
    {
        public int RepairCalls { get; private set; }
        public int PlayCalls { get; private set; }

        public Task<LauncherOperationResult> RepairAsync(LauncherOperationRequest request, CancellationToken cancellationToken, IProgress<LauncherProgress>? progress = null)
        {
            RepairCalls++;
            return Task.FromResult(new LauncherOperationResult(true, "repair", null, "ok"));
        }

        public Task<LauncherOperationResult> PlayAsync(LauncherOperationRequest request, CancellationToken cancellationToken, IProgress<LauncherProgress>? progress = null)
        {
            PlayCalls++;
            return Task.FromResult(new LauncherOperationResult(true, "play", null, "ok"));
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
