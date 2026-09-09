using System.Runtime.InteropServices;

namespace CopiMineLauncher.Infrastructure.Launch;

public static class LauncherMemoryLimits
{
    public const int MinimumRamMb = 512;

    public static int MaximumRamMb => ResolveMaximumRamMb();

    private static int ResolveMaximumRamMb()
    {
        long totalKilobytes = 0;
        if (OperatingSystem.IsWindows() && GetPhysicallyInstalledSystemMemory(out totalKilobytes) && totalKilobytes > 0)
        {
            var totalMb = totalKilobytes / 1024d;
            return RoundToLauncherStep(totalMb);
        }

        var availableBytes = GC.GetGCMemoryInfo().TotalAvailableMemoryBytes;
        if (availableBytes <= 0)
        {
            return 32768;
        }

        return RoundToLauncherStep(availableBytes / 1024d / 1024d);
    }

    private static int RoundToLauncherStep(double megabytes)
    {
        var rounded = (long)(Math.Round(megabytes / 512d, MidpointRounding.AwayFromZero) * 512d);
        return (int)Math.Clamp(rounded, MinimumRamMb, int.MaxValue);
    }

    [DllImport("kernel32.dll", SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool GetPhysicallyInstalledSystemMemory(out long totalMemoryInKilobytes);
}
