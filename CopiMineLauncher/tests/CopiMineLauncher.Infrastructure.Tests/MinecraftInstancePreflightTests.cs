using System.IO.Compression;
using CopiMineLauncher.Infrastructure.Launch;
using FluentAssertions;
using Xunit;

namespace CopiMineLauncher.Infrastructure.Tests;

public sealed class MinecraftInstancePreflightTests
{
    [Fact]
    public void Invalid_mod_archive_is_reported_with_the_exact_path_without_deleting_it()
    {
        using var temp = new TemporaryDirectory();
        var modPath = Path.Combine(temp.Path, "mods", "broken.jar");
        Directory.CreateDirectory(Path.GetDirectoryName(modPath)!);
        File.WriteAllBytes(modPath, new byte[] { 1, 2, 3, 4 });

        var action = () => MinecraftInstancePreflight.ValidateModArchives(temp.Path);

        var exception = action.Should().Throw<MinecraftPreflightException>().Which;
        exception.Code.Should().Be("INVALID_MOD_ARCHIVE");
        exception.Message.Should().Contain(Path.GetFullPath(modPath));
        File.Exists(modPath).Should().BeTrue();
    }

    [Fact]
    public void Valid_mod_archive_passes_preflight()
    {
        using var temp = new TemporaryDirectory();
        var modsDirectory = Path.Combine(temp.Path, "mods");
        Directory.CreateDirectory(modsDirectory);
        using (var archive = ZipFile.Open(Path.Combine(modsDirectory, "valid.jar"), ZipArchiveMode.Create))
        {
            archive.CreateEntry("fabric.mod.json");
        }

        var action = () => MinecraftInstancePreflight.ValidateModArchives(temp.Path);

        action.Should().NotThrow();
    }

    private sealed class TemporaryDirectory : IDisposable
    {
        public TemporaryDirectory() => Path = Directory.CreateTempSubdirectory("copimine-preflight-").FullName;

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
