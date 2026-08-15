using System.Windows;
using Velopack;

namespace CopiMineLauncher.App;

public partial class App : Application
{
    public App()
    {
        VelopackApp.Build().Run();
    }
}
