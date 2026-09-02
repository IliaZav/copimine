using NSec.Cryptography;
using CopiMineLauncher.Core.Manifest;
using System.Security.Cryptography;

namespace CopiMineLauncher.Infrastructure.Manifest;

public sealed class Ed25519ManifestVerifier : IManifestSignatureVerifier
{
    private const int PublicKeyLength = 32;
    private const int SignatureLength = 64;

    public SignatureVerificationResult Verify(
        ReadOnlySpan<byte> manifestBytes,
        ReadOnlySpan<byte> signatureBytes,
        ReadOnlySpan<byte> publicKeyBytes)
    {
        if (signatureBytes.Length != SignatureLength)
        {
            return SignatureVerificationResult.Invalid("SIGNATURE_LENGTH_INVALID");
        }

        if (publicKeyBytes.Length != PublicKeyLength)
        {
            return SignatureVerificationResult.Invalid("PUBLIC_KEY_LENGTH_INVALID");
        }

        try
        {
            var algorithm = SignatureAlgorithm.Ed25519;
            var publicKey = PublicKey.Import(algorithm, publicKeyBytes.ToArray(), KeyBlobFormat.RawPublicKey);
            return algorithm.Verify(publicKey, manifestBytes, signatureBytes)
                ? SignatureVerificationResult.Valid()
                : SignatureVerificationResult.Invalid("SIGNATURE_INVALID");
        }
        catch (CryptographicException)
        {
            return SignatureVerificationResult.Invalid("SIGNATURE_INVALID");
        }
        catch (ArgumentException)
        {
            return SignatureVerificationResult.Invalid("SIGNATURE_INPUT_INVALID");
        }
    }
}
