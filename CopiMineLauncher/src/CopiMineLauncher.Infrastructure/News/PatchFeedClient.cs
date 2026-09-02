using System.Net.Http;
using System.Text.Json;
using CopiMineLauncher.Core.News;

namespace CopiMineLauncher.Infrastructure.News;

public sealed record PatchFeedFetchResult(
    IReadOnlyList<PatchFeedItem> Items,
    IReadOnlyList<string> Diagnostics,
    bool FromCache)
{
    public bool IsAvailable => Items.Count > 0;
}

public interface IPatchFeedClient
{
    Task<PatchFeedFetchResult> GetLatestAsync(CancellationToken cancellationToken);
}

public sealed class PatchFeedClient : IPatchFeedClient
{
    private static readonly TimeSpan MaxCacheAge = TimeSpan.FromDays(7);
    private static readonly Uri FeedUri = new("https://copimine.ru/assets/public-data/patches/index.json", UriKind.Absolute);
    private readonly HttpClient httpClient;
    private readonly string cachePath;

    public PatchFeedClient(HttpClient httpClient, string cachePath)
    {
        this.httpClient = httpClient ?? throw new ArgumentNullException(nameof(httpClient));
        this.cachePath = Path.GetFullPath(cachePath ?? throw new ArgumentNullException(nameof(cachePath)));
    }

    public async Task<PatchFeedFetchResult> GetLatestAsync(CancellationToken cancellationToken)
    {
        using var timeout = CancellationTokenSource.CreateLinkedTokenSource(cancellationToken);
        timeout.CancelAfter(TimeSpan.FromMilliseconds(4500));
        try
        {
            using var response = await httpClient.GetAsync(FeedUri, HttpCompletionOption.ResponseHeadersRead, timeout.Token);
            response.EnsureSuccessStatusCode();
            var json = await response.Content.ReadAsStringAsync(timeout.Token);
            var parsed = PatchFeedParser.Parse(json);
            if (!parsed.IsDocumentValid)
            {
                return await FromCacheAsync(parsed.Diagnostics, cancellationToken);
            }

            await WriteCacheAsync(json, cancellationToken);
            return new(parsed.Items, parsed.Diagnostics, false);
        }
        catch (Exception exception) when (exception is HttpRequestException or IOException or JsonException or OperationCanceledException)
        {
            return await FromCacheAsync(new[] { "PATCH_FEED_NETWORK_UNAVAILABLE" }, cancellationToken);
        }
    }

    private async Task<PatchFeedFetchResult> FromCacheAsync(IReadOnlyList<string> diagnostics, CancellationToken cancellationToken)
    {
        if (!File.Exists(cachePath))
        {
            return new(Array.Empty<PatchFeedItem>(), diagnostics.Append("PATCH_FEED_CACHE_MISSING").ToArray(), false);
        }

        try
        {
            var cacheFile = new FileInfo(cachePath);
            if (DateTimeOffset.UtcNow - cacheFile.LastWriteTimeUtc > MaxCacheAge)
            {
                return new(Array.Empty<PatchFeedItem>(), diagnostics.Append("PATCH_FEED_CACHE_STALE").ToArray(), false);
            }

            var json = await File.ReadAllTextAsync(cachePath, cancellationToken);
            var parsed = PatchFeedParser.Parse(json);
            if (!parsed.IsDocumentValid)
            {
                return new(Array.Empty<PatchFeedItem>(), diagnostics.Concat(parsed.Diagnostics).Append("PATCH_FEED_CACHE_INVALID").ToArray(), false);
            }

            return new(parsed.Items, diagnostics.Concat(parsed.Diagnostics).Append("PATCH_FEED_CACHE_FALLBACK").ToArray(), true);
        }
        catch (Exception exception) when (exception is IOException or UnauthorizedAccessException or OperationCanceledException)
        {
            return new(Array.Empty<PatchFeedItem>(), diagnostics.Append("PATCH_FEED_CACHE_READ_FAILED").ToArray(), false);
        }
    }

    private async Task WriteCacheAsync(string json, CancellationToken cancellationToken)
    {
        var directory = Path.GetDirectoryName(cachePath);
        if (string.IsNullOrWhiteSpace(directory)) throw new InvalidOperationException("Patch feed cache path has no directory");
        Directory.CreateDirectory(directory);
        var temporary = cachePath + ".part";
        await File.WriteAllTextAsync(temporary, json, cancellationToken);
        File.Move(temporary, cachePath, overwrite: true);
    }
}
