using System.Net;
using System.Text.Json;

namespace CopiMineLauncher.Infrastructure.Skins;

public sealed class ElyByCatalogClient
{
    private static readonly Uri CatalogEndpoint = new("https://ely.by/skins/get?_url=%2Fskins", UriKind.Absolute);
    private readonly HttpClient httpClient;

    public ElyByCatalogClient(HttpClient httpClient)
    {
        this.httpClient = httpClient ?? throw new ArgumentNullException(nameof(httpClient));
    }

    public async Task<CosmeticCatalogPage> GetPageAsync(CosmeticCatalogQuery query, CancellationToken cancellationToken)
    {
        ArgumentNullException.ThrowIfNull(query);
        if (query.Page is < 1 or > 250)
        {
            throw new ArgumentOutOfRangeException(nameof(query.Page));
        }

        var form = new Dictionary<string, string>
        {
            ["skinsPage"] = query.Page.ToString(System.Globalization.CultureInfo.InvariantCulture),
            ["sort"] = query.Sort is "time" ? "time" : "best",
            ["type"] = query.Type is "old" or "new" or "slim" ? query.Type : "any",
            ["tags"] = query.Tags.Trim()
        };
        if (query.IncludeSensitive)
        {
            form["showSensitiveContent"] = "1";
        }

        using var timeout = CancellationTokenSource.CreateLinkedTokenSource(cancellationToken);
        timeout.CancelAfter(TimeSpan.FromSeconds(12));
        using var response = await httpClient.PostAsync(CatalogEndpoint, new FormUrlEncodedContent(form), timeout.Token);
        if (response.StatusCode == HttpStatusCode.NoContent)
        {
            return new(Array.Empty<CosmeticCatalogItem>(), query.Page, query.Page, 0, false, "Ely.by", ["ELYBY_CATALOG_EMPTY"]);
        }

        response.EnsureSuccessStatusCode();
        var payload = await response.Content.ReadAsStringAsync(timeout.Token);
        if (payload.Length > 4 * 1024 * 1024)
        {
            throw new InvalidDataException("Ответ каталога Ely.by превышает безопасный размер.");
        }

        using var document = JsonDocument.Parse(payload);
        var root = document.RootElement;
        var diagnostics = new List<string>();
        var items = new List<CosmeticCatalogItem>();
        if (root.TryGetProperty("items", out var itemArray) && itemArray.ValueKind == JsonValueKind.Array)
        {
            foreach (var item in itemArray.EnumerateArray())
            {
                if (!item.TryGetProperty("skin_url", out var urlElement)
                    || !Uri.TryCreate(urlElement.GetString(), UriKind.Absolute, out var rawUrl)
                    || !CosmeticTextureSources.TryNormalize(rawUrl, out var textureUrl))
                {
                    diagnostics.Add("ELYBY_ITEM_URL_REJECTED");
                    continue;
                }

                var id = item.TryGetProperty("id", out var idElement) ? idElement.ToString() : string.Empty;
                if (string.IsNullOrWhiteSpace(id))
                {
                    continue;
                }

                var tags = item.TryGetProperty("tags", out var tagsElement) && tagsElement.ValueKind == JsonValueKind.Array
                    ? tagsElement.EnumerateArray().Select(value => value.GetString() ?? string.Empty).Where(value => value.Length > 0).ToArray()
                    : Array.Empty<string>();
                var sensitive = item.TryGetProperty("is_sensitive", out var sensitiveElement) && sensitiveElement.ValueKind == JsonValueKind.True
                    || tags.Any(IsSensitiveTag);
                if (sensitive && !query.IncludeSensitive)
                {
                    continue;
                }

                items.Add(new(
                    id,
                    textureUrl,
                    item.TryGetProperty("is_slim", out var slimElement) && slimElement.ValueKind == JsonValueKind.True,
                    tags,
                    ReadInt(item, "count_views_total"),
                    ReadInt(item, "count_wearers"),
                    "Ely.by"));
            }
        }

        var currentPage = ReadInt(root, "current", query.Page);
        var lastPage = ReadInt(root, "last", currentPage);
        var total = ReadInt(root, "total_items", items.Count);
        return new(items, currentPage, lastPage, total, currentPage < lastPage, "Ely.by", diagnostics);
    }

    private static bool IsSensitiveTag(string tag)
    {
        var normalized = tag.Trim().ToLowerInvariant();
        return normalized.Contains("porn", StringComparison.Ordinal)
            || normalized.Contains("nsfw", StringComparison.Ordinal)
            || normalized.Contains("nude", StringComparison.Ordinal)
            || normalized.Contains("naked", StringComparison.Ordinal)
            || normalized.Contains("sex", StringComparison.Ordinal)
            || normalized.Contains("xxx", StringComparison.Ordinal)
            || normalized.Contains("hitler", StringComparison.Ordinal)
            || normalized.Contains("adolf", StringComparison.Ordinal)
            || normalized.Contains("fasc", StringComparison.Ordinal);
    }

    private static int ReadInt(JsonElement element, string property, int fallback = 0) =>
        element.TryGetProperty(property, out var value) && value.TryGetInt32(out var result) ? result : fallback;
}
