using System.Windows;
using System.Windows.Controls;

namespace CopiMineLauncher.App;

public partial class LauncherSettingsWindow : UserControl
{
    private readonly LauncherViewModel viewModel;

    public event EventHandler? BackRequested;

    public LauncherSettingsWindow(LauncherViewModel viewModel)
    {
        InitializeComponent();
        this.viewModel = viewModel ?? throw new ArgumentNullException(nameof(viewModel));
        DataContext = viewModel;
    }

    private void Save_Click(object sender, RoutedEventArgs e)
    {
        try
        {
            viewModel.SaveSettings();
            BackRequested?.Invoke(this, EventArgs.Empty);
        }
        catch (Exception exception)
        {
            MessageBox.Show(exception.Message, "Проверьте настройки", MessageBoxButton.OK, MessageBoxImage.Warning);
        }
    }

    private void Cancel_Click(object sender, RoutedEventArgs e) => BackRequested?.Invoke(this, EventArgs.Empty);
}
