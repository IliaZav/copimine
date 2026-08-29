using System.Diagnostics;
using System.IO;
using System.Net.Http;
using System.Reflection;
using System.Windows;
using CopiMineLauncher.Core.Installation;

namespace CopiMineLauncher.Installer;

public partial class MainWindow : Window
{
    private static readonly HttpClient Http = new() { Timeout = TimeSpan.FromMinutes(20) };
    private readonly string msiUrl;

    public MainWindow()
    {
        InitializeComponent();
        msiUrl = ResolveMsiUrl(Environment.GetCommandLineArgs());
        InstallPathBox.Text = Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
            "CopiMine Launcher");
    }

    private void BrowseButton_OnClick(object sender, RoutedEventArgs e)
    {
        using var dialog = new System.Windows.Forms.FolderBrowserDialog
        {
            Description = "Выберите папку для Launcher и Minecraft",
            UseDescriptionForTitle = true,
            SelectedPath = Directory.Exists(InstallPathBox.Text)
                ? InstallPathBox.Text
                : Path.GetDirectoryName(Path.GetFullPath(InstallPathBox.Text)) ?? string.Empty,
            ShowNewFolderButton = true,
        };

        if (dialog.ShowDialog() == System.Windows.Forms.DialogResult.OK)
        {
            InstallPathBox.Text = dialog.SelectedPath;
        }
    }

    private async void InstallButton_OnClick(object sender, RoutedEventArgs e)
    {
        if (!TryResolveInstallPath(InstallPathBox.Text, out var installPath, out var pathError))
        {
            ShowError(pathError);
            return;
        }

        SetBusy(true, "Скачиваем установочные файлы…");
        var temporaryRoot = Path.Combine(Path.GetTempPath(), "CopiMineLauncherInstaller", Guid.NewGuid().ToString("N"));
        var msiPath = Path.Combine(temporaryRoot, "CopiMineLauncher.msi");
        try
        {
            Directory.CreateDirectory(temporaryRoot);
            await DownloadMsiAsync(msiPath, CancellationToken.None);
            SetBusy(true, "Устанавливаем Launcher в выбранную папку…");
            var exitCode = await RunMsiAsync(msiPath, installPath);
            if (exitCode is not 0 and not 3010)
            {
                throw new InvalidOperationException($"Установщик Windows завершился с кодом {exitCode}.");
            }

            var launcherPath = Path.Combine(installPath, "current", "CopiMineLauncher.App.exe");
            if (!File.Exists(launcherPath))
            {
                throw new InvalidOperationException("Установка завершилась без файла Launcher. Откройте диагностику установщика и повторите попытку.");
            }

            StatusText.Text = "Установка завершена. Launcher готов к запуску.";
            InstallButton.Content = "Запустить Launcher";
            InstallButton.Click -= InstallButton_OnClick;
            InstallButton.Click += LaunchButton_OnClick;
            BrowseButton.IsEnabled = false;
        }
        catch (Exception exception)
        {
            ShowError($"Не удалось установить Launcher: {exception.Message}");
        }
        finally
        {
            TryDeleteDirectory(temporaryRoot);
            SetBusy(false, StatusText.Text);
        }
    }

    private void LaunchButton_OnClick(object sender, RoutedEventArgs e)
    {
        if (TryResolveInstallPath(InstallPathBox.Text, out var installPath, out _))
        {
            var launcherPath = Path.Combine(installPath, "current", "CopiMineLauncher.App.exe");
            if (File.Exists(launcherPath))
            {
                Process.Start(new ProcessStartInfo(launcherPath) { UseShellExecute = true });
                Close();
            }
        }
    }

    private void CancelButton_OnClick(object sender, RoutedEventArgs e) => Close();

    private async Task DownloadMsiAsync(string destination, CancellationToken cancellationToken)
    {
        var expected = ResolveExpectedMsiIntegrity()
            ?? throw new InvalidOperationException("У установщика нет опубликованной проверки целостности MSI. Скачивание остановлено.");
        using var response = await Http.GetAsync(msiUrl, HttpCompletionOption.ResponseHeadersRead, cancellationToken);
        response.EnsureSuccessStatusCode();
        if (response.Content.Headers.ContentLength is long contentLength && contentLength != expected.SizeBytes)
        {
            throw new InvalidDataException($"INSTALLER_MSI_SIZE_MISMATCH: сервер сообщил {contentLength} байт, ожидалось {expected.SizeBytes}.");
        }

        await using var input = await response.Content.ReadAsStreamAsync(cancellationToken);
        await using var output = File.Create(destination);
        await input.CopyToAsync(output, cancellationToken);
        await MsiIntegrityVerifier.VerifyFileAsync(destination, expected.SizeBytes, expected.Sha256, cancellationToken);
    }

    private static MsiExpectedIntegrity? ResolveExpectedMsiIntegrity()
    {
        var metadata = Assembly.GetEntryAssembly()?
            .GetCustomAttributes<AssemblyMetadataAttribute>()
            .ToDictionary(attribute => attribute.Key, attribute => attribute.Value, StringComparer.Ordinal);
        if (metadata is null
            || !metadata.TryGetValue("CopiMineInstallerMsiSha256", out var sha256)
            || !metadata.TryGetValue("CopiMineInstallerMsiSizeBytes", out var sizeText)
            || !long.TryParse(sizeText, out var sizeBytes)
            || sizeBytes <= 0
            || string.IsNullOrWhiteSpace(sha256))
        {
            return null;
        }

        return new MsiExpectedIntegrity(sizeBytes, sha256);
    }

    private static async Task<int> RunMsiAsync(string msiPath, string installPath)
    {
        var startInfo = new ProcessStartInfo("msiexec.exe")
        {
            UseShellExecute = false,
            CreateNoWindow = true,
            Arguments = $"/i \"{msiPath}\" /qn /norestart VELOPACK_INSTALLDIR=\"{installPath}\"",
        };
        using var process = Process.Start(startInfo) ?? throw new InvalidOperationException("Не удалось запустить Windows Installer.");
        await process.WaitForExitAsync();
        return process.ExitCode;
    }

    private static string ResolveMsiUrl(IReadOnlyList<string> arguments)
    {
        var source = Environment.GetEnvironmentVariable("COPIMINE_LAUNCHER_INSTALLER_SOURCE");
        for (var index = 0; index + 1 < arguments.Count; index++)
        {
            if (string.Equals(arguments[index], "--source", StringComparison.OrdinalIgnoreCase))
            {
                source = arguments[index + 1];
                break;
            }
        }

        if (string.IsNullOrWhiteSpace(source))
        {
            var version = Assembly.GetEntryAssembly()?.GetName().Version?.ToString(3) ?? "1.0.3";
            source = $"https://copimine.ru/downloads/launcher/CopiMineLauncherSetup-{version}.msi";
        }

        if (!Uri.TryCreate(source, UriKind.Absolute, out var uri)
            || uri.UserInfo.Length != 0
            || (uri.Scheme != Uri.UriSchemeHttp && uri.Scheme != Uri.UriSchemeHttps)
            || (!uri.IsLoopback && !string.Equals(uri.Host, "copimine.ru", StringComparison.OrdinalIgnoreCase))
            || !uri.AbsolutePath.EndsWith(".msi", StringComparison.OrdinalIgnoreCase))
        {
            throw new InvalidOperationException("Источник установщика должен быть официальным copimine.ru или локальным staging-сервером.");
        }

        return uri.AbsoluteUri;
    }

    private static bool TryResolveInstallPath(string raw, out string path, out string error)
    {
        path = string.Empty;
        error = string.Empty;
        try
        {
            path = Path.GetFullPath(raw.Trim());
            var root = Path.GetPathRoot(path);
            if (string.IsNullOrWhiteSpace(root) || string.Equals(path.TrimEnd(Path.DirectorySeparatorChar), root.TrimEnd(Path.DirectorySeparatorChar), StringComparison.OrdinalIgnoreCase))
            {
                error = "Выберите отдельную папку, а не корень диска.";
                return false;
            }

            Directory.CreateDirectory(path);
            return true;
        }
        catch (Exception exception)
        {
            error = $"Путь установки недоступен: {exception.Message}";
            return false;
        }
    }

    private void SetBusy(bool busy, string status)
    {
        InstallProgress.Visibility = busy ? Visibility.Visible : Visibility.Collapsed;
        InstallButton.IsEnabled = !busy;
        BrowseButton.IsEnabled = !busy;
        CancelButton.IsEnabled = !busy;
        StatusText.Text = status;
    }

    private void ShowError(string message)
    {
        SetBusy(false, message);
        System.Windows.MessageBox.Show(this, message, "CopiMine Launcher", MessageBoxButton.OK, MessageBoxImage.Error);
    }

    private static void TryDeleteDirectory(string path)
    {
        try
        {
            if (Directory.Exists(path)) Directory.Delete(path, recursive: true);
        }
        catch
        {
            // Temporary installer data is harmless if an antivirus still has a handle.
        }
    }

    private sealed record MsiExpectedIntegrity(long SizeBytes, string Sha256);
}
