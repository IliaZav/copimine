using System.IO;
using FluentAssertions;
using CopiMineLauncher.Infrastructure.Binding;
using Xunit;

namespace CopiMineLauncher.App.Tests;

public sealed class LauncherProtocolSecurityTests
{
    [Fact]
    public void Custom_protocol_callback_rejects_userinfo_before_dispatch()
    {
        LauncherProtocolCallbackParser.TryParse(
            "copimine://attacker:secret@launcher/link?challenge=challenge-1234567890",
            out _).Should().BeFalse();
    }

    [Fact]
    public void Pending_binding_store_rejects_credentials_in_authorization_url()
    {
        using var temp = new TemporaryDirectory();
        var store = new LauncherBindingStateStore(temp.Path);
        var challenge = new LauncherLinkChallenge(
            "challenge-store-123456",
            "poll-store-abcdefghijklmnopqrstuvwxyz-123456",
            new Uri("https://attacker:secret@copimine.ru/cabinet/link.html"),
            DateTimeOffset.UtcNow.AddMinutes(5),
            "Player");

        Action action = () => store.SavePendingChallenge(challenge);

        action.Should().Throw<ArgumentException>();
    }

    private sealed class TemporaryDirectory : IDisposable
    {
        public TemporaryDirectory() => Path = Directory.CreateTempSubdirectory("copimine-protocol-security-").FullName;

        public string Path { get; }

        public void Dispose()
        {
            if (Directory.Exists(Path))
            {
                Directory.Delete(Path, recursive: true);
            }
        }
    }
}
