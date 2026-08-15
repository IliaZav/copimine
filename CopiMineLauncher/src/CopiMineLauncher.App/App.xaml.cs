using System.Windows;
using System.Net.Http;
using System.IO;
using CopiMineLauncher.Core;
using Velopack;
using CopiMineLauncher.Infrastructure.Launch;
using CopiMineLauncher.Infrastructure.Manifest;
using CopiMineLauncher.Infrastructure.News;
using CopiMineLauncher.Infrastructure.Provisioning;
using CopiMineLauncher.Infrastructure.Runtime;
using CopiMineLauncher.Infrastructure.SelfUpdate;
using CopiMineLauncher.Infrastructure.Servers;
using CopiMineLauncher.Infrastructure.Updates;

namespace CopiMineLauncher.App;

public partial class App : Application
{
    public App()
    {
        VelopackApp.Build().Run();
    }

    protected override void OnStartup(StartupEventArgs e)
    {
        base.OnStartup(e);
        var httpClient = new HttpClient
        {
            Timeout = TimeSpan.FromSeconds(30)
        };
        var cachePath = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "CopiMine", "Launcher", "patch-feed.json");
        var feedClient = new PatchFeedClient(httpClient, cachePath);
        var manifestClient = new SignedInstanceManifestClient(
            httpClient,
            new Ed25519ManifestVerifier(),
            PinnedManifestKey.PublicKey,
            PinnedManifestKey.KeyId);
        var downloads = new ResumableDownloadManager(httpClient);
        var runtimeCoordinator = new LauncherRuntimeCoordinator(
            manifestClient,
            new MinecraftProvisioner(httpClient),
            new JavaProvisioner(downloads),
            new TransactionalReconcilerFactory(downloads),
            new ServersDatService(),
            new MinecraftLaunchService(httpClient));
        var selfUpdate = new VelopackSelfUpdateService(
            new Uri("https://copimine.ru/downloads/launcher/"),
            new VelopackUpdateBackend(),
            Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "CopiMine", "Launcher"),
            new SelfUpdatePolicy(),
            () => LauncherVersionInfo.Version);
        var window = new MainWindow(new LauncherViewModel(feedClient, runtimeCoordinator, selfUpdate));
        MainWindow = window;
        window.Show();
    }
}
