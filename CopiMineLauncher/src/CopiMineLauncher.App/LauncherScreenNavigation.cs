namespace CopiMineLauncher.App;

public enum LauncherScreen
{
    Home,
    Skins,
    Settings
}

public sealed class LauncherScreenNavigation
{
    public LauncherScreen Current { get; private set; } = LauncherScreen.Home;

    public bool CanGoBack => Current != LauncherScreen.Home;

    public void NavigateTo(LauncherScreen screen) => Current = screen;

    public void NavigateBack() => Current = LauncherScreen.Home;
}
