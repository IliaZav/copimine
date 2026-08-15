using System.Text.Json.Serialization;

namespace CopiMineLauncher.Core.Manifest;

/// <summary>
/// The public instance-manifest wire contract. It deliberately remains separate
/// from the internal reconciler DTO so the published JSON can evolve without
/// silently changing update semantics.
/// </summary>
public sealed record InstanceManifestDocument(
    int SchemaVersion,
    string Channel,
    string InstanceVersion,
    DateTimeOffset PublishedAt,
    string MinimumLauncherVersion,
    InstanceMinecraft Minecraft,
    InstanceManifestServer Server,
    IReadOnlyList<InstanceManifestFile> Files,
    IReadOnlyList<InstanceConfigPolicy> ConfigPolicies,
    string NewsUrl,
    long ReleaseSequence,
    InstanceJavaRuntime? JavaRuntime = null,
    string? PublicKeyId = null);

public sealed record InstanceMinecraft(
    string Version,
    string FabricLoader,
    int JavaMajor);

public sealed record InstanceManifestServer(
    string Name,
    string Address,
    bool AcceptServerResourcePack,
    int Port = 25565);

public sealed record InstanceManifestFile(
    string ComponentId,
    string Path,
    string Url,
    string Sha256,
    [property: JsonPropertyName("size")] long SizeBytes,
    string Ownership,
    bool Required,
    string? Kind = null,
    string? Version = null,
    string InstallPolicy = "REPLACE");

public sealed record InstanceConfigPolicy(
    string Path,
    string Mode = "MERGE");

public sealed record InstanceJavaRuntime(
    string Provider,
    string BuildId,
    string Platform,
    string Version,
    string Url,
    long SizeBytes,
    string Sha256);

public static class InstanceManifestAdapter
{
    public static LauncherManifest ToLauncherManifest(
        InstanceManifestDocument document,
        string publicKeyId)
    {
        ArgumentNullException.ThrowIfNull(document);

        var java = document.JavaRuntime is null
            ? null
            : new JavaRuntimeMetadata(
                document.JavaRuntime.Version,
                document.JavaRuntime.Url,
                document.JavaRuntime.SizeBytes,
                document.JavaRuntime.Sha256);

        var files = document.Files.Select(file => new ManifestFileEntry(
            file.ComponentId,
            file.Path,
            string.IsNullOrWhiteSpace(file.Kind) ? "file" : file.Kind,
            string.IsNullOrWhiteSpace(file.Version) ? document.InstanceVersion : file.Version,
            file.Url,
            file.SizeBytes,
            file.Sha256,
            file.Required,
            file.Ownership.ToLowerInvariant())).ToArray();

        return new LauncherManifest(
            1,
            "CopiMineLauncher",
            document.Channel,
            document.ReleaseSequence,
            document.MinimumLauncherVersion,
            document.Minecraft.Version,
            document.Minecraft.FabricLoader,
            document.PublishedAt,
            null,
            java,
            files,
            new ManifestServer(document.Server.Address, document.Server.Port, document.Server.Name),
            publicKeyId);
    }
}
