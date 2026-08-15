namespace CopiMineLauncher.Core.Manifest;

public sealed record ManifestSignature(
    string Algorithm,
    string PublicKeyId,
    string SignatureBase64);

public sealed record SignatureVerificationResult(
    bool IsValid,
    string? ErrorCode = null)
{
    public static SignatureVerificationResult Valid() => new(true);

    public static SignatureVerificationResult Invalid(string code) => new(false, code);
}

public interface IManifestSignatureVerifier
{
    SignatureVerificationResult Verify(
        ReadOnlySpan<byte> manifestBytes,
        ReadOnlySpan<byte> signatureBytes,
        ReadOnlySpan<byte> publicKeyBytes);
}
