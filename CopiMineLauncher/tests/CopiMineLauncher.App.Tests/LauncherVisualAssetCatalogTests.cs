using System.IO;
using System.Windows.Media.Imaging;
using FluentAssertions;
using Xunit;

namespace CopiMineLauncher.App.Tests;

public sealed class LauncherVisualAssetCatalogTests
{
    private static readonly IReadOnlyDictionary<string, (int Width, int Height)> DisplayAssets =
        new Dictionary<string, (int Width, int Height)>(StringComparer.OrdinalIgnoreCase)
        {
            ["launcher-home-background.png"] = (2560, 1440),
            ["update-background.png"] = (1920, 1080),
            ["loading-emblem.png"] = (1024, 1024),
            ["copimine-logo.png"] = (1066, 600),
            ["installer-banner.png"] = (1200, 700),
            ["news-01.png"] = (1600, 600),
            ["news-02.png"] = (1600, 600),
            ["news-03.png"] = (1600, 600)
        };

    [Fact]
    public void All_archive_assets_are_present_and_non_empty()
    {
        var root = ResolveAssetRoot();

        LauncherVisualAssetCatalog.RequiredSourceAssets.Should().HaveCount(12);
        foreach (var name in LauncherVisualAssetCatalog.RequiredSourceAssets)
        {
            var path = Path.Combine(root, name);
            File.Exists(path).Should().BeTrue($"the supplied asset {name} must be packaged");
            new FileInfo(path).Length.Should().BeGreaterThan(0, $"the supplied asset {name} must not be empty");
        }
    }

    [Fact]
    public void Derived_display_assets_keep_the_supplied_dimensions()
    {
        var root = ResolveAssetRoot();

        LauncherVisualAssetCatalog.RequiredSourceAssets
            .Concat(LauncherVisualAssetCatalog.DerivedDisplayAssets)
            .Should().Contain(DisplayAssets.Keys);
        foreach (var pair in DisplayAssets)
        {
            var path = Path.Combine(root, pair.Key);
            File.Exists(path).Should().BeTrue($"the WPF display asset {pair.Key} must be present");
            using var stream = File.OpenRead(path);
            var decoder = BitmapDecoder.Create(
                stream,
                BitmapCreateOptions.PreservePixelFormat,
                BitmapCacheOption.OnLoad);
            decoder.Frames.Should().NotBeEmpty($"the WPF display asset {pair.Key} must decode");
            decoder.Frames[0].PixelWidth.Should().Be(pair.Value.Width, pair.Key);
            decoder.Frames[0].PixelHeight.Should().Be(pair.Value.Height, pair.Key);
        }
    }

    [Fact]
    public void News_artwork_has_three_stable_slots()
    {
        var names = new[]
        {
            LauncherVisualAssetCatalog.News01,
            LauncherVisualAssetCatalog.News02,
            LauncherVisualAssetCatalog.News03
        };

        names.Should().OnlyHaveUniqueItems();
        names.Should().HaveCount(3);
        names.Select(name => Path.Combine(ResolveAssetRoot(), name))
            .Should().OnlyContain(path => File.Exists(path));
    }

    [Fact]
    public void Header_logo_variant_is_tightly_framed_and_animated()
    {
        var path = Path.Combine(ResolveAssetRoot(), LauncherVisualAssetCatalog.CopiMineHeaderAnimatedLogo);
        using var stream = File.OpenRead(path);
        var decoder = BitmapDecoder.Create(
            stream,
            BitmapCreateOptions.PreservePixelFormat,
            BitmapCacheOption.OnLoad);

        decoder.Frames.Should().HaveCountGreaterThan(1);
        decoder.Frames[0].PixelWidth.Should().BeLessThan(700);
        decoder.Frames[0].PixelHeight.Should().BeLessThan(220);
    }

    [Fact]
    public void Skin_preview_contains_local_shader_landscapes_without_hud_or_player_assets()
    {
        var root = Path.GetFullPath(Path.Combine(
            AppContext.BaseDirectory,
            "..", "..", "..", "..", "..",
            "src", "CopiMineLauncher.App", "Assets", "SkinPreview"));

        foreach (var name in new[] { "shader-forest.jpg", "shader-river.jpg", "shader-mountain.jpg" })
        {
            var path = Path.Combine(root, name);
            File.Exists(path).Should().BeTrue(name);
            new FileInfo(path).Length.Should().BeGreaterThan(50_000, name);
        }

        File.ReadAllText(Path.Combine(root, "ASSET-CREDITS.txt"))
            .Should().Contain("imgur.com/gallery/minecraft-4k-shaders-screenshots-OZ53f");
    }

    private static string ResolveAssetRoot()
    {
        var sourceRoot = Path.GetFullPath(Path.Combine(
            AppContext.BaseDirectory,
            "..", "..", "..", "..", "..",
            "src", "CopiMineLauncher.App", "Assets", "LauncherVisuals"));
        return sourceRoot;
    }
}
