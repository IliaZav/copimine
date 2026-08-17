using System.IO;

namespace CopiMineLauncher.App;

public static class LauncherVisualAssetCatalog
{
    public const string Root = "Assets/LauncherVisuals";
    public const string LauncherHomeBackground = "launcher-home-background.png";
    public const string LauncherHomeSource = "launcher-home-source.webp";
    public const string UpdateBackground = "update-background.png";
    public const string UpdateBackgroundSource = "update-background-source.webp";
    public const string Splash = "splash.gif";
    public const string LoadingEmblem = "loading-emblem.png";
    public const string CopiMineLogo = "copimine-logo.png";
    public const string CopiMineAnimatedLogo = "copimine-logo-animated.gif";
    public const string CopiMineHeaderAnimatedLogo = "copimine-logo-header.gif";
    public const string CopiMineIcon = "copimine-icon.png";
    public const string InstallerBanner = "installer-banner.png";
    public const string News01 = "news-01.png";
    public const string News02 = "news-02.png";
    public const string News03 = "news-03.png";
    public const string News01Source = "news-01-source.webp";
    public const string News02Source = "news-02-source.webp";
    public const string News03Source = "news-03-source.webp";

    public static IReadOnlyList<string> RequiredSourceAssets { get; } =
    [
        CopiMineIcon,
        InstallerBanner,
        LauncherHomeSource,
        LauncherHomeBackground,
        LoadingEmblem,
        CopiMineAnimatedLogo,
        CopiMineLogo,
        News01Source,
        News02Source,
        News03Source,
        Splash,
        UpdateBackgroundSource
    ];

    public static IReadOnlyList<string> DerivedDisplayAssets { get; } =
    [
        CopiMineHeaderAnimatedLogo,
        UpdateBackground,
        News01,
        News02,
        News03
    ];

    public static string GetNewsArtwork(int index) => index switch
    {
        0 => News01,
        1 => News02,
        2 => News03,
        _ => LauncherHomeBackground
    };

    public static string ResolveAbsolutePath(string assetName) =>
        Path.Combine(
            AppContext.BaseDirectory,
            "Assets",
            "LauncherVisuals",
            assetName.Replace('/', Path.DirectorySeparatorChar));
}
