using CopiMineLauncher.Infrastructure.Launch;
using FluentAssertions;
using Xunit;

namespace CopiMineLauncher.Infrastructure.Tests;

public sealed class MinecraftSettingsDefaultsTests
{
    [Fact]
    public void Fresh_instance_gets_russian_language_and_narrator_off()
    {
        using var temp = new TemporaryDirectory();

        MinecraftSettingsDefaults.EnsureDefaults(temp.Path).Should().BeTrue();

        File.ReadAllLines(Path.Combine(temp.Path, "options.txt"))
            .Should().ContainInOrder("lang:ru_ru", "narrator:0", "soundCategory_master:0.15");
    }

    [Fact]
    public void Existing_player_preferences_are_not_overwritten()
    {
        using var temp = new TemporaryDirectory();
        File.WriteAllLines(Path.Combine(temp.Path, "options.txt"), ["lang:en_us", "narrator:1", "soundCategory_master:0.8"]);

        MinecraftSettingsDefaults.EnsureDefaults(temp.Path).Should().BeFalse();
        File.ReadAllLines(Path.Combine(temp.Path, "options.txt"))
            .Should().Equal("lang:en_us", "narrator:1", "soundCategory_master:0.8");
    }

    [Fact]
    public void Installer_selection_applies_only_the_checked_game_defaults()
    {
        using var temp = new TemporaryDirectory();
        MinecraftDefaultSettingsStore.Save(
            temp.Path,
            new MinecraftDefaultSettings(
                UseRussianLanguage: false,
                DisableNarrator: true,
                SetMasterVolumeToFifteenPercent: false));

        MinecraftSettingsDefaults.EnsureDefaults(temp.Path).Should().BeTrue();

        File.ReadAllLines(Path.Combine(temp.Path, "options.txt"))
            .Should().Equal("narrator:0");
        MinecraftDefaultSettingsStore.IsConfigured(temp.Path).Should().BeTrue();
    }

    [Fact]
    public void Configured_defaults_are_not_reapplied_after_a_player_changes_options()
    {
        using var temp = new TemporaryDirectory();
        MinecraftDefaultSettingsStore.Save(temp.Path, new MinecraftDefaultSettings());
        MinecraftSettingsDefaults.EnsureDefaults(temp.Path).Should().BeTrue();

        File.WriteAllLines(
            Path.Combine(temp.Path, "options.txt"),
            ["lang:en_us", "narrator:1", "soundCategory_master:0.8"]);

        MinecraftSettingsDefaults.EnsureDefaults(temp.Path).Should().BeFalse();
        File.ReadAllLines(Path.Combine(temp.Path, "options.txt"))
            .Should().Equal("lang:en_us", "narrator:1", "soundCategory_master:0.8");
    }

    private sealed class TemporaryDirectory : IDisposable
    {
        public TemporaryDirectory() => Path = Directory.CreateTempSubdirectory("copimine-launcher-settings-tests-").FullName;

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
