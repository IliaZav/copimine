using System.Security.Cryptography;
using CopiMineLauncher.Core.Installation;
using FluentAssertions;
using Xunit;

namespace CopiMineLauncher.Core.Tests;

public sealed class MsiIntegrityVerifierTests
{
    [Fact]
    public async Task Accepts_a_file_when_size_and_sha256_match()
    {
        using var temp = new TemporaryFile("CopiMine MSI payload");
        var bytes = await File.ReadAllBytesAsync(temp.Path);

        var action = () => MsiIntegrityVerifier.VerifyFileAsync(
            temp.Path,
            bytes.LongLength,
            Convert.ToHexString(SHA256.HashData(bytes)),
            CancellationToken.None);

        await action.Should().NotThrowAsync();
    }

    [Fact]
    public async Task Rejects_a_file_when_size_is_not_the_published_size()
    {
        using var temp = new TemporaryFile("CopiMine MSI payload");
        var bytes = await File.ReadAllBytesAsync(temp.Path);

        var action = () => MsiIntegrityVerifier.VerifyFileAsync(
            temp.Path,
            bytes.LongLength + 1,
            Convert.ToHexString(SHA256.HashData(bytes)),
            CancellationToken.None);

        await action.Should().ThrowAsync<InvalidDataException>()
            .WithMessage("INSTALLER_MSI_SIZE_MISMATCH*");
    }

    [Fact]
    public async Task Rejects_a_file_when_content_does_not_match_the_published_hash()
    {
        using var temp = new TemporaryFile("CopiMine MSI payload");
        var bytes = await File.ReadAllBytesAsync(temp.Path);

        var action = () => MsiIntegrityVerifier.VerifyFileAsync(
            temp.Path,
            bytes.LongLength,
            new string('0', 64),
            CancellationToken.None);

        await action.Should().ThrowAsync<InvalidDataException>()
            .WithMessage("INSTALLER_MSI_HASH_MISMATCH*");
    }

    private sealed class TemporaryFile : IDisposable
    {
        public TemporaryFile(string content)
        {
            Path = System.IO.Path.Combine(System.IO.Path.GetTempPath(), $"copimine-installer-{Guid.NewGuid():N}.msi");
            File.WriteAllText(Path, content);
        }

        public string Path { get; }

        public void Dispose()
        {
            if (File.Exists(Path)) File.Delete(Path);
        }
    }
}
