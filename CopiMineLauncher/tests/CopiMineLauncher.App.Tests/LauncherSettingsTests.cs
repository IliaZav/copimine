using System.IO;
using FluentAssertions;
using Xunit;

namespace CopiMineLauncher.App.Tests;

public sealed class LauncherSettingsTests
{
    [Fact]
    public void Missing_settings_use_safe_defaults()
    {
        using var temp = new TemporaryDirectory();

        new LauncherSettingsStore(temp.Path).Load()
            .Should().Be(new LauncherSettings());
    }

    [Fact]
    public void Settings_round_trip_without_storing_credentials_or_editable_path()
    {
        using var temp = new TemporaryDirectory();
        var store = new LauncherSettingsStore(temp.Path);
        var expected = new LauncherSettings(8192, 1920, 1080, true);

        store.Save(expected);

        store.Load().Should().Be(expected);
        File.ReadAllText(Path.Combine(temp.Path, "launcher-settings.json"))
            .Should().NotContain("password")
            .And.NotContain("instancePath");
    }

    [Fact]
    public void Invalid_settings_are_rejected_before_launch()
    {
        Action save = () => new LauncherSettingsStore(".").Save(new LauncherSettings(256, 320, 200, false));

        save.Should().Throw<ArgumentOutOfRangeException>();
    }

    private sealed class TemporaryDirectory : IDisposable
    {
        public TemporaryDirectory() => Path = Directory.CreateTempSubdirectory("copimine-launcher-settings-app-tests-").FullName;

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
