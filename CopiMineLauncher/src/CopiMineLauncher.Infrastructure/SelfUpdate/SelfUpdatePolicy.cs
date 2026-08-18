using Velopack;

namespace CopiMineLauncher.Infrastructure.SelfUpdate;

public sealed record VerifiedSelfUpdate(
    string Product,
    string Channel,
    string Version,
    Uri FeedUri,
    Uri PackageUri,
    string PackageFileName,
    long SizeBytes,
    string Sha256,
    string? ReleaseNotes = null);

public sealed record SelfUpdateValidationResult(
    bool IsValid,
    string? ErrorCode = null,
    string? Diagnostic = null)
{
    public static SelfUpdateValidationResult Valid { get; } = new(true);

    public static SelfUpdateValidationResult Invalid(string errorCode, string diagnostic) =>
        new(false, errorCode, diagnostic);
}

public sealed class SelfUpdatePolicy
{
    private const long MaximumPackageSizeBytes = 4L * 1024 * 1024 * 1024;
    private readonly HashSet<string> allowedHosts;

    public SelfUpdatePolicy(IEnumerable<string>? allowedHosts = null)
    {
        this.allowedHosts = new HashSet<string>(
            allowedHosts ?? new[] { "copimine.ru", "www.copimine.ru", "cdn.copimine.ru" },
            StringComparer.OrdinalIgnoreCase);
    }

    public SelfUpdateValidationResult Validate(VerifiedSelfUpdate update, string currentVersion)
    {
        ArgumentNullException.ThrowIfNull(update);

        if (!string.Equals(update.Product, "CopiMineLauncher", StringComparison.Ordinal))
        {
            return SelfUpdateValidationResult.Invalid("SELF_UPDATE_PRODUCT_MISMATCH", "The update product is not CopiMineLauncher.");
        }

        if (string.IsNullOrWhiteSpace(update.Channel))
        {
            return SelfUpdateValidationResult.Invalid("SELF_UPDATE_CHANNEL_INVALID", "The update channel is empty.");
        }

        var feedValidation = ValidateUri(update.FeedUri, "feed");
        if (!feedValidation.IsValid)
        {
            return feedValidation;
        }

        var packageValidation = ValidateUri(update.PackageUri, "package");
        if (!packageValidation.IsValid)
        {
            return packageValidation;
        }

        if (string.IsNullOrWhiteSpace(update.PackageFileName)
            || !string.Equals(Path.GetFileName(update.PackageFileName), update.PackageFileName, StringComparison.Ordinal)
            || !update.PackageFileName.EndsWith(".nupkg", StringComparison.OrdinalIgnoreCase))
        {
            return SelfUpdateValidationResult.Invalid("SELF_UPDATE_PACKAGE_NAME_INVALID", "The Velopack package name is not a safe .nupkg file name.");
        }

        if (!Velopack.SemanticVersion.TryParse(update.Version, out var targetVersion)
            || !Velopack.SemanticVersion.TryParse(currentVersion, out var installedVersion))
        {
            return SelfUpdateValidationResult.Invalid("SELF_UPDATE_VERSION_INVALID", "The installed or target version is not valid Velopack semantic version syntax.");
        }

        if (targetVersion <= installedVersion)
        {
            return SelfUpdateValidationResult.Invalid("SELF_UPDATE_NOT_NEWER", $"Target {targetVersion} is not newer than installed {installedVersion}.");
        }

        if (update.SizeBytes <= 0 || update.SizeBytes > MaximumPackageSizeBytes)
        {
            return SelfUpdateValidationResult.Invalid("SELF_UPDATE_SIZE_INVALID", "The package size is outside the safe bound.");
        }

        if (update.Sha256.Length != 64 || update.Sha256.Any(character => !Uri.IsHexDigit(character))
            || !string.Equals(update.Sha256, update.Sha256.ToLowerInvariant(), StringComparison.Ordinal))
        {
            return SelfUpdateValidationResult.Invalid("SELF_UPDATE_HASH_INVALID", "The package SHA-256 must be a lowercase 64-character hexadecimal value.");
        }

        return SelfUpdateValidationResult.Valid;
    }

    private SelfUpdateValidationResult ValidateUri(Uri uri, string kind)
    {
        if (uri is null
            || !uri.IsAbsoluteUri
            || !string.Equals(uri.Scheme, Uri.UriSchemeHttps, StringComparison.OrdinalIgnoreCase)
            || !string.IsNullOrEmpty(uri.UserInfo)
            || (uri.Port != 443 && uri.Port != -1)
            || !IsAllowedHost(uri.Host))
        {
            return SelfUpdateValidationResult.Invalid("SELF_UPDATE_SOURCE_NOT_ALLOWED", $"The {kind} endpoint must be HTTPS on an allowlisted CopiMine host.");
        }

        return SelfUpdateValidationResult.Valid;
    }

    private bool IsAllowedHost(string host) => allowedHosts.Contains(host)
        || allowedHosts.Any(allowed => host.EndsWith("." + allowed, StringComparison.OrdinalIgnoreCase));
}
