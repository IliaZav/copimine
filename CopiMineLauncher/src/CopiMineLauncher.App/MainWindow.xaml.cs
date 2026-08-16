using System.Windows;

namespace CopiMineLauncher.App;

public partial class MainWindow : Window
{
    private readonly LauncherViewModel viewModel;

    public MainWindow(LauncherViewModel viewModel)
    {
        InitializeComponent();
        this.viewModel = viewModel ?? throw new ArgumentNullException(nameof(viewModel));
        DataContext = viewModel;
        Loaded += OnLoaded;
        viewModel.LauncherHideRequested += OnLauncherHideRequested;
        viewModel.LauncherRestoreRequested += OnLauncherRestoreRequested;
        Closed += OnClosed;
    }

    private async void OnLoaded(object sender, RoutedEventArgs e)
    {
        Loaded -= OnLoaded;
        try
        {
            await viewModel.InitializeAsync();
        }
        catch (Exception exception)
        {
            viewModel.Status = "Обновления недоступны";
            viewModel.Diagnostic = exception.Message;
        }
    }

    private void OpenSettings_Click(object sender, RoutedEventArgs e)
    {
        var settings = new LauncherSettingsWindow(viewModel)
        {
            Owner = this
        };
        settings.ShowDialog();
    }

    private void OpenSkins_Click(object sender, RoutedEventArgs e)
    {
        var cosmetics = new SkinManagerWindow(
            viewModel.InstancePath,
            viewModel.PlayerName,
            LauncherInstallPaths.ResolveLauncherDataRoot())
        {
            Owner = this
        };
        cosmetics.ShowDialog();
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

    private void OnClosed(object? sender, EventArgs e)
    {
        viewModel.LauncherHideRequested -= OnLauncherHideRequested;
        viewModel.LauncherRestoreRequested -= OnLauncherRestoreRequested;
        Closed -= OnClosed;
    }
}
