using System.Text.RegularExpressions;
using CopiMineLauncher.Core.Filesystem;

namespace CopiMineLauncher.Core.Manifest;

public sealed record ManifestValidationError(string Code, string Message, string? Path = null);

public sealed class ManifestValidationResult
{
    public ManifestValidationResult(IReadOnlyList<ManifestValidationError> errors)
    {
        Errors = errors;
    }

    public IReadOnlyList<ManifestValidationError> Errors { get; }

    public IReadOnlyList<string> ErrorCodes => Errors.Select(error => error.Code).Distinct(StringComparer.Ordinal).ToArray();

    public bool IsValid => Errors.Count == 0;

    public override string ToString() => IsValid
        ? "valid"
        : string.Join("; ", Errors.Select(error => $"{error.Code}: {error.Message}"));
}

public sealed class ManifestValidator
{
    public const string RequiredMinecraftVersion = "1.21.1";
    public const string RequiredFabricLoaderVersion = "0.19.3";
    private const long MaximumFileSizeBytes = 4L * 1024 * 1024 * 1024;

    private static readonly Regex Sha256Pattern = new("^[0-9a-f]{64}$", RegexOptions.CultureInvariant);
    private readonly HashSet<string> allowedHosts;

    public ManifestValidator(IEnumerable<string>? allowedHosts = null)
    {
        this.allowedHosts = new HashSet<string>(
            allowedHosts ?? new[] { "copimine.ru", "www.copimine.ru", "cdn.copimine.ru" },
            StringComparer.OrdinalIgnoreCase);
    }

    public ManifestValidationResult Validate(LauncherManifest manifest, DateTimeOffset now, long? previousSequence = null)
    {
        ArgumentNullException.ThrowIfNull(manifest);
        var errors = new List<ManifestValidationError>();

        if (manifest.SchemaVersion != 1)
        {
            errors.Add(new("MANIFEST_SCHEMA_UNSUPPORTED", "schemaVersion must be 1"));
        }

        if (!string.Equals(manifest.Product, "CopiMineLauncher", StringComparison.Ordinal))
        {
            errors.Add(new("MANIFEST_PRODUCT_INVALID", "product must be CopiMineLauncher"));
        }

        if (manifest.Sequence <= 0)
        {
            errors.Add(new("MANIFEST_SEQUENCE_INVALID", "sequence must be positive"));
        }
        else if (previousSequence is not null && manifest.Sequence <= previousSequence.Value)
        {
            errors.Add(new("MANIFEST_SEQUENCE_ROLLBACK", "manifest sequence is not newer than the committed sequence"));
        }

        if (!string.Equals(manifest.MinecraftVersion, RequiredMinecraftVersion, StringComparison.Ordinal))
        {
            errors.Add(new("MINECRAFT_VERSION_UNSUPPORTED", $"minecraftVersion must be {RequiredMinecraftVersion}"));
        }

        if (!string.Equals(manifest.FabricLoaderVersion, RequiredFabricLoaderVersion, StringComparison.Ordinal))
        {
            errors.Add(new("FABRIC_LOADER_VERSION_UNSUPPORTED", $"fabricLoaderVersion must be {RequiredFabricLoaderVersion}"));
        }

        if (string.IsNullOrWhiteSpace(manifest.Channel) || string.IsNullOrWhiteSpace(manifest.LauncherVersion) || string.IsNullOrWhiteSpace(manifest.PublicKeyId))
        {
            errors.Add(new("MANIFEST_METADATA_MISSING", "channel, launcherVersion, and publicKeyId are required"));
        }

        if (manifest.IssuedAtUtc > now.AddMinutes(5))
        {
            errors.Add(new("MANIFEST_ISSUED_IN_FUTURE", "issuedAtUtc is too far in the future"));
        }

        if (manifest.ExpiresAtUtc is not null && manifest.ExpiresAtUtc <= now)
        {
            errors.Add(new("MANIFEST_EXPIRED", "manifest has expired"));
        }

        ValidateJava(manifest.JavaRuntime, errors);
        ValidateServer(manifest.Server, errors);
        ValidateFiles(manifest.Files, errors);

        return new ManifestValidationResult(errors);
    }

