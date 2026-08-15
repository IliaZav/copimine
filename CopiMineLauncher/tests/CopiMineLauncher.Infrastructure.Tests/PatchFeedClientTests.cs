using System.Net;
using System.Net.Http;
using System.Text;
using CopiMineLauncher.Infrastructure.News;
using FluentAssertions;
using Xunit;

namespace CopiMineLauncher.Infrastructure.Tests;

public sealed class PatchFeedClientTests
{
    [Fact]
    public async Task Valid_network_feed_is_returned_and_cached()
    {
        using var temp = new TemporaryDirectory();
        using var http = new HttpClient(new StaticHandler(ValidFeed())) { BaseAddress = new Uri("https://copimine.ru/") };
        var client = new PatchFeedClient(http, Path.Combine(temp.Path, "patch-feed.json"));

        var result = await client.GetLatestAsync(CancellationToken.None);

        result.FromCache.Should().BeFalse();
        result.Items.Should().ContainSingle();
        File.Exists(Path.Combine(temp.Path, "patch-feed.json")).Should().BeTrue();
    }

    [Fact]
    public async Task Network_failure_uses_last_valid_cache_without_blocking_launcher()
    {
        using var temp = new TemporaryDirectory();
        var cachePath = Path.Combine(temp.Path, "patch-feed.json");
        await File.WriteAllTextAsync(cachePath, ValidFeed());
        using var http = new HttpClient(new ThrowingHandler()) { BaseAddress = new Uri("https://copimine.ru/") };
        var client = new PatchFeedClient(http, cachePath);

        var result = await client.GetLatestAsync(CancellationToken.None);

        result.FromCache.Should().BeTrue();
        result.Items.Should().ContainSingle();
        result.Diagnostics.Should().Contain(item => item.Contains("CACHE_FALLBACK"));
    }

    [Fact]
    public async Task Invalid_cache_is_ignored_and_returns_empty_feed()
    {
        using var temp = new TemporaryDirectory();
        var cachePath = Path.Combine(temp.Path, "patch-feed.json");
        await File.WriteAllTextAsync(cachePath, "not-json");
        using var http = new HttpClient(new ThrowingHandler()) { BaseAddress = new Uri("https://copimine.ru/") };
        var client = new PatchFeedClient(http, cachePath);

        var result = await client.GetLatestAsync(CancellationToken.None);

        result.FromCache.Should().BeFalse();
        result.Items.Should().BeEmpty();
        result.Diagnostics.Should().Contain(item => item.Contains("CACHE_INVALID"));
    }

    private static string ValidFeed() => """
    {"schemaVersion":1,"patches":[{"id":"good","version":"1.0.0","title":"Good","publishedAt":"2026-08-15T12:00:00Z","summary":["One"],"detailUrl":"/news/good.html"}]}
    """;

    private sealed class StaticHandler(string payload) : HttpMessageHandler
    {
        protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken) =>
            Task.FromResult(new HttpResponseMessage(HttpStatusCode.OK) { Content = new StringContent(payload, Encoding.UTF8, "application/json") });
    }

    private sealed class ThrowingHandler : HttpMessageHandler
    {
        protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken) =>
            throw new HttpRequestException("offline");
    }

    private sealed class TemporaryDirectory : IDisposable
    {
        public TemporaryDirectory() => Path = Directory.CreateTempSubdirectory("copimine-patch-feed-tests-").FullName;
        public string Path { get; }
        public void Dispose() { if (Directory.Exists(Path)) Directory.Delete(Path, recursive: true); }
    }
}
