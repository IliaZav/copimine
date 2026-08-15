using System.Security.Cryptography;

namespace CopiMineLauncher.Infrastructure.Skins;

public sealed class LocalCosmeticsStore
{
    private readonly string instanceRoot;
    private readonly string cacheRoot;

    public LocalCosmeticsStore(string instanceRoot, string launcherDataRoot)
    {
        this.instanceRoot = Path.GetFullPath(instanceRoot ?? throw new ArgumentNullException(nameof(instanceRoot)));
        cacheRoot = Path.Combine(Path.GetFullPath(launcherDataRoot ?? throw new ArgumentNullException(nameof(launcherDataRoot))), "cosmetics", "cache");
    }

    public string GetInstalledPath(string playerName, CosmeticTextureKind kind)
    {
        ValidatePlayerName(playerName);
        var bucket = kind == CosmeticTextureKind.Skin ? "skins" : "capes";
        return Path.Combine(instanceRoot, "CustomSkinLoader", "LocalSkin", bucket, playerName + ".png");
    }

    public string InstallFile(string sourcePath, string playerName, CosmeticTextureKind kind)
    {
        var source = Path.GetFullPath(sourcePath ?? throw new ArgumentNullException(nameof(sourcePath)));
        _ = SkinTextureValidator.ValidateFile(source, kind);
        var destination = GetInstalledPath(playerName, kind);
        Directory.CreateDirectory(Path.GetDirectoryName(destination)!);
        var temporary = destination + ".part";
        File.Copy(source, temporary, overwrite: true);
        File.Move(temporary, destination, overwrite: true);
        return destination;
    }

    public async Task<string> CacheRemoteAsync(HttpClient httpClient, Uri source, CosmeticTextureKind kind, CancellationToken cancellationToken)
    {
        ArgumentNullException.ThrowIfNull(httpClient);
        var normalized = CosmeticTextureSources.NormalizeOrThrow(source);
        Directory.CreateDirectory(cacheRoot);
        var key = Convert.ToHexString(SHA256.HashData(System.Text.Encoding.UTF8.GetBytes(normalized.AbsoluteUri))).ToLowerInvariant();
        var cachedPath = Path.Combine(cacheRoot, key + ".png");
        if (File.Exists(cachedPath))
        {
            try
            {
                _ = SkinTextureValidator.ValidateFile(cachedPath, kind);
                return cachedPath;
            }
            catch (InvalidDataException)
            {
                File.Delete(cachedPath);
            }
        }

        using var timeout = CancellationTokenSource.CreateLinkedTokenSource(cancellationToken);
        timeout.CancelAfter(TimeSpan.FromSeconds(20));
        using var response = await httpClient.GetAsync(normalized, HttpCompletionOption.ResponseHeadersRead, timeout.Token);
        response.EnsureSuccessStatusCode();
        if (response.Content.Headers.ContentLength > SkinTextureValidator.MaximumFileBytes)
        {
            throw new InvalidDataException("Удалённая текстура превышает безопасный размер.");
        }

        var temporary = cachedPath + ".part";
        await using (var input = await response.Content.ReadAsStreamAsync(timeout.Token))
        await using (var output = File.Create(temporary))
        {
            var buffer = new byte[32 * 1024];
            long total = 0;
            while (true)
            {
                var read = await input.ReadAsync(buffer, timeout.Token);
                if (read == 0) break;
                total += read;
                if (total > SkinTextureValidator.MaximumFileBytes)
                {
                    throw new InvalidDataException("Удалённая текстура превышает безопасный размер.");
                }

                await output.WriteAsync(buffer.AsMemory(0, read), timeout.Token);
            }
        }

        _ = SkinTextureValidator.ValidateFile(temporary, kind);
        File.Move(temporary, cachedPath, overwrite: true);
        return cachedPath;
    }

    public string InstallCached(string cachedPath, string playerName, CosmeticTextureKind kind) =>
        InstallFile(cachedPath, playerName, kind);

    private static void ValidatePlayerName(string playerName)
    {
        if (!PlayerCosmeticsClient.IsValidNickname(playerName))
        {
            throw new ArgumentException("Ник должен содержать 3–16 символов A–Z, 0–9 или _.", nameof(playerName));
        }
    }
}
