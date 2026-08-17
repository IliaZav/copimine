using System.Diagnostics;
using System.Text;
using CmlLib.Core;
using CmlLib.Core.Auth;
using CmlLib.Core.ProcessBuilder;
using CmlLib.Core.VersionLoader;
using CopiMineLauncher.Core.Launch;
using CopiMineLauncher.Infrastructure.Provisioning;

namespace CopiMineLauncher.Infrastructure.Launch;

public sealed record LaunchRequest(
    string InstanceRoot,
    string FabricVersionName,
    string Username,
    string? JavaExecutablePath,
    int MaximumRamMb = 4096,
    int ResolutionWidth = 1280,
    int ResolutionHeight = 720,
    bool Fullscreen = false,
    string? ServerAddress = null,
    int ServerPort = 25565,
    IReadOnlyCollection<string>? ManagedModFileNames = null);

public sealed record LaunchEvidence(
    Process Process,
    DateTimeOffset StartedAtUtc,
    string FabricVersionName,
    string InstanceRoot,
    string JavaExecutablePath,
    string? ProcessLogPath = null);

public sealed class MinecraftLaunchException : InvalidOperationException
{
    public MinecraftLaunchException(string code, MinecraftLaunchFailureReport report, string message, Exception? innerException = null)
        : base(message, innerException)
    {
        Code = code;
        Report = report;
    }

    public string Code { get; }

    public MinecraftLaunchFailureReport Report { get; }
}

public interface IMinecraftLaunchService
{
    Task<LaunchEvidence> LaunchAsync(LaunchRequest request, CancellationToken cancellationToken);
}

public static class MinecraftLaunchStartup
{
    // The probe only needs to catch immediate crashes (for example, a bad
    // user-installed mod). Waiting for the process to finish its full game
    // bootstrap makes every successful launch feel frozen in the Launcher.
    public static TimeSpan DefaultGracePeriod { get; } = TimeSpan.FromSeconds(3);

    public static async Task EnsureAliveAsync(
        Process process,
        string logPath,
        TimeSpan gracePeriod,
        CancellationToken cancellationToken,
        string? instanceRoot = null,
        IReadOnlyCollection<string>? userModFileNames = null)
    {
        ArgumentNullException.ThrowIfNull(process);
        ArgumentException.ThrowIfNullOrWhiteSpace(logPath);
        if (gracePeriod <= TimeSpan.Zero)
        {
            throw new ArgumentOutOfRangeException(nameof(gracePeriod));
        }

        if (process.HasExited)
        {
            throw BuildExitException(process, logPath, instanceRoot, userModFileNames);
        }

        using var timeout = CancellationTokenSource.CreateLinkedTokenSource(cancellationToken);
        timeout.CancelAfter(gracePeriod);
        try
        {
            await process.WaitForExitAsync(timeout.Token);
        }
        catch (OperationCanceledException) when (!cancellationToken.IsCancellationRequested)
        {
            return;
        }

        if (process.HasExited)
        {
            throw BuildExitException(process, logPath, instanceRoot, userModFileNames);
        }
    }

    private static MinecraftLaunchException BuildExitException(
        Process process,
        string logPath,
        string? instanceRoot,
        IReadOnlyCollection<string>? userModFileNames)
    {
        try
        {
            process.WaitForExit();
        }
        catch
        {
            // The process may disappear between the Exited event and this check.
        }

        var logText = ReadStartupLogs(logPath, instanceRoot);
        var report = MinecraftLaunchFailureParser.Parse(logText, logPath, userModFileNames);
        var exitCode = TryReadExitCode(process);
        return new MinecraftLaunchException(
            "MINECRAFT_START_FAILED",
            report,
            $"MINECRAFT_START_FAILED: {report.Summary} (код {exitCode}). Полный лог: {logPath}");
    }

