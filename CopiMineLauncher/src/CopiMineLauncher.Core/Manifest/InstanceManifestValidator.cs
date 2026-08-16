using System.Text.RegularExpressions;
using CopiMineLauncher.Core.Filesystem;

namespace CopiMineLauncher.Core.Manifest;

public sealed class InstanceManifestValidator
{
    private const int MaximumFileCount = 512;
    private static readonly Regex VersionPattern = new("^[0-9A-Za-z][0-9A-Za-z._+-]{0,63}$", RegexOptions.CultureInvariant);
    private readonly ManifestValidator launcherManifestValidator;

    public InstanceManifestValidator(IEnumerable<string>? allowedHosts = null)
    {
        launcherManifestValidator = new ManifestValidator(allowedHosts);
    }

    public ManifestValidationResult Validate(
        InstanceManifestDocument document,
        DateTimeOffset now,
        long? previousReleaseSequence = null,
        string? signaturePublicKeyId = null)
    {
        ArgumentNullException.ThrowIfNull(document);
        var errors = new List<ManifestValidationError>();

        if (document.SchemaVersion != 2)
        {
            errors.Add(new("INSTANCE_MANIFEST_SCHEMA_UNSUPPORTED", "schemaVersion must be 2"));
        }

        if (string.IsNullOrWhiteSpace(document.Channel))
        {
            errors.Add(new("INSTANCE_MANIFEST_CHANNEL_MISSING", "channel is required"));
        }

        if (!IsSafeVersion(document.InstanceVersion))
        {
            errors.Add(new("RELEASE_ID_INVALID", "releaseId is not a bounded release identifier"));
        }

        if (!IsSafeVersion(document.MinimumLauncherVersion))
        {
            errors.Add(new("MINIMUM_LAUNCHER_VERSION_INVALID", "minimumLauncherVersion is not a bounded release identifier"));
        }

        if (document.PublishedAt > now.AddMinutes(5))
        {
            errors.Add(new("INSTANCE_MANIFEST_PUBLISHED_IN_FUTURE", "publishedAtUtc is too far in the future"));
        }

        if (document.ReleaseSequence <= 0)
        {
            errors.Add(new("RELEASE_SEQUENCE_INVALID", "releaseSequence must be positive"));
        }
        else if (previousReleaseSequence is not null && document.ReleaseSequence <= previousReleaseSequence.Value)
        {
            errors.Add(new("RELEASE_SEQUENCE_ROLLBACK", "releaseSequence is not newer than the committed sequence"));
        }

        if (document.Minecraft is null)
        {
            errors.Add(new("MINECRAFT_METADATA_MISSING", "minecraft metadata is required"));
        }
        else
        {
            if (!string.Equals(document.Minecraft.Version, ManifestValidator.RequiredMinecraftVersion, StringComparison.Ordinal))
            {
                errors.Add(new("MINECRAFT_VERSION_UNSUPPORTED", $"minecraft.version must be {ManifestValidator.RequiredMinecraftVersion}"));
            }

            if (!string.Equals(document.Minecraft.FabricLoader, ManifestValidator.RequiredFabricLoaderVersion, StringComparison.Ordinal))
            {
                errors.Add(new("FABRIC_LOADER_VERSION_UNSUPPORTED", $"minecraft.fabricLoaderVersion must be {ManifestValidator.RequiredFabricLoaderVersion}"));
            }

            if (document.Minecraft.JavaMajor != 21)
            {
                errors.Add(new("JAVA_MAJOR_UNSUPPORTED", "minecraft.javaMajor must be 21"));
            }
        }

        if (document.Server is null
            || string.IsNullOrWhiteSpace(document.Server.Name)
            || string.IsNullOrWhiteSpace(document.Server.Address)
            || document.Server.Port is < 1 or > 65535)
        {
            errors.Add(new("SERVER_METADATA_INVALID", "server name, address, and a valid port are required"));
        }

        if (!string.IsNullOrWhiteSpace(signaturePublicKeyId)
            && !string.IsNullOrWhiteSpace(document.PublicKeyId)
            && !string.Equals(signaturePublicKeyId, document.PublicKeyId, StringComparison.Ordinal))
        {
            errors.Add(new("PUBLIC_KEY_ID_MISMATCH", "manifest publicKeyId does not match the detached signature"));
        }

        if (!IsAllowedNewsUrl(document.NewsUrl))
        {
            errors.Add(new("NEWS_URL_INVALID", "newsUrl must be an HTTPS URL on an allowed host"));
        }

        if (document.JavaRuntime is null)
        {
            errors.Add(new("JAVA_RUNTIME_MISSING", "javaRuntime metadata is required for a self-contained instance"));
        }
        else
        {
            if (!string.Equals(document.JavaRuntime.Platform, "windows-x64", StringComparison.OrdinalIgnoreCase))
            {
                errors.Add(new("JAVA_PLATFORM_UNSUPPORTED", "javaRuntime.platform must be windows-x64"));
            }

            if (!IsSafeVersion(document.JavaRuntime.Version))
            {
                errors.Add(new("JAVA_VERSION_INVALID", "javaRuntime.version is invalid"));
            }
        }

        if (document.Files is null || document.Files.Count == 0)
        {
            errors.Add(new("INSTANCE_FILES_MISSING", "files must contain at least one managed entry"));
        }
        else if (document.Files.Count > MaximumFileCount)
        {
            errors.Add(new("INSTANCE_FILES_TOO_MANY", $"files cannot contain more than {MaximumFileCount} entries"));
        }

        ValidateFileEntries(document.Files, errors);

        if (errors.Count == 0)
        {
            var internalManifest = InstanceManifestAdapter.ToLauncherManifest(document, signaturePublicKeyId ?? document.PublicKeyId ?? "unknown");
            var internalResult = launcherManifestValidator.Validate(internalManifest, now, previousReleaseSequence);
            errors.AddRange(internalResult.Errors);
        }

        return new ManifestValidationResult(errors);
    }

