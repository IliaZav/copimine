using System.Windows;
using System.Net.Http;
using System.IO;
using Velopack;
using CopiMineLauncher.Infrastructure.News;

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
        var cachePath = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "CopiMine", "Launcher", "patch-feed.json");
        var feedClient = new PatchFeedClient(new HttpClient(), cachePath);
        var window = new MainWindow(new LauncherViewModel(feedClient));
        MainWindow = window;
        window.Show();
    }
}
