using System.Security.Cryptography;

namespace CopiMineLauncher.Infrastructure.Skins;

public sealed class LocalCosmeticsStore
{
    private readonly string instanceRoot;
    private readonly string skinLibraryRoot;
    private readonly string capeLibraryRoot;
    private readonly string cacheRoot;

    public LocalCosmeticsStore(string instanceRoot, string launcherDataRoot)
    {
        this.instanceRoot = Path.GetFullPath(instanceRoot ?? throw new ArgumentNullException(nameof(instanceRoot)));
        var cosmeticsRoot = Path.Combine(Path.GetFullPath(launcherDataRoot ?? throw new ArgumentNullException(nameof(launcherDataRoot))), "cosmetics");
        skinLibraryRoot = Path.Combine(cosmeticsRoot, "skins");
        capeLibraryRoot = Path.Combine(cosmeticsRoot, "capes");
        cacheRoot = Path.Combine(cosmeticsRoot, "cache");
        _ = CustomSkinLoaderConfigService.EnsureLocalSkinPriority(this.instanceRoot);
    }

    public string GetInstalledPath(string playerName, CosmeticTextureKind kind)
    {
        ValidatePlayerName(playerName);
        return GetInstalledPath(playerName, kind, ".png");
    }

    public string GetLibraryPath(string playerName, CosmeticTextureKind kind, string extension = ".png")
    {
        ValidatePlayerName(playerName);
        var normalizedExtension = NormalizeExtension(extension);
        if (kind == CosmeticTextureKind.Skin && normalizedExtension != ".png")
        {
            throw new ArgumentException("Скин в библиотеке должен быть PNG-файлом.", nameof(extension));
        }

        var directory = kind == CosmeticTextureKind.Skin ? skinLibraryRoot : capeLibraryRoot;
        return Path.Combine(directory, playerName + normalizedExtension);
    }

    public string? FindLibraryPath(string playerName, CosmeticTextureKind kind)
    {
        ValidatePlayerName(playerName);
        var extensions = kind == CosmeticTextureKind.Cape
            ? new[] { ".gif", ".png" }
            : new[] { ".png" };
        return extensions
            .Select(extension => GetLibraryPath(playerName, kind, extension))
            .FirstOrDefault(File.Exists);
    }

    public string SaveToLibrary(string sourcePath, string playerName, CosmeticTextureKind kind)
    {
        var source = Path.GetFullPath(sourcePath ?? throw new ArgumentNullException(nameof(sourcePath)));
        _ = SkinTextureValidator.ValidateFile(source, kind);
        var extension = kind == CosmeticTextureKind.Cape && SkinTextureValidator.IsGifFile(source)
            ? ".gif"
            : ".png";
        var destination = GetLibraryPath(playerName, kind, extension);
        Directory.CreateDirectory(Path.GetDirectoryName(destination)!);
        CopyAtomically(source, destination);

        foreach (var siblingExtension in kind == CosmeticTextureKind.Cape ? new[] { ".png", ".gif" } : new[] { ".png" })
        {
            var sibling = GetLibraryPath(playerName, kind, siblingExtension);
            if (!string.Equals(sibling, destination, StringComparison.OrdinalIgnoreCase))
            {
                TryDeleteFile(sibling);
            }
        }

        return destination;
    }

    public string InstallFile(string sourcePath, string playerName, CosmeticTextureKind kind)
    {
        var source = SaveToLibrary(sourcePath, playerName, kind);
        if (kind == CosmeticTextureKind.Cape && SkinTextureValidator.IsGifFile(source))
        {
            throw new InvalidDataException("GIF-плащ сохранён в библиотеке, но для игры нужен PNG-кадр.");
        }

        return InstallPngFile(source, playerName, kind);
    }