    private void ValidateFileEntries(IReadOnlyList<InstanceManifestFile>? files, ICollection<ManifestValidationError> errors)
    {
        if (files is null)
        {
            return;
        }

        var componentIds = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
        var paths = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
        for (var index = 0; index < files.Count; index++)
        {
            var file = files[index];
            var prefix = $"files[{index}]";
            if (string.IsNullOrWhiteSpace(file.ComponentId) || !componentIds.Add(file.ComponentId))
            {
                errors.Add(new("DUPLICATE_OR_MISSING_COMPONENT_ID", "componentId must be present and unique", prefix));
            }

            try
            {
                var normalized = SafeRelativePath.Parse(file.Path).Value;
                if (!paths.Add(normalized))
                {
                    errors.Add(new("DUPLICATE_FILE_PATH", "path is duplicated ignoring case", prefix));
                }
            }
            catch (ArgumentException exception)
            {
                errors.Add(new("FILE_PATH_INVALID", exception.Message, prefix));
            }

            if (file.Ownership is not ("MANAGED" or "MERGE" or "USER"))
            {
                errors.Add(new("OWNERSHIP_INVALID", "ownership must be MANAGED, MERGE, or USER", prefix));
            }

            if (file.InstallPolicy is not ("ADD" or "REPLACE" or "PRESERVE"))
            {
                errors.Add(new("INSTALL_POLICY_INVALID", "installPolicy must be ADD, REPLACE, or PRESERVE", prefix));
            }
        }
    }

    private static bool IsSafeVersion(string? value) => !string.IsNullOrWhiteSpace(value) && VersionPattern.IsMatch(value);

    private static bool IsAllowedNewsUrl(string? value)
    {
        return Uri.TryCreate(value, UriKind.Absolute, out var uri)
            && string.Equals(uri.Scheme, Uri.UriSchemeHttps, StringComparison.OrdinalIgnoreCase)
            && string.IsNullOrEmpty(uri.UserInfo)
            && string.Equals(uri.Host, "copimine.ru", StringComparison.OrdinalIgnoreCase)
            && uri.AbsolutePath.StartsWith("/news", StringComparison.Ordinal);
    }
}
