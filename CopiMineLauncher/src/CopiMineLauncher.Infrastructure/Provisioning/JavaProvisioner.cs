using System.Diagnostics;
using System.IO.Compression;
using System.Text.RegularExpressions;
using CopiMineLauncher.Core.Manifest;
using CopiMineLauncher.Infrastructure.Updates;

namespace CopiMineLauncher.Infrastructure.Provisioning;

public sealed record JavaProvisioningResult(string JavaExecutablePath, string VersionOutput, bool Downloaded);

public interface IJavaProvisioner
{
    Task<JavaProvisioningResult> EnsureJava21Async(string instanceRoot, LauncherManifest manifest, CancellationToken cancellationToken);
}

public sealed class JavaProvisioner : IJavaProvisioner
{
    private static readonly Regex Java21Pattern = new("(?:version|openjdk)\\s*[=\\\"]*21(?:[.\\s\\\"]|$)", RegexOptions.IgnoreCase | RegexOptions.CultureInvariant);
    private readonly IResumableDownloadManager downloads;

    public JavaProvisioner(IResumableDownloadManager downloads)
    {
        this.downloads = downloads;
    }

    public async Task<JavaProvisioningResult> EnsureJava21Async(string instanceRoot, LauncherManifest manifest, CancellationToken cancellationToken)
    {
        instanceRoot = Path.GetFullPath(instanceRoot);
        var metadata = manifest.JavaRuntime ?? throw new InvalidDataException("Manifest does not contain Java runtime metadata");
        var javaRoot = Path.Combine(instanceRoot, ".copimine", "java", metadata.Version);
        var existing = FindJavaExecutable(javaRoot);
        if (existing is not null)
        {
            var output = await ReadJavaVersionAsync(existing, cancellationToken);
            if (Java21Pattern.IsMatch(output))
            {
                return new(existing, output, false);
            }
        }

        var transactionId = Guid.NewGuid().ToString("N");
        var stagingRoot = Path.Combine(instanceRoot, ".copimine", "staging", "java", transactionId);
        var archivePath = Path.Combine(stagingRoot, "java-runtime.zip");
        var verifiedArchive = await downloads.DownloadAsync(new Uri(metadata.Url, UriKind.Absolute), archivePath, metadata.SizeBytes, metadata.Sha256, cancellationToken);
        var extractedRoot = Path.Combine(stagingRoot, "extracted");
        Directory.CreateDirectory(extractedRoot);
        ExtractZipSafely(verifiedArchive, extractedRoot);
        var javaExecutable = FindJavaExecutable(extractedRoot)
            ?? throw new InvalidDataException("Verified Java archive does not contain bin/java.exe");
        var versionOutput = await ReadJavaVersionAsync(javaExecutable, cancellationToken);
        if (!Java21Pattern.IsMatch(versionOutput))
        {
            throw new InvalidDataException("Provisioned Java runtime is not Java 21");
        }

        Directory.CreateDirectory(Path.GetDirectoryName(javaRoot)!);
        var backupRoot = javaRoot + ".previous-" + transactionId;
        if (Directory.Exists(javaRoot))
        {
            Directory.Move(javaRoot, backupRoot);
        }

        Directory.Move(extractedRoot, javaRoot);
        var finalExecutable = FindJavaExecutable(javaRoot)
            ?? throw new InvalidDataException("Java runtime disappeared during commit");
        return new(finalExecutable, versionOutput, true);
    }

    private static void ExtractZipSafely(string archivePath, string destinationRoot)
    {
        var fullRoot = Path.GetFullPath(destinationRoot).TrimEnd(Path.DirectorySeparatorChar) + Path.DirectorySeparatorChar;
        using var archive = ZipFile.OpenRead(archivePath);
        foreach (var entry in archive.Entries)
        {
            if (string.IsNullOrEmpty(entry.FullName) || entry.FullName.EndsWith('/'))
            {
                continue;
            }

            var normalizedEntry = NormalizeArchiveEntryPath(entry.FullName);
            if (normalizedEntry is null)
            {
                continue;
            }

            var safe = Core.Filesystem.SafeRelativePath.Parse(normalizedEntry).Value.Replace('/', Path.DirectorySeparatorChar);
            var target = Path.GetFullPath(Path.Combine(destinationRoot, safe));
            if (!target.StartsWith(fullRoot, StringComparison.OrdinalIgnoreCase))
            {
                throw new InvalidDataException("Java archive contains a path outside its staging root");
            }

            Directory.CreateDirectory(Path.GetDirectoryName(target)!);
            entry.ExtractToFile(target, overwrite: true);
        }
    }

    private static string? NormalizeArchiveEntryPath(string entryName)
    {
        var normalized = entryName.Replace('\\', '/');
        while (normalized.StartsWith("./", StringComparison.Ordinal))
        {
            normalized = normalized[2..];
        }

        if (string.IsNullOrEmpty(normalized) || normalized == "." || normalized.EndsWith("/", StringComparison.Ordinal))
        {
            return null;
        }

        var segments = normalized.Split('/');
        if (segments.Any(segment => segment.Length == 0 || segment is "." or ".."))
        {
            throw new InvalidDataException("Java archive contains an empty or traversal path segment");
        }

        return normalized;
    }

    private static string? FindJavaExecutable(string root)
    {
        if (!Directory.Exists(root))
        {
            return null;
        }

        return Directory.EnumerateFiles(root, OperatingSystem.IsWindows() ? "java.exe" : "java", SearchOption.AllDirectories)
            .FirstOrDefault(path => string.Equals(new DirectoryInfo(Path.GetDirectoryName(path)!).Name, "bin", StringComparison.OrdinalIgnoreCase));
    }

    private static async Task<string> ReadJavaVersionAsync(string executable, CancellationToken cancellationToken)
    {
        var startInfo = new ProcessStartInfo
        {
            FileName = executable,
            Arguments = "-version",
            RedirectStandardOutput = true,
            RedirectStandardError = true,
            UseShellExecute = false,
            CreateNoWindow = true
        };
        using var process = Process.Start(startInfo) ?? throw new InvalidOperationException("Could not start Java runtime");
        var stdout = process.StandardOutput.ReadToEndAsync(cancellationToken);
        var stderr = process.StandardError.ReadToEndAsync(cancellationToken);
        await process.WaitForExitAsync(cancellationToken);
        return (await stdout) + Environment.NewLine + (await stderr);
    }
}
