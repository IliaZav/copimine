using System.Reflection;

namespace CopiMineLauncher.Core;

public static class LauncherVersionInfo
{
    public const string Product = "CopiMineLauncher";

    public static string Version => AssemblyVersionString();

    private static string AssemblyVersionString()
    {
        var version = typeof(LauncherVersionInfo).Assembly.GetName().Version;
        return version is null
            ? "1.0.3"
            : $"{version.Major}.{version.Minor}.{version.Build}";
    }
}
