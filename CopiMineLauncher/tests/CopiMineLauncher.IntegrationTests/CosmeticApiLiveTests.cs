using CopiMineLauncher.Infrastructure.Skins;
using Xunit;

namespace CopiMineLauncher.IntegrationTests;

public sealed class CosmeticApiLiveTests
{
    [LiveCosmeticsFact]
    public async Task Public_skin_profile_catalog_and_cape_index_are_reachable()
    {
        using var httpClient = new HttpClient { Timeout = TimeSpan.FromSeconds(30) };

        var skinPage = await new ElyByCatalogClient(httpClient)
            .GetPageAsync(new CosmeticCatalogQuery(Page: 1), CancellationToken.None);
        Assert.NotEmpty(skinPage.Items);

        var profile = await new PlayerCosmeticsClient(httpClient)
            .ResolveByNicknameAsync("Notch", CancellationToken.None);
        Assert.NotNull(profile);
        Assert.NotNull(profile!.SkinUrl);

        var capes = await new CapesDevClient(httpClient)
            .GetPlayerCapesAsync("Notch", CancellationToken.None);
        Assert.NotEmpty(capes);
    }

    private sealed class LiveCosmeticsFactAttribute : FactAttribute
    {
        public LiveCosmeticsFactAttribute()
        {
            if (!string.Equals(Environment.GetEnvironmentVariable("COPIMINE_COSMETICS_LIVE"), "1", StringComparison.Ordinal))
            {
                Skip = "Set COPIMINE_COSMETICS_LIVE=1 to run external skin/cape provider checks.";
            }
        }
    }
}
