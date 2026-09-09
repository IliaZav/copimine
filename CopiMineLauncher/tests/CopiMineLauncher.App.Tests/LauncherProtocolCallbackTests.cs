using CopiMineLauncher.Infrastructure.Binding;
using FluentAssertions;
using System.IO;
using Xunit;

namespace CopiMineLauncher.App.Tests;

public sealed class LauncherProtocolCallbackTests
{
    [Fact]
    public void Parses_only_the_expected_copimine_link_callback()
    {
        LauncherProtocolCallbackParser.TryParse(
            "copimine://launcher/link?challenge=challenge-1234567890",
            out var callback).Should().BeTrue();

        callback.ChallengeId.Should().Be("challenge-1234567890");
        LauncherProtocolCallbackParser.TryParse(
            "https://evil.example/link?challenge=challenge-1234567890",
            out _).Should().BeFalse();
        LauncherProtocolCallbackParser.TryParse(
            "copimine://launcher/link?challenge=bad value",
            out _).Should().BeFalse();
    }

    [Fact]
    public void Persists_a_pending_challenge_for_a_browser_callback_from_a_new_launcher_process()
    {
        var root = Path.Combine(Path.GetTempPath(), "copimine-launcher-callback-tests", Guid.NewGuid().ToString("N"));
        try
        {
            var store = new LauncherBindingStateStore(root);
            var challenge = new LauncherLinkChallenge(
                "challenge-1234567890",
                "poll-token-abcdefghijklmnopqrstuvwxyz-123456",
                new Uri("http://127.0.0.1:8090/cabinet/link.html?launcher_challenge=challenge-1234567890"),
                DateTimeOffset.UtcNow.AddMinutes(5),
                "SmokePlayer");

            store.SavePendingChallenge(challenge);
            var restored = store.LoadPendingChallenge();

            restored.Should().BeEquivalentTo(challenge);
            store.ClearPendingChallenge();
            store.LoadPendingChallenge().Should().BeNull();
        }
        finally
        {
            if (Directory.Exists(root)) Directory.Delete(root, recursive: true);
        }
    }
}
