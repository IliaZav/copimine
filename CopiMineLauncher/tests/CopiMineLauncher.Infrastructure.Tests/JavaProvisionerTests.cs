using System.IO.Compression;
using CopiMineLauncher.Infrastructure.Provisioning;
using FluentAssertions;
using Xunit;

namespace CopiMineLauncher.Infrastructure.Tests;

public sealed class JavaProvisionerTests
{
    [Fact]
    public async Task ExtractZipSafely_creates_windows_directory_entries_before_files()
    {
        using var temp = new TemporaryDirectory();
        var archivePath = Path.Combine(temp.Path, "java-runtime.zip");
        var destinationRoot = Path.Combine(temp.Path, "extracted");

        using (var archive = ZipFile.Open(archivePath, ZipArchiveMode.Create))
        {
            var binDirectory = archive.CreateEntry(".\\bin");
            binDirectory.ExternalAttributes = unchecked((int)0x41FF0000);

            var javaEntry = archive.CreateEntry(".\\bin\\java.exe");
            await using (var stream = javaEntry.Open())
            await using (var writer = new StreamWriter(stream))
            {
                await writer.WriteAsync("java fixture");
            }
        }

        var action = () => JavaProvisioner.ExtractZipSafely(archivePath, destinationRoot);

        action.Should().NotThrow();
        Directory.Exists(Path.Combine(destinationRoot, "bin")).Should().BeTrue();
        File.ReadAllText(Path.Combine(destinationRoot, "bin", "java.exe"))
            .Should().Be("java fixture");
    }

    private sealed class TemporaryDirectory : IDisposable
    {
        public TemporaryDirectory() => Path = Directory.CreateTempSubdirectory("copimine-java-provisioner-").FullName;

        public string Path { get; }

        public void Dispose()
        {
            if (Directory.Exists(Path))
            {
                Directory.Delete(Path, recursive: true);
            }
        }
    }
}
