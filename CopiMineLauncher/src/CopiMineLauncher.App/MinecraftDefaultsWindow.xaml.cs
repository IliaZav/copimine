using System.Windows;
using System.Windows.Controls;

namespace CopiMineLauncher.App;

public partial class MinecraftDefaultsWindow : UserControl
{
    private readonly LauncherViewModel viewModel;

    public event EventHandler? Completed;

    public MinecraftDefaultsWindow(LauncherViewModel viewModel)
    {
        InitializeComponent();
        this.viewModel = viewModel ?? throw new ArgumentNullException(nameof(viewModel));
        DataContext = viewModel;
    }

    private void Save_Click(object sender, RoutedEventArgs e)
    {
        try
        {
            viewModel.SaveMinecraftDefaults();
            viewModel.Status = "Настройки Minecraft сохранены";
            viewModel.Diagnostic = "Стартовые настройки сохранены. При следующем запуске существующие значения в игре не будут перезаписаны.";
            Completed?.Invoke(this, EventArgs.Empty);
        }
        catch (Exception exception)
        {
            MessageBox.Show(exception.Message, "Настройки не сохранены", MessageBoxButton.OK, MessageBoxImage.Warning);
        }
    }
}
