namespace CopiMineLauncher.Infrastructure.Skins;

public sealed record CosmeticCatalogQuery(
    int Page = 1,
    string Sort = "best",
    string Type = "any",
    string Tags = "",
    bool IncludeSensitive = false);

public sealed record CosmeticCatalogItem(
    string Id,
    Uri TextureUrl,
    bool IsSlim,
    IReadOnlyList<string> Tags,
    int Views,
    int Wearers,
    string Source);

public sealed record CosmeticCatalogPage(
    IReadOnlyList<CosmeticCatalogItem> Items,
    int Page,
    int PageCount,
    int TotalItems,
    bool HasNext,
    string Source,
    IReadOnlyList<string> Diagnostics);

public sealed record PlayerCosmeticsProfile(
    string PlayerName,
    Uri? SkinUrl,
    Uri? CapeUrl,
    bool IsSlim,
    string Source);

public sealed record CapeCatalogItem(
    string Type,
    Uri TextureUrl,
    string PlayerName,
    bool IsAnimated,
    string Source);

public static class CosmeticTextureSources
{
    private static readonly HashSet<string> AllowedHosts = new(StringComparer.OrdinalIgnoreCase)
    {
        "textures.minecraft.net",
        "api.capes.dev",
        "ely.by",
        "www.ely.by",
        "skinsystem.ely.by",
        "littleskin.cn",
        "littleskin.org",
        "copimine.ru",
        "www.copimine.ru"
    };

    public static bool TryNormalize(Uri? source, out Uri normalized)
    {
        normalized = null!;
        if (source is null
            || !AllowedHosts.Contains(source.Host)
            || !string.Equals(source.Scheme, Uri.UriSchemeHttps, StringComparison.OrdinalIgnoreCase)
                && !string.Equals(source.Scheme, Uri.UriSchemeHttp, StringComparison.OrdinalIgnoreCase))
        {
            return false;
        }

        var builder = new UriBuilder(source)
        {
            Scheme = Uri.UriSchemeHttps,
            Port = -1
        };
        normalized = builder.Uri;
        return true;
    }

    public static Uri NormalizeOrThrow(Uri source)
    {
        if (!TryNormalize(source, out var normalized))
        {
            throw new InvalidDataException($"Источник текстуры не разрешён: {source}");
        }

        return normalized;
    }
}
