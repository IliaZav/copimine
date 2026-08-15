using System.Diagnostics;
using System.Text;
using CmlLib.Core;
using CmlLib.Core.Auth;
using CmlLib.Core.ProcessBuilder;

namespace CopiMineLauncher.Infrastructure.Launch;

public sealed record LaunchRequest(
    string InstanceRoot,
    string FabricVersionName,
    string Username,
    string? JavaExecutablePath,
    int MaximumRamMb = 4096);

public sealed record LaunchEvidence(
    Process Process,
    DateTimeOffset StartedAtUtc,
    string FabricVersionName,
    string InstanceRoot,
    string JavaExecutablePath,
    string? ProcessLogPath = null);

public interface IMinecraftLaunchService
{
    Task<LaunchEvidence> LaunchAsync(LaunchRequest request, CancellationToken cancellationToken);
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
        if (request.MaximumRamMb is < 1024 or > 32768)
        {
            throw new ArgumentOutOfRangeException(nameof(request.MaximumRamMb), "Launcher memory must be between 1024 and 32768 MB");
        }

        var parameters = MinecraftLauncherParameters.CreateDefault(new MinecraftPath(request.InstanceRoot), httpClient);
        var launcher = new MinecraftLauncher(parameters);
        var javaPath = request.JavaExecutablePath ?? launcher.GetDefaultJavaPath()
            ?? throw new InvalidOperationException("No Java runtime is available for Minecraft launch");
        var options = new MLaunchOption
        {
            Session = MSession.CreateOfflineSession(request.Username),
            JavaPath = javaPath,
            MaximumRamMb = request.MaximumRamMb,
            MinimumRamMb = Math.Min(1024, request.MaximumRamMb)
        };
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

        process.StartInfo.UseShellExecute = false;
        process.StartInfo.RedirectStandardOutput = true;
        process.StartInfo.RedirectStandardError = true;
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

        return new(process, DateTimeOffset.UtcNow, request.FabricVersionName, Path.GetFullPath(request.InstanceRoot), javaPath, logPath);
    }
}
