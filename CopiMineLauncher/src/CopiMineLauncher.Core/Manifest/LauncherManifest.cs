namespace CopiMineLauncher.Core.Manifest;

public sealed record LauncherManifest(
    int SchemaVersion,
    string Product,
    string Channel,
    long Sequence,
    string LauncherVersion,
    string MinecraftVersion,
    string FabricLoaderVersion,
    DateTimeOffset IssuedAtUtc,
    DateTimeOffset? ExpiresAtUtc,
    JavaRuntimeMetadata? JavaRuntime,
    IReadOnlyList<ManifestFileEntry> Files,
    ManifestServer Server,
    string PublicKeyId);

public sealed record JavaRuntimeMetadata(
    string Version,
    string Url,
    long SizeBytes,
    string Sha256);

public sealed record ManifestServer(
    string Address,
    int Port,
    string DisplayName);
