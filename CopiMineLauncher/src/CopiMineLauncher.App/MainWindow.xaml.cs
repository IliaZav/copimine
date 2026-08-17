using System.Windows;
using System.Windows.Controls;

namespace CopiMineLauncher.App;

public partial class MainWindow : Window
{
    private readonly LauncherViewModel viewModel;
    private readonly LauncherScreenNavigation navigation = new();
    private UserControl? currentScreen;

    public MainWindow(LauncherViewModel viewModel)
    {
        InitializeComponent();
        this.viewModel = viewModel ?? throw new ArgumentNullException(nameof(viewModel));
        DataContext = viewModel;
        Loaded += OnLoaded;
        viewModel.LauncherHideRequested += OnLauncherHideRequested;
        viewModel.LauncherRestoreRequested += OnLauncherRestoreRequested;
        viewModel.LauncherBindingRequired += OnLauncherBindingRequired;
        Closed += OnClosed;
    }

    private async void OnLoaded(object sender, RoutedEventArgs e)
    {
        Loaded -= OnLoaded;
        try
        {
            await viewModel.InitializeAsync();
            if (!viewModel.HasMinecraftDefaultsSelection)
            {
                ShowFirstRunDefaultsScreen();
            }
        }
        catch (Exception exception)
        {
            viewModel.Status = "Обновления недоступны";
            viewModel.Diagnostic = exception.Message;
        }
    }

    private void OpenSettings_Click(object sender, RoutedEventArgs e)
    {
        ShowScreen(LauncherScreen.Settings, "Настройки", new LauncherSettingsWindow(viewModel));
    }

    private void OpenSkins_Click(object sender, RoutedEventArgs e)
    {
        ShowScreen(LauncherScreen.Skins, "Скины и плащи", new SkinManagerWindow(
            viewModel.InstancePath,
            viewModel.PlayerName,
            LauncherInstallPaths.ResolveLauncherDataRoot()));
    }

    private void ShowFirstRunDefaultsScreen()
    {
        ShowScreen(LauncherScreen.MinecraftDefaults, "Настройка Minecraft", new MinecraftDefaultsWindow(viewModel));
        BackButton.IsEnabled = false;
    }

    private void ShowScreen(LauncherScreen screen, string title, UserControl content)
    {
        if (currentScreen is not null)
        {
            UnsubscribeFromScreen(currentScreen);
        }

        navigation.NavigateTo(screen);
        currentScreen = content;
        SubscribeToScreen(content);
        ScreenTitleText.Text = title;
        ScreenContent.Content = content;
        HomeView.Visibility = Visibility.Collapsed;
        ScreenView.Visibility = Visibility.Visible;
        BackButton.IsEnabled = screen != LauncherScreen.MinecraftDefaults;
        BackButton.Focus();
    }

    private void BackFromScreen_Click(object sender, RoutedEventArgs e) => ShowHomeScreen();

    private void OnScreenBackRequested(object? sender, EventArgs e) => ShowHomeScreen();

    private void ShowHomeScreen()
    {
        if (currentScreen is not null)
        {
            UnsubscribeFromScreen(currentScreen);
        }

        navigation.NavigateBack();
        ScreenContent.Content = null;
        currentScreen = null;
        ScreenView.Visibility = Visibility.Collapsed;
        HomeView.Visibility = Visibility.Visible;
        BackButton.IsEnabled = true;
    }

    private void SubscribeToScreen(UserControl screen)
    {
        if (screen is LauncherSettingsWindow settings)
        {
            settings.BackRequested += OnScreenBackRequested;
        }
        else if (screen is SkinManagerWindow skins)
        {
            skins.BackRequested += OnScreenBackRequested;
        }
        else if (screen is MinecraftDefaultsWindow defaults)
        {
            defaults.Completed += OnScreenBackRequested;
        }
    }

    private void UnsubscribeFromScreen(UserControl screen)
    {
        if (screen is LauncherSettingsWindow settings)
        {
            settings.BackRequested -= OnScreenBackRequested;
        }
        else if (screen is SkinManagerWindow skins)
        {
            skins.BackRequested -= OnScreenBackRequested;
        }
        else if (screen is MinecraftDefaultsWindow defaults)
        {
            defaults.Completed -= OnScreenBackRequested;
        }
    }

    private void OnLauncherHideRequested(object? sender, EventArgs e)
    {
        if (!Dispatcher.CheckAccess())
        {
            Dispatcher.BeginInvoke(() => OnLauncherHideRequested(sender, e));
            return;
        }

        ShowInTaskbar = false;
        Hide();
    }

    private void OnLauncherRestoreRequested(object? sender, EventArgs e)
    {
        if (!Dispatcher.CheckAccess())
        {
            Dispatcher.BeginInvoke(() => OnLauncherRestoreRequested(sender, e));
            return;
        }

        ShowInTaskbar = true;
        if (WindowState == WindowState.Minimized)
        {
            WindowState = WindowState.Normal;
        }

        Show();
        Activate();
        Focus();
    }

    private async void OnLauncherBindingRequired(object? sender, EventArgs e)
    {
        var answer = MessageBox.Show(
            this,
            "Аккаунт не привязан к Launcher. Сначала привяжите аккаунт на сайте, затем повторите запуск Minecraft. Открыть страницу привязки сейчас?",
            "Аккаунт не привязан",
            MessageBoxButton.OKCancel,
            MessageBoxImage.Warning);
        if (answer == MessageBoxResult.OK)
        {
            await viewModel.OpenAccountLinkCommand.ExecuteAsync(null);
        }
    }

    public async Task HandleLauncherProtocolCallbackAsync(string callback)
    {
        await viewModel.HandleLauncherProtocolCallbackAsync(callback);
        Activate();
    }

    private void OnClosed(object? sender, EventArgs e)
    {
        if (currentScreen is not null)
        {
            UnsubscribeFromScreen(currentScreen);
            ScreenContent.Content = null;
        }

        viewModel.LauncherHideRequested -= OnLauncherHideRequested;
        viewModel.LauncherRestoreRequested -= OnLauncherRestoreRequested;
        viewModel.LauncherBindingRequired -= OnLauncherBindingRequired;
        Closed -= OnClosed;
    }
}
