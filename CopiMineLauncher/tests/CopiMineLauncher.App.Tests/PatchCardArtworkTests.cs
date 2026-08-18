using System.IO;
using CopiMineLauncher.Core.News;
using FluentAssertions;
using Xunit;

namespace CopiMineLauncher.App.Tests;

public sealed class PatchCardArtworkTests
{
    [Fact]
    public void First_three_cards_use_three_local_artwork_slots()
    {
        var cardType = typeof(PatchFeedCardViewModel);
        var artworkProperty = cardType.GetProperty("ArtworkPath");
        artworkProperty.Should().NotBeNull("news cards need a local visual fallback");
        var constructor = cardType.GetConstructor([typeof(PatchFeedItem), typeof(int)]);
        constructor.Should().NotBeNull("card artwork must be selected by feed index");

        for (var index = 0; index < 3; index++)
        {
            var card = constructor!.Invoke([CreateItem(index), index]);
            var artwork = (string)artworkProperty!.GetValue(card)!;

            artwork.Should().Be($"{LauncherVisualAssetCatalog.Root}/{LauncherVisualAssetCatalog.GetNewsArtwork(index)}");
        }
    }

    [Fact]
    public void A_card_without_remote_thumbnail_still_has_local_artwork()
    {
        var cardType = typeof(PatchFeedCardViewModel);
        var constructor = cardType.GetConstructor([typeof(PatchFeedItem), typeof(int)]);
        constructor.Should().NotBeNull();
        var card = constructor!.Invoke([CreateItem(0), 0]);

        card.GetType().GetProperty("UsesRemoteThumbnail")!.GetValue(card).Should().Be(false);
        ((string)card.GetType().GetProperty("ArtworkPath")!.GetValue(card)!)
            .Should().Be($"{LauncherVisualAssetCatalog.Root}/{LauncherVisualAssetCatalog.News01}");
    }

    [Fact]
    public void Main_window_binds_news_artwork_without_removing_text_actions()
    {
        var xaml = File.ReadAllText(Path.Combine(
            AppContext.BaseDirectory,
            "..", "..", "..", "..", "..",
            "src", "CopiMineLauncher.App", "MainWindow.xaml"));

        xaml.Should().Contain("{Binding ArtworkPath}");
        xaml.Should().Contain("Stretch=\"UniformToFill\"");
        xaml.Should().Contain("{Binding Title}");
        xaml.Should().Contain("Content=\"Подробнее\"");
    }

    private static PatchFeedItem CreateItem(int index) => new(
        $"news-{index}",
        "1.0.1",
        $"Новость {index}",
        DateTimeOffset.UtcNow.AddMinutes(-index),
        new[] { "Короткое описание" },
        new Uri("https://copimine.ru/news/launcher.html"),
        null);
}
