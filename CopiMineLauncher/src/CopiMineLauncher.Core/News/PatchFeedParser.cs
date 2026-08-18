using System.Text.Json;

namespace CopiMineLauncher.Core.News;

public sealed record PatchFeedItem(
    string Id,
    string Version,
    string Title,
    DateTimeOffset PublishedAt,
    IReadOnlyList<string> Summary,
    Uri DetailUrl,
    Uri? ThumbnailUrl);

public sealed record PatchFeedParseResult(
    bool IsDocumentValid,
    IReadOnlyList<PatchFeedItem> Items,
    IReadOnlyList<string> Diagnostics)
{
    public static PatchFeedParseResult Invalid(string diagnostic) => new(false, Array.Empty<PatchFeedItem>(), new[] { diagnostic });
}

public static class PatchFeedParser
{
    private static readonly Uri PublicBaseUri = new("https://copimine.ru/", UriKind.Absolute);
    private const int MaximumItems = 3;

    public static PatchFeedParseResult Parse(string json)
    {
        if (string.IsNullOrWhiteSpace(json))
        {
            return PatchFeedParseResult.Invalid("PATCH_FEED_EMPTY");
        }

        try
        {
            using var document = JsonDocument.Parse(json);
            var root = document.RootElement;
            if (root.ValueKind != JsonValueKind.Object
                || !root.TryGetProperty("schemaVersion", out var schema)
                || schema.ValueKind != JsonValueKind.Number
                || schema.GetInt32() != 1)
            {
                return PatchFeedParseResult.Invalid("PATCH_FEED_SCHEMA_INVALID");
            }

            if (!root.TryGetProperty("patches", out var patches) || patches.ValueKind != JsonValueKind.Array)
            {
                return PatchFeedParseResult.Invalid("PATCH_FEED_PATCHES_INVALID");
            }

            var items = new List<PatchFeedItem>();
            var diagnostics = new List<string>();
            var seenIds = new HashSet<string>(StringComparer.Ordinal);
            foreach (var raw in patches.EnumerateArray())
            {
                if (!TryParseItem(raw, seenIds, out var item, out var diagnostic))
                {
                    diagnostics.Add(diagnostic!);
                    continue;
                }

                items.Add(item!);
            }

            var ordered = items
                .OrderByDescending(item => item.PublishedAt)
                .ThenByDescending(item => item.Id, StringComparer.Ordinal)
                .Take(MaximumItems)
                .ToArray();
            return new(true, ordered, diagnostics);
        }
        catch (JsonException)
        {
            return PatchFeedParseResult.Invalid("PATCH_FEED_JSON_INVALID");
        }
        catch (Exception exception) when (exception is FormatException or OverflowException)
        {
            return PatchFeedParseResult.Invalid("PATCH_FEED_VALUE_INVALID");
        }
    }

    private static bool TryParseItem(JsonElement raw, HashSet<string> seenIds, out PatchFeedItem? item, out string? diagnostic)
    {
        item = null;
        diagnostic = null;
        if (raw.ValueKind != JsonValueKind.Object)
        {
            diagnostic = "PATCH_FEED_ITEM_INVALID";
            return false;
        }

        var id = ReadText(raw, "id");
        if (id is null)
        {
            diagnostic = "PATCH_FEED_ITEM_MISSING_ID";
            return false;
        }

        if (!seenIds.Add(id)) return Reject(id, "DUPLICATE_ID", out diagnostic);
        var version = ReadText(raw, "version");
        var title = ReadText(raw, "title");
        var dateText = ReadText(raw, "publishedAt");
        var detailText = ReadText(raw, "detailUrl");
        if (version is null || title is null || dateText is null || detailText is null) return Reject(id, "MISSING_REQUIRED_FIELD", out diagnostic);
        if (!DateTimeOffset.TryParse(dateText, out var publishedAt)) return Reject(id, "DATE_INVALID", out diagnostic);
        if (!TryAllowedUri(detailText, "/news/", out var detailUrl)) return Reject(id, "DETAIL_URL_NOT_ALLOWED", out diagnostic);
        if (!raw.TryGetProperty("summary", out var summaryElement) || summaryElement.ValueKind != JsonValueKind.Array) return Reject(id, "SUMMARY_INVALID", out diagnostic);

        var summary = new List<string>();
        foreach (var summaryValue in summaryElement.EnumerateArray())
        {
            if (summaryValue.ValueKind != JsonValueKind.String) return Reject(id, "SUMMARY_ENTRY_INVALID", out diagnostic);
            var value = summaryValue.GetString()?.Trim();
            if (string.IsNullOrWhiteSpace(value) || value.Length > 280) return Reject(id, "SUMMARY_ENTRY_INVALID", out diagnostic);
            summary.Add(value);
        }

        if (summary.Count is < 1 or > 3) return Reject(id, "SUMMARY_LENGTH_INVALID", out diagnostic);
        Uri? thumbnailUrl = null;
        var thumbnailText = ReadText(raw, "thumbnailUrl");
        if (thumbnailText is not null && !TryAllowedUri(thumbnailText, "/assets/", out thumbnailUrl)) return Reject(id, "THUMBNAIL_URL_NOT_ALLOWED", out diagnostic);
        item = new PatchFeedItem(id, version, title, publishedAt, summary, detailUrl!, thumbnailUrl);
        return true;
    }

    private static bool Reject(string id, string reason, out string? diagnostic)
    {
        diagnostic = $"{id}:{reason}";
        return false;
    }

    private static string? ReadText(JsonElement element, string property)
    {
        if (!element.TryGetProperty(property, out var value) || value.ValueKind != JsonValueKind.String) return null;
        var text = value.GetString()?.Trim();
        return string.IsNullOrWhiteSpace(text) ? null : text;
    }

    private static bool TryAllowedUri(string raw, string pathPrefix, out Uri? uri)
    {
        uri = null;
        if (!Uri.TryCreate(raw, UriKind.RelativeOrAbsolute, out var candidate)) return false;
        if (!candidate.IsAbsoluteUri)
        {
            if (!raw.StartsWith(pathPrefix, StringComparison.Ordinal) || raw.Contains("..", StringComparison.Ordinal)) return false;
            uri = new Uri(PublicBaseUri, raw);
            return true;
        }

        if (!string.Equals(candidate.Scheme, Uri.UriSchemeHttps, StringComparison.OrdinalIgnoreCase)
            || !string.Equals(candidate.Host, PublicBaseUri.Host, StringComparison.OrdinalIgnoreCase)
            || !candidate.AbsolutePath.StartsWith(pathPrefix, StringComparison.Ordinal)) return false;
        uri = candidate;
        return true;
    }
}