    private static string ReadStartupLogs(string primaryLogPath, string? instanceRoot)
    {
        var paths = new List<string> { primaryLogPath };
        if (!string.IsNullOrWhiteSpace(instanceRoot))
        {
            var fullRoot = Path.GetFullPath(instanceRoot);
            paths.Add(Path.Combine(fullRoot, "logs", "latest.log"));
            var crashDirectory = Path.Combine(fullRoot, "crash-reports");
            if (Directory.Exists(crashDirectory))
            {
                paths.AddRange(Directory.EnumerateFiles(crashDirectory, "*.txt")
                    .OrderByDescending(File.GetLastWriteTimeUtc)
                    .Take(2));
            }
        }

        return string.Join(
            Environment.NewLine,
            paths.Distinct(StringComparer.OrdinalIgnoreCase)
                .Select(ReadLogFile)
                .Where(text => !string.IsNullOrWhiteSpace(text)));
    }

    private static string ReadLogFile(string path)
    {
        try
        {
            return File.Exists(path)
                ? File.ReadAllText(path, Encoding.UTF8)
                : string.Empty;
        }
        catch (IOException)
        {
            return string.Empty;
        }
        catch (UnauthorizedAccessException)
        {
            return string.Empty;
        }
    }

    private static int TryReadExitCode(Process process)
    {
        try
        {
            return process.ExitCode;
        }
        catch
        {
            return -1;
        }
    }
}

public static class MinecraftLaunchProcessConfiguration
{
    public static void Apply(Process process, string instanceRoot)
    {
        ArgumentNullException.ThrowIfNull(process);
        ArgumentException.ThrowIfNullOrWhiteSpace(instanceRoot);

        var fullInstanceRoot = Path.GetFullPath(instanceRoot);
        Directory.CreateDirectory(fullInstanceRoot);
        process.StartInfo.WorkingDirectory = fullInstanceRoot;
        process.StartInfo.UseShellExecute = false;
        process.StartInfo.CreateNoWindow = true;
        process.StartInfo.WindowStyle = ProcessWindowStyle.Hidden;
        process.StartInfo.RedirectStandardOutput = true;
        process.StartInfo.RedirectStandardError = true;
    }
}

public sealed class MinecraftLaunchService : IMinecraftLaunchService
{
    private readonly HttpClient httpClient;

    public MinecraftLaunchService(HttpClient httpClient)
    {
        this.httpClient = httpClient;
    }

