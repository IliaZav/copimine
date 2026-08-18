using System.Diagnostics;
using System.IO;
using CopiMineLauncher.Core.Launch;

namespace CopiMineLauncher.App;

public partial class LaunchFailureDialog : System.Windows.Window
{
    private readonly MinecraftLaunchFailureReport report;

    public LaunchFailureDialog(MinecraftLaunchFailureReport report)
    {
        this.report = report ?? throw new ArgumentNullException(nameof(report));
        InitializeComponent();
        DataContext = new LaunchFailureDialogModel(report);
    }

    private void OpenLog_Click(object sender, System.Windows.RoutedEventArgs e)
    {
        try
        {
            if (File.Exists(report.LogPath))
            {
                Process.Start(new ProcessStartInfo
                {
                    FileName = report.LogPath,
                    UseShellExecute = true
                });
                return;
            }

            var directory = Path.GetDirectoryName(report.LogPath);
            if (!string.IsNullOrWhiteSpace(directory) && Directory.Exists(directory))
            {
                Process.Start(new ProcessStartInfo
                {
                    FileName = directory,
                    UseShellExecute = true
                });
            }

            System.Windows.MessageBox.Show(
                this,
                $"Файл лога ещё не найден:\n{report.LogPath}",
                "Лог недоступен",
                System.Windows.MessageBoxButton.OK,
                System.Windows.MessageBoxImage.Information);
        }
        catch (Exception exception)
        {
            System.Windows.MessageBox.Show(
                this,
                $"Не удалось открыть лог:\n{exception.Message}",
                "Ошибка открытия лога",
                System.Windows.MessageBoxButton.OK,
                System.Windows.MessageBoxImage.Error);
        }
    }

    private void Close_Click(object sender, System.Windows.RoutedEventArgs e)
    {
        DialogResult = false;
        Close();
    }

    private sealed class LaunchFailureDialogModel(MinecraftLaunchFailureReport report)
    {
        public string Title => report.Title;
        public string Summary => report.Summary;
        public string Explanation => report.Explanation;
        public string LogPath => $"Полный лог: {report.LogPath}";
        public string EvidenceText => string.Join(Environment.NewLine, report.Evidence);
        public string ModHint => report.IsLikelyUserMod && report.SuspectedModFileName is not null
            ? $"Подозрительный пользовательский файл: {report.SuspectedModFileName}"
            : "Точный пользовательский файл по этому логу определить не удалось.";
    }
}