    private void ValidateFiles(IReadOnlyList<ManifestFileEntry>? files, ICollection<ManifestValidationError> errors)
    {
        if (files is null || files.Count == 0)
        {
            errors.Add(new("MANIFEST_FILES_MISSING", "files must contain at least one managed entry"));
            return;
        }

        var ids = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
        var paths = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
        for (var index = 0; index < files.Count; index++)
        {
            var entry = files[index];
            var prefix = $"files[{index}]";
            if (string.IsNullOrWhiteSpace(entry.ComponentId))
            {
                errors.Add(new("COMPONENT_ID_MISSING", "componentId is required", prefix));
            }
            else if (!ids.Add(entry.ComponentId))
            {
                errors.Add(new("DUPLICATE_COMPONENT_ID", "componentId is duplicated", prefix));
            }

            try
            {
                var normalizedPath = SafeRelativePath.Parse(entry.Path).Value;
                if (!paths.Add(normalizedPath))
                {
                    errors.Add(new("DUPLICATE_FILE_PATH", "path is duplicated", prefix));
                }
            }
            catch (ArgumentException exception)
            {
                errors.Add(new("FILE_PATH_INVALID", exception.Message, prefix));
            }

            if (string.IsNullOrWhiteSpace(entry.Kind) || string.IsNullOrWhiteSpace(entry.Version))
            {
                errors.Add(new("FILE_METADATA_MISSING", "kind and version are required", prefix));
            }

            ValidateUrl(entry.Url, "FILE_URL_INVALID", prefix, errors);
            ValidateHashAndSize(entry.Sha256, entry.SizeBytes, prefix, errors);
            if (!IsKnownOwnership(entry.Ownership))
            {
                errors.Add(new("OWNERSHIP_INVALID", "ownership must be managed, merge, or user", prefix));
            }
        }
    }

    private void ValidateJava(JavaRuntimeMetadata? java, ICollection<ManifestValidationError> errors)
    {
        if (java is null)
        {
            errors.Add(new("JAVA_RUNTIME_MISSING", "javaRuntime metadata is required"));
            return;
        }

        if (string.IsNullOrWhiteSpace(java.Version))
        {
            errors.Add(new("JAVA_VERSION_MISSING", "java runtime version is required"));
        }

        ValidateUrl(java.Url, "JAVA_URL_INVALID", "javaRuntime", errors);
        ValidateHashAndSize(java.Sha256, java.SizeBytes, "javaRuntime", errors);
    }

    private static void ValidateServer(ManifestServer? server, ICollection<ManifestValidationError> errors)
    {
        if (server is null || string.IsNullOrWhiteSpace(server.Address) || string.IsNullOrWhiteSpace(server.DisplayName) || server.Port is < 1 or > 65535)
        {
            errors.Add(new("SERVER_METADATA_INVALID", "server address, displayName, and a valid port are required"));
        }
    }

    private void ValidateUrl(string? value, string code, string path, ICollection<ManifestValidationError> errors)
    {
        if (!Uri.TryCreate(value, UriKind.Absolute, out var uri)
            || !string.Equals(uri.Scheme, Uri.UriSchemeHttps, StringComparison.OrdinalIgnoreCase)
            || string.IsNullOrEmpty(uri.Host)
            || uri.UserInfo.Length != 0
            || !IsAllowedHost(uri.Host))
        {
            errors.Add(new(code, "URL must be an HTTPS URL on an allowed CopiMine host", path));
        }
    }

    private bool IsAllowedHost(string host) => allowedHosts.Contains(host)
        || allowedHosts.Any(allowed => host.EndsWith("." + allowed, StringComparison.OrdinalIgnoreCase));

    private static void ValidateHashAndSize(string? hash, long size, string path, ICollection<ManifestValidationError> errors)
    {
        if (size <= 0 || size > MaximumFileSizeBytes)
        {
            errors.Add(new("FILE_SIZE_INVALID", "sizeBytes must be positive and within the maximum bound", path));
        }

        if (hash is null || !Sha256Pattern.IsMatch(hash))
        {
            errors.Add(new("FILE_SHA256_INVALID", "sha256 must be lowercase hexadecimal with 64 characters", path));
        }
    }

    private static bool IsKnownOwnership(string? ownership) => ownership is "managed" or "merge" or "user";
}
