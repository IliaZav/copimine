using System.Security.Cryptography;

namespace CopiMineLauncher.Core.Installation;

public static class MsiIntegrityVerifier
{
    public static async Task VerifyFileAsync(
        string path,
        long expectedSizeBytes,
        string expectedSha256,
        CancellationToken cancellationToken = default)
    {
        if (string.IsNullOrWhiteSpace(path)) throw new ArgumentException("MSI path is required.", nameof(path));
        if (expectedSizeBytes <= 0) throw new ArgumentOutOfRangeException(nameof(expectedSizeBytes));
        if (string.IsNullOrWhiteSpace(expectedSha256) || expectedSha256.Length != 64 || !expectedSha256.All(Uri.IsHexDigit))
        {
            throw new ArgumentException("Expected MSI SHA-256 must be a 64-character hexadecimal value.", nameof(expectedSha256));
        }

        var file = new FileInfo(path);
        if (!file.Exists)
        {
            throw new InvalidDataException("INSTALLER_MSI_MISSING: MSI was not downloaded.");
        }

        if (file.Length != expectedSizeBytes)
        {
            throw new InvalidDataException($"INSTALLER_MSI_SIZE_MISMATCH: expected {expectedSizeBytes} bytes, got {file.Length}.");
        }

        await using var stream = file.OpenRead();
        var actualSha256 = Convert.ToHexString(await SHA256.HashDataAsync(stream, cancellationToken));
        if (!string.Equals(actualSha256, expectedSha256, StringComparison.OrdinalIgnoreCase))
        {
            throw new InvalidDataException($"INSTALLER_MSI_HASH_MISMATCH: expected {expectedSha256}, got {actualSha256}.");
        }
    }
}