    public string InstallPngFile(string sourcePath, string playerName, CosmeticTextureKind kind)
    {
        var source = Path.GetFullPath(sourcePath ?? throw new ArgumentNullException(nameof(sourcePath)));
        _ = SkinTextureValidator.ValidateFile(source, kind);
        if (SkinTextureValidator.IsGifFile(source))
        {
            throw new InvalidDataException("В игровой каталог можно установить только PNG-файл.");
        }

        var destination = GetInstalledPath(playerName, kind, ".png");
        Directory.CreateDirectory(Path.GetDirectoryName(destination)!);
        CopyAtomically(source, destination);
        foreach (var siblingExtension in kind == CosmeticTextureKind.Cape ? new[] { ".png", ".gif" } : new[] { ".png" })
        {
            var sibling = GetInstalledPath(playerName, kind, siblingExtension);
            if (!string.Equals(sibling, destination, StringComparison.OrdinalIgnoreCase))
            {
                TryDeleteFile(sibling);
            }
        }
        return destination;
    }

    public async Task<string> CacheRemoteAsync(HttpClient httpClient, Uri source, CosmeticTextureKind kind, CancellationToken cancellationToken)
    {
        ArgumentNullException.ThrowIfNull(httpClient);
        var normalized = CosmeticTextureSources.NormalizeOrThrow(source);
        Directory.CreateDirectory(cacheRoot);
        var key = Convert.ToHexString(SHA256.HashData(System.Text.Encoding.UTF8.GetBytes(normalized.AbsoluteUri))).ToLowerInvariant();
        foreach (var existingPath in new[] { Path.Combine(cacheRoot, key + ".gif"), Path.Combine(cacheRoot, key + ".png") })
        {
            if (!File.Exists(existingPath)) continue;
            try
            {
                var info = SkinTextureValidator.ValidateFile(existingPath, kind);
                var expectedPath = Path.Combine(cacheRoot, key + (SkinTextureValidator.IsGifFile(existingPath) ? ".gif" : ".png"));
                if (!string.Equals(existingPath, expectedPath, StringComparison.OrdinalIgnoreCase))
                {
                    File.Move(existingPath, expectedPath, overwrite: true);
                    return expectedPath;
                }

                _ = info;
                return existingPath;
            }
            catch (InvalidDataException)
            {
                TryDeleteFile(existingPath);
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

        var temporary = Path.Combine(cacheRoot, key + ".part");
        try
        {
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
            var cachedPath = Path.Combine(cacheRoot, key + (SkinTextureValidator.IsGifFile(temporary) ? ".gif" : ".png"));
            File.Move(temporary, cachedPath, overwrite: true);
            TryDeleteFile(Path.Combine(cacheRoot, key + (cachedPath.EndsWith(".gif", StringComparison.OrdinalIgnoreCase) ? ".png" : ".gif")));
            return cachedPath;
        }
        finally
        {
            TryDeleteFile(temporary);
        }
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

    private string GetInstalledPath(string playerName, CosmeticTextureKind kind, string extension)
    {
        var normalizedExtension = NormalizeExtension(extension);
        if (kind == CosmeticTextureKind.Skin && normalizedExtension != ".png")
        {
            throw new ArgumentException("Скин в игре должен быть PNG-файлом.", nameof(extension));
        }

        var bucket = kind == CosmeticTextureKind.Skin ? "skins" : "capes";
        return Path.Combine(instanceRoot, "CustomSkinLoader", "LocalSkin", bucket, playerName + normalizedExtension);
    }

    private static string NormalizeExtension(string extension)
    {
        var normalized = extension.StartsWith(".", StringComparison.Ordinal) ? extension : "." + extension;
        return normalized.ToLowerInvariant() switch
        {
            ".png" => ".png",
            ".gif" => ".gif",
            _ => throw new ArgumentException("Поддерживаются только расширения PNG и GIF.", nameof(extension))
        };
    }

    private static void CopyAtomically(string source, string destination)
    {
        var temporary = destination + ".part";
        try
        {
            File.Copy(source, temporary, overwrite: true);
            File.Move(temporary, destination, overwrite: true);
        }
        finally
        {
            TryDeleteFile(temporary);
        }
    }

    private static void TryDeleteFile(string path)
    {
        try
        {
            if (File.Exists(path)) File.Delete(path);
        }
        catch (IOException)
        {
        }
        catch (UnauthorizedAccessException)
        {
        }
    }
}
