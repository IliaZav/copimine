using System.Windows;

namespace CopiMineLauncher.App;

public static class LauncherMotion
{
    public static TimeSpan ShortTransition => TimeSpan.FromMilliseconds(180);

    public static TimeSpan MediumTransition => TimeSpan.FromMilliseconds(320);

    public static Duration ShortDuration => new(ShortTransition);

    public static Duration MediumDuration => new(MediumTransition);

    public static bool ReducedMotion => !SystemParameters.ClientAreaAnimation;

    public static double GetOpacityAt(double progress) => Math.Clamp(progress, 0d, 1d);
}
