using Microsoft.Win32;

namespace CopiMineLauncher.App;

internal static class LauncherProtocolRegistration
{
    private const string ProtocolKeyPath = @"Software\Classes\copimine";

    public static void EnsureRegistered()
    {
        if (!OperatingSystem.IsWindows()) return;

        try
        {
            var executablePath = Environment.ProcessPath;
            if (string.IsNullOrWhiteSpace(executablePath)) return;

            using var protocolKey = Registry.CurrentUser.CreateSubKey(ProtocolKeyPath);
            if (protocolKey is null) return;

            protocolKey.SetValue(string.Empty, "URL:CopiMine Launcher");
            protocolKey.SetValue("URL Protocol", string.Empty);
            using var commandKey = protocolKey.CreateSubKey(@"shell\open\command");
            commandKey?.SetValue(string.Empty, $"{Quote(executablePath)} \"%1\"");
        }
        catch (Exception exception)
        {
            // A locked-down machine may reject per-user protocol registration.
            // The Launcher continues normally; the polling flow does not depend
            // on the browser hand-off succeeding.
            System.Diagnostics.Trace.WriteLine($"Launcher protocol registration failed: {exception.Message}");
        }
    }

    private static string Quote(string value) => $"\"{value.Replace("\"", "\\\"")}\"";
}
