using CopiMineLauncher.Core.Manifest;
using CopiMineLauncher.Infrastructure.Manifest;
using FluentAssertions;
using Xunit;

namespace CopiMineLauncher.Infrastructure.Tests;

public sealed class ManifestSignatureTests
{
    [Fact]
    public void Rfc8032_ed25519_signature_is_verified()
    {
        var message = Array.Empty<byte>();
        var publicKey = Convert.FromHexString("d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a");
        var signature = Convert.FromHexString("e5564300c360ac729086e2cc806e828a84877f1eb8e5d974d873e06522490155" +
            "5fb8821590a33bacc61e39701cf9b46bd25bf5f0595bbe24655141438e7a100b");

        var result = new Ed25519ManifestVerifier().Verify(message, signature, publicKey);

        result.IsValid.Should().BeTrue(result.ErrorCode);
    }

    [Fact]
    public void Mutating_manifest_bytes_or_signature_fails_verification()
    {
        var publicKey = Convert.FromHexString("d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a");
        var signature = Convert.FromHexString("e5564300c360ac729086e2cc806e828a84877f1eb8e5d974d873e06522490155" +
            "5fb8821590a33bacc61e39701cf9b46bd25bf5f0595bbe24655141438e7a100b");

        var changedMessage = new byte[] { 0x01 };
        var changedSignature = signature.ToArray();
        changedSignature[0] ^= 0x01;

        new Ed25519ManifestVerifier().Verify(changedMessage, signature, publicKey).IsValid.Should().BeFalse();
        new Ed25519ManifestVerifier().Verify(Array.Empty<byte>(), changedSignature, publicKey).IsValid.Should().BeFalse();
    }

    [Fact]
    public void Canonical_json_is_deterministic_and_rejects_duplicate_properties()
    {
        var first = CanonicalJson.Normalize("{\"b\":2,\"a\":1}"u8);
        var second = CanonicalJson.Normalize("{\"a\":1,\"b\":2}"u8);

        first.Should().Equal(second);
        var action = () => CanonicalJson.Normalize("{\"a\":1,\"a\":2}"u8);
        action.Should().Throw<FormatException>().Which.Message.Should().Contain("duplicate");
    }
}
