using System.Windows;

namespace CopiMineLauncher.App;

public partial class LauncherSettingsWindow : Window
{
    private readonly LauncherViewModel viewModel;

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
            DialogResult = true;
        }
        catch (Exception exception)
        {
            MessageBox.Show(exception.Message, "Проверьте настройки", MessageBoxButton.OK, MessageBoxImage.Warning);
        }
    }

    private void Cancel_Click(object sender, RoutedEventArgs e) => Close();
}
