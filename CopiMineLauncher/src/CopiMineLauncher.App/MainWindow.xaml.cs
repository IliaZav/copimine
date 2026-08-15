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
}
