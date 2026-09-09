using System.Text;
using System.Text.Json;
using System.Text.Json.Nodes;

namespace CopiMineLauncher.Infrastructure.Skins;

/// <summary>
/// Keeps the launcher-managed LocalSkin provider ahead of public skin providers.
/// CustomSkinLoader stops at the first profile that returns a texture; leaving
/// Mojang/Ely.by before LocalSkin makes a selected launcher skin appear to be
/// ignored for offline and non-Mojang accounts.
/// </summary>
public static class CustomSkinLoaderConfigService
{
    private const string LocalSkinName = "LocalSkin";
    private const string ConfigFileName = "CustomSkinLoader.json";
    private const int AtomicReplaceAttempts = 6;

    private static readonly JsonSerializerOptions WriteOptions = new()
    {
        WriteIndented = true
    };

    public static string EnsureLocalSkinPriority(string instanceRoot)
    {
        ArgumentException.ThrowIfNullOrWhiteSpace(instanceRoot);

        var fullInstanceRoot = Path.GetFullPath(instanceRoot);
        var customSkinLoaderRoot = Path.Combine(fullInstanceRoot, "CustomSkinLoader");
        var configPath = Path.Combine(customSkinLoaderRoot, ConfigFileName);
        Directory.CreateDirectory(Path.Combine(customSkinLoaderRoot, "LocalSkin", "skins"));
        Directory.CreateDirectory(Path.Combine(customSkinLoaderRoot, "LocalSkin", "capes"));

        var root = LoadRoot(configPath);
        var existingLoaders = ReadLoaders(root);
        var preservedLoaders = existingLoaders
            .Where(loader => !string.Equals(ReadLoaderName(loader), LocalSkinName, StringComparison.OrdinalIgnoreCase))
            .ToArray();

        var loadlist = new JsonArray { CreateLocalSkinLoader() };
        if (preservedLoaders.Length == 0)
        {
            loadlist.Add(new JsonObject
            {
                ["name"] = "GameProfile",
                ["type"] = "GameProfile"
            });
            loadlist.Add(new JsonObject
            {
                ["name"] = "Mojang",
                ["type"] = "MojangAPI",
                ["apiRoot"] = "https://api.mojang.com/",
                ["sessionRoot"] = "https://sessionserver.mojang.com/"
            });
        }
        else
        {
            foreach (var loader in preservedLoaders)
            {
                loadlist.Add(loader);
            }
        }

        root["loadlist"] = loadlist;
        EnsureDefault(root, "version", "14.26.1");
        EnsureDefault(root, "buildNumber", 36);
        EnsureDefault(root, "enableDynamicSkull", true);
        EnsureDefault(root, "enableTransparentSkin", true);
        EnsureDefault(root, "forceLoadAllTextures", true);
        root["enableCape"] = true;
        EnsureDefault(root, "threadPoolSize", 8);
        EnsureDefault(root, "enableLogStdOut", false);
        EnsureDefault(root, "cacheExpiry", 30);
        EnsureDefault(root, "forceUpdateSkull", false);
        EnsureDefault(root, "enableLocalProfileCache", false);
        EnsureDefault(root, "enableCacheAutoClean", false);
        EnsureDefault(root, "forceDisableCache", false);

        WriteAtomically(configPath, root.ToJsonString(WriteOptions));
        return configPath;
    }

    private static JsonObject LoadRoot(string configPath)
    {
        if (!File.Exists(configPath))
        {
            return new JsonObject();
        }

        try
        {
            return JsonNode.Parse(File.ReadAllText(configPath)) as JsonObject ?? new JsonObject();
        }
        catch (JsonException)
        {
            return new JsonObject();
        }
        catch (IOException)
        {
            return new JsonObject();
        }
    }

    private static IReadOnlyList<JsonObject> ReadLoaders(JsonObject root)
    {
        if (root["loadlist"] is not JsonArray loadlist)
        {
            return Array.Empty<JsonObject>();
        }

        return loadlist
            .OfType<JsonObject>()
            .Select(loader => loader.DeepClone() as JsonObject)
            .Where(loader => loader is not null)
            .Cast<JsonObject>()
            .ToArray();
    }

    private static string? ReadLoaderName(JsonObject loader)
    {
        try
        {
            return loader["name"]?.GetValue<string>();
        }
        catch (InvalidOperationException)
        {
            return null;
        }
    }

    private static JsonObject CreateLocalSkinLoader() => new()
    {
        ["name"] = LocalSkinName,
        ["type"] = "Legacy",
        ["checkPNG"] = false,
        ["skin"] = "LocalSkin/skins/{USERNAME}.png",
        ["model"] = "auto",
        ["cape"] = "LocalSkin/capes/{USERNAME}.png"
    };

    private static void EnsureDefault<T>(JsonObject root, string key, T value)
    {
        if (root[key] is null)
        {
            root[key] = JsonValue.Create(value);
        }
    }

    private static void WriteAtomically(string path, string content)
    {
        // A fixed sidecar name makes two launcher operations (or two launcher
        // processes sharing an instance) overwrite each other's temp file.
        // Keep each transaction isolated, then replace the destination on the
        // same volume so the final config is never a half-written document.
        var temporary = $"{path}.{Guid.NewGuid():N}.part";
        try
        {
            File.WriteAllText(temporary, content + Environment.NewLine, new UTF8Encoding(encoderShouldEmitUTF8Identifier: false));
            for (var attempt = 1; ; attempt++)
            {
                try
                {
                    File.Move(temporary, path, overwrite: true);
                    break;
                }
                catch (IOException) when (attempt < AtomicReplaceAttempts)
                {
                    Thread.Sleep(TimeSpan.FromMilliseconds(20 * attempt));
                }
                catch (UnauthorizedAccessException) when (attempt < AtomicReplaceAttempts)
                {
                    Thread.Sleep(TimeSpan.FromMilliseconds(20 * attempt));
                }
            }
        }
        finally
        {
            if (File.Exists(temporary))
            {
                File.Delete(temporary);
            }
        }
    }
}
