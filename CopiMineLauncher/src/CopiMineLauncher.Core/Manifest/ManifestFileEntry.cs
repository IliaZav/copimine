namespace CopiMineLauncher.Core.Manifest;

public sealed record ManifestFileEntry(
    string ComponentId,
    string Path,
    string Kind,
    string Version,
    string Url,
    long SizeBytes,
    string Sha256,
    bool Required,
    string Ownership);