    public async Task<LaunchEvidence> LaunchAsync(LaunchRequest request, CancellationToken cancellationToken)
    {
        if (request.MaximumRamMb < LauncherMemoryLimits.MinimumRamMb || request.MaximumRamMb > LauncherMemoryLimits.MaximumRamMb)
        {
            throw new ArgumentOutOfRangeException(nameof(request.MaximumRamMb), $"Launcher memory must be between {LauncherMemoryLimits.MinimumRamMb} and {LauncherMemoryLimits.MaximumRamMb} MB for this PC");
        }

        if (request.ResolutionWidth is < 800 or > 7680)
        {
            throw new ArgumentOutOfRangeException(nameof(request.ResolutionWidth), "Launcher width must be between 800 and 7680 pixels");
        }

        if (request.ResolutionHeight is < 600 or > 4320)
        {
            throw new ArgumentOutOfRangeException(nameof(request.ResolutionHeight), "Launcher height must be between 600 and 4320 pixels");
        }

        var minecraftPath = new MinecraftPath(request.InstanceRoot);
        var parameters = MinecraftLauncherParameters.CreateDefault(minecraftPath, httpClient);
        if (OfflineMinecraftBaseline.IsMinecraftProfileReady(
                request.InstanceRoot,
                "1.21.1",
                "0.19.3"))
        {
            parameters.VersionLoader = new MojangJsonVersionLoaderV2(minecraftPath, httpClient)
            {
                UseLocalManifestWhenError = true
            };
        }

        var launcher = new MinecraftLauncher(parameters);
        var javaPath = request.JavaExecutablePath ?? launcher.GetDefaultJavaPath()
            ?? throw new InvalidOperationException("No Java runtime is available for Minecraft launch");
        MinecraftSettingsDefaults.EnsureDefaults(request.InstanceRoot);
        var options = new MLaunchOption
        {
            Session = MSession.CreateOfflineSession(request.Username),
            JavaPath = javaPath,
            MaximumRamMb = request.MaximumRamMb,
            MinimumRamMb = Math.Min(1024, request.MaximumRamMb),
            ScreenWidth = request.ResolutionWidth,
            ScreenHeight = request.ResolutionHeight,
            FullScreen = request.Fullscreen
        };
        MinecraftLaunchServerConfiguration.Apply(options, request.ServerAddress, request.ServerPort);
        var process = await launcher.BuildProcessAsync(request.FabricVersionName, options, cancellationToken);
        var logPath = Path.Combine(Path.GetFullPath(request.InstanceRoot), "logs", "launcher-process.log");
        Directory.CreateDirectory(Path.GetDirectoryName(logPath)!);
        var logWriter = new StreamWriter(logPath, append: true, Encoding.UTF8)
        {
            AutoFlush = true
        };
        var logLock = new object();
        void WriteLog(string line)
        {
            lock (logLock)
            {
                logWriter.WriteLine($"{DateTimeOffset.UtcNow:O} {line}");
            }
        }

        MinecraftLaunchProcessConfiguration.Apply(process, request.InstanceRoot);
        process.EnableRaisingEvents = true;
        process.OutputDataReceived += (_, args) =>
        {
            if (!string.IsNullOrWhiteSpace(args.Data))
            {
                WriteLog($"OUT {args.Data}");
            }
        };
        process.ErrorDataReceived += (_, args) =>
        {
            if (!string.IsNullOrWhiteSpace(args.Data))
            {
                WriteLog($"ERR {args.Data}");
            }
        };
        process.Exited += (_, _) =>
        {
            WriteLog($"EXIT code={process.ExitCode}");
            logWriter.Dispose();
        };

        WriteLog($"START fabric={request.FabricVersionName} java={javaPath}");
        WriteLog($"COMMAND file={process.StartInfo.FileName} cwd={process.StartInfo.WorkingDirectory} args={process.StartInfo.Arguments}");
        try
        {
            process.Start();
            process.BeginOutputReadLine();
            process.BeginErrorReadLine();
        }
        catch
        {
            logWriter.Dispose();
            throw;
        }

        await MinecraftLaunchStartup.EnsureAliveAsync(
            process,
            logPath,
            MinecraftLaunchStartup.DefaultGracePeriod,
            cancellationToken,
            request.InstanceRoot,
            GetUserModFileNames(request));

        return new(process, DateTimeOffset.UtcNow, request.FabricVersionName, Path.GetFullPath(request.InstanceRoot), javaPath, logPath);
    }

    private static IReadOnlyCollection<string> GetUserModFileNames(LaunchRequest request)
    {
        if (request.ManagedModFileNames is null)
        {
            return Array.Empty<string>();
        }

        try
        {
            var managed = request.ManagedModFileNames.ToHashSet(StringComparer.OrdinalIgnoreCase);
            var modsDirectory = Path.Combine(Path.GetFullPath(request.InstanceRoot), "mods");
            return Directory.Exists(modsDirectory)
                ? Directory.EnumerateFiles(modsDirectory, "*.jar", SearchOption.TopDirectoryOnly)
                    .Select(Path.GetFileName)
                    .Where(name => !string.IsNullOrWhiteSpace(name) && !managed.Contains(name!))
                    .Cast<string>()
                    .ToArray()
                : Array.Empty<string>();
        }
        catch (IOException)
        {
            return Array.Empty<string>();
        }
        catch (UnauthorizedAccessException)
        {
            return Array.Empty<string>();
        }
    }
}
