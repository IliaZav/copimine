using CopiMineLauncher.Core.News;
using FluentAssertions;
using Xunit;

namespace CopiMineLauncher.Core.Tests;

public sealed class PatchFeedTests
{
    [Fact]
    public void Valid_feed_parses_only_safe_news_links_and_keeps_valid_items()
    {
        var result = PatchFeedParser.Parse("""
        {
          "schemaVersion": 1,
          "patches": [
            {"id":"good","version":"1.0.0","title":"Good","publishedAt":"2026-08-15T12:00:00Z","summary":["One"],"detailUrl":"/news/good.html","thumbnailUrl":"/assets/patch-items/eternal_totem.webp"},
            {"id":"foreign","version":"1.0.0","title":"Foreign","publishedAt":"2026-08-15T12:00:00Z","summary":["One"],"detailUrl":"https://evil.example/news.html"}
          ]
        }
        """);

        result.IsDocumentValid.Should().BeTrue();
        result.Items.Should().ContainSingle();
        result.Items[0].DetailUrl.Should().Be(new Uri("https://copimine.ru/news/good.html"));
        result.Diagnostics.Should().ContainSingle().Which.Should().Contain("foreign");
    }

    [Fact]
    public void Malformed_item_is_skipped_without_invalidating_the_feed()
    {
        var result = PatchFeedParser.Parse("""
        {"schemaVersion":1,"patches":[
          {"id":"valid","version":"1.0.0","title":"Valid","publishedAt":"2026-08-15T12:00:00Z","summary":["One"],"detailUrl":"/news/valid.html"},
          {"id":"broken","version":"1.0.0","title":"Broken","publishedAt":"bad","summary":[],"detailUrl":"/news/broken.html"}
        ]}
        """);

        result.IsDocumentValid.Should().BeTrue();
        result.Items.Should().ContainSingle(item => item.Id == "valid");
        result.Diagnostics.Should().ContainSingle().Which.Should().Contain("broken");
    }

    [Fact]
    public void Feed_rejects_wrong_schema_and_does_not_accept_empty_data()
    {
        PatchFeedParser.Parse("{\"schemaVersion\":2,\"patches\":[]}").IsDocumentValid.Should().BeFalse();
        PatchFeedParser.Parse("{\"schemaVersion\":1,\"patches\":[]}").IsDocumentValid.Should().BeTrue();
    }
}
